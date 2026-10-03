# Deploying to Cloud Run

The commands that put the three services on Cloud Run. Run them from the repository root, in Git Bash or another POSIX shell. No value in this file belongs to a real project: set the variables first.

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
