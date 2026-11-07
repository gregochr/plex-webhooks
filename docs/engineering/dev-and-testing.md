# Development, gates and CI

What runs where, and how to run it yourself. Ported from goldenhour's `dev-and-testing.md` on 2026-11-07 and adapted
to this single-module, private, database-free app. When you change a gate or a workflow, update this file in the
same PR.

## Local setup

- JDK 25 is the target (`maven.compiler.release` 25; CI uses Temurin 25). Older JDKs cannot build it.
- Copy `src/main/resources/application.properties.example` to `application.properties` (git-ignored).
- `CINELIGHT_ADMIN_PASSWORD` sets the admin password (dev fallback `changeme`).
- `./mvnw spring-boot:run` starts the app; the React dashboard is `GET /` and the legacy Thymeleaf one is
  `GET /classic` (until it is retired). See "The frontend" below.
- `hue-java-client` comes from JitPack, so no GitHub token or `~/.m2/settings.xml` entry is needed.

## Project layout

```
src/main/java/gregochr/
├── Main.java, RestClientConfig.java
├── clients/          LocationClient (postcodes.io), SunriseSunsetClient (sunrise-sunset.org), dto/
├── commands/         PlexCommand, PlexEvent, PlexOutcome, PlexMediaType, PlexCommandFactory, *Config
├── controllers/      PlexController (POST /plex), DashboardController (GET /classic), ConfigController (/api/config/**),
│                     SseController (GET /api/events), DebugController (POST /api/debug/{command}),
│                     PlaybackController (GET /api/playback/history, /now), GlobalExceptionHandler,
│                     hue/ (/api/hue/**, HueConfig, HueSceneCatalog + /api/hue/scenes), dirigera/ (/api/dirigera/status)
└── services/         ScheduledTaskManager, config/ (ConfigService, ClientConfigService, PropertiesRepository,
                      ConfigurationView*, SensitiveKeys, SecurityConfig, events), events/ (PlaybackService, the one
                      execute → record → broadcast pipeline; PlaybackHistory, NowPlaying, PlexPayloadSanitizer),
                      plex/dto/, sse/ (SseService, SseClient)
```

Tests mirror this layout under `src/test/java`. The React app lives in `frontend/` (see below); its build output goes
to `target/classes/static`.

## The frontend (`frontend/`)

Vite + React 19, JavaScript + PropTypes, the "Classical" design system (`src/styles/classical.css` is the handover's
`styles.css` with only its Google Fonts `@import` removed; the fonts are self-hosted through `@fontsource`). Versions
in `package.json` are exact; `package-lock.json` is committed.

```bash
cd frontend
npm ci                    # install exactly what the lock says
npm run dev               # Vite dev server; proxies /api and /plex to localhost:8080 (run spring-boot:run too)
npm run lint              # ESLint (jsx-a11y; dangerouslySetInnerHTML and innerHTML are errors), 0 warnings
npm test                  # Vitest + Testing Library
npm run test:coverage     # the same, with the floor: 90% lines / 85% branches on src/
npm run format            # Prettier
npm audit --audit-level=high
```

- **Maven builds it.** `frontend-maven-plugin` (Node 22, downloaded into `target/`, no system Node needed) runs
  `npm ci` and `npm run build` at `generate-resources`. Vite writes to `target/classes/static` with
  `emptyOutDir: false` (Maven copies `favicon.svg` and the logos there). `clean verify` therefore builds the SPA.
