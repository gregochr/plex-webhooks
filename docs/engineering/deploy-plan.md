# Deploy plan: Docker, release.sh and Postgres (`utils-pg`), ported from goldenhour

Status: **planned 2026-11-07**. Source: `~/IdeaProjects/goldenhour` (`release.sh`, `.github/workflows/deploy.yml`,
`docker-compose.yml`, `scripts/`), which deploys to the same host.

## Owner decisions (2026-11-07)

| Question | Decision |
|---|---|
| Host | **goldenhour's box**: `dockermacmini`, Ubuntu, `gregochr@100.76.73.16` (Tailscale), on the home LAN with the Hue bridge and DIRIGERA hub. |
| Database | **PostgreSQL in the existing shared `utils-pg` container** on the host (the misc database for one-off, low-volume apps). CineLight gets its own database and role there, not a container of its own. Flyway, Testcontainers. It holds event history and, later, settings. |
| Release flow | goldenhour's: `./release.sh` tags `master`, a tag push builds images to GHCR and deploys over SSH from a self-hosted runner. |

## What CineLight has today, and what that means for a container

- **No packaged app.** There is no `spring-boot-maven-plugin` execution, so no boot jar exists; it runs only through
  `./mvnw spring-boot:run`. A container needs the repackaged jar.
- **Settings are written into the classpath.** `PropertiesRepository` reads and rewrites
  `new ClassPathResource("application.properties")` through `getFile()`. Under `spring-boot:run` that is
  `target/classes/application.properties`, so **every dashboard change is lost on the next `mvn clean`** (a
  bug today). Inside a jar `getFile()` throws, so every config save would fail. The file must live outside the
  app.
- **No database, no health endpoint, no version tags, no `CHANGELOG.md`** (only `changelog.d/`).
- **One artifact, not two.** goldenhour ships a backend image plus an nginx frontend image. The React plan
  (`react-ui-plan.md`, PR 2a) builds the SPA into the Spring Boot jar's `static/`, so CineLight is **one image**
  and needs no nginx. Plex posts to it directly on the LAN.

## Phases (one PR each, branched from the updated `master`)

| Phase | Scope | Depends on |
|---|---|---|
| D1 | Container, external config file, health, `release.sh`, deploy workflow, health check, image rollback | nothing: start now |
| D2 | Postgres + Flyway, event history in the database, backups, restore check, rollback with DB restore | React PR 1 merged (it introduces the history store D2 moves into Postgres) |
| D3 | Settings in Postgres (one-time import from the file), hot-reload kept | D2 |

D1 and the React PRs both touch `pom.xml`; whichever merges second rebases. Nothing else overlaps.

## D1: container and release pipeline

**App changes (Java):**
- `spring-boot-maven-plugin` with `repackage`, so `./mvnw package` produces `target/cinelight.jar`
  (`<finalName>cinelight</finalName>`).
- **External settings file.** New property `cinelight.config-file`. When set, `PropertiesRepository` reads and
  writes that file (`FileSystemResource`), and Spring loads it with
  `spring.config.import=optional:file:${cinelight.config-file}`. When unset, today's classpath behaviour remains
  for development. The container sets `CINELIGHT_CONFIG_FILE=/config/application.properties`. Writes go to a
  temp file in the same directory, then an atomic move, so a crash mid-write cannot truncate the settings.
  Tests: read, write, hot-reload and the atomic move against a `@TempDir` file; startup with the file absent
  fails with a clear message naming the path.
- **Health.** Add `spring-boot-starter-actuator`. Expose only `health`, `permitAll` on `/actuator/health/**`,
  `show-details: never`. Add a `container` health group with only `ping` (D2 adds `db`), so a Hue or DIRIGERA
  outage never marks the container unhealthy (the graceful-degradation invariant). Tests: anonymous
  `GET /actuator/health/container` is 200 with `UP`; `/actuator/env` and `/actuator/beans` are 404.
- **Version.** `APP_VERSION`, `GIT_COMMIT_SHA` build args become env vars and are logged at startup. No UI.

**Files (ported from goldenhour, adapted):**
- `Dockerfile`: goldenhour's `backend/Dockerfile`. Builder `eclipse-temurin:25-jdk-alpine` using `./mvnw` (not
  apk maven) with a BuildKit cache for `~/.m2`, `package -DskipTests`. Runtime `eclipse-temurin:25-jre-alpine`
  plus `curl`, non-root uid 1001, `EXPOSE 8080`, `HEALTHCHECK` on `/actuator/health/container`, `VOLUME /config`.
  `.dockerignore` excludes `target/`, `.git`, `.claude/`, `frontend/node_modules/` and
  `src/main/resources/application.properties`, **so the developer's own credentials can never be baked into
  an image**. A CI test builds the image and asserts the file is absent from it.
