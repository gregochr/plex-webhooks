# AGENTS.md

CineLight: a single-module Spring Boot 4.1 / Java 25 app that turns Plex webhook events into Philips Hue (and IKEA
DIRIGERA) lighting changes. `CLAUDE.md` is the project guide; `docs/engineering/test-standards.md` holds the test
rules.

- Build gate: `./mvnw clean verify` runs the tests, JaCoCo, Checkstyle and SpotBugs + FindSecBugs (High). Gate on
  Maven's exit code. Tests tagged `real-api` call live third-party services and are excluded from the gate.
- Conventional commits (`feat:`, `fix:`, `test:`, `chore:`, `docs:`, `refactor:`), one `changelog.d/` entry per
  meaningful change. Never commit `src/main/resources/application.properties`; only `application.properties.example`.

## Code Review Rules

Report prioritised P0/P1 findings with `file:line` references. Checkstyle and SpotBugs gate style and common bug
patterns in CI, so do not spend findings on them.

Focus on:
- **Thread safety.** Webhook requests, the SSE scheduler and `@EventListener` config reloads run on different
  threads. Shared mutable state must be `volatile` or otherwise safely published (see "Deliberate decisions").
- **Hot-reload correctness.** Config changes arrive as `PropertiesUpdatedEvent`; a component that caches a property
  and does not listen for the event serves stale config until restart.
- **Graceful degradation.** A Hue bridge, DIRIGERA hub or third-party API failure must log and carry on, never
  crash the app or fail the Plex webhook response.
- **Config write safety.** Only whitelisted keys (`ConfigurationViewService`) may be written through the config
  API; anything that widens what can be written, or writes outside `PropertiesRepository`, is a defect.
- **Security.** `SecurityConfig` is stateless HTTP Basic with CSRF off. Reads (`GET` dashboard, SSE, Hue,
  DIRIGERA and config views) and the Plex webhook (`POST /plex`) are public; only `PUT /api/config/**` and
  `POST /api/debug/**` require authentication, and every other request falls through to `anyRequest().permitAll()`.
  So any new state-changing route is public unless it is added to an `authenticated()` matcher: flag that, and any
  secret logged, returned in a response or rendered into a page. The public dashboard's config tab must never carry
  a credential value (`hue.application-key`, passwords, tokens) in its HTML, masked or not.
- **Deploy safety.** Nothing may bake `application.properties` or `.env` into the image (`.dockerignore`, and the
  CI image test). Settings are read and written only at `cinelight.config-file` when it is set. Only
  `/actuator/health/**` is reachable, and the `container` health group includes no third party. **A
  `runs-on: self-hosted` job must never trigger on `pull_request`, `pull_request_target` or `issue_comment`**:
  this repo is public, and the runner is the production host.
- **Backup scripts (`ops/utils-pg/`) fail closed and leak nothing.** This repo's workflow logs are public, so
  no table name, row, `set -x` output or dump artifact may reach them. A missing database, a partial dump or a
  low restored row count must fail, never warn. Scripts are installed with temp-then-`mv`, never `cp` over a
  file that may be running.
- **The playback database never touches the lights (D2).** `PlaybackService.process` only offers to
  `PlaybackHistoryWriter`'s bounded queue; any code that makes the webhook wait on JDBC, lets a database exception
  reach `process`, or makes startup depend on the database is a defect. Event ids come from
  `max(nextId, millis*1000)` inside the lock and are raised past the stored `max(id)` before the writer is marked
  ready. The `database` health group stays out of `container`, and nothing may gate on aggregate `/actuator/health`.
- **Tests that cannot fail.** `any()` where the value is knowable, `lenient()`, `Thread.sleep`, a fixed `Clock` on
  a date that could be today, a live network call outside a `real-api` test.

### Deliberate decisions: do NOT report these as bugs without new evidence

- **Trust-all SSL in `hue-java-client`.** Hue bridges on the LAN use self-signed certificates. It is documented in
  that library's README.
- **`volatile` fields instead of `synchronized`** for hot-reloaded config (`ConfigService`, `HueSceneController`,
  `LocationClient`). Writes are whole-value replacements from one event thread; readers need visibility, not
  atomic compound updates.
- **`real-api` tests excluded from the build**, run weekly instead. A provider's data changing must not break PRs.
- **Public read endpoints** (dashboard, SSE, Hue/DIRIGERA status, config views): the app runs on a home LAN and
  the dashboard is meant to be viewable without signing in. Writes are what is protected.
- **Jackson 3 (`tools.jackson`) in the app's own code; Jackson 2 only inside `hue-java-client`.** Checkstyle bans
  `com.fasterxml.jackson.databind`/`.core` imports (the shared `com.fasterxml.jackson.annotation` is fine).
  Jackson 3 silently ignores Jackson 2's `@JsonDeserialize`, so a Jackson 2 import is a defect, not a style nit.
- **Playback events are dropped, not queued, while the database is not ready (D2).** The in-memory ring still has
  them; history matters less than the lights, so there is no backlog replay. Likewise a full writer queue drops.
- **Aggregate `/actuator/health` goes DOWN when the database is down.** Nothing reads it; Docker and the scripts
  read `/actuator/health/container`.
- **Admin password default `changeme`** via `${CINELIGHT_ADMIN_PASSWORD:changeme}`: a development fallback only.
