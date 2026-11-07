# CLAUDE.md — CineLight (plex-webhooks)

CineLight turns Plex media-server webhook events into Philips Hue (and IKEA DIRIGERA) lighting changes: a movie
starts, the lights go down. Java 25, Spring Boot 4.1.2 (Framework 7, Jackson 3), Maven (`./mvnw`), Thymeleaf
dashboard, Spring Security 7 (stateless HTTP Basic), Server-Sent Events.

## ⚠️ Keep this file small

It is loaded into every session and subagent. Add a line here only for something a session must know *before it
opens any file*: a command, an invariant, a safety rule. Everything else goes in `docs/engineering/`:

| Topic | File |
|---|---|
| Builds, gates, CI workflows, what runs where | `docs/engineering/dev-and-testing.md` |
| How to write tests (read before touching any test) | `docs/engineering/test-standards.md` |
| The testing/CI reboot and its open phases | `docs/engineering/reboot-plan.md` |
| Deploy, release and Postgres phases | `docs/engineering/deploy-plan.md` |
| Roadmap (Apple TV, React UI) | `docs/engineering/roadmap.md` |
| Code review rules and deliberate decisions | `AGENTS.md` |

---

## ⚠️ How every code change is made (owner rule, 2026-11-07)

**Opus and Fable plan and review; Sonnet writes all the code.** A planning session never edits source, tests,
build files or workflows itself, not even a one-line fix. Docs (this file, `docs/`, `AGENTS.md`) may be written by
the planner.

1. **Plan, then review the plan adversarially**, with agents sized to the risk. A multi-phase change gets a plan
   doc under `docs/engineering/`.
2. **Delegate the build to a Sonnet subagent** (Agent tool, `model: "sonnet"`, its own worktree). The brief carries
   the plan, the files, the tests to write, the gates below and the `changelog.d/` entry. Sonnet runs the gates
   until green; the planner reviews the diff read-only and sends fixes back to Sonnet.
3. **Commit** on a feature branch (conventional commit, changelog entry).
4. **Push and open a ready PR** (not a draft) against `master`.
5. **Wait for CI and Codex, fix what they find** (through Sonnet), push again. Codex reviews a PR when it is opened
   and on an `@codex review` comment, **not** on later pushes: after a fix push, comment `@codex review`. 👀 means
   running (never merge on it), 👍 is a clean review, comments are findings.
6. **Merge (squash) only when CI is green and Codex has given 👍 on the exact head commit.** Re-read both
   immediately before merging, and merge with `gh pr merge --squash --match-head-commit <sha>`. There is no branch
   protection on this private repo, so this step *is* the gate.
7. **Multi-phase plans repeat 2–6 per phase**, each branched from the updated `master`.

Never done by a session: creating or deleting tags, pushing to `master`, force-pushing a shared branch, running
`./release.sh` (the owner releases; see `docs/engineering/dev-and-testing.md` "Release and deploy").

---

## The build ladder (gate on Maven's exit code, never on a grep of its output)

```bash
./mvnw compile -q                                            # seconds
./mvnw test -Dtest=SomeClassTest -q                          # one class
./mvnw checkstyle:check                                      # style only
./mvnw clean verify --batch-mode --no-transfer-progress      # the CI gate
```

`clean verify` is exactly CI's `Build, Test & Coverage` job: Checkstyle (at `validate`), all unit and integration
tests, JaCoCo (**95% line and 90% branch for every class**, no exceptions; 80% bundle), and
SpotBugs + FindSecBugs (High). PRs also need **90% of changed `src/main/java` lines** covered (`diff-cover`). Run
Maven in the foreground. Detail, plus PIT, the live-API tests and OWASP: `docs/engineering/dev-and-testing.md`.

- `application.properties` is git-ignored: copy `src/main/resources/application.properties.example`. The admin
  password comes from `CINELIGHT_ADMIN_PASSWORD` (dev fallback `changeme`).
- `hue-java-client` resolves from JitPack (`com.github.gregochr:hue-java-client:v1.0.0`); no token needed.

---

## Cross-cutting invariants

- **Credentials never reach the browser.** `SensitiveKeys` defines what a credential is; the dashboard, the APIs
  and SSE carry only "set / not set". `GET /`, the SSE stream and the status APIs are public.
- **Hot-reload, no restarts.** Config changes go through `PropertiesRepository` and publish
  `PropertiesUpdatedEvent`; listeners update `volatile` fields. Only whitelisted keys are writable
  (`ConfigurationViewService`).
- **Graceful degradation.** A Hue, DIRIGERA or third-party failure logs and carries on; it never crashes the app or
  fails the Plex webhook.
- **Periodic work uses `ScheduledTaskManager`**; do not invent another scheduling pattern.
- **`hue-java-client` stays Spring-free.** Bridge-API concerns go there; application concerns in
  `gregochr.controllers.hue`.
- **Live network calls only in `@Tag("real-api")` tests**, which the gate excludes.

---

## Code standards and git

- Checkstyle (`.checkstyle/checkstyle.xml`, Google style with 4-space indent) and SpotBugs (High) fail the build.
  Suppress a SpotBugs finding only with a written justification (`spotbugs-exclude.xml`).
- Conventional commits: `feat:`, `fix:`, `test:`, `build:`, `ci:`, `docs:`, `refactor:`, `chore:`.
- Every meaningful change adds its own `changelog.d/YYYYMMDD-<slug>.md` (see `changelog.d/README.md`).
- Never commit `application.properties` or any secret.
- When you change behaviour a `docs/engineering/` file describes, update that file in the same PR.
- **When mutating source to prove a test can fail, never restore with `git checkout --`, `git restore` or `git
  stash`**: they silently delete uncommitted work. Back up and restore with `cp`.