- `docker-compose.yml`: service `cinelight`, image `ghcr.io/gregochr/cinelight:latest`, `8080:8080`,
  `env_file: ./.env` (owner-held: `CINELIGHT_ADMIN_PASSWORD`), `CINELIGHT_CONFIG_FILE`, bind mount
  `/home/gregochr/cinelight-data/config:/config`, `restart: always`, log rotation (10m by 3),
  `stop_grace_period: 20s`, the healthcheck as above.
- `.github/workflows/deploy.yml`: goldenhour's shape. On a `v*` tag: (1) `test` reuses `ci.yml` (add a
  `workflow_call` trigger); (2) `build-and-push` to `ghcr.io/gregochr/cinelight:{latest,<version>}` with
  `APP_VERSION`/`GIT_COMMIT_SHA`; (3) `deploy` on the self-hosted runner over SSH (`appleboy/ssh-action`, host
  `100.76.73.16`, user `gregochr`, `DEPLOY_SSH_KEY`). The deploy step: refuse to run over a rollback pin
  (`docker-compose.override.yml`); `git fetch --tags`; write `docker-compose.yml` **from the tag** with
  `git show`; refuse to start if `/home/gregochr/cinelight-data/config/application.properties` is missing (the
  silent-empty-state guard, the equivalent of goldenhour's PGDATA check); record the outgoing digest; pull; `up -d`;
  run `prod-health.sh --deploy`; record the new digest. Plus (4) `notify-failure` via `ci-failure-issue.yml`.
- `release.sh` and `scripts/promote-changelog.sh`: goldenhour's, with `main` → `master`. The first release
  offers `1.0.0` when no tag exists. The changelog promotion folds `changelog.d/*.md` into a new `CHANGELOG.md`
  through a docs-only PR. It merges with `gh pr merge --squash --match-head-commit` once CI is green: there is no
  branch protection here, so the script waits for the check itself. Then the script tags, pushes, watches
  the Deploy run and reports with a bell and a macOS notification. **Only the owner runs it.** Sessions never
  create tags (CLAUDE.md).
- `scripts/prod-health.sh` (`--deploy` / `--daily`): compose health, `GET /actuator/health/container`, `GET /`
  is 200, **`/api/hue/status` reports the bridge reachable** (a warning, not a failure: Hue being down is not a
  bad release), and Tailscale up. No cloudflared: CineLight is LAN-only.
- `.github/workflows/prod-health.yml`: daily on the self-hosted runner, opening an issue on failure.
- `scripts/record-deployed-images.sh` and `scripts/rollback.sh`: images only in D1 (pin the previous digest in
  `docker-compose.override.yml`, copy the settings file aside first). D2 adds the database restore.
- Shell tests: goldenhour tests its scripts against fixtures (`promote-changelog.sh` is separate for exactly
  this). Port those tests, and run `shellcheck` on `scripts/` and `release.sh` in CI.

**Docs (planner):** `dev-and-testing.md` gains a "Release and deploy" section; `CLAUDE.md` gains one line
("Releases: owner runs `./release.sh`; sessions never tag"); `AGENTS.md` gains the deploy invariants (config file
outside the image, health group excludes third parties).

### Owner actions for D1 (before the first release)

1. On `dockermacmini`, register a **self-hosted runner for `gregochr/plex-webhooks`**. A personal account's
   runner serves one repo, so goldenhour's cannot be reused. Use a second runner instance in its own directory.
2. Add the repo secret `DEPLOY_SSH_KEY` (it can be the same key goldenhour uses).
3. On the host: `docker login ghcr.io` with a token that has `read:packages`. This repo is private, so its
   image is private, unlike goldenhour's.
4. On the host: `mkdir -p ~/cinelight ~/cinelight-data/config`, `git clone` this repo into `~/cinelight`,
   copy your current `application.properties` to `~/cinelight-data/config/`, and write `~/cinelight/.env` with
   `CINELIGHT_ADMIN_PASSWORD=...` (mode 600).
5. Check that port 8080 is free on the host (goldenhour uses 80 and 8082, and Postgres 5432).
6. After the first deploy, point Plex's webhook at `http://<dockermacmini LAN IP>:8080/plex` and stop the old
   instance.

## D2: Postgres in `utils-pg` (after React PR 1)

### What `utils-pg` is (read from the host by the owner, 2026-11-07)

| Fact | Value |
|---|---|
| Container | `utils-pg`, `postgres:16` (16.13), `restart: unless-stopped`, healthy |
| Compose project | `/home/gregochr/services/utils-pg`; data `/srv/utils-pg/data`; root password from a secret file |
| Network | `apps-net`, aliases `utils-pg` and `postgres`; **no port published to the host** |
| Init scripts | `/home/gregochr/services/utils-pg/init`, which runs only when the data directory is first created, so it will **not** run for a new app now |
| Existing pattern | one database plus one non-superuser role per app (`wainwrights`/`wainwrights`), and that app's containers on `apps-net` |
| Backups | **none**. The host's only database backups are goldenhour's (`goldenhour-backup.timer` and a 3am `pg_dump` cron of `goldenhour-db`). `wainwrights` is not backed up today. |
| Port 8080 | free |

### Design

- **Follow the `wainwrights` pattern.** A database `cinelight` owned by a role `cinelight` (LOGIN, no superuser,
  no CREATEDB, no CREATEROLE), with `CONNECT` revoked from `PUBLIC` on `cinelight`. Created once by
  `scripts/utils-pg-bootstrap.sh`, run by the owner. It is idempotent (it checks `pg_roles` and `pg_database`
  first) and uses `docker exec utils-pg psql -U postgres`. The password is read from `~/cinelight/.env`, never
  passed on the command line. Put a copy in `utils-pg/init/` too, for a rebuild from scratch.
- **Network:** the `cinelight` service joins `apps-net` (`external: true` in compose) and connects to
  `jdbc:postgresql://utils-pg:5432/cinelight`. `SPRING_DATASOURCE_*` come from `.env`.
- **Plain JDBC, not JPA:** `spring-boot-starter-jdbc` with `JdbcClient`, the PostgreSQL driver,
  `spring-boot-starter-flyway` with `flyway-database-postgresql`. There is one table, and Hibernate would need
  a connection at boot.
- **CineLight starts and works when `utils-pg` is down** (the graceful-degradation invariant: the lights matter,
  history does not):
  - Boot's automatic Flyway run is off, and Hikari's `initialization-fail-timeout` is `-1`.
  - A `ScheduledTaskManager` task runs Flyway with a retry until it succeeds, then flags the database ready.
  - History writes are skipped, with a rate-limited warning, until that flag is set, and on any later
    `DataAccessException`.
  - Tests start the app with the database unreachable and check that a Plex play event still changes the
    lights; and that history appears once the database comes back.
- `V1__event_history.sql`: the history React PR 1 keeps in memory moves to a table, written after each event is
  executed. Retention: 90 days, pruned by a `ScheduledTaskManager` task. The UI label changes from "since
  CineLight started" to real history. Testcontainers uses **`postgres:16`** to match. Docker becomes a
  prerequisite for `clean verify`, as in goldenhour: document it.
- Health: a separate `db` group, which `prod-health.sh` warns on. It is **not** in `container`, so a database
  outage never makes Docker restart CineLight.
- **Backups:** the deploy dumps `cinelight` before pulling (`docker exec utils-pg pg_dump -U postgres -d cinelight
  -Fc`), so `rollback.sh` can restore it.

### Prerequisite owner decision: back up `utils-pg` itself (a gap for every app, not just CineLight)

`utils-pg` has no backup, so `wainwrights` is unprotected now. Fix it once in `/home/gregochr/services/utils-pg`, not
per app: a nightly `pg_dumpall` (or one `pg_dump -Fc` per database) with rotation, and a restore check modelled on
goldenhour's `backup-verify.yml`. That work belongs to the `utils-pg` setup, which is not in this repo. It should
land before CineLight's history matters, but it does not block D1.

## D3: settings in Postgres

- `V2__settings.sql`: key/value with `updated_at`. On first boot, an empty table is filled once from the
  properties file (logged, idempotent). After that the database is the source of truth and the file is no
  longer written.
- Keep every invariant: the write whitelist (`ConfigurationViewService`), `PropertiesUpdatedEvent` hot-reload,
  validation before write, and credentials never sent to the browser. The admin password stays an env var.
  The Hue application key moves into the database with the rest.
- If the database is unreachable at startup, the settings fall back to the last file copy (kept read-only as
  a snapshot after the import), so the lights still work.
- Rollback note: D3 is forward-only. Rolling back past it means restoring the pre-release dump, which
  `rollback.sh` does.

## Risks accepted

- **Same host as goldenhour.** A goldenhour incident or reboot takes CineLight down too. `restart: always`
  brings it back.
- **The LAN-only webhook has no TLS.** As today.
- **A self-hosted runner per repo** is a second process on the host to keep updated.
