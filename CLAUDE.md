# CineLight (plex-webhooks) — Claude Code Guide

## Project Overview

**CineLight** is a Spring Boot application that automates Philips Hue lighting based on Plex media server webhook events. When a movie plays, pauses, or stops in Plex, the application adjusts smart lights accordingly.

- **Language:** Java 17
- **Framework:** Spring Boot 3.5.7
- **Build:** Maven (wrapper included — use `./mvnw`)
- **Templating:** Thymeleaf
- **Auth:** Spring Security (HTTP Basic, stateless)
- **Real-time:** Server-Sent Events (SSE)

---

## Build & Run

```bash
# Build (skip tests)
./mvnw clean package -DskipTests

# Build with tests
./mvnw clean verify

# Run
./mvnw spring-boot:run

# Run unit tests only
./mvnw test

# Run integration tests only
./mvnw test -Dgroups=integration
```

> `application.properties` is git-ignored. Copy `application.properties.example` and fill in your values.
> The admin password is sourced from `CINELIGHT_ADMIN_PASSWORD` env var (falls back to `changeme` in dev).

---

## Project Structure

```
src/main/java/gregochr/
├── Main.java                          Entry point (@SpringBootApplication + @EnableScheduling)
├── RestClientConfig.java              Spring RestClient bean
├── commands/                          Plex command model (PlexCommand, PlexEvent, PlexMediaType, PlexCommandFactory)
├── controllers/
│   ├── PlexController.java            POST /plex — receives Plex webhooks
│   ├── DashboardController.java       GET / — Thymeleaf dashboard
│   ├── ConfigController.java          GET|PUT /api/config/** — config API
│   ├── SseController.java             GET /api/events — SSE subscription
│   ├── GlobalExceptionHandler.java
│   ├── hue/                           Hue REST controllers + HueConfig bean
│   └── dirigera/                      IKEA DIRIGERA hub controllers
└── services/
    ├── config/                        ConfigService, PropertiesRepository, event publish/listen
    ├── plex/                          PlexPayloadDto, PlexMetadataDto
    └── sse/                           SseService, SseClient, SseEventDto, ScheduledTaskManager
```

### External Library

`hue-java-client` (v1.0-SNAPSHOT, located at `../hue-java-client`) is the standalone Philips Hue HTTP client. It has **no Spring dependency** — pure `java.net.http.HttpClient`. It is installed to local Maven and referenced as a coordinate dependency.

---

## Architecture Notes

- **Hot-reload config:** `PropertiesUpdatedEvent` is published when properties change via the API. Components listen via `@EventListener` and update volatile fields without restart.
- **Thread safety:** Shared mutable state in `ConfigService`, `HueSceneController`, and `LocationClient` uses `volatile` fields for cross-thread visibility.
- **Graceful degradation:** Hue/Dirigera failures log warnings but never crash the application.
- **Property whitelisting:** Only a defined set of keys can be written via the config API (`ConfigurationViewService`).

---

## Non-Negotiables

These requirements apply to every PR and must be enforced in CI.

### 1. Test Coverage — 80% Minimum

- Line coverage must be ≥ 80%, enforced by JaCoCo in the Maven build.
- Add JaCoCo to `pom.xml` if not already present; configure it to **fail the build** below threshold.
- Unit tests live in `src/test/java`, tagged by default (no tag = unit).
- Integration tests are tagged `@Tag("integration")`.
- Do not mock what you can test for real (e.g. use real `PropertiesRepository` with temp files rather than mocking file I/O unless truly impractical).

### 2. GitHub Actions CI Pipeline

The pipeline runs on every push and pull request to `main`. It must include all the jobs below.

#### Required CI Jobs

| Job | Tool | Notes |
|-----|------|-------|
| **Build & Unit Tests** | `./mvnw verify` | Fail fast |
| **Integration Tests** | `./mvnw test -Dgroups=integration` | Separate job |
| **Code Coverage** | JaCoCo | Fail below 80% |
| **Mutation Testing** | PIT (pitest-maven-plugin) | Minimum mutation score TBD |
| **Code Style** | Checkstyle (Google Java Style) | Fail on violation |
| **Bug Detection** | SpotBugs + Find Security Bugs | Fail on HIGH bugs |
| **OWASP / CVE Scan** | OWASP Dependency-Check | Fail on CVSS ≥ 7 |
| **Dependency Updates** | Dependabot | Auto-PRs for patch/minor |

