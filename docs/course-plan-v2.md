# Course plan v2 — decisions and intent

Oct 2, 2026 · Mark Varadi

This file holds the decisions made when rewriting the course. It is the input for the course spec (`bmad-spec`), not the spec itself. `docs/reference/course-plan-v1.md` is the previous plan, kept for the curriculum mapping and lessons learned. Where the two disagree, this file wins.

## Why a rewrite

The v1 course (repo `java-course-v1`) was too much content for 10 sessions. Several sessions covered three or four concepts at once (Session 7: REST resilience, RabbitMQ, idempotency, DLQ, tracing and virtual threads). Sessions 4–6 were three back-to-back architecture sessions (Spring Modulith, hexagonal, DDD). Sessions 8–10 spent most of their time on GCP and Kubernetes setup. The system grew to more than 60 classes in one service, and students mostly fixed planted bugs inside a codebase they had not written.

The model for v2 is the FastTrackIT fullstack01 course: one concept per session, small code, students write the lab code themselves, and homework is a short spec for a *different* small app that uses the same concept.

## Course facts

|  |  |
| --- | --- |
| Duration | 30 hours, 10 sessions of 3 hours, twice a week over 5 weeks |
| Audience | Developers who already work with Java |
| Focus | Building a system with Java and Spring; MongoDB; RabbitMQ; proper containerization; CI/CD; connecting to cloud platforms |
| Out of scope | Frontend, authentication, Kubernetes/GKE, Spring Modulith, ArchUnit, full hexagonal/DDD treatment, Resilience4j, distributed tracing stack |
| Cost constraint | Everything students use must be free |
| Status | Rewrite before the course's first run |

### Session template

40 min theory · 40 min live coding · 15 min break · 70 min lab · 15 min wrap-up.

## Teaching principles

1. **One concept per session.** If a session needs two headline concepts, one of them moves.
2. **Students write the lab code.** No planted-bug archaeology in a large codebase. The starter gives them only what they should not have to type.
3. **Simplest version that lands the lesson.** Early sessions use plain shapes; rigor is added only where it is the lesson.
4. **Homework transfers the concept.** A one-page spec for a different small app (library, gym, products), capped at about an hour.
5. **One reference project at the end.** The live-coding thread builds one system across all 10 sessions; it is the reference implementation students keep.

## The reference project

Domain: event ticketing (events, bookings, reviews). Package root: `ro.fasttrackit.ticketing`.

### Final shape: three deployables

| Service | Contains | Talks to |
| --- | --- | --- |
| `api-gateway` | Spring Cloud Gateway: routing, single public entry point, a correlation-id filter | `ticketing-service`, `notification-service` |
| `ticketing-service` | events, bookings, reviews | MongoDB, RabbitMQ (publish) |
| `notification-service` | consumes booking events, stores notifications, exposes `GET /notifications` | RabbitMQ (consume), MongoDB |

Size target: about 20–25 production classes across the three services.

### Persistence

- **MongoDB is the store for the whole project.** Local MongoDB in Docker Compose; MongoDB Atlas (free cluster) in the cloud, switched by Spring profile.
- **SQL is demonstrated, not kept.** Session 3 shows the same data with JPA + Postgres first (entities, relationships, one N+1 example), then in MongoDB, to compare the two models. Postgres does not appear in the reference project.
- Overbooking is prevented with an atomic conditional update in MongoDB (or optimistic locking with `@Version`); this is the Session 4 data-integrity lesson.

### Messaging

- RabbitMQ: `ticketing-service` publishes `BookingConfirmed`; `notification-service` consumes it.
- Idempotent consumer (delivery is at-least-once) and a dead-letter queue.
- Local RabbitMQ in Compose; CloudAMQP (free Little Lemur plan) in the cloud.

### Deployment

- Multi-stage Dockerfiles for all three services (Buildpacks via `bootBuildImage` shown for comparison).
- Full system runnable in Docker Compose, locally or pointing at Atlas and CloudAMQP.
- Cloud Run on GCP (free trial): all three services, with the gateway as the public URL. Images in Artifact Registry, secrets in Secret Manager.
- GitHub Actions: build and test from Session 2; full pipeline (SHA-tagged image, push, deploy to Cloud Run, Workload Identity Federation, rollback by revision) in Session 10. Workload Identity Federation is pre-configured for students if it proves to eat lab time.

## Sessions

| # | Session | Concept | What students build |
| --- | --- | --- | --- |
| 1 | Modern Java | records, sealed types, pattern matching, streams; virtual threads as theory only; what changed since Java 17/21 | Ticketing domain model in plain Java, with unit tests |
| 2 | Spring Boot REST | DI, configuration properties, profiles, validation, `ProblemDetail` | Events and bookings API, in-memory; `@WebMvcTest` controller tests; first GitHub Actions workflow (build + test) |
| 3 | SQL vs NoSQL | JPA + Postgres demo, then MongoDB: documents, embed vs reference, Spring Data repositories | Persist to MongoDB; first `compose.yaml`; Testcontainers persistence tests; switch to Atlas by profile |
| 4 | MongoDB in depth | `MongoTemplate`/Criteria, indexes, aggregation, atomic updates | Reviews with a rating aggregation; overbooking-safe booking, proven by a test |
| 5 | Architecture | monolith vs modulith vs microservices and the costs of distribution; layered vs hexagonal (ports and adapters); sync vs async communication; how to draw service boundaries | Light hexagonal refactor of booking (ports for persistence and event publishing); the design of the service split |
| 6 | RabbitMQ | exchanges, queues, bindings, acks, at-least-once delivery, DLQ | `notification-service`, `BookingConfirmed` event, idempotent consumer; local RabbitMQ, then CloudAMQP |
| 7 | API gateway and observability | why a gateway, routing, filters; correlation ids; Actuator health and metrics | `api-gateway` routing to both services; correlation id across all three |
| 8 | Containerization | multi-stage Dockerfile, layered jars, non-root user, JVM memory in containers, health checks, `bootBuildImage` comparison | Images for all three services; full system in Compose against Atlas and CloudAMQP |
| 9 | Cloud Run | Artifact Registry, env vars, Secret Manager, logs, revisions; theory block: Cloud Run vs Kubernetes (15–20 min) | All three services on Cloud Run; gateway is the public URL |
| 10 | CI/CD | pipeline stages, image tagging by SHA, Workload Identity Federation, deploy, rollback | A commit to `main` reaches Cloud Run |

