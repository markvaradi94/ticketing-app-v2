# Ticketing App

The reference project for a 10-session course on building and deploying a distributed system with Java and Spring Boot: MongoDB, RabbitMQ, an API gateway, Docker, Cloud Run, and GitHub Actions.

Students build the project in their own repository, coding along session by session. Each session has one tag here, `session-NN`, with the finished state of that session: use it as the reference, or continue from `session-(N-1)` if you fell behind.

## The system

Three services, each its own Gradle project and image:

| Service | Does | Port |
| --- | --- | --- |
| `api-gateway` | the single entry point: routing, a per-client rate limit, the correlation id | 8080 |
| `ticketing-service` | events, bookings, reviews; publishes `BookingConfirmed` | 8081 |
| `notification-service` | consumes `BookingConfirmed`, stores one notification per booking | 8082 |

MongoDB stores the data and RabbitMQ carries the events: locally in Docker, in the cloud on MongoDB Atlas and CloudAMQP.

## Run it

```bash
./gradlew build                 # all three projects, with their tests (needs Docker for Testcontainers)
docker compose up --build       # the whole system; the gateway answers on http://localhost:8080
```

`http/ticketing.postman_collection.json` exercises the API through the gateway.

## Deploy it

- `deploy/cloud-run.md`: the services on Google Cloud Run, configured with Secret Manager, with only the gateway public.
- `deploy/github-actions.md`: the pipeline. A green build on `main` deploys itself through Workload Identity Federation, with no keys.
- `deploy/rollback.md`: rolling back to a previous revision.
