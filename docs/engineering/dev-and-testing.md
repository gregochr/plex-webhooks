# Development, gates and CI

What runs where, and how to run it yourself. Ported from goldenhour's `dev-and-testing.md` on 2026-11-07 and adapted
to this single-module, private, database-free app. When you change a gate or a workflow, update this file in the
same PR.

## Local setup

- JDK 17 is the target (CI uses Temurin 17). JDK 21 and 25 also build: the SpotBugs plugin 4.9.8.3 runs on 25.
- Copy `src/main/resources/application.properties.example` to `application.properties` (git-ignored).
- `CINELIGHT_ADMIN_PASSWORD` sets the admin password (dev fallback `changeme`).
- `./mvnw spring-boot:run` starts the app; the dashboard is `GET /`.
- `hue-java-client` comes from JitPack, so no GitHub token or `~/.m2/settings.xml` entry is needed.

## Project layout

```
src/main/java/gregochr/
├── Main.java, RestClientConfig.java
├── clients/          LocationClient (postcodes.io), SunriseSunsetClient (sunrise-sunset.org), dto/
├── commands/         PlexCommand, PlexEvent, PlexMediaType, PlexCommandFactory, *Config
├── controllers/      PlexController (POST /plex), DashboardController (GET /), ConfigController (/api/config/**),
│                     SseController (GET /api/events), DebugController (POST /api/debug/{command}),
│                     GlobalExceptionHandler, hue/ (/api/hue/**, HueConfig), dirigera/ (/api/dirigera/status)
└── services/         ScheduledTaskManager, config/ (ConfigService, PropertiesRepository, ConfigurationView*,
                      SensitiveKeys, SecurityConfig, events), plex/dto/, sse/ (SseService, SseClient, SseEventDto)
```

Tests mirror this layout under `src/test/java`.

## The gate: `./mvnw clean verify`

Gate on the exit code. In order:

1. **Checkstyle** at `validate` (seconds): `.checkstyle/checkstyle.xml`, Google style with 4-space indentation.
2. **Tests**: every test except those tagged `real-api` (`surefire.excludedGroups`). Tags: none = unit,
   `integration` = Spring context or several real components (no network), `real-api` = live third-party calls.
3. **JaCoCo** (`check` in `verify`, report at `test`; `target/site/jacoco/`):
   - default rule: every class **95% line, 90% branch**;
   - classes below that today are excluded from the default rule and held to **their own measured figure** by
     their own rule (comment: "measured 2026-11-07, Phase 2 raises to 0.95/0.90"); a figure can only go up;
   - a bundle rule (80% line) is the backstop. No class is excluded from checking altogether.
   - A class with no branches has no branch ratio, and JaCoCo skips that counter.
4. **SpotBugs + FindSecBugs**, threshold High, `effort` Max, `spotbugs-exclude.xml`.

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
| PIT mutation testing | `./mvnw test-compile pitest:mutationCoverage` | Weekly (`pitest.yml`), never on PRs. Threshold 70 (`pitest.mutation.threshold`), measured 80% on 2026-11-07. Narrow with `-Dpit.targetClasses='gregochr.services.sse.*'`. Takes ~3 minutes locally. |
| Live third-party APIs | `./mvnw test -Dsurefire.excludedGroups= -Dgroups=real-api` | Weekly (`real-api-smoke.yml`), which fails if 0 tests ran. |
| OWASP Dependency-Check | `./mvnw dependency-check:check` (needs `NVD_API_KEY`) | **Not in CI** until a valid `NVD_API_KEY` secret is set (the current one is rejected by NVD). Fails on CVSS ≥ 7; suppressions in `owasp-suppressions.xml`. |

## CI workflows (`.github/workflows/`)

### `ci.yml` (every PR against any base, push to `master`, manual dispatch)

- **Detect docs-only change**: `git diff --no-renames --name-only` between the PR's base and head. If every path is
  `*.md` or under `docs/`, the build job skips its steps but still reports success. Fail-safe: not a PR, an empty
  diff or any error means the full build runs. `--no-renames` stops a rename into `docs/` from hiding deleted code.
- **Build, Test & Coverage**: `./mvnw clean verify`, then on PRs `diff-cover` at 90% of changed lines with a step
  summary. Uploads the JaCoCo and SpotBugs reports. Check names are stable: the auto-merge workflow reads them.
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

Maven and GitHub Actions, weekly on Monday; Spring and log4j updates are grouped. It labels PRs `dependencies` and
`github-actions`.
