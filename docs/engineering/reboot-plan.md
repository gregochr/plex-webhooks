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

### Bugs found in Phase 2 (fixed in the follow-up PR, branch `fix/phase2-bugs`)

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

#### Fix plan (2026-11-07)

1. **iOS detection.** Check `iPhone`/`iPad` before `Mac OS X` (iOS user agents contain "like Mac OS X"). Keep
   `Android` before `Linux`. Tests: real iPhone, iPad and macOS Safari user-agent strings.
2. **Interval validation, before the write.** `ConfigurationViewService` gains a value check for the three
   interval keys: a whole number of milliseconds from **1000 to 86 400 000** (1 s to 1 day; the upper bound keeps
   `now + interval` far from overflow). `PUT /api/config/properties` returns **400 and writes nothing** if any
   whitelisted key has an invalid value (no partial writes). Defence in depth: `ScheduledTaskManager` ignores an
   unparseable or out-of-range value in `onPropertiesUpdated`, logs a warning and keeps its current schedule.
   Tests: `abc`, empty, `0`, `-1`, `999`, `1000`, `86400000`, `86400001`, and a mixed valid + invalid body.
3. **One boolean parser.** A single helper with Spring's semantics, case-insensitive and trimmed: `true`, `on`,
   `yes`, `1` → true; anything else (including null, empty and garbage) → false. Used by
   `PlexCommandConfigFactory`, `PropertiesRepository.readBooleanProperties` and
   `PlexCommandConfig.onPropertiesUpdated`, so startup, the dashboard view and hot-reload always agree.
4. **No `Metadata`.** `PlexCommandFactory` returns an unrecognised command (`new PlexCommand(event, null)`) when
   `metadata` is null, so `execute` ignores it. Same for a null media type. Tests: the factory, and a
   `POST /plex` with a server-level payload returning 200 without touching Hue.
5. **Media types from the enum.** `VALID_MEDIA_TYPES` derives from `PlexMediaType.values()`, so `track` and
   `photo` are accepted and a new enum value never needs a second edit.

Existing tests that pin the old behaviour change to the new. The gates stay as they are: JaCoCo 95/90 per class,
PIT 98, diff-cover 90%.

## Phase 3 — JDK 25 and Spring Boot 4.1 (plan, 2026-11-07, reviewed)

One PR, branch `build/phase3-jdk25-boot4`, under the Phase 1–2 gates unchanged (JaCoCo 95/90 per class with no
overrides, PIT 98, diff-cover 90%). The target is what goldenhour already runs in production: **JDK 25, Spring Boot
4.1.2** (latest on Maven Central today), Spring Framework 7, Spring Security 7, Jackson 3, Tomcat 11, JUnit 6.

### Versions

| What | From | To | Why |
|---|---|---|---|
| `spring-boot-starter-parent` | 3.5.7 | 4.1.2 | goldenhour's version |
| `maven.compiler.release` (drop `source`/`target`) | 17 | 25 | |
| CI `java-version` in `ci.yml`, `pitest.yml`, `real-api-smoke.yml` | 17 | 25 | |
| JaCoCo | 0.8.14 | 0.8.16 | goldenhour's, full Java 25 support |
| PIT / pitest-junit5-plugin | 1.22.1 / 1.2.3 | 1.30.1 / 1.2.3 | goldenhour's, runs on JUnit 6 |
| SpotBugs plugin / FindSecBugs | 4.9.8.3 / 1.13.0 | 4.10.5.0 / 1.14.0 | goldenhour's |
| `spotbugs.version` (engine + annotations) | 4.9.3 | 4.10.5 | **blocker**: the 4.9.3 engine's ASM cannot read release-25 class files; the plugin bump alone leaves it in place |
| Checkstyle | 10.21.4 | 13.4.2 | needs JDK 21+, so it belongs here; supersedes Dependabot #20. **Unverified**: goldenhour runs the plugin's default 9.3, so nothing shows 13.x with plugin 3.6.0 yet. Watch `RedundantModifier` |

If a version above does not work, use the newest that does and say so; do not lower a gate to make it pass.

### Dependencies

- `spring-boot-starter-web` → `spring-boot-starter-webmvc` (renamed in Boot 4).
- Add `spring-boot-starter-webmvc-test` and `spring-boot-starter-security-test` (test scope): Boot 4 split the
  test auto-configuration into modules. `@WebMvcTest` and `@AutoConfigureMockMvc` move to
  `org.springframework.boot.webmvc.test.autoconfigure`.
- **Remove every hard-coded version that Boot manages**: `spring-boot-starter-validation` is pinned to `3.5.7`
  (a Boot 3 starter inside a Boot 4 app) and `log4j-api`/`log4j-core` are pinned to `2.24.3`.
- Remove `log4j-core` entirely and keep `log4j-api` unversioned. Nothing uses log4j-core: the app calls only
  log4j-api, and Boot's `log4j-to-slf4j` routes that to Logback, so routing does not change.
