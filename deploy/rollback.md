# Rolling back a bad deploy

Every deploy creates a new Cloud Run revision, and a revision never changes: it keeps the exact image (tagged with the commit SHA) and configuration it was created with. Rolling back doesn't rebuild anything. It sends the traffic back to a revision that worked.

## 1. Find the revision to go back to

```bash
gcloud run revisions list --service api-gateway --region $REGION
```

The newest is first. Pick the last one that worked: usually the second. Its image tag tells you the commit it runs:

```bash
gcloud run revisions describe <revision> --region $REGION --format='value(spec.containers[0].image)'
```

## 2. Send all traffic to it

```bash
gcloud run services update-traffic api-gateway --to-revisions <revision>=100 --region $REGION
```

Check it, then check the system:

```bash
gcloud run services describe api-gateway --region $REGION --format='value(status.traffic)'
curl -fsS $GATEWAY_URL/actuator/health
```

Roll back only the service that broke. The services don't depend on each other's revision numbers, only on the API and the message they share.

## 3. Fix forward, and know what the next deploy does

While traffic is pinned to an old revision, **a new deploy gets no traffic.** The new revision is created, but the pin stays. (Checked on Cloud Run: after a rollback, a new revision of `api-gateway` stayed at 0 % until `--to-latest`.)

That's why the pipeline ends every deploy with:

```bash
gcloud run services update-traffic <service> --to-latest
```

So the normal way out of a rollback is: fix the code, push to `main`, and let the pipeline deploy it and move traffic to the newest revision. To undo the rollback by hand without a new deploy, run that same `--to-latest` command.