### Testing and CI are threads, not sessions

Students already write tests at work, so testing has no session of its own. Every lab includes the tests for what it builds, at the layer that fits: unit tests from Session 1, `@WebMvcTest` from Session 2, Testcontainers from Session 3. CI grows the same way: build and test from Session 2, then Docker image builds in Session 8, then the full deploy pipeline in Session 10.

### Curriculum coverage

| Source module | Covered by |
| --- | --- |
| 1. Foundation: Java, Spring, Spring Data, AI assistants | Sessions 1–4; Copilot tasks named in every lab |
| 2. Deploying: GCP, Cloud Run, GKE | Session 9 (Cloud Run hands-on; Kubernetes as a theory block), Session 10 |
| 3. Architectural patterns: system, application, component level | Session 5, applied in Sessions 6–7 |
| 4. Distributed system: REST, RabbitMQ, config, discovery, observability, CI/CD, performance | Sessions 2, 6, 7, 8, 10; config through profiles, env vars and Secret Manager; discovery through Compose DNS and gateway routing; performance at mention level |

## Stack

| | Choice |
| --- | --- |
| Java | 25 (Temurin, current LTS), Gradle toolchain with the foojay resolver so Gradle downloads the JDK itself |
| Spring Boot | 4.x (v1 ran on 4.1.1); Boot 3.x is end-of-life, so not an option for a new course |
| Build | Gradle (Kotlin DSL), wrapper committed, version catalog; multi-project once there is more than one service |
| Lombok | Yes, see conventions |
| Tests | JUnit, Spring Boot test slices, Testcontainers |
| Free services | MongoDB Atlas free cluster, CloudAMQP Little Lemur, GCP free trial, GitHub Actions on a public repo |
| AI assistants | GitHub Copilot Free (50 chat requests/month: name one or two specific chat tasks per lab) and free browser chat |

## Code conventions (carried over from v1)

- **Entities vs value objects.** Types with persistent identity (`Event`, `Booking`, …) are Lombok classes: `@Getter @ToString @NoArgsConstructor @AllArgsConstructor @Builder(toBuilder = true) @EqualsAndHashCode(of = "id")`. Value objects, DTOs, events and report values are records.
- **Constructor injection with Lombok.** Spring beans declare dependencies `private final` and use `@RequiredArgsConstructor`.
- **Test layers are explicit and labeled.** Unit (no Spring), controller (`@WebMvcTest` + `@MockitoBean`, no database), persistence (real database via Testcontainers), and a small number of full integration tests. Each test class has a Javadoc naming its layer.

## Repo and session workflow

- Public GitHub repo `markvaradi94/ticketing-app-v2`, default branch `main`.
- One tag per session: `session-NN`, the finished state of that session (live coding and lab). Students start Session N from `session-(N-1)`. There are no separate start refs, because students write the lab code themselves. Scaffolding a session needs (a new service skeleton, a Compose file) is either created during live coding or added as a small commit at the end of the previous session. Tags, not branches.
- After each session's code is built and verified, an instructor run-of-show script (HTML) is produced for that session.
- Everything runs locally through Session 8; no cloud account is needed before Session 9 except Atlas (after Session 3) and CloudAMQP (after Session 6).

## Student setup timeline

| When | Task |
| --- | --- |
| Before Session 1 | Any JDK (Gradle provisions 25), IntelliJ IDEA 2025.2+ or current VS Code with the Java extensions, Git, GitHub account with Copilot Free. Environment check: `./gradlew build` green on a hello-world project |
| Before Session 3 | Docker (or Podman/Rancher) working |
| After Session 3 | MongoDB Atlas account and free cluster |
| After Session 6 | CloudAMQP account and Little Lemur instance |
| Before Session 9 | GCP trial activated, `gcloud` CLI installed |

## Lessons learned from v1 to keep

- Spring Boot 4 modularised its artifacts; Jackson 3 and JUnit 6 break older snippets. Generate the starter from start.spring.io.
- `TestRestTemplate` moved in Boot 4. Check test imports against Boot 4, not older docs.
- Workload Identity Federation setup is the most likely thing to eat an hour; v1 has a working setup and ADR (`docs/adr/0001-workload-identity-federation-for-ci.md` in `java-course-v1`).
- Protect the small CI blocks in Sessions 2–8; cutting them makes Session 10 a cold start.

## Open questions

- Final project definition of done and deadline (v1: two weeks after Session 10).
- Whether `api-gateway` adds anything beyond routing and a correlation-id filter (CORS, rate limiting).
- Exact Spring Cloud Gateway variant for Boot 4 (Server WebMVC vs WebFlux).