#### Dependabot Configuration

Create `.github/dependabot.yml`:
- Maven dependencies: weekly, auto-merge patch
- GitHub Actions: weekly

### 3. Mutation Testing

Use **PIT** (`pitest-maven-plugin`). Run on every PR. Target packages: `gregochr.commands`, `gregochr.services`, `gregochr.controllers`. Aim for ≥ 70% mutation coverage initially, rising to ≥ 80% over time.

### 4. Code Style

**Checkstyle** with Google Java Style. Configuration at `.checkstyle/google_checks.xml`. Violations fail the build. IDE plugin recommended.

### 5. Bug Detection

**SpotBugs** with the **Find Security Bugs** plugin. Fail on any bug of category HIGH or above. Suppress with `@SuppressFBWarnings` only with a documented justification.

### 6. OWASP / CVE Scanning

**OWASP Dependency-Check** Maven plugin. Fail the build on any dependency with CVSS score ≥ 7.0. Run in CI (not required locally due to download time). NVD API key configured as a GitHub Actions secret.

---

## Roadmap & Future Work

### [MOST URGENT] Philips Hue Client — Publish as Standalone Library

The extraction of `HueBridgeClient` into `hue-java-client` (local SNAPSHOT) is already complete. The remaining work is to publish it as a proper open-source Maven artifact on GitHub.

**Plan:**
1. Create a dedicated GitHub repository: `gregochr/hue-java-client`
2. Add a GitHub Actions workflow to that repo: build, test, and publish to **GitHub Packages** (Maven registry)
3. Update `plex-webhooks/pom.xml` to reference the published GitHub Packages coordinate instead of the local SNAPSHOT
4. Add the GitHub Packages repository to `pom.xml` (or `settings.xml`)
5. Remove the local install step from any scripts
6. Tag an initial release (`v1.0.0`) and write a README with usage instructions
7. **Delete the local `../hue-java-client` directory** — once plex-webhooks references the published artifact, the local copy just creates noise

**API surface to document:**
- `HueBridgeClient.create(hubId, appKey)` — factory
- `activateScene(sceneId)` — returns boolean
- `testConnection()` — returns boolean
- `getResources()` — returns JSON string

> Note: The trust-all SSL context is intentional for local LAN bridges that use self-signed certs. Document this explicitly in the library README.

---

### Apple TV Integration

Two deployment flavours:

1. **Plex-only** (current) — unchanged
2. **AppleTV** — listens for media playback events from the Apple TV (via MQTT, ATV remote, or `pyatv`-style HTTP bridge) so that light automation works across **any** app (Netflix, Disney+, etc.), not just Plex

**Approach:** Abstract the lighting trigger behind a `MediaPlaybackEvent` interface. Both the Plex webhook handler and the future Apple TV adapter publish to the same interface. The Hue controller subscribes to it.

---

### React UI Rewrite

Replace the Thymeleaf server-rendered dashboard with a standalone React SPA.

- The existing REST API (`/api/**`) and SSE (`/api/events`) stay unchanged — the React app is a pure consumer
- Host the React app as static assets served by Spring Boot (or a separate dev server with proxy)
- State management: React Query for server state, local state for UI
- Real-time updates: consume the existing SSE stream

---

## Development Guidelines

- **Do not commit `application.properties`** — it is git-ignored. Use `application.properties.example` as the template.
- Keep `hue-java-client` free of Spring dependencies. It must remain a plain Java library.
- New features that touch Hue should go into `hue-java-client` if they are bridge-API concerns, or into `gregochr.controllers.hue` if they are application concerns.
- All config changes go via `PropertiesRepository` + `PropertiesUpdatedEvent` — do not restart to pick up property changes.
- Prefer `@EventListener` + volatile fields over synchronized blocks for hot-reload scenarios.
- The `ScheduledTaskManager` is the standard way to create reschedule-able periodic tasks — reuse it, don't invent a new pattern.
