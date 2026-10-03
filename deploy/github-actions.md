# Deploying from GitHub Actions

How a push to `main` deploys the three services, with no service-account key anywhere. GitHub Actions gets a short-lived OIDC token for each run, and Google exchanges it for credentials of a deployer account, but only for this repository: that's Workload Identity Federation. Run the set-up once, after `deploy/cloud-run.md`; the pipeline itself is `.github/workflows/build.yml`.

```bash
export PROJECT_ID=your-project-id
export PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')
export REGION=europe-west1
export GITHUB_REPO=your-github-user/your-repository        # owner/name, exactly as on GitHub
export DEPLOYER=github-deployer@$PROJECT_ID.iam.gserviceaccount.com
```

## 1. The pool and the provider

The provider trusts GitHub's tokens, but only from your repository (`--attribute-condition`). Without the condition, any GitHub repository could try to use your pool.

```bash
gcloud iam workload-identity-pools create github --location=global --display-name="GitHub Actions"

gcloud iam workload-identity-pools providers create-oidc your-provider-name \
    --location=global --workload-identity-pool=github \
    --issuer-uri=https://token.actions.githubusercontent.com \
    --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
    --attribute-condition="assertion.repository=='$GITHUB_REPO'"
```

## 2. The deployer account and its three roles

Only what deploying needs: push images to this one repository, update Cloud Run services, and run them as the three runtime accounts.

```bash
gcloud iam service-accounts create github-deployer --display-name="GitHub Actions deployer"

gcloud artifacts repositories add-iam-policy-binding ticketing --location=$REGION \
    --member=serviceAccount:$DEPLOYER --role=roles/artifactregistry.writer

gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member=serviceAccount:$DEPLOYER --role=roles/run.developer --condition=None

for sa in ticketing-run notification-run gateway-run; do
  gcloud iam service-accounts add-iam-policy-binding $sa@$PROJECT_ID.iam.gserviceaccount.com \
      --member=serviceAccount:$DEPLOYER --role=roles/iam.serviceAccountUser
done
```

## 3. Only your repository may act as the deployer

```bash
gcloud iam service-accounts add-iam-policy-binding $DEPLOYER \
    --role=roles/iam.workloadIdentityUser \
    --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/$GITHUB_REPO"
```

Check: the deployer has **no keys**, and that's how it stays.

```bash
gcloud iam service-accounts keys list --iam-account $DEPLOYER --managed-by user    # empty
```

## 4. The repository's variables

The workflow reads four identifiers from GitHub Actions **variables**. They aren't secrets: they name things, they don't unlock them. They are printed in the run logs, which are public on a public repository. Secret values never go into a log.

```bash
gh variable set GCP_PROJECT_ID   --body $PROJECT_ID --repo $GITHUB_REPO
gh variable set GCP_REGION       --body $REGION --repo $GITHUB_REPO
gh variable set GCP_WIF_PROVIDER --body projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/providers/your-provider-name --repo $GITHUB_REPO
gh variable set GCP_DEPLOYER     --body $DEPLOYER --repo $GITHUB_REPO
```

## 5. The pipeline

`.github/workflows/build.yml` has two jobs:

- `build` runs on every push and pull request: the Gradle build with all tests, then the three images.
- `deploy` runs after `build` passes, only on `main` (or when started by hand). Steps:
  1. it authenticates with Workload Identity Federation;
  2. it builds the three images tagged with the commit SHA and pushes them;
  3. it gives each Cloud Run service the new image;
  4. it sends traffic to the new revisions;
  5. it smoke-tests the gateway's public URL.

Only the deploy job may request an OIDC token (`id-token: write`); everything else reads the repository and nothing more.

To start a deploy by hand: GitHub → Actions → build → Run workflow, or `gh workflow run build.yml --ref main`.
