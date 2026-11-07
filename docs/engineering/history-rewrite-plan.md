# History rewrite before going public

Status: **planned 2026-11-07**. Owner decision: rotate the Hue key **and** rewrite history, then make the code
public. This file names no real values. The mapping with the real values (`replacements.txt`) exists only on
the owner's machine and is never committed.

## What is exposed (scanned 2026-11-07, every ref)

| Item | Where |
|---|---|
| Real Hue application key | the old `src/main/resources/application.properties` (first commits until PR #21), old `HueController`/`HueLightController` (hardcoded), and **8 current test files** used as a fixture |
| Bridge ID (`hue.hub-id`) | the old properties file and old controllers |
| Home postcode | the old properties file, **`application.properties.example`**, and **6 current test files** |
| Home coordinates (the postcode's centroid, plus one nearby point) | ~120 lines across history; current `LocationClient*Test`, `SunriseSunsetClient*Test` and their real-API tests |
| Everything else | clean: placeholders (`${…}`), test passwords, `GITHUB_TOKEN` references |

## Why a new repository, not a force-push

GitHub keeps `refs/pull/*/head` for every PR, closed or merged; there are 34 here, Dependabot's included. They
cannot be rewritten or deleted by the owner. A force-pushed `master` still leaves every old commit reachable
by those refs and by SHA, so flipping **this** repo to public would publish the old commits anyway, unless
GitHub Support purges them (a manual request with no fixed turnaround).

So: **push the rewritten history to a new public repo, `gregochr/cinelight`, and archive `plex-webhooks` as
private.** The old repo keeps its PR and issue history privately. The new name matches the image
(`ghcr.io/gregochr/cinelight`). It is the right moment: D1's runner, secrets and image are not set up yet.

## Steps

1. **Owner, now:** rotate the Hue key. Create a new application key on the bridge (press the link button,
   then `POST /api` with a `devicetype`, or re-pair through the app), put it in the live
   `application.properties`, and confirm the lights still work. Then **delete the old key**, the old app entry,
   under Philips Hue account → Apps, or with the bridge's whitelist API. Rotating alone leaves the old key valid.
   Also change the admin password (an outstanding owner action).
2. **PR "scrub fixtures"** (Sonnet, after D1 merges, because D1 touches some of the same tests): replace the
   real values in the current tree with public stand-ins. Postcode → `SW1A 1AA`. The home coordinates →
   `51.50101, -0.141563` (SW1A 1AA's real centroid, already used in a real-API test), and the nearby point to
   a second central-London point. The key and bridge ID become obviously fake strings. Real-API tests are
   re-run live against the stand-ins, and their expected values are updated. All gates stay green.
3. **Freeze:** no open PRs and no pushes. The React session finishes or commits its current PR first.
4. **Owner runs the rewrite** on a fresh mirror clone, never in a working checkout:
   - `git clone --mirror git@github.com:gregochr/plex-webhooks.git cinelight.git`
   - `git filter-repo --invert-paths --path src/main/resources/application.properties --replace-text ../replacements.txt`
     using the same mapping as step 2, so the old commits read like the scrubbed tree and the tip is
     byte-identical to step 2's `master`.
   - **Verify before publishing:** for each real value, `git log --all -S"<value>"` and
     `git grep "<value>" $(git rev-list --all)` find nothing, and `git diff <old master> <new master> --stat` is
     empty.
   - Create `gregochr/cinelight` **private** first, push the mirror, run CI there, and only then switch it to
     public.
5. **Cut over:** `gh repo archive gregochr/plex-webhooks`. Re-point every local clone and worktree with
   `git remote set-url origin …`. In-flight branches rebase onto the new `master` with
   `git rebase --onto <new master> <old master>` (the trees are identical, so there are no conflicts). Re-enable
   Codex and Dependabot, add `NVD_API_KEY`, create the labels, and turn on branch protection (now free:
   require `Build, Test & Coverage`). Add CodeQL (now free; skipped earlier because the repo was private).
6. **Docs:** update the repo name in `CLAUDE.md`, `AGENTS.md`, the workflows (`org.opencontainers.image.source`,
   the deploy notes) and `deploy-plan.md` (the private-repo reasoning still holds as design, but GHCR pulls
   become anonymous).
