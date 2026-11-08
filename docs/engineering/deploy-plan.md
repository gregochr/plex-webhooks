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
  size and **exact** live row total (`count(*)` over every user table, taken right after that database's dump;
  `n_live_tup` was rejected because it is an estimate that drifts high after deletes). It is written only if
  every database succeeded. One failing database does not stop the others from being dumped, but it fails the
  run and withholds the marker.
- **Off-box copy**, optional: `OFFBOX_DEST` comes from a host-only `EnvironmentFile` (`~/.config/utils-pg-backup.env`),
  never from this public repo. The script rsyncs the per-database tree **without `--delete`** and with
  `BatchMode`, so the copy accumulates, and pruning on the Mac is the owner's job. Keep goldenhour's
  `offbox-status` file and its 48-hour staleness check. A sleeping Mac is a warning, not a failure.
- Never touches goldenhour's own `goldenhour-db`.

### Units

`utils-pg-backup.service` (`Type=oneshot`, `User=gregochr`, `Requires=docker.service`, `TimeoutStartSec=30min`,
`Environment=TZ=Europe/London`, `EnvironmentFile=-/home/gregochr/.config/utils-pg-backup.env`) and
`utils-pg-backup.timer` (`OnCalendar=*-*-* 02:30 Europe/London`, `Persistent=true`, `RandomizedDelaySec=10min`).
`EnvironmentFile` uses the absolute `/home/gregochr/.config/utils-pg-backup.env`: in a system unit `%h` is
root's home, not the `User=`'s.
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
- **Gate:** fail if the restored row total is below **90%** of the marker's live total (writes can land between
  the dump and the count), or zero while the live total was not. A legitimately empty new database
  (`cinelight` right after bootstrap) passes.
- The scratch server's tmpfs is capped (`VERIFY_TMPFS_SIZE`, default `2g`), so a large restore fails loudly
  instead of squeezing the host's RAM. Raise it if a database grows past it.
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

### Design (refined 2026-11-07 against the code React PR 1 merged; revised after review; built 2026-11-07)