- Remove `spring-security-test`, which `spring-boot-starter-security-test` replaces.
- Remove the explicit `com.fasterxml.jackson.core:jackson-databind`. `hue-java-client` v1.0.0 still brings
  Jackson 2 transitively for its own use; Boot 4 still manages the Jackson 2 BOM, and that library stays
  untouched (it is Spring-free and on its own release cycle).

### Jackson 3: the trap

Spring Framework 7's `RestClient.create()` and MVC's converters use **Jackson 3 (`tools.jackson`) when it is on
the classpath**, and Boot 4's webmvc starter puts it there. Jackson 3 still reads the shared
`com.fasterxml.jackson.annotation` annotations (`@JsonProperty`, `@JsonIgnoreProperties`, `@JsonRawValue`), but it
**ignores Jackson 2's `com.fasterxml.jackson.databind.annotation.JsonDeserialize`**. So
`SunriseSunsetResponseDto.ResultsDeserializer` (blank string → null) would silently stop running: it compiles,
and only a test that goes through `RestClient` would notice.

So the app's own Jackson code moves to Jackson 3: `SunriseSunsetResponseDto` (deserializer becomes a
`tools.jackson.databind.ValueDeserializer` with Jackson 3's `@JsonDeserialize`), `PlexController`, `SseService`
and the tests that use `ObjectMapper`. `JsonProcessingException` becomes `JacksonException` (unchecked in 3):
keep the existing handling and logging behaviour. The proof already exists:
`SunriseSunsetClientTest.blankStringResultsHasNullResult` goes through `MockRestServiceServer` and the real
`RestClient`. Mutation-check it: break the deserializer (back up with `cp`) and watch that test go red.

Two more behaviour changes Jackson 3 would make silently, both of which this PR prevents:

- **Property order.** Jackson 3 sorts properties alphabetically by default (`MapperFeature.SORT_PROPERTIES_ALPHABETICALLY`
  is `true` in 3.x). Keep today's declaration order on the wire: build the app's own mappers with
  `JsonMapper.builder().disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)`, and if any payload goes through
  Boot's auto-configured mapper (MVC responses, `SseEmitter.send` of an object), disable it there with a
  `JsonMapperBuilderCustomizer` bean. **In code, not in `application.properties`**: the live properties file is
  the owner's and git-ignored. `SseEventDtoTest`'s exact-string assertions stay unchanged.
- **Malformed Plex payloads.** `JacksonException` is unchecked, so it no longer reaches
  `GlobalExceptionHandler.handleIOException`. Add a `JacksonException` handler that keeps today's status (500)
  and body, with a test, and update `PlexControllerTest`'s expectation of `JsonProcessingException`.
- **Stop Jackson 2 creeping back.** `hue-java-client` keeps Jackson 2 databind on the compile classpath, so a
  half-migrated file would still compile. Add `com.fasterxml.jackson.databind` and `com.fasterxml.jackson.core`
  to Checkstyle's `IllegalImport` (the shared `com.fasterxml.jackson.annotation` stays allowed).

### Other Boot 4 checks

- Mockito self-attaches its agent, and JDK 25 warns about that. Add `-XX:+EnableDynamicAgentLoading` to surefire's
  `argLine`, which must start with `@{argLine}` or JaCoCo's agent is dropped and the coverage gate breaks.
- Run once with `spring-boot-properties-migrator` against `application.properties.example` and fix any renamed
  key, then remove the migrator.
- `SecurityConfig` uses only string `requestMatchers`, `httpBasic` and `authorizeHttpRequests`, which Security 7
  keeps. `SecurityConfigTest` and `DashboardSecretsTest` must pass unchanged in what they assert.
- The `@SpringBootTest` (`DashboardSecretsTest`) must still prove no credential reaches the page, SSE or
  `/api/hue/status`.
- The app must still start: `./mvnw spring-boot:run` with the example properties, `GET /` returns 200, then stop
  it.
- Fix new compiler, Checkstyle 13 or SpotBugs 4.10 findings in code. A rule that Checkstyle 13 renamed or removed
  may be changed in `.checkstyle/checkstyle.xml` with a comment; do not switch rules off to get a pass.

### Docs (planner)

`CLAUDE.md`, `AGENTS.md`, `dev-and-testing.md` (including the SpotBugs line), `roadmap.md` and `docs/index.html`
("Requires Java 17+") move from Java 17 / Boot 3.5 to Java 25 / Boot 4.1.

### Owner action this creates

Whatever runs CineLight at home must run it on **JDK 25** after this merges.

## Owner actions

- **Create a new Hue application key on the bridge.** The old one is in git history (PR #21 only stops tracking it).
- **Replace the `NVD_API_KEY` secret.** The value set on 2026-11-07 came from `~/.zshrc`, and NVD rejects it
  (`Invalid API Key`; HTTP 404 with the key, 200 without). Goldenhour's secret holds a different, working key.
  Request a new key, set it on plex-webhooks (and goldenhour if wanted), and update `~/.zshrc`.
- **Confirm Codex reviews PRs on this repo**, or install it; the merge gate depends on it.
- Optional: GitHub Pro (or making the repo public after the key is replaced) would allow branch protection, which
  makes the CI gate binding rather than procedural.
