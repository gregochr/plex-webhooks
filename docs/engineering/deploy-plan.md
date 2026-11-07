# Deploy plan: Docker, release.sh and Postgres (`utils-pg`), ported from goldenhour

Status: **planned 2026-11-07**. Source: `~/IdeaProjects/goldenhour` (`release.sh`, `.github/workflows/deploy.yml`,
`docker-compose.yml`, `scripts/`), which deploys to the same host.

## Owner decisions (2026-11-07)

| Question | Decision |
|---|---|
| Host | **goldenhour's box**: `dockermacmini`, Ubuntu, `gregochr@100.76.73.16` (Tailscale), on the home LAN with the Hue bridge and DIRIGERA hub. |
| Database | **PostgreSQL in the existing shared `utils-pg` container** on the host (the misc database for one-off, low-volume apps). CineLight gets its own database and role there, not a container of its own. Flyway, Testcontainers. It holds event history and, later, settings. |
| Release flow | goldenhour's: `./release.sh` tags `master`, a tag push builds images to GHCR and deploys over SSH from a self-hosted runner. |

## Repository visibility (owner decision, 2026-11-07)

**`gregochr/plex-webhooks` is public**, made public in place. The owner judged the old values in history defunct
(the Hue key is dead, and the location no longer matters), so the planned rewrite into a new repo was dropped.
What follows from that:

- **Self-hosted runner safety (an invariant):** a workflow with `runs-on: self-hosted` must **never** trigger on
  `pull_request`, `pull_request_target` or `issue_comment`. Allowed triggers are only `push` of a tag,
  `schedule` and `workflow_dispatch`. Otherwise a fork's PR could run code on the host. goldenhour runs the
  same model. Also set Settings → Actions → "Require approval for all outside collaborators".
- D1's design still holds: checkout on the runner, no clone on the host. GHCR pulls could become anonymous if
  the package is made public, but the job-scoped `GITHUB_TOKEN` login works either way, so keep it.
- Now free: branch protection (require `Build, Test & Coverage`), CodeQL, and secret scanning with push
  protection. Each is a separate owner decision.

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
| B | **Nightly backups of all of `utils-pg`** (CineLight and wainwrights), off-box copy, a daily restore check, its own install workflow | D1's self-hosted runner (for install and verify only) |
| D2 | Postgres in `utils-pg`, event history, pre-release dump, rollback with restore | B, and React PR 1 merged (it introduces the history store D2 moves into Postgres) |
| D3 | Settings in Postgres (one-time import from the file), hot-reload kept | D2 |

D1 and the React PRs both touch `pom.xml`; whichever merges second rebases. Nothing else overlaps.

## D1: container and release pipeline (revised after review, 2026-11-07)

**Why it differs from goldenhour:** this repo was **private** when D1 was designed (it went public the same day,
and the design stands). goldenhour's deploy SSHes to the host and runs `git fetch` in a clone there; a clone of a
private repo with
no credentials cannot fetch. The self-hosted runner already sits on the host, so the SSH hop adds nothing.
**No `appleboy/ssh-action`, no `DEPLOY_SSH_KEY`, no clone on the host.**

**App changes (Java):**
- `spring-boot-maven-plugin` with `repackage` and `<finalName>cinelight</finalName>`, so `./mvnw package`
  produces `target/cinelight.jar`. Update `react-ui-plan.md`'s "no boot jar is packaged" line (planner).
- **External settings file, set only in the container.** Compose sets
  `SPRING_CONFIG_IMPORT=file:/config/application.properties` (**not** `optional:`, so a missing file fails startup
  with `ConfigDataLocationNotFoundException` naming the path) and `CINELIGHT_CONFIG_FILE` to the same path.
  When `cinelight.config-file` is set, `PropertiesRepository` reads and writes that file (`FileSystemResource`).
  When it is unset, today's classpath behaviour remains for development, and dev startup is unchanged.