React PR 1 (#27) added `PlaybackHistory`: an in-memory ring of the last 100 media events, appended by
`PlaybackService.process` **inside the lock** that also assigns ids and broadcasts SSE, so two webhooks are
never recorded in one order and broadcast in another. `GET /api/playback/history?limit≤100` reads it, and the
React reducer merges events **by `id`**. D2 keeps all of that and adds durability behind it.

**1. The memory ring stays the live source; Postgres is a write-behind store.**
- `process` still appends to `PlaybackHistory` inside the lock. After the lock, it hands the event to a
  `PlaybackHistoryWriter`: one daemon thread with a **bounded queue** (1,000 events), which inserts it through
  a `PlaybackEventRepository` interface (its Postgres implementation uses `JdbcClient`). **The Plex webhook
  never waits on the database**, and a slow or dead database can never block the lights. A full queue drops
  the event and logs a rate-limited warning.
- **Lifecycle.** `@PreDestroy` stops accepting, drains the queue for at most 5 s (inside compose's
  `stop_grace_period: 20s`), and logs how many events it dropped. An `offer` after close returns quietly; it
  never throws into `process`.
- **Test seam.** The writer's loop body is a package-private `drainOnce()`, so unit tests drive it
  synchronously against a fake repository. Integration tests use Awaitility (in `spring-boot-starter-test`).
  No `Thread.sleep` anywhere.
- Reads (`recent(limit)`) still come from memory, so they work with the database down.
- **Seeding:** once the database is ready (see 3), the newest 100 rows (`ORDER BY id DESC`, never by
  `occurred_at`) go into one synchronized `PlaybackHistory.seed(rows)` that merges with events recorded since
  boot by `id`, sorts by `id`, and keeps the newest 100. One locked call, never a read followed by a write.
  A row whose `outcome` is not a known `PlexOutcome.Kind` is skipped with a warning; it never fails the seed.
- Pagination beyond 100 is out of scope. The table keeps 90 days for later use and for the owner's queries.
- `nowPlaying` is **not** restored after a restart: it describes the live session. Once D2 lands, history
  survives restarts, so the React rail's "since CineLight started" label is no longer true.
  The label isn't in the frontend yet (React PR 2b), so whichever of D2 and PR 2b lands second uses "Recent"
  (see `react-ui-plan.md`).

**2. Ids that survive a restart.** Today `nextId` restarts at 1, which would collide with stored rows and break
the reducer's merge by `id`. New rule inside the lock: `id = max(nextId, clock.millis() * 1000)`, then
`nextId = id + 1`. It stays below JavaScript's `MAX_SAFE_INTEGER` (about 1.8e15 against 9e15), so the
reducer's `b.id - a.id` is exact. The clock can go back, though (an NTP or RTC jump after a reboot), so:
- When the database becomes ready, `PlaybackService` reads `max(id)` and, **inside its lock**, raises
  `nextId` past it.
- The `id` is the table's primary key, and inserts use `ON CONFLICT (id) DO NOTHING`. An insert that affects
  0 rows logs a **warning** (it is a collision, not a duplicate we expect).
- Tests use a pinned `Clock`. Tests that assume ids 1 and 2 change: `PlaybackServiceTest` (around lines
  137-138) and `PlaybackHistoryTest` (around lines 60-73).

**3. Starting and working with the database down, or with no database configured.**
- **No database configured** (development, and every test that does not ask for one): when
  `spring.datasource.url` is unset, nothing database-related is created, and history is memory-only, exactly
  as today. A test asserts that this context starts and has no `DataSource` bean.
- **Auto-configuration.** Flyway comes from `flyway-core` plus `flyway-database-postgresql`, **not**
  `spring-boot-starter-flyway`, so there is no Flyway auto-configuration. JDBC comes from
  `spring-boot-starter-jdbc`, and its `DataSourceAutoConfiguration` is excluded in `application.yml` by its
  exact Boot 4 class name, `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`. Verify
  that name against the 4.1.2 jar: an exclude naming a class that is not on the classpath is ignored silently.
  Exclude the matching JDBC health and `JdbcClient`/`JdbcTemplate` auto-configurations only if they would
  otherwise create beans or fail without a `DataSource`. A test proves the no-database context is clean.
- **Configured:** a `@Configuration` conditional on `spring.datasource.url` builds everything **in code**,
  because with the auto-configuration excluded no `spring.datasource.hikari.*` key is bound. The
  `HikariDataSource` gets `setInitializationFailTimeout(-1)` (the pool never fails startup) and
  `setConnectionTimeout(3000)` (the 30 s default would stall a scheduler thread and `/actuator/health/db`).
  It also builds the `JdbcClient`, the `Flyway` instance and the Postgres repository.
- **Database configured but down:** a `ScheduledTaskManager` task runs `flyway.migrate()` **at once, then
  every 30 s** until it succeeds. It catches `RuntimeException` (`migrate()` throws `FlywayException`, not
  `DataAccessException`) and logs on a **change of state** only (first failure, recovery), not every 30 s.
  On success it sets `ready`, raises `nextId` (see 2), seeds the ring, runs retention once (see 5), and stops
  itself. Until `ready`, the writer drops events with a rate-limited warning. There is no backlog replay,
  because the ring still has them and history matters less than the lights.
- **After `ready`:** a `DataAccessException` logs, drops that event and carries on. If the table has vanished
  (SQL state `42P01`, for example `utils-pg` rebuilt empty), the writer clears `ready` and restarts the
  migration task, so the schema comes back without a restart.
- **`ScheduledTaskManager` changes**, because today it fits none of this. Its first run is one full interval
  after start, a task cannot stop itself, and it needs a `propertyKey`. Add an initial-delay option, a way
  for the task to stop its own schedule, and a constructor with no `propertyKey` (not hot-reloadable;
  `onPropertiesUpdated` ignores it). Existing callers and their tests keep their behaviour.
  `SchedulingConfig.POOL_SIZE` goes from 4 to 6 (migration plus retention), and its Javadoc lists them.
- Tests: the app with an unreachable database URL still turns a Plex play into a scene, and starts within
  normal time. History appears in the table once the database comes up. With no URL configured, no
  `DataSource` bean exists.

**4. Schema: `V1__playback_history.sql`.** `playback_event(id bigint primary key, occurred_at timestamptz not
null, event text not null, media_type text, title text, grandparent_title text, parent_index int, idx int,
year int, player text, outcome text not null, scene_id text, source text not null, payload jsonb,
command_title text, duration int)`, plus an index on `occurred_at`. `command_title` and `duration` are stored so
that a row read back rebuilds the legacy `command` object exactly (for unsupported media, `PlexCommand`'s title
is `""`, not the event's `title`) until PR 3c removes it. Only `media.*` events are stored, as in the ring. The
payload is the already-sanitised one (`PlexPayloadSanitizer`), inserted with `cast(:payload as jsonb)`.
`jsonb` reorders keys; that is accepted, since the dashboard renders the payload as a tree, not as text.

**5. Retention:** a `ScheduledTaskManager` task deletes rows older than 90 days (`occurred_at`). It runs once
when the database becomes ready, then every 24 h. Running it at ready matters: with a release more often
than daily, an interval-only task would never run.

**6. Tests run on embedded Postgres 16, not Testcontainers.** The owner's Mac has no Docker, and the gate
(`clean verify`) must run there. Use `io.zonky.test:embedded-postgres` with the PostgreSQL 16 binaries pinned
through `io.zonky.test.postgres:embedded-postgres-binaries-bom` 16.x, and declare the platform binaries
explicitly: `darwin-arm64v8`, `darwin-amd64` and `linux-amd64` (the defaults lack Apple Silicon). One
Postgres per JVM, shared by the database tests. If zonky cannot provide 16 for both platforms, stop and report;
do not fall back to H2.
- **PIT.** PIT's `targetTests` is `gregochr.*` and the build has about 4 survivors of headroom. Keep the logic
  (writer, seeding, id rule, retry state machine) testable with fakes of `PlaybackEventRepository`, and
  exclude the Postgres integration tests from PIT's test set, so mutation testing stays fast and the score
  holds.

**7. Wiring on the host.**
- `scripts/utils-pg-bootstrap.sh` (the owner runs it once; idempotent): creates the role `cinelight`
  (LOGIN, no superuser, no CREATEDB, no CREATEROLE) and the database `cinelight` owned by it, and revokes
  `CONNECT` from `PUBLIC`.
  - It runs `docker exec -i -e CINELIGHT_DB_PASSWORD utils-pg psql -U postgres -v ON_ERROR_STOP=1`, with the
    variable exported from `~/cinelight/.env`. Never `-t`. The password is never on a command line (no `ps`
    exposure), never echoed, and never spliced into SQL text, which would break on quotes.
  - The SQL starts with `SET log_statement = 'none'`, so the password is not written to Postgres's log. It
    reads the password with `\getenv pw CINELIGHT_DB_PASSWORD` and sets it with
    `ALTER ROLE cinelight PASSWORD :'pw'`.
  - The role and the database are created idempotently through `SELECT … WHERE NOT EXISTS … \gexec`, because
    `CREATE DATABASE` cannot run inside a `DO` block.
  - It prints the SQL to drop into `utils-pg/init/` for a rebuild from scratch, **without** the password.
  - The same SQL (minus the password) is reused by `rollback.sh --restore-db` to recreate the database.
- `docker-compose.yml`: the `cinelight` service joins `apps-net` (`external: true`) **as well as** its default
  network, and gets `SPRING_DATASOURCE_URL=jdbc:postgresql://utils-pg:5432/cinelight`,
  `SPRING_DATASOURCE_USERNAME=cinelight` and `SPRING_DATASOURCE_PASSWORD=${CINELIGHT_DB_PASSWORD}` from `.env`.
- **Health:** a `database` group (`GET /actuator/health/database`, Boot's `db` indicator), **not** in
  `container`. It is declared **only** in `docker-compose.yml` (`MANAGEMENT_ENDPOINT_HEALTH_GROUP_DATABASE_INCLUDE=db`),
  never in `application.yml`: Boot validates group membership at startup, and with no `DataSource` there is no
  `db` contributor, so a yml group would fail every test context and every dev boot. It cannot be named `db`
  either: Boot refuses to start when a group has a contributor's name (found in the build, 2026-11-07).
  `/actuator/health/**` is already public. `prod-health.sh` **warns** when `database` is not `UP`. With the
  database down the aggregate `/actuator/health` reports DOWN too; nothing reads it (Docker, compose and the
  scripts all read `container`), and that must stay so.
- **Deploy (`pre-release.sh`):** dumps the outgoing state with
  `docker exec -i utils-pg pg_dump -U postgres -Fc -d cinelight` (never `-t`, which corrupts binary output)
  into the release folder (mode 600). It checks the dump with `pg_restore -l` (or `-f /dev/null`) before
  recording it. It records one of three states, and warns on the last two rather than blocking the release:
  `db=dump` (dumped and checked), `db=none` (`utils-pg` is up but there is no `cinelight` database, the
  bootstrap has not been run) and `db=unavailable` (`utils-pg` is down or unreachable).
- **Rollback (`rollback.sh --restore-db`, never the default,** because restoring discards history written
  since the release). It refuses with a clear message when the release has no checked dump. With a dump, and
  with the app stopped:
  1. `DROP DATABASE cinelight WITH (FORCE)`, then recreate it with the bootstrap SQL. `pg_restore --clean`
     alone would leave behind objects a later migration created (D3's `V2`), and the next forward deploy would
     then fail its migration on every retry.
  2. `pg_restore --no-owner --role cinelight --exit-on-error --single-transaction`.
  3. If the restore fails, abort **before** starting the old image, and say so.
- The phase B nightly backups already pick up `cinelight` once it exists, with no change.

### Owner actions for D2 (after it merges, before the next release)

1. Add `CINELIGHT_DB_PASSWORD=…` to `~/cinelight/.env`.
2. Check who owns `apps-net`: `docker network inspect apps-net --format '{{json .Labels}}'`. It is expected to
   be the `utils-pg` compose project. If it is ever missing, CineLight's `docker compose up` fails (see Risks).
3. Run `scripts/utils-pg-bootstrap.sh` on the host (from a checkout of the tag, or the runner workspace).
4. Release as usual. CineLight starts even if step 3 was missed: history just stays memory-only, and
   `prod-health` warns.

## D3: settings copied to Postgres, with a change log (planned 2026-11-08)

### Owner decisions (2026-11-08)

| Question | Decision |
|---|---|
| What D3 is for | **A copy plus a change log.** Every dashboard save also goes to Postgres, with a log of what changed. The settings file stays the only thing the app starts from and reads. |
| Credentials | **Stay in the file.** No credential is ever written to the database, so none reaches a nightly dump or a release dump. |

This replaces the earlier "database becomes the source of truth" idea: `@Value` startup reads would need JDBC
before the context exists, which conflicts with "start when the database is down", and the owner chose the copy.

### What exists today (read 2026-11-08)

- `PropertiesRepository` reads and atomically rewrites one properties file (`cinelight.config-file`, or the
  classpath file in development). `ConfigurationViewService.saveProperties(updates)` writes the file, then publishes
  `PropertiesUpdatedEvent`. Its callers are the three `PUT /api/config/**` handlers in `ConfigController`.
- The writable keys are the whitelist in `ConfigurationViewService`: the `plex.<type>-<event>-enabled` toggles,
  `hue.start-scene`/`hue.stop-scene`, and four intervals. None is a credential.
- `readAllEntries` resolves values through the Spring `Environment`, which does **not** see a save until restart. So
  the copy must be built by parsing the file's text, never through the `Environment`.
- D2's `PlaybackDatabaseLifecycle` owns "migrate, retry every 30 s, then ready", and the `DataSource`, `JdbcClient`
  and `Flyway` beans live in `PlaybackDatabaseConfig`, conditional on `spring.datasource.url`.

### Design

**1. One database lifecycle, two users.** Move the shared part of D2 out of `gregochr.services.events` into
`gregochr.services.database`:
- `DatabaseConfig` (the conditional `DataSource`, `JdbcClient` and `Flyway` beans, unchanged behaviour) and
  `DatabaseLifecycle` (the migration task, its retry, state-change logging and 42P01 restart).
- On a successful migration, `DatabaseLifecycle` calls every `DatabaseReadyListener` bean in a fixed order
  (`@Order`), each in its own try/catch: one failing listener logs and does not stop the others or mark the
  database not ready. Playback (raise the id, mark the writer ready, seed, retention) becomes one listener. Settings
  becomes another.
- Any component may report "table missing" (SQL state 42P01) to restart the migration, as the playback writer does
  today.
- This is a refactor of D2's code: D2's behaviour and tests stay, moved and renamed where needed. The no-database
  context still has none of these beans.

**2. Schema: `V2__settings.sql`.**
- `setting(key text primary key, value text not null, updated_at timestamptz not null)`: the copy, one row per
  non-credential key in the file.
- `setting_change(id bigserial primary key, changed_at timestamptz not null, key text not null, old_value text,
  new_value text, source text not null)`, indexed on `changed_at`. `old_value` is null for a key first seen;
  `new_value` is null for a key that disappeared from the file. `source` is `dashboard` (a save) or `sync` (a
  difference found when the copy is brought up to date, for example a hand edit of the file or a save made while
  the database was down).
- No retention: the log is tiny (a few rows a week).

**3. Credentials never reach the database.** Every row is filtered through `SensitiveKeys.isSensitive` before it
is written, in the one class that writes both tables. The admin password is an env var, not in the file, and stays
so. A test puts every credential-shaped key in the file (the `SensitiveKeys` cases, including `hue.application-key`
and any DIRIGERA token key) and proves neither table ever holds it, by key or by value.

**4. The mirror: `SettingsMirror`** (in `gregochr.services.config`, one daemon thread, a bounded queue of 100,
the same shape as `PlaybackHistoryWriter`, including `drainOnce()` and a `@PreDestroy` drain of at most 5 s).
- **On a save:** `ConfigurationViewService.saveProperties` writes the file **first**, exactly as today, then offers
  the update map to the mirror. The offer never blocks, never throws and never fails the save, whether the database
  is down, missing or slow. With no database configured there is no mirror bean (`Optional`, as D2's writer).
- **Applying a save (mirror thread):** in one transaction, for each non-credential key, read the current row,
  upsert the new value, and insert a `setting_change` row (`source = dashboard`) only if the value really changed.
  `old_value` comes from the database row, not from the file. One thread applies saves in the order they were
  made.
- **On database ready (the settings `DatabaseReadyListener`):** parse the file's text (keys and values, ignoring
  comments and blank lines), drop credentials, and reconcile the table against it in one transaction. Upsert keys
  that are new or changed, delete keys no longer in the file, and log each difference as `source = sync`. A first
  run on an empty table logs every key as first seen (`old_value` null). This is what makes the copy right after a
  hand edit, or after saves made while the database was down. Then the mirror is marked ready.
- **Until ready** the mirror drops offers with a rate-limited warning. That is safe, because the sync at ready
  catches up from the file, which is always right.
- Failures after ready: a `DataAccessException` logs and drops that save (the next sync repairs the copy); 42P01
  restarts the migration through the lifecycle (point 1).
- The parse reuses `PropertiesRepository`'s file rules (first `=` splits; trim; `#` starts a comment). Add a
  `readFileEntries()` there that reads the file's text without the `Environment`, and use it for the sync.

**5. Reading it back.** No API or UI change. `scripts/settings-export.sh` (the owner runs it on the host) prints the
copy as `key=value` lines through `docker exec -i utils-pg psql -U postgres -d cinelight -At`, to rebuild or compare
a settings file. It never prints credentials, because there are none to print. With `--changes [N]` it prints the
last N (default 20) `setting_change` rows. ShellCheck clean, with fixture tests that stub `docker`.

**6. Tests** (embedded Postgres 16, tagged `postgres`, as D2; logic tested with a fake repository and excluded from
PIT the same way):
- A save writes the file, then (Awaitility) the row and one change row; saving the same value again adds no
  change row.
- The save succeeds and the file is written with the database down, unreachable, or not configured.
- A sync after a hand edit records `sync` rows for changed, added and removed keys; a second sync records none.
- Saves made before ready are caught up by the sync.
- Credentials never reach either table (point 3).
- Two listeners: a throwing settings listener does not stop playback becoming ready, and the other way round.
- The moved D2 tests still pass, and the no-database context has no database beans.

**7. Deploy and rollback.** Nothing new on the host: the database, role, dumps and nightly backups are D2's and
phase B's. `V2` is additive. Rolling back past D3 is D2's `rollback.sh --restore-db`, which drops and recreates
the database, so V2's tables never outlive a restore (that is why D2 restores that way).

### Owner actions for D3

None beyond D2's. After the first release with D3, `scripts/settings-export.sh` on the host should print the
settings, without credentials.

## Risks accepted

- **Same host as goldenhour.** A goldenhour incident or reboot takes CineLight down too. `restart: always`
  brings it back.
- **The LAN-only webhook has no TLS.** As today.
- **A self-hosted runner per repo** is a second process on the host to keep updated.
- **`apps-net` is external (D2).** If that network is missing, `docker compose up` fails and CineLight (and so
  the lights) does not start, for the sake of history. Accepted: `apps-net` belongs to the long-lived
  `utils-pg` project and wainwrights depends on it too. The fix is `docker network create apps-net` or bringing
  `utils-pg` up.
