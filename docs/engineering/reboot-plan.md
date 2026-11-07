# Reboot plan — testing and CI, ported from goldenhour

Started 2026-11-07. Owner decisions are recorded in the next section. Each phase is one PR, branched from `master`
after the previous phase merged, and follows the change process in `CLAUDE.md`: Opus plans and reviews, a Sonnet
subagent writes the code, and a PR merges only when CI is green and Codex has given 👍 on the head commit.

The plan was reviewed adversarially on 2026-11-07; its findings are folded in below (marked *review*).

## Owner decisions (2026-11-07)

| Question | Decision |
|---|---|
| JDK 25 + Spring Boot 4 (goldenhour's stack) | **Separate PR after this reboot** (Phase 3), so the upgrade lands under the new gates |
| Coverage bar | **Goldenhour's: 95% line and 90% branch per class**, plus 90% of changed lines on PRs |
| CodeQL | **Skipped.** The repo is private and CodeQL there needs GitHub Advanced Security. SpotBugs + FindSecBugs and OWASP cover static and dependency security |
| Working rules | Slim CLAUDE.md with detail in `docs/engineering/`; conventional commits with `changelog.d/`; Opus plans, Sonnet codes; Codex review gate |

## What goldenhour has that this repo adopts, and what it does not

Adopted, adapted to a single-module, private, database-free Spring Boot app:

- **Checks that always report.** CI runs on PRs against *any* base (stacked PRs included), and a docs-only diff skips
  the expensive steps rather than the job, so the merge gate always has checks to read on the head SHA.
- **Changed-lines coverage** with `diff-cover` at 90% on PRs, on top of the per-class floors.
- **Per-class JaCoCo floors** (line and branch), with no class excluded from checking.
- **OWASP done cheaply**: the NVD database is refreshed and cached on `master` only; a PR scans only when it touches
  dependency inputs, and normally reads the cache instead of downloading.
- **PIT off the PR path**: weekly and on demand, never blocking a PR.
- **Live third-party API tests out of the gate**: tag `real-api`, excluded by default, run weekly on their own.
- **Scheduled failures open an issue** (`ci-failure-issue.yml`).
- **Dependabot auto-merge** for minor and patch updates, only after every gate is green against current `master`.
- **Test standards** (`docs/engineering/test-standards.md`) and the build ladder.
- **The library from JitPack**, as goldenhour did with solar-utils, so no token is needed to build.

Not adopted, because there is nothing here for it to check: Stryker and the frontend gates (no JS build), ZAP (no
deployed target), Playwright (deferred until the React UI), Testcontainers (no database), ShellCheck (no scripts),
the deploy, backup and prod-health workflows.

## Constraints found while planning

- **No branch protection.** The repo is private on a free plan, so `master` cannot have required checks (GitHub
  returns 403) and `gh pr merge --auto` (goldenhour's auto-merge) cannot gate on CI. Nothing stops a red merge except
  the process; every merge, human or bot, must read the checks on the exact head commit first.
- **Paid Actions minutes.** The repo is private: weekly rather than per-push PIT, PR-scoped OWASP, the docs-only skip.
  A daily few-minute NVD refresh is well inside the 2,000 free minutes a month.
- **NVD key.** `NVD_API_KEY` was not set until 2026-11-07, so earlier OWASP runs downloaded NVD unauthenticated and
  rate-limited. It is now set (shared with goldenhour); each repo keeps its own cache.
- **No Actions caches yet**, so the first PR under the new OWASP design has no database to read (see Phase 1).
- **14 open Dependabot PRs** (#1–#20). Several are pom bumps Phase 1 makes anyway.
- **Codex.** The gate needs the Codex GitHub integration on this repo. It has not reviewed PR #21, so it may not be
  installed.

## Phase 0 — make PR #21 mergeable

Pushed 2026-11-07:
- `2d67aea`: the live-API tests and JitPack.
- `5897dd2`: 12 tests lifting PIT from 69% to 78% (it had never run, because `master`'s build was red).
- `7c1474f`: dependency-check 12.1.0 → 12.2.2, whose NVD client crashed.

Merged 2026-11-07 as `eea17b9`: build, integration and PIT green, Codex clean. OWASP was red on the invalid key and,
by the owner's decision, did not block the merge.

PR #21 (untrack `application.properties`, refresh CLAUDE.md) failed CI because `SunriseSunsetClientTest` asserted
exact times from the live sunrise-sunset.org API, which shifted by two minutes. The fix:
- tags the live tests of both clients (sunrise-sunset.org and postcodes.io) `real-api`, excluded through the
  `surefire.excludedGroups` property so a smoke run can override it;
- replaces their coverage with `MockRestServiceServer` unit tests (exact URLs, parsed fields, error paths);
- switches `hue-java-client` to JitPack (`com.github.gregochr:hue-java-client:v1.0.0`) and drops GitHub Packages
  auth from CI.

Local gate green on JDK 21: 238 tests, bundle line coverage 85.2%; the live tests also pass (7).

Known behaviour, left for Phase 2: sunrise-sunset.org returns `"results":""` on errors, which fails deserialisation,
so `SunriseSunsetClient` throws `RestClientException` (pinned by `emptyStringResultsThrows`).

## Security fix — secrets on the public dashboard (merged 2026-11-07, PR #22, `1c855e0`)

The fix also found the admin password's resolved value on the page, and the Hue key in clear on the public
`/api/hue/status` and its SSE broadcast; all three are closed. Not yet traced: `HueResourceController`'s pass-through
of the bridge's resource JSON, and the Plex webhook payload relayed on the SSE `message` event.

`GET /` is public (`SecurityConfig`), and its *App config* tab (`index.html`, `tab-app-config`, not gated by
`dashboard.admin-tab-enabled`) renders every property from `application.properties` through
`ConfigurationViewFormatter`, which hides only `hue.start-scene` and `hue.stop-scene`. So `hue.application-key` (full
control of the Hue bridge), `hue.hub-id` and `hue.hub-address` are in the page HTML for anyone on the network.
Password-named fields are masked on screen but their values are still in the source. The admin password itself is
not exposed: the file holds only its `${CINELIGHT_ADMIN_PASSWORD…}` placeholder.

Fix: never send secret values to the browser. A deny-list in `ConfigurationViewFormatter` for credential keys
(`hue.application-key`, anything matching `password`, `key`, `token`, `secret`), shown as "set / not set" and never
editable from the page; tests asserting the rendered HTML of `GET /` contains no secret value. Consider gating the
whole config tab behind authentication.

## Phase 1 — build and CI hardening

**pom.xml**
- Remove the `mockito-core` 2.7.7 pin (2017, overrides Boot's Mockito 5). Risk is low: tests use only `mock()`,
  `spy()`, `ArgumentMatchers` and `@MockBean`; watch varargs `any()`, whose semantics changed. Compare the surefire
  test count before and after.
- Remove `junit-platform-suite` (`1.11.0-M1` milestone), delete `UnitTestSuite`/`IntegrationTestSuite` and their
  surefire excludes, and **add a Boot-managed `junit-platform-launcher` test dependency**. The suite artifact
  currently brings in the launcher transitively, and pitest-junit5-plugin needs it (*review*).
- Remove the unused `org.immutables:value` dependency (*review*).
- Bump plugins to what the open Dependabot PRs offer, then close those PRs as superseded: SpotBugs plugin
  4.9.3.0 → 4.9.8.3 (#16), JaCoCo 0.8.12 → 0.8.14 (#9), pitest-junit5-plugin 1.2.1 → 1.2.3 (#14),
  maven-compiler-plugin → 3.15.0 (#13), dependency-check 12.1.0 → 12.2.2 (#18). SpotBugs 4.9.3 also fails when Maven
  itself runs on JDK 25 (the owner's default JDK); confirm the bump fixes that, otherwise the local gate needs
  `JAVA_HOME` on JDK 17–21. Close #7 and #19 (made moot by the removals above). Leave #20 (Checkstyle 13, a
  major) and #17 and #5 for review after Phase 1.
- Add a test for `Main` (`mockStatic(SpringApplication.class)`, as goldenhour's `GoldenHourApplicationTest` does,
  which needs Mockito 5), so `Main` is not a permanent override (*review*).
- Checkstyle: bind `check` to `validate` so style fails in seconds.
- JaCoCo: `report` at the `test` phase. `check` gets a default per-class rule (95% line, 90% branch) that excludes
  the override classes, plus one rule per override class at its own measured figure, rounded down to the percent.
  Measure from `target/site/jacoco/jacoco.csv` **after** the Mockito change and the `Main` test, and include nested
  `Outer$Inner` classes, which `<exclude>` matches by name. Every override carries a comment naming why. No class is
  excluded from checking altogether; a bundle rule stays as a backstop. (SpotBugs `effort` is already Max.)
- PIT: targets via a `pit.targetClasses` property so a run can be narrowed with `-D`; threshold stays 70.
- OWASP: the plugin moves out of its profile into the main build, never bound to `verify`; run with
  `dependency-check:check`. Suppression file stays `owasp-suppressions.xml`.

**Workflows** (`.github/workflows/`). All paths are at the repo root, not goldenhour's `backend/`. JDK 17. Every
workflow declares its `permissions`; callers of `ci-failure-issue.yml` grant `issues: write`, because the repo
default token is read-only.

- `ci.yml`: `pull_request` unfiltered, `push` to `master`, and `workflow_dispatch` (so a bot merge can start a
  `master` run: a merge made with `GITHUB_TOKEN` fires no `push` workflows). A `changes` job detects docs-only
  diffs with `git diff --no-renames --name-only` (a rename into `docs/` must not hide deleted code, *review*); it is
  fail-safe. One job *Build, Test & Coverage* runs `./mvnw clean verify`. On PRs, `diff-cover
  target/site/jacoco/jacoco.xml --src-roots src/main/java` at 90% with a step summary. The step **fails** if
  diff-cover reports "No lines with coverage information" while the diff touches `src/main/java`, since a wrong path
  mapping passes silently otherwise (*review*). JaCoCo and SpotBugs report artifacts; `timeout-minutes`. The separate
  integration job goes: `verify` already runs those tests.
- **OWASP is deferred (owner, 2026-11-07)** until a valid `NVD_API_KEY` is set: the key copied from `~/.zshrc` is
  rejected by NVD. Phase 1 removes the OWASP job from `ci.yml`, so PRs are not held red by it, and the pom keeps the
  plugin (12.2.2). `security-scan.yml` below lands in its own PR once the key works.
- `security-scan.yml` (deferred, see above): goldenhour's design (restore/save split so a failing scan still saves the database, single
  cached copy, daily schedule to keep the cache warm, PR-scoped by changed paths `pom.xml`, `owasp-suppressions.xml`,
  `mvnw`, `mvnw.cmd`, `.mvn/**`, the workflow itself), with `master` for `main`. One deliberate change (*review*):
  when a PR restores **no** cache, it downloads NVD itself (auto-update on, 60-minute limit) instead of failing.
  Otherwise the Phase 1 PR, which introduces the workflow, could never pass: `workflow_dispatch` cannot run on
  `master` until the file is there. A PR that bumps the dependency-check plugin may hit a database format change;
  it is re-run after a `master` refresh.
- `pitest.yml`: weekly (Monday) and `workflow_dispatch`; report artifact and score summary; notify on `failure` or
  `cancelled`.
- `real-api-smoke.yml`: weekly and on demand, `-Dsurefire.excludedGroups= -Dgroups=real-api`, and **assert the
  surefire test count is greater than 0**, so a mis-set exclusion cannot pass having run nothing (*review*). Notify
  on failure.
- `ci-failure-issue.yml`: reusable; one open issue per workflow, later failures comment on it.
- `dependabot-auto-merge.yml`, two jobs (*review*, all points):
  1. **Label**, `pull_request` (`opened`, `synchronize`, `reopened`) by `dependabot[bot]`: run
     `dependabot/fetch-metadata` (it needs the `pull_request` event and cannot run under `workflow_run`). Add the
     label `automerge-ok` for minor/patch, and **remove** it when a regenerated PR turns major. The step creates the
     label if missing.
  2. **Merge**, `workflow_run` of CI `completed`, filtered to `conclusion == success`, `event == pull_request`,
     `actor == dependabot[bot]` and a same-repo head. Uses `concurrency: {group: dependabot-merge,
     cancel-in-progress: false}` so merges are serial. Find the PR from `workflow_run.pull_requests`, falling back to
     `gh pr list --head <head_branch>`. Merge only if all of these hold:
     - the PR carries `automerge-ok`;
     - every check-run on the head SHA is complete and none failed, which covers both **CI and Security Scan**;
     - CI's base SHA equals the current `master` SHA. Otherwise a second Dependabot PR, green against the old
       `master`, would land an untested combination.

     Then `gh pr merge --squash --match-head-commit <sha>`, and `gh workflow run ci.yml --ref master` so the merged
     commit is built. A PR skipped for a stale base waits for its next CI run (Dependabot rebases it).
- `.github/dependabot.yml`: keep weekly Maven and Actions and the Spring and logging groups. Create the
  `dependencies` and `github-actions` labels it already references (the repo has only GitHub's defaults).

**Docs** (written by the planner): slim `CLAUDE.md`; `docs/engineering/dev-and-testing.md` (build ladder, gates,
profiles, what runs where); `docs/engineering/test-standards.md`; `changelog.d/README.md`.

**Done when, before merge:** `./mvnw clean verify` exits 0 locally and every check is green on the PR head; the
surefire test count is no lower than before the Mockito change, apart from the removed suite classes; the JaCoCo
override list matches `jacoco.csv`; a throwaway docs-only PR shows the skip step and green checks (then closed).

**Done when, after merge:** `security-scan.yml` dispatched on `master` saves an NVD cache; `pitest.yml` and
`real-api-smoke.yml` each pass once via `workflow_dispatch` (the smoke run reports more than 0 tests); one
minor/patch Dependabot PR auto-merges after green checks, and a red or stale-base one does not.

## Phase 1 — status

Merged 2026-11-07 as `f852399` (PR #23). Post-merge checks:
- the first `pitest.yml` run passed (80%);
- the first `real-api-smoke.yml` run passed (7 tests).

Still unproven: a Dependabot PR auto-merging (next Monday's batch). The superseded Dependabot PRs #7, #9, #13, #14,
#16, #18 and #19 are closed.

## Phase 2 — tests to goldenhour's bar

- Raise every class to 95% line and 90% branch and delete every JaCoCo override that can be deleted. An override
  survives only with a comment naming the line it cannot reach and why, as goldenhour's seven do (*review*).
- Fix the smells measured on 2026-11-07: `any()` in `verify()`/`when()` (11 uses in 3 files) where the value is
  knowable; 8 `@SpringBootTest` classes, each kept only if it needs the context; the 4 `now()` calls in `src/main`
  (`DirigeraStatusController`, `HueStatusController`, `SseEventDto`, `SseService`) take an injected `Clock`,
  tested with a clock pinned to a date that cannot be today.
- Move `@MockBean` to `@MockitoBean` (deprecated in Boot 3.4, removed in Boot 4, so it unblocks Phase 3).
- Decide `SunriseSunsetClient`'s handling of the `"results":""` error body.
- Boundary tests for every threshold (sunset comparison, Plex event/media-type gating, config validation).
- Run PIT locally (the codebase is small enough), kill survivors, and raise `pitest.mutation.threshold` to the
  measured score rounded down.

**Result (2026-11-07):**
- **Coverage:** all nine JaCoCo overrides are deleted; every class is ≥ 95% line and ≥ 90% branch.
- **Tests:** 301 → 552.
- **PIT:** 98.77% (241/244) on PIT 1.22.1; the threshold is now 98.
- **Smells:** no `@MockBean`, `lenient()`, `Thread.sleep`, `@Disabled`, or `any()` in `verify`/`when`.
  `DashboardSecretsTest` is the only `@SpringBootTest` left.
- **Production changes:**
  - `Clock` and a shared `TaskScheduler` are injected (`ClockConfig`, `SchedulingConfig`).
  - Test seams for the DIRIGERA socket, the SSE emitter and the properties resource.
  - sunrise-sunset.org's `"results":""` error body now reads as a null result instead of throwing.

### Bugs found in Phase 2, pinned by tests but not changed (follow-ups)

1. **iPhone/iPad Safari is reported as macOS.** `SseClient.getBrowserInfo()` checks `Mac OS X` before `iPhone`.
2. **Interval hot-reload is unvalidated.** A non-numeric value throws `NumberFormatException` from the event
   listener *after* the file is written; `0` or a negative value would fail in the scheduler. The config API should
   reject them before writing.
3. **Boolean parsing disagrees.** `PlexCommandConfigFactory` reads booleans through Spring (`yes`/`on` → true),
   while `PropertiesRepository.readBooleanProperties` and `PlexCommandConfig.onPropertiesUpdated` accept only
   `"true"`.
4. **`PlexCommandFactory` throws NPE when a payload has no `Metadata`**, for example server-level Plex events. The
   webhook must degrade gracefully.
5. **`ConfigurationViewService.isValidMediaType` rejects `track` and `photo`**, although the enum and the config
   have them.

## Phase 3 — JDK 25 and Spring Boot 4 (separate, later)

Under the Phase 1–2 gates. It needs JaCoCo ≥ 0.8.13 (Phase 1 already brings 0.8.14) and `@MockitoBean` (Phase 2).
Checkstyle 13 (#20) belongs here if it needs a newer JDK. Otherwise out of scope.

## Owner actions

- **Create a new Hue application key on the bridge.** The old one is in git history (PR #21 only stops tracking it).
- **Replace the `NVD_API_KEY` secret.** The value set on 2026-11-07 came from `~/.zshrc`, and NVD rejects it
  (`Invalid API Key`; HTTP 404 with the key, 200 without). Goldenhour's secret holds a different, working key.
  Request a new key, set it on plex-webhooks (and goldenhour if wanted), and update `~/.zshrc`.
- **Confirm Codex reviews PRs on this repo**, or install it; the merge gate depends on it.
- Optional: GitHub Pro (or making the repo public after the key is replaced) would allow branch protection, which
  makes the CI gate binding rather than procedural.