- **Atomic, permission-preserving write.** Write a temp file in the **same directory**, copy the original's
  POSIX permissions onto it, `fsync`, then `ATOMIC_MOVE`. Compose bind-mounts the **directory**, never the file:
  a rename onto a bind-mounted file fails with `EBUSY`. Tests against `@TempDir`: the content round-trips,
  hot-reload fires, permissions are preserved, and a failed write leaves the original intact.
- **Framework config lives in a committed, secret-free `src/main/resources/application.yml`**, because
  `application.properties` is git-ignored and never reaches the image: the `management.*` keys and the app
  name. `PropertiesRepository` reads only the `.properties` file, so these keys never reach the dashboard.
  Note that `src/test/resources/application.properties` is force-tracked and shadows the main one in tests.
- **Health.** Add `spring-boot-starter-actuator`. Expose only `health`, `show-details: never`, and a `container`
  group with `ping` only, so a Hue, DIRIGERA or database outage never marks the container unhealthy.
  `SecurityConfig` gets `permitAll` on `/actuator/health/**`, then **`denyAll` on `/actuator/**`**, before the
  catch-all `anyRequest().permitAll()`, so an endpoint exposed later is not public by accident. Tests: an
  anonymous `GET /actuator/health/container` is 200 `UP`; `/actuator/env` is not 200.
- **Version:** the `APP_VERSION` and `GIT_COMMIT_SHA` build args become env vars and are logged at startup.

**Container and compose:**
- `Dockerfile`: builder **`eclipse-temurin:25-jdk`** (Ubuntu, not alpine: the React build's
  `frontend-maven-plugin` downloads a glibc Node that cannot run on musl), using `./mvnw package -DskipTests`.
  Runtime `eclipse-temurin:25-jre-alpine` plus `curl`, `EXPOSE 8080`, `HEALTHCHECK` on
  `/actuator/health/container`, and the label
  `org.opencontainers.image.source=https://github.com/gregochr/plex-webhooks`, which ties the package to this
  repo because the image name differs from the repo name.
- `.dockerignore`: `target/`, `.git`, `.claude/`, `frontend/node_modules/`, `.env`, and
  `src/main/resources/application.properties`. **CI test:** write a dummy `application.properties` containing a
  marker, build the image, and assert the marker is absent. A clean checkout never has the file, so testing
  without writing it first would prove nothing.
