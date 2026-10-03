# syntax=docker/dockerfile:1
# Build from the repository root: docker build -f ticketing-service/Dockerfile -t ticketing-service .

# 1. Build: the project's own Gradle wrapper on a JDK. The context is the repository root, because the services
#    share settings.gradle.kts and the version catalog. The cache mount keeps Gradle's downloads between builds.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle ./gradlew :ticketing-service:bootJar --no-daemon --quiet
# Split the jar into layers: dependencies change rarely, the application on every commit.
RUN java -Djarmode=tools -jar ticketing-service/build/libs/ticketing-service.jar extract --layers --launcher --destination /extracted

# 2. Run: a JRE only, as a non-root user. curl is there for the health check.
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 spring
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER spring
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8081/actuator/health/readiness || exit 1
# The heap follows the container's memory limit (the JVM's default is only 25 %).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
