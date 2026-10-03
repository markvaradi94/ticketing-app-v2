# Deploying to Cloud Run

The commands that put the three services on Cloud Run. Run them from the repository root, in Git Bash or another POSIX shell. No value in this file belongs to a real project: set the variables first.

> **Git Bash on Windows rewrites arguments that look like Unix paths.** `httpGet.path=/actuator/health/readiness` reaches Cloud Run as `C:/Program Files/Git/actuator/...`, and the startup probe fails after two minutes. Prefix those commands with `MSYS2_ARG_CONV_EXCL="httpGet.path="`, as below. Don't set `MSYS_NO_PATHCONV=1`: it breaks the `gcloud` launcher itself.

```bash
export PROJECT_ID=your-project-id
export REGION=europe-west1
export REPO=$REGION-docker.pkg.dev/$PROJECT_ID/ticketing
gcloud config set project $PROJECT_ID
gcloud config set run/region $REGION
```

## 1. The APIs

A new project needs these APIs enabled once. Enabling one that is already on does nothing, so run the whole list.

| API | What it's for | Session |
| --- | --- | --- |
| `run.googleapis.com` | Cloud Run: the services and their revisions | 9 |
| `artifactregistry.googleapis.com` | Artifact Registry: the images | 9 |
| `secretmanager.googleapis.com` | Secret Manager: the Atlas and CloudAMQP URLs | 9 |
| `iam.googleapis.com` | service accounts and their roles | 9 |
| `logging.googleapis.com` | Cloud Logging: the services' logs (on by default) | 9 |
| `iamcredentials.googleapis.com` | short-lived credentials for service accounts (Workload Identity Federation) | 10 |
| `sts.googleapis.com` | Security Token Service: GitHub's token exchanged for a Google one | 10 |

```bash
gcloud services enable run.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com \
    iam.googleapis.com logging.googleapis.com iamcredentials.googleapis.com sts.googleapis.com
```

## 2. The image repository

```bash
gcloud artifacts repositories create ticketing --repository-format=docker --location=$REGION
gcloud auth configure-docker $REGION-docker.pkg.dev
```

## 3. The secrets

Each secret's value comes from an environment variable you already have, so it is never typed on the command line or kept in your shell history. `--data-file=-` reads it from the pipe.

```bash
printf '%s' "$MONGODB_URI" | gcloud secrets create mongodb-uri --data-file=- --replication-policy=automatic
printf '%s' "$CLOUDAMQP_URL" | gcloud secrets create cloudamqp-url --data-file=- --replication-policy=automatic
gcloud secrets list                      # names only; never print the values
```

## 4. One service account per service

Each service runs with its own identity and only the roles it needs. The backends may read the two secrets; the gateway gets permission to call the backends once they exist (section 6).

```bash
gcloud iam service-accounts create ticketing-run --display-name="ticketing-service on Cloud Run"
gcloud iam service-accounts create notification-run --display-name="notification-service on Cloud Run"
gcloud iam service-accounts create gateway-run --display-name="api-gateway on Cloud Run"

for secret in mongodb-uri cloudamqp-url; do
  for sa in ticketing-run notification-run; do
    gcloud secrets add-iam-policy-binding $secret \
        --member="serviceAccount:$sa@$PROJECT_ID.iam.gserviceaccount.com" \
        --role=roles/secretmanager.secretAccessor
  done
done
```

## 5. The images

Build the three images from the repository root and push them, tagged with the session. Session 10's pipeline tags them with the commit SHA instead.

```bash
for service in ticketing-service notification-service api-gateway; do
  docker build -f $service/Dockerfile -t $REPO/$service:session-09 .
  docker push $REPO/$service:session-09
done
```

## 6. The backends, private

Neither backend accepts unauthenticated calls (`--no-allow-unauthenticated`): only the gateway will call them. Both get the secrets as the same environment variables as `compose.cloud.yaml`, and a startup probe on Actuator's readiness.

`notification-service` consumes RabbitMQ and gets no HTTP traffic. By default Cloud Run gives an instance CPU only during a request and scales to zero, so it would never consume. It keeps one instance running with CPU always allocated (`--no-cpu-throttling --min-instances 1`). That instance costs money for as long as it runs: see section 8.