- **`-Dfrontend.skip=true`** (pom property `frontend.skip`, which feeds every goal's `<skip>`) skips all of it: use it
  for the inner Java loop. `pitest.yml` and `real-api-smoke.yml` pass it. Java tests never need the SPA: the "SPA at
  `/`" test serves the fixture `src/test/resources/static/index.html`.
- **How it ships.** There is no boot jar (no `spring-boot-maven-plugin`); the app runs through
  `./mvnw spring-boot:run`, which serves `target/classes/static`. So a build without `-Dfrontend.skip` and without
  `clean` is all it takes for `GET /` to show the SPA. (Without `clean`, old hashed files linger in `assets/`; harmless.)
- **Serving.** `GET /` is the static `index.html` (Boot's welcome page); `DashboardController` serves the legacy
  Thymeleaf page at `GET /classic` from `templates/classic.html`. The template is not named `index`, because Boot's
  welcome page falls back to a template of that name.
- **Admin writes.** `apiWrite()` (`src/api/http.js`) always sends `X-Requested-With: XMLHttpRequest`, so Spring's HTTP
  Basic answers a bare 401 with no `WWW-Authenticate` (`SecurityConfigTest` pins this) and the browser's own prompt
  never appears. A `<dialog>` takes the login; credentials live in memory only. Requests without the header (curl)
  still get the Basic challenge.
- **Data.** `useCineLight()` (`src/state/`) seeds from the GET endpoints, then follows one `EventSource`
  (`message`, `now-playing`, `config`, `hue-status`, `dirigera-status`, `heartbeat`), reconnecting with backoff and
  re-fetching history and now-playing after a reconnect.

## The gate: `./mvnw clean verify`

Gate on the exit code. In order:

1. **Checkstyle** 13 at `validate` (seconds): `.checkstyle/checkstyle.xml`, Google style with 4-space indentation.
   `IllegalImport` bans Jackson 2 (`com.fasterxml.jackson.databind`/`.core`) in the app's code.
2. **Tests**: every test except those tagged `real-api` (`surefire.excludedGroups`). Tags: none = unit,
   `integration` = Spring context or several real components (no network), `real-api` = live third-party calls.
3. **JaCoCo** (`check` in `verify`, report at `test`; `target/site/jacoco/`):
   - every class **95% line, 90% branch**, with **no exceptions** (the last overrides were removed in Phase 2,
     2026-11-07). If a class genuinely cannot reach the bar, an override at its measured figure needs an XML
     comment naming the exact unreachable line and why, as goldenhour does; never exclude a class from checking;
   - a bundle rule (80% line) is the backstop.
   - A class with no branches has no branch ratio, and JaCoCo skips that counter.
4. **SpotBugs + FindSecBugs**, threshold High, `effort` Max, `spotbugs-exclude.xml`. The engine version is
   `spotbugs.version` (4.10.5), separate from the plugin's: an engine older than the class-file version cannot read
   the classes.

Changed-lines coverage is CI-only (PRs): see below. To reproduce it locally after `verify`:

```bash
pip install diff-cover==10.0.0
diff-cover target/site/jacoco/jacoco.xml --src-roots src/main/java --compare-branch=origin/master --fail-under=90
```

The report must show real percentages. "No lines with coverage information" on a diff that touches
`src/main/java` means the path mapping is wrong, and CI fails it.

## Other runs

| What | Command | Where it runs |
|---|---|---|
| PIT mutation testing | `./mvnw test-compile pitest:mutationCoverage -Dfrontend.skip=true` | Weekly (`pitest.yml`), never on PRs. PIT 1.30.1 with pitest-junit5-plugin 1.2.3 (JUnit 6). Threshold 98 (`pitest.mutation.threshold`), measured 99% (264/267) after Phase 3 on 2026-11-07; the 3 survivors are 2 equivalent mutants and `SecurityConfig.securityFilterChain` returning null, which PIT cannot kill through a cached Spring context. With ~4 survivors of headroom, a new equivalent mutant can turn the weekly run red: decide then whether it is a test gap or equivalent. Narrow with `-Dpit.targetClasses='gregochr.services.sse.*'`. Takes a few minutes locally. |
| Live third-party APIs | `./mvnw test -Dsurefire.excludedGroups= -Dgroups=real-api -Dfrontend.skip=true` | Weekly (`real-api-smoke.yml`), which fails if 0 tests ran. |
| OWASP Dependency-Check | `./mvnw dependency-check:check` (needs `NVD_API_KEY`) | **Not in CI** until a valid `NVD_API_KEY` secret is set (the current one is rejected by NVD). Fails on CVSS ≥ 7; suppressions in `owasp-suppressions.xml`. |

## CI workflows (`.github/workflows/`)

### `ci.yml` (every PR against any base, push to `master`, manual dispatch)

- **Detect docs-only change**: `git diff --no-renames --name-only` between the PR's base and head. If every path is
  `*.md` or under `docs/`, the build job skips its steps but still reports success. Fail-safe: not a PR, an empty
  diff or any error means the full build runs. `--no-renames` stops a rename into `docs/` from hiding deleted code.
- **Build, Test & Coverage**: `./mvnw clean verify` (which also builds the SPA, see "The frontend"), then on PRs `diff-cover` at 90% of changed lines with a step
  summary. Uploads the JaCoCo and SpotBugs reports. Check names are stable: the auto-merge workflow reads them.
- **Frontend, Lint & Test** (job `frontend`): Node 22, `npm ci`, `npm run lint` (0 warnings), `npm run test:coverage`
  (90% lines / 85% branches), on PRs `diff-cover` at 90% of changed `frontend/src` lines, and
  `npm audit --audit-level=high`. lcov `SF:` paths are relative to `frontend/` but diff-cover resolves them against
  the repo root, so the step prefixes `SF:` with `frontend/` and runs from the root (otherwise it silently matches
  nothing). Anything under `frontend/` counts as code for the docs-only detection, even a `.md`.
- PR runs are cancelled by a newer push; `master` runs never are.
- There is no PR filter on base branch, so stacked PRs get CI too.

### `pitest.yml` and `real-api-smoke.yml` (weekly Monday, manual dispatch)

Both call `ci-failure-issue.yml` on `failure` **or** `cancelled` (a timed-out job ends as cancelled).

### `ci-failure-issue.yml` (reusable)

Opens one issue per failing scheduled workflow, labelled `ci-failure`; later failures comment on it. Close it once
the workflow is green; the next failure opens a new one.

### `dependabot-auto-merge.yml`

There is no branch protection (private repo, free plan), so GitHub's native auto-merge cannot wait for CI. This
workflow is the gate:

1. **Label** (on the Dependabot PR event): `dependabot/fetch-metadata` decides the update type. Minor and patch
   get `automerge-ok`; anything else has it removed.
2. **Merge** (when CI completes successfully for a Dependabot PR), one merge at a time, and only if:
   - the PR is open, from Dependabot, against `master`, labelled `automerge-ok`, and its head is the commit CI ran
     on;
   - every check-run on that commit is complete, `Build, Test & Coverage` succeeded and none failed;
   - **CI ran against the current `master`**. Otherwise two PRs, each green against an older `master`, could land a
     combination nobody built.

   It then squash-merges pinned to the head commit and dispatches `ci.yml` on `master`, because a merge made with
   `GITHUB_TOKEN` starts no push workflows.

⚠️ Dependabot rebases a PR by itself only when it conflicts. A green PR skipped for a stale base waits until
someone comments `@dependabot rebase` on it (a comment from a workflow's bot token is ignored by Dependabot). Majors
are always merged by hand.

### Dependabot (`.github/dependabot.yml`)

Maven, npm (`/frontend`) and GitHub Actions, weekly on Monday; Spring and log4j updates are grouped. It labels PRs `dependencies` and
`github-actions`.