- `docker-compose.yml`: service `cinelight`, image `ghcr.io/gregochr/cinelight:latest`, `8080:8080`,
  **`user: "${CINELIGHT_UID}:${CINELIGHT_GID}"`** (gregochr's ids, from `.env`), so files the app writes in
  `/config` stay readable by the host user and by `rollback.sh` (the host has no non-interactive sudo),
  `env_file: ./.env`, the two config env vars, a bind mount of `/home/gregochr/cinelight-data/config:/config`,
  `restart: always`, log rotation (10m by 3), `stop_grace_period: 20s`, and the healthcheck.

**`.github/workflows/deploy.yml`** (on a `v*` tag; concurrency group literally `cinelight-deploy`, never
cancelled):
1. `test`: reuses `ci.yml` (add `workflow_call`). On a tag `event_name` is `push`, so the full build runs and
   diff-cover is skipped (verified).
2. `build-and-push` (ubuntu-latest, `packages: write`): `ghcr.io/gregochr/cinelight:{latest,<version>}` with
   the build args. Use the GitHub Actions layer cache (`cache-from/to: type=gha`).
3. `deploy` (self-hosted on the host, `packages: read`, job name **"Deploy to Production"**, which
   `release.sh` greps for): `actions/checkout` the tag, then log in to GHCR with `GITHUB_TOKEN` under a
   job-temporary `DOCKER_CONFIG`, so the host never stores a token. Then:
   - refuse to run over a rollback pin (`~/cinelight/docker-compose.override.yml`);
   - refuse to run if `~/cinelight-data/config/application.properties` is missing;
   - run `scripts/pre-release.sh <version>`: it writes `~/cinelight-data/releases/<version>/manifest` with the
     outgoing image digest (or `none` on the first deploy, when nothing is running yet) and a 0600 copy of the
     settings file;
   - copy `docker-compose.yml` from the checkout into `~/cinelight`;
   - `docker compose pull` and `up -d`;
   - run `scripts/prod-health.sh --deploy`, then `scripts/record-deployed-images.sh`.

   Every temporary file uses `mktemp`, never a fixed `/tmp` name: goldenhour's deploy uses fixed names on the
   same host, so a fixed name could be overwritten by the other deploy.
4. `notify-failure`, through `ci-failure-issue.yml`.

**Scripts** (ported from goldenhour, shellchecked in CI, with fixture tests where goldenhour has them):
- `release.sh` and `scripts/promote-changelog.sh`: `main` becomes `master`; the first release offers `1.0.0`.
  D1 **commits a `CHANGELOG.md` seed** with `## [Unreleased]`, because both scripts abort without one. The
  promotion PR does **not** use `gh pr merge --auto`: with no required checks that merges at once. The script
  waits until `Build, Test & Coverage` exists and has succeeded on the head SHA **and** Codex has given 👍,
  then merges with `--match-head-commit`. That keeps CLAUDE.md's merge rule for this PR too. Then it tags,
  pushes, watches the Deploy run, and notifies with a bell and macOS. **Only the owner runs it.**
- `scripts/prod-health.sh` (`--deploy` / `--daily`):
  - fails if compose health is not healthy, if `GET /actuator/health/container` is not `UP`, or if `GET /`
    is not 200;
  - **warns** if `/api/hue/status` shows the bridge unreachable (a public GET).
  - It has **no host checks** (reboot-required, apt, Tailscale): goldenhour's prod-health already runs them on
    this host, and running them twice would open duplicate issues.
- `.github/workflows/prod-health.yml`: daily on the self-hosted runner, plus goldenhour's off-box watchdog that
  catches a runner that never picks the job up.
- `scripts/record-deployed-images.sh`, and `scripts/rollback.sh` for images and the settings file: pin the
  previous digest in `docker-compose.override.yml` and restore the settings copy, after saving the current one
  aside.

**Docs (planner):**
- `dev-and-testing.md`: a "Release and deploy" section.
- `CLAUDE.md`: one line saying that releases are run by the owner with `./release.sh` and that sessions never
  tag.
- `AGENTS.md`: the deploy invariants. The settings file lives outside the image, the `container` health group
  excludes third parties, `/actuator/**` is denied except health, and nothing on the host clones the repo (the
  runner checks out the tag).

### Owner actions for D1 (before the first release)

1. On `dockermacmini`, register a **self-hosted runner for `gregochr/plex-webhooks`**, running as `gregochr`
   (in the `docker` group). A personal account's runner serves one repo, so goldenhour's cannot be reused. Use a
   second runner instance in its own directory, installed as a service.
2. On the host: `mkdir -p ~/cinelight ~/cinelight-data/config`, copy your current `application.properties` to
   `~/cinelight-data/config/`, and write `~/cinelight/.env` (mode 600) with `CINELIGHT_ADMIN_PASSWORD`, and
   `CINELIGHT_UID` and `CINELIGHT_GID` from `id -u` and `id -g`.
3. Port 8080 is free (checked 2026-11-07).
4. After the first deploy, point Plex's webhook at `http://<dockermacmini LAN IP>:8080/plex` and stop the old
   instance.

## B: back up all of `utils-pg` (protects CineLight and wainwrights; revised after review, 2026-11-07)

Ported from goldenhour's `backup-postgres.sh`, `goldenhour-backup.{service,timer}` and `backup-verify.yml`, which
exist because goldenhour's first backup failed silently for 132 nights. **This repo is public**, so no dump, row
or table name ever reaches a workflow log or artifact.

**Facts already established:** `docker exec utils-pg psql -U postgres` works with no password (the owner ran it,
2026-11-07: local socket trust in the official image). `postgres` is the only superuser, and a superuser is
required, because per-app `CONNECT` is revoked.

### Delivery: not through CineLight's deploy

The scripts live in `ops/utils-pg/` and are installed by **their own workflow**, `install-utils-pg-backup.yml`
(`workflow_dispatch` only, self-hosted). Tying them to CineLight's deploy would stop wainwrights' backups from
updating whenever CineLight is pinned or broken. The job:
- writes each script to a temp file **in** `~/services/utils-pg/backup/`, sets `chmod 755`, then `mv`s it into
  place. `cp` over a running bash script corrupts it, because bash reads by byte offset; `mv` swaps the inode
  and leaves the running copy intact;
- fails if `/etc/systemd/system/utils-pg-backup.{service,timer}` differ from the repo's, and prints the `sudo`
  commands to fix them. The units need `sudo`, so they never update themselves.

### `ops/utils-pg/backup.sh` (run as `gregochr` by the timer)

- `set -euo pipefail`, `umask 077`, backup folders mode 700, `flock`, so a manual run and the timer cannot
  overlap, and a `trap` that removes temp files. Temp names (`.partial-*`) never match the rotation or
  "newest" patterns. No `set -x`.
- **Precondition:** `docker exec utils-pg psql -U postgres -Atc 'select 1'` must print `1`.
- **Enumerate** `datallowconn and not datistemplate and datname <> 'postgres'`. **Fail if the list is empty or
  lacks `wainwrights`**: an empty list must never be a success.
- Per database: `docker exec utils-pg pg_dump -U postgres -Fc --lock-wait-timeout=60s -d <db>`, **never
  `exec -t`** (a terminal corrupts binary output). It writes to `daily/<db>/.partial-*`, relying on `pipefail` and the
  exit code. Then **`pg_restore -f /dev/null`** checks the file. That reads every data block; `--list` does not,
  so a dump truncated mid-data would pass it. Only then is the file renamed to `daily/<db>/<db>_<date>.dump`. A
  re-run the same day replaces that day's file.
- `pg_dumpall --globals-only --no-role-passwords` goes to `globals/`. The passwords live in each app's `.env`.
- Rotation per database: 7 daily, 4 weekly. A database that no longer exists keeps its dumps (never rotated
  away), and the script logs a warning.
- **Marker** (`~/backups/utils-pg/marker`): the timestamp, then one line per database with its name, dump
  size and live row total (the sum of `n_live_tup` from `pg_stat_user_tables` at dump time). It is written only
  if every database succeeded.
- **Off-box copy**, optional: `OFFBOX_DEST` comes from a host-only `EnvironmentFile` (`~/.config/utils-pg-backup.env`),
  never from this public repo. The script rsyncs the per-database tree **without `--delete`** and with
  `BatchMode`, so the copy accumulates, and pruning on the Mac is the owner's job. Keep goldenhour's
  `offbox-status` file and its 48-hour staleness check. A sleeping Mac is a warning, not a failure.
- Never touches goldenhour's own `goldenhour-db`.

### Units

`utils-pg-backup.service` (`Type=oneshot`, `User=gregochr`, `Requires=docker.service`, `TimeoutStartSec=30min`,
`Environment=TZ=Europe/London`, `EnvironmentFile=-%h/.config/utils-pg-backup.env`) and
`utils-pg-backup.timer` (`OnCalendar=*-*-* 02:30 Europe/London`, `Persistent=true`, `RandomizedDelaySec=10min`).
That is clear of goldenhour's 02:00 timer and its 03:00 cron.

### `ops/utils-pg/verify.sh` and `.github/workflows/utils-pg-backup-verify.yml`

The workflow runs daily at `30 7 * * *` UTC (clear of goldenhour's 08:00 verify), with `workflow_dispatch`, on
the self-hosted runner, and calls `verify.sh`. The logic lives in the script so CI can test it.

- **Drift:** the `sha256sum` of the installed scripts must match the checkout.
- **Freshness:** each database's newest dump is under 26 hours old, the marker's list of databases equals the
  live `pg_database` list, and a database present live but missing from the marker fails.
- **Restore each database** into a scratch server started from **`utils-pg`'s own image**
  (`docker inspect -f '{{.Image}}' utils-pg`), not a floating tag. It runs as
  `docker run --rm --network none --tmpfs /var/lib/postgresql/data --name utils-pg-verify-$GITHUB_RUN_ID`:
  no `-p`, and never goldenhour's `backup-verify-db` name. The tmpfs keeps restored data off the disk, and a
  plain `rm -f` would leave the image's anonymous volume behind. An `if: always()` step runs `docker rm -fv`.
- `pg_restore --no-owner --no-acl --exit-on-error`. **Every `pg_restore`/`psql` error goes to a mode-600 file
  on the host, never to the log**: a failed `COPY` prints row data. The log shows only the database name, the
  error count, the number of tables and the total rows. **No table names, no artifacts**, and none of
  goldenhour's `tail backup.log`.
- **Gate:** fail if the restored row total is below the marker's live total for that database, or zero while the
  live total was not. A legitimately empty new database (`cinelight` right after bootstrap) passes.
- **Globals:** the file is non-empty and contains `CREATE ROLE` (`psql -f` exits 0 on errors, so running it
  proves nothing).
- On failure, `ci-failure-issue.yml` opens an issue, plus goldenhour's off-box watchdog for a runner that
  never picks the job up.

### Tests

- Fixture tests with a fake `docker` on `PATH`: enumeration, the empty list and a missing `wainwrights`
  failing, temp-then-rename, a partial file never being "newest", rotation, a dropped database's dumps kept,
  the marker written only on full success, and that `set -x` and table names never appear in output.
- **A real-Postgres CI job on `ubuntu-latest`** (not self-hosted): start `postgres:16` as a container named
  `utils-pg`, load two databases (one named `wainwrights`), run `backup.sh` and `verify.sh` end to end, then
  truncate a dump and prove both scripts fail. Only that job proves authentication, a complete archive and a
  working restore.
- `shellcheck` over `ops/`.

### Owner actions

1. Run `install-utils-pg-backup.yml` (needs D1's runner registered).
2. Once, with `sudo`: copy the units to `/etc/systemd/system/`, `daemon-reload`, `enable --now` the timer.
3. Optional: `~/.config/utils-pg-backup.env` with `OFFBOX_DEST=…`.
4. `systemctl start utils-pg-backup` once by hand, then dispatch the verify and check it passes.

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
- **Backups:** nightly backups come from phase B. The deploy also dumps `cinelight` before pulling
  (`docker exec utils-pg pg_dump -U postgres -d cinelight -Fc`), so `rollback.sh` can restore it, with
  `--no-owner --role cinelight`.

## D3: settings in Postgres

- `V2__settings.sql`: key/value with `updated_at`. On first boot, an empty table is filled once from the
  properties file (logged, idempotent). After that the database is the source of truth and the file is no
  longer written.
- Keep every invariant: the write whitelist (`ConfigurationViewService`), `PropertiesUpdatedEvent` hot-reload,
  validation before write, and credentials never sent to the browser. The admin password stays an env var.
  The Hue application key moves into the database with the rest.
- **The hard part (from review):** `@Value` startup reads need settings in the `Environment` before beans exist,
  so a database-backed source means JDBC before the context starts, which conflicts with "start when the
  database is down". So D3 **writes through**: every save goes to the database *and* the file, and the file
  stays the startup source. The database is the queryable copy. Re-plan D3 in detail before building it.
- Rollback note: D3 is forward-only. Rolling back past it means restoring the pre-release dump, which
  `rollback.sh` does.

## Risks accepted

- **Same host as goldenhour.** A goldenhour incident or reboot takes CineLight down too. `restart: always`
  brings it back.
- **The LAN-only webhook has no TLS.** As today.
- **A self-hosted runner per repo** is a second process on the host to keep updated.