```bash
MSYS2_ARG_CONV_EXCL="httpGet.path=" gcloud run deploy notification-service \
    --image $REPO/notification-service:session-09 \
    --service-account notification-run@$PROJECT_ID.iam.gserviceaccount.com \
    --no-allow-unauthenticated --port 8082 --cpu 1 --memory 512Mi \
    --no-cpu-throttling --min-instances 1 --max-instances 1 \
    --set-secrets SPRING_MONGODB_URI=mongodb-uri:latest,SPRING_RABBITMQ_ADDRESSES=cloudamqp-url:latest \
    --set-env-vars SPRING_MONGODB_DATABASE=notifications \
    --startup-probe httpGet.path=/actuator/health/readiness,httpGet.port=8082,periodSeconds=5,timeoutSeconds=3,failureThreshold=24

MSYS2_ARG_CONV_EXCL="httpGet.path=" gcloud run deploy ticketing-service \
    --image $REPO/ticketing-service:session-09 \
    --service-account ticketing-run@$PROJECT_ID.iam.gserviceaccount.com \
    --no-allow-unauthenticated --port 8081 --cpu 1 --memory 512Mi \
    --min-instances 0 --max-instances 2 \
    --set-secrets SPRING_MONGODB_URI=mongodb-uri:latest,SPRING_RABBITMQ_ADDRESSES=cloudamqp-url:latest \
    --set-env-vars SPRING_MONGODB_DATABASE=ticketing,SPRING_PROFILES_ACTIVE=dev \
    --startup-probe httpGet.path=/actuator/health/readiness,httpGet.port=8081,periodSeconds=5,timeoutSeconds=3,failureThreshold=24
```

Check them: without credentials they answer 403, and with your identity token they answer.

```bash
TICKETING_URL=$(gcloud run services describe ticketing-service --format='value(status.url)')
curl -i $TICKETING_URL/events                                                        # 403
curl -H "Authorization: Bearer $(gcloud auth print-identity-token)" $TICKETING_URL/events   # 200
gcloud run services logs read notification-service --limit 20                       # started, connected
```

## 7. The gateway, the only public URL

The gateway runs as `gateway-run`, which may call both backends (`roles/run.invoker`). With `GATEWAY_CLOUD_RUN_AUTH_ENABLED=true` it asks Cloud Run's metadata server for an ID token for each backend's URL and sends it as `Authorization: Bearer ...` (`IdentityTokens`). Any `Authorization` header from the client is replaced, never forwarded.

```bash
for service in ticketing-service notification-service; do
  gcloud run services add-iam-policy-binding $service \
      --member="serviceAccount:gateway-run@$PROJECT_ID.iam.gserviceaccount.com" --role=roles/run.invoker
done

TICKETING_URL=$(gcloud run services describe ticketing-service --format='value(status.url)')
NOTIFICATIONS_URL=$(gcloud run services describe notification-service --format='value(status.url)')

MSYS2_ARG_CONV_EXCL="httpGet.path=" gcloud run deploy api-gateway \
    --image $REPO/api-gateway:session-09 \
    --service-account gateway-run@$PROJECT_ID.iam.gserviceaccount.com \
    --allow-unauthenticated --port 8080 --cpu 1 --memory 512Mi \
    --min-instances 0 --max-instances 2 \
    --set-env-vars GATEWAY_TICKETING_URI=$TICKETING_URL,GATEWAY_NOTIFICATIONS_URI=$NOTIFICATIONS_URL,GATEWAY_CLOUD_RUN_AUTH_ENABLED=true \
    --startup-probe httpGet.path=/actuator/health/readiness,httpGet.port=8080,periodSeconds=5,timeoutSeconds=3,failureThreshold=24
```

> **New IAM bindings take a minute or two to apply.** Right after the `add-iam-policy-binding` calls, the gateway's requests can still get 403 from the backends. Wait, then try again; don't start changing the configuration.

Check it end to end: the Postman collection against the public URL, and the booking's notification through the same URL.

```bash
GATEWAY_URL=$(gcloud run services describe api-gateway --format='value(status.url)')
npx newman run http/ticketing.postman_collection.json --env-var baseUrl=$GATEWAY_URL
curl $GATEWAY_URL/notifications
```

## 8. Costs: scale down when you're done

`notification-service` is the only service that runs when nobody uses it: about $0.07 an hour (1 vCPU, 512 MiB, CPU always allocated). The others scale to zero by themselves. When you're done, scale it to zero; scale it up again before the next demo.

```bash
gcloud run services update notification-service --min-instances 0      # done for today
gcloud run services update notification-service --min-instances 1      # before the next demo
```

To remove everything at the end of the course: delete the three services, the repository and the two secrets.

```bash
gcloud run services delete api-gateway --quiet
gcloud run services delete ticketing-service --quiet
gcloud run services delete notification-service --quiet
gcloud artifacts repositories delete ticketing --location=$REGION --quiet
gcloud secrets delete mongodb-uri --quiet
gcloud secrets delete cloudamqp-url --quiet
```
