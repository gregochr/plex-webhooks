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

# Run all tests (unit + integration; `real-api` tests are excluded by default)
./mvnw test

# Run the live third-party API tests on purpose (need network; may drift with provider data)
./mvnw test -Dsurefire.excludedGroups= -Dgroups=real-api

# Run integration tests only
./mvnw test -Dgroups=integration

# Mutation testing (PIT) — opt-in profile
./mvnw verify -P mutation-testing

# OWASP Dependency-Check — opt-in profile, slow (needs NVD_API_KEY env var)
./mvnw verify -P security-scan -DskipTests
```

> `src/main/resources/application.properties` is listed in `.gitignore`. Copy `application.properties.example` and fill in your values.
> The admin password is sourced from `CINELIGHT_ADMIN_PASSWORD` env var (falls back to `changeme` in dev).
> `hue-java-client` is resolved from JitPack (`https://jitpack.io`) — no token or `settings.xml` entry needed.

---

## Project Structure

```
src/main/java/gregochr/
├── Main.java                          Entry point (@SpringBootApplication + @EnableScheduling)
├── RestClientConfig.java              Spring RestClient bean
├── clients/                           LocationClient, SunriseSunsetClient (+ dto/) — external HTTP APIs
├── commands/                          Plex command model (PlexCommand, PlexEvent, PlexMediaType, PlexCommandFactory, *Config)
├── controllers/
│   ├── PlexController.java            POST /plex — receives Plex webhooks
│   ├── DashboardController.java       GET / — Thymeleaf dashboard
│   ├── ConfigController.java          GET|PUT /api/config/** — config API
│   ├── SseController.java             GET /api/events — SSE subscription
│   ├── DebugController.java           POST /api/debug/{command} — fire a Plex command manually
│   ├── GlobalExceptionHandler.java
│   ├── hue/                           Hue REST controllers (/api/hue/**) + HueConfig bean
│   └── dirigera/                      IKEA DIRIGERA hub status (/api/dirigera/status)
└── services/
    ├── ScheduledTaskManager.java      Reschedule-able periodic tasks
    ├── config/                        ConfigService, PropertiesRepository, ConfigurationView*, SecurityConfig, events
    ├── plex/dto/                      PlexPayloadDto, PlexMetadataDto
    └── sse/                           SseService, SseClient, SseEventDto
```

Tests mirror this layout; `UnitTestSuite` / `IntegrationTestSuite` in the default package are aggregators excluded from Surefire.

### External Library

`hue-java-client` (`com.github.gregochr:hue-java-client:v1.0.0`) is the standalone Philips Hue HTTP client, built by JitPack from the `v1.0.0` tag of `gregochr/hue-java-client`. It has **no Spring dependency** — pure `java.net.http.HttpClient`.

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

- Line coverage must be ≥ 80%, enforced by JaCoCo in `verify` (fails the build below threshold).
- Unit tests live in `src/test/java`, tagged by default (no tag = unit).
- Integration tests are tagged `@Tag("integration")`.
- Tests that call a live third-party service are tagged `@Tag("real-api")` and are excluded from the build gate (`surefire.excludedGroups` in `pom.xml`).
- Do not mock what you can test for real (e.g. use real `PropertiesRepository` with temp files rather than mocking file I/O unless truly impractical).

### 2. GitHub Actions CI Pipeline

Defined in `.github/workflows/ci.yml`; runs on every push and pull request to `master` (and `main`).

#### CI Jobs

| Job | Tool | Notes |
|-----|------|-------|
| **Build & Verify** | `./mvnw verify` | Runs all tests + JaCoCo, Checkstyle, SpotBugs gates |
| **Integration Tests** | `./mvnw test -Dgroups=integration` | Separate job, after build |
| **Mutation Testing** | PIT, `-P mutation-testing` | Threshold 70% (`pitest.mutation.threshold`) |
| **OWASP / CVE Scan** | Dependency-Check, `-P security-scan` | Fail on CVSS ≥ 7; NVD DB cached |
| **Dependency Updates** | Dependabot (`.github/dependabot.yml`) | Maven + Actions, weekly (Mon); Spring and log4j grouped |

Thresholds live as properties at the top of `pom.xml`. Dependabot auto-merge is **not** configured.

### 3. Mutation Testing

Use **PIT** (`pitest-maven`, `mutation-testing` profile). Runs in CI on every push/PR. Target packages: `gregochr.commands`, `gregochr.services`, `gregochr.controllers`. Threshold is currently 70% (`pitest.mutation.threshold`); raise to 80% over time.

### 4. Code Style

**Checkstyle** based on Google Java Style, but with 4-space indentation to match the codebase. Configuration at `.checkstyle/checkstyle.xml`. Violations fail the build. IDE plugin recommended.

### 5. Bug Detection

**SpotBugs** with the **Find Security Bugs** plugin. Fail on any bug of category HIGH or above. Suppress with `@SuppressFBWarnings` (or `spotbugs-exclude.xml`) only with a documented justification.

### 6. OWASP / CVE Scanning

**OWASP Dependency-Check** Maven plugin. Fail the build on any dependency with CVSS score ≥ 7.0. Run in CI (not required locally due to download time). NVD API key configured as a GitHub Actions secret.

---

## Roadmap & Future Work

### Philips Hue Client — published (done)

`hue-java-client` now lives in `gregochr/hue-java-client` and is consumed from JitPack as `com.github.gregochr:hue-java-client:v1.0.0`; the local `../hue-java-client` copy is gone. Library API and the intentional trust-all SSL context (for self-signed LAN bridge certs) are documented in that repo's README.

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
