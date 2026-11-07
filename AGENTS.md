# AGENTS.md

CineLight: a single-module Spring Boot 3.5 / Java 17 app that turns Plex webhook events into Philips Hue (and IKEA
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
- **Admin password default `changeme`** via `${CINELIGHT_ADMIN_PASSWORD:changeme}`: a development fallback only.
