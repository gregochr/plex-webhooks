# CineLight: one image, one Spring Boot jar. Plex posts to it directly on the LAN, so there is no nginx.

# ── Build ──────────────────────────────────────────────────────────────────────────────────────────────────
# eclipse-temurin:25-jdk is Ubuntu-based, not alpine, on purpose: the React build's frontend-maven-plugin
# downloads a glibc Node, which cannot run on musl.
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Layer 1: the wrapper and the POM, so the dependency download is cached until the POM changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline -B --no-transfer-progress

# Layer 2: the source, including frontend/ (built by frontend-maven-plugin during package). Checkstyle, tests, JaCoCo and SpotBugs are the CI gate (ci.yml); the image build only
# packages, and deploy.yml builds it only after that gate has passed on the same tag.
# Not offline (-o): go-offline can miss a transitive, and the cache mount still makes the common case fast.
COPY frontend frontend
COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw package -DskipTests -Dcheckstyle.skip=true -B --no-transfer-progress

# ── Runtime ────────────────────────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine

# Ties the GHCR package to this repository. The image is named cinelight but the repository is plex-webhooks,
# so without this label GitHub does not link the two and `GITHUB_TOKEN` cannot manage the package.
LABEL org.opencontainers.image.source="https://github.com/gregochr/plex-webhooks"

RUN apk add --no-cache curl

# A default non-root user for `docker run` without --user. docker-compose.yml overrides it with the host user's
# uid:gid, so that the settings file the app rewrites in /config stays owned by that host user.
RUN addgroup -g 1001 appgroup && adduser -D -u 1001 -G appgroup appuser

WORKDIR /app
COPY --from=builder /app/target/cinelight.jar app.jar

# The settings file lives outside the image, in a mounted directory (see docker-compose.yml).
RUN mkdir -p /config && chown appuser:appgroup /config

# The `container` health group is `ping` only, so a Hue, DIRIGERA or third-party outage never marks the
# container unhealthy and never gets it restarted.
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health/container >/dev/null || exit 1

USER appuser
EXPOSE 8080

# deploy.yml passes these; StartupInfoLogger logs them at startup.
ARG APP_VERSION=dev
ARG GIT_COMMIT_SHA=
ENV APP_VERSION=${APP_VERSION}
ENV GIT_COMMIT_SHA=${GIT_COMMIT_SHA}

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
