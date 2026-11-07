#!/bin/bash
# Tag a commit on master as a release version and push it,
# triggering the GitHub Actions deploy pipeline.
#
# Asks ONE question: the version. The commits since the last tag are printed
# first, so choosing the version is the go-ahead — the CHANGELOG promotion, tag
# and push then run without further prompts. It offers three versions — the
# next major, patch and minor — as 1/2/3 (Enter takes the patch). With no tag
# yet it offers 1.0.0.
#
# ONLY THE OWNER RUNS THIS. Sessions (including Claude sessions) never tag, never push
# to master and never run this script: it tags, pushes and starts a production deploy.
#
# Usage: ./release.sh [VERSION] [--target REF] [-m MESSAGE]
#   VERSION       skip the question (e.g. ./release.sh 1.0.0)
#   --target REF  tag an earlier commit on master instead of HEAD
#   -m MESSAGE    tag message (default: "Release vVERSION")
#
# Safety checks:
#   - must be on master, working tree clean
#   - syncs with origin/master before tagging, and refuses to run when local master is
#     ahead (unpushed commits get swept into the CHANGELOG promotion PR)
#   - if origin has moved a tag since the last fetch (e.g. a release re-tagged onto a
#     later commit after a failed deploy), updates the local tag to match instead of
#     failing silently
#   - target commit defaults to HEAD (--target to override)
#   - verifies target is reachable from master
#   - shows commits between last tag and target for review
#   - confirms tag doesn't already exist
#   - promotes CHANGELOG's [Unreleased] entries to the version being tagged,
#     via a docs-only PR that this script merges ITSELF once `Build, Test & Coverage`
#     has succeeded on the PR head and Codex has given it a thumbs up (never
#     `gh pr merge --auto`: this repo has no required checks, so auto-merge would
#     merge at once), and refuses to tag if they are still unpromoted or if nothing
#     is documented for the version
#   - stops to ask only if master gained an unexpected commit during the promotion
#   - after pushing the tag, watches the Deploy workflow and reports (with a bell
#     and a macOS notification) when production is deployed or the deploy fails

set -euo pipefail

# 0. Arguments
VERSION=""
TARGET="HEAD"
MESSAGE=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --target) TARGET="${2:?--target needs a commit ref}"; shift 2 ;;
        -m)       MESSAGE="${2:?-m needs a message}"; shift 2 ;;
        -h|--help) sed -n '2,/^set -euo/p' "$0" | sed '$d; s/^# \{0,1\}//'; exit 0 ;;
        -*)       echo "Error: unknown option $1 (see ./release.sh --help)"; exit 1 ;;
        *)        VERSION="$1"; shift ;;
    esac
done

# 1. Branch check
BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [[ "$BRANCH" != "master" ]]; then
    echo "Error: not on master (current: $BRANCH)"
    echo "Releases tag from master only. Switch with: git checkout master"
    exit 1
fi

# 1.5. Anchor to the repo root.
#
# The promotion block reads and rewrites CHANGELOG.md by relative path. Run from a
# subdirectory, that resolves to nothing — and it would fail AFTER the branch had been
# created and checked out, stranding the user mid-promotion with no message of its own.
# Every git command here is already root-relative, so this only ever helps.
cd "$(git rev-parse --show-toplevel)"

# 2. Clean working tree check
if [[ -n "$(git status --porcelain)" ]]; then
    echo "Error: working tree not clean. Commit or stash first."
    git status --short
    exit 1
fi

# 3. Sync with remote
#
# `--quiet` used to hide a real failure here (goldenhour, from which this script is ported). A release
# whose first deploy failed was re-tagged onto a fixed commit and origin's tag was force-pushed to point
# there; the next `git fetch origin --tags --quiet` then rejected the tag update with
# "! [rejected] v2.22.2 -> v2.22.2 (would clobber existing tag)" and exited 1 — a real
# error `--quiet` swallowed, leaving nothing on screen but "Fetching from origin..."
# before the script silently exited. Origin is authoritative for tags (the Deploy
# workflow runs off origin's tags, and this script is the only thing that pushes them),
# so a clobbered tag is reconciled here rather than left to error: each rejected tag is
# force-updated individually to match origin, never a blanket --force across every tag,
# and a local-only tag origin doesn't have is left untouched.
fetch_tags_from_origin() {
    local fetch_out clobbered tag local_sha
    if fetch_out=$(git fetch origin --tags 2>&1); then
        return 0
    fi

    clobbered=$(printf '%s\n' "$fetch_out" | awk '/would clobber existing tag/ {print $5}')
    if [[ -z "$clobbered" ]]; then
        echo "Error: git fetch origin failed:"
        echo "$fetch_out"
        exit 1
    fi

    while IFS= read -r tag; do
        [[ -z "$tag" ]] && continue
        local_sha=$(git rev-parse --verify "$tag" 2>/dev/null || echo "")
        git fetch origin --force "refs/tags/$tag:refs/tags/$tag"
        echo ""
        echo "Notice: origin's tag $tag has moved since it was last fetched here —"
        echo "updating the local tag to match. Origin is authoritative: the Deploy"
        echo "workflow runs off origin's tags, and this script is the only thing that"
        echo "pushes them."
        [[ -n "$local_sha" ]] && echo "  local (old): $(git log -1 --oneline "$local_sha")"
        echo "  origin (now local): $(git log -1 --oneline "$tag")"
    done <<< "$clobbered"

    # Re-run the plain fetch: everything else it brings down (branches, other tags)
    # still needs fetching, and this also surfaces any OTHER failure that was hiding
    # behind the clobber (network blip, auth) which the targeted updates above did not fix.
    if ! fetch_out=$(git fetch origin --tags 2>&1); then
        echo "Error: git fetch origin failed:"
        echo "$fetch_out"
        exit 1
    fi
}

echo "Fetching from origin..."
fetch_tags_from_origin

# "Differs from origin/master" is three states, not one, and only one of them is safe
# to fast-forward. Behind is the ordinary case. Ahead is fatal: the promotion block
# below cuts its branch from LOCAL master, so an unpushed commit rides into the
# changelog PR and comes back squashed, leaving master un-fast-forwardable at step 9.
# `git pull --ff-only` does not catch this — on an ahead branch it prints
# "Already up to date." and exits 0, which is exactly how a stray commit got into
# a goldenhour promotion PR.
COUNTS=$(git rev-list --left-right --count master...origin/master)
AHEAD=${COUNTS%%[[:space:]]*}
BEHIND=${COUNTS##*[[:space:]]}

if [[ "$AHEAD" -gt 0 ]]; then
    echo "Error: local master is $AHEAD commit(s) ahead of origin/master."
    echo ""
    git log --oneline origin/master..master
    echo ""
    echo "Release branches are cut from local master, so these would be swept into"
    echo "the CHANGELOG promotion PR and squash-merged under its commit message."
    echo "Push them first, or drop them with: git reset --hard origin/master"
    exit 1
fi

if [[ "$BEHIND" -gt 0 ]]; then
    echo "Local master is $BEHIND commit(s) behind origin/master. Fast-forwarding..."
    git pull --ff-only origin master
fi

# 4. Target commit (default HEAD; --target to override)
TARGET_SHA=$(git rev-parse --verify "$TARGET" 2>/dev/null || true)
if [[ -z "$TARGET_SHA" ]]; then
    echo "Error: '$TARGET' is not a valid commit reference"
    exit 1
fi

# Verify target is reachable from master (don't tag commits that aren't on master)
if ! git merge-base --is-ancestor "$TARGET_SHA" master; then
    echo "Error: $TARGET ($TARGET_SHA) is not reachable from master"
    echo "Releases tag commits on master only."
    exit 1
fi

# 5. Show what's about to be tagged
CURRENT=$(git tag --list 'v[0-9]*' | sort -V | tail -1 || true)
CURRENT=${CURRENT:-none}
echo ""
echo "Current latest tag: $CURRENT"
echo "Target commit:      $(git log -1 --oneline "$TARGET_SHA")"
echo "Target date:        $(git log -1 --format=%cI "$TARGET_SHA")"
echo ""

if [[ "$CURRENT" != "none" ]]; then
    COMMIT_COUNT=$(git rev-list --count "$CURRENT".."$TARGET_SHA")
    if [[ "$COMMIT_COUNT" -eq 0 ]]; then
        echo "No new commits between $CURRENT and target. Nothing to release."
        exit 1
    fi
    echo "Commits between $CURRENT and target ($COMMIT_COUNT total):"
    git log "$CURRENT".."$TARGET_SHA" --oneline --reverse
else
    echo "No previous tag found. Showing recent commits up to target:"
    # `| head` has the same SIGPIPE-under-pipefail hazard as the grep below; -n caps
    # the output at the source instead of killing the writer mid-stream.
    git log "$TARGET_SHA" --oneline --reverse -n 20
fi
echo ""

# 6. Version — the one question. Offers major / patch / minor bumps of the latest
# tag; answer 1-3, Enter for the patch, or type any x.y.z.
if [[ -z "$VERSION" ]]; then
    MAJOR_V="" PATCH_V="" MINOR_V=""
    if [[ "$CURRENT" == "none" ]]; then
        # The first release: there is nothing to bump, so offer 1.0.0 as the default.
        echo "No previous release. First release:"
        printf "  1) %-9s first release (default)\n" "1.0.0"
        PATCH_V="1.0.0"
        MAJOR_V="1.0.0"
        MINOR_V="1.0.0"
    elif [[ "$CURRENT" =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
        MAJOR_V="$((BASH_REMATCH[1] + 1)).0.0"
        PATCH_V="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.$((BASH_REMATCH[3] + 1))"
        MINOR_V="${BASH_REMATCH[1]}.$((BASH_REMATCH[2] + 1)).0"
        echo "Next version:"
        printf "  1) %-9s major\n" "$MAJOR_V"
        printf "  2) %-9s patch (default)\n" "$PATCH_V"
        printf "  3) %-9s minor\n" "$MINOR_V"
    fi
    read -r -p "Choose a number, Enter for ${PATCH_V:-?}, or type a version (Ctrl-C to stop): " VERSION
    case "$VERSION" in
        1) VERSION="$MAJOR_V" ;;
        2|"") VERSION="$PATCH_V" ;;
        3) VERSION="$MINOR_V" ;;
    esac
fi

if [[ "$VERSION" =~ ^v ]]; then
    echo "Error: don't include the 'v' prefix — just the version number e.g. 1.0.0"
    exit 1
fi

if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Error: version must be in format x.y.z e.g. 1.0.0"
    exit 1
fi

# 7. Tag-already-exists check
if git rev-parse "v$VERSION" >/dev/null 2>&1; then
    echo "Error: tag v$VERSION already exists at $(git rev-parse --short "v$VERSION")"
    echo "Delete it first if you really mean to retag:"
    echo "  git tag -d v$VERSION && git push origin :refs/tags/v$VERSION"
    exit 1
fi

# 7.5. CHANGELOG guard, with automatic promotion
#
# Release notes arrive from TWO places now: one file per change under changelog.d/
# (the convention — it exists so two open PRs never conflict on the [Unreleased]
# line; see changelog.d/README.md) and, for stragglers or history, entries written
# directly under [Unreleased]. The promotion folds both under the new version
# heading via scripts/promote-changelog.sh.
#
# Without this, [Unreleased] silently accumulates across releases and every set of
# release notes becomes indistinguishable from the next. That is exactly what happened
# in goldenhour — 83 entries across 13 releases piled under one heading, and untangling
# them afterwards needed a git-archaeology pass over the file's own history.
#
# The promotion is mechanical — insert one dated heading directly below [Unreleased],
# changing no entry text — so the script performs it rather than printing instructions
# for a hand-made PR. That hand-made PR was the whole cost of the guard: it turned every
# release into two, and its CHANGELOG edit reliably conflicted with any feature branch
# that had also written under [Unreleased].
#
# It goes through a PR, and the script merges that PR itself, under CLAUDE.md's merge rule: CI green
# and Codex's thumbs up on the EXACT head commit. This repo is private on the free plan, so there is no
# branch protection: nothing but this script and the owner's discipline stands between a red or
# unreviewed commit and master, which is why the merge is gated here and not left to GitHub.
#
# NOT `gh pr merge --auto`. With no required status checks, auto-merge has nothing to wait for and
# merges the instant the PR is mergeable — before `Build, Test & Coverage` has even started.
# The script waits explicitly instead, until BOTH hold on the PR's head SHA:
#   - the `Build, Test & Coverage` check run exists and its conclusion is `success`
#     (ci.yml reports it, skipping its steps, for a docs-only change; the promotion PR is
#     docs-only, so this takes a minute or two); and
#   - chatgpt-codex-connector[bot] has reacted with a thumbs up on the PR.
# Then it merges with `--squash --match-head-commit <sha>`, which GitHub refuses if the head moved
# since it was checked.
#
# Checked against the TARGET COMMIT, not the working tree: the tag ships whatever
# CHANGELOG.md looks like at that commit.

unreleased_body_at() {
    git show "$1:CHANGELOG.md" 2>/dev/null | awk '
        /^## \[Unreleased\]/ { inside = 1; next }
        /^## \[/            { inside = 0 }
        inside && NF          { print }
    '
}

CHANGELOG_AT_TARGET=$(git show "$TARGET_SHA:CHANGELOG.md" 2>/dev/null || true)
if [[ -z "$CHANGELOG_AT_TARGET" ]]; then
    echo "Error: no CHANGELOG.md at $TARGET"
    exit 1
fi

UNRELEASED_BODY=$(unreleased_body_at "$TARGET_SHA" || true)

# Entries also arrive as one file per change under changelog.d/ (see its README) —
# the convention that ended the every-two-PRs-conflict on the [Unreleased] line.
# Read at the TARGET commit like the body above; the fold itself runs on the working
# tree, which the target-is-master-HEAD and clean-tree guards make the same content.
#
# Refuse FIRST on anything in the directory that is not a dated entry. The fold's
# glob matches exactly YYYYMMDD-<slug>.md, so a dot-prefixed or misnamed tracked file
# is invisible to it while remaining visible to the accounting here — promoting would
# insert the heading, leave that file behind, trip the post-promotion leftover check,
# and the duplicate-heading guard would then block a retry at this version. The helper
# re-checks this on the promotion branch; checking here as well fails BEFORE any
# branch exists, so nothing is left to clean up.
STRAY_AT_TARGET=$(git ls-tree -r --name-only "$TARGET_SHA" -- changelog.d 2>/dev/null | grep -v 'README\.md$' | grep -Ev '^changelog\.d/[0-9]{8}-[^/]+\.md$' || true)
if [[ -n "$STRAY_AT_TARGET" ]]; then
    echo "Error: changelog.d/ contains files that are not YYYYMMDD-<slug>.md entries:"
    printf '%s\n' "$STRAY_AT_TARGET" | sed 's/^/  /'
    echo "The release fold would not see them and the release would wedge at its"
    echo "leftover check. Rename or remove them first (see changelog.d/README.md)."
    exit 1
fi
PENDING_AT_TARGET=$(git ls-tree -r --name-only "$TARGET_SHA" -- changelog.d 2>/dev/null | grep -E '^changelog\.d/[0-9]{8}-[^/]+\.md$' || true)

if [[ -n "$UNRELEASED_BODY" || -n "$PENDING_AT_TARGET" ]]; then
    DIRECT_COUNT=0
    if [[ -n "$UNRELEASED_BODY" ]]; then
        DIRECT_COUNT=$(printf '%s\n' "$UNRELEASED_BODY" | grep -c '^### ' || true)
    fi
    PENDING_COUNT=0
    if [[ -n "$PENDING_AT_TARGET" ]]; then
        PENDING_COUNT=$(printf '%s\n' "$PENDING_AT_TARGET" | grep -c '.' || true)
    fi
    ENTRY_COUNT=$((DIRECT_COUNT + PENDING_COUNT))
    PROMOTE_BRANCH="docs/changelog-v$VERSION"
    TODAY=$(date +%Y-%m-%d)

    echo ""
    echo "Pending release notes: $PENDING_COUNT changelog.d file(s) + $DIRECT_COUNT direct [Unreleased] entr(y/ies)."
    echo "These must be promoted to v$VERSION before tagging, or they will be"
    echo "attributed to whichever release happens to be cut next."
    echo ""

    # Refuse if v$VERSION is ALREADY promoted at the target. Every other guard here is
    # blind to this: inserting a second identical heading still adds exactly 2 lines and
    # removes 0, still leaves [Unreleased] empty, and the version check below is `grep -q`,
    # which matches the first of the two. So without this the script would tag a CHANGELOG
    # whose notes are split across two same-named sections carrying different dates —
    # precisely the attribution corruption section 7.5 exists to prevent.
    #
    # Reachable without any race, because the script's own exits create the state: the
    # promotion PR merges to master, and then the run stops before tagging — the user answers
    # "n" at the final confirmation, or Ctrl-Cs the poll, or hits the 15-minute timeout
    # whose message says "merge it, then re-run ./release.sh". master now carries the heading
    # with no tag. Later entries land under [Unreleased], the user re-runs at the same
    # version (correctly — step 7 confirms the tag is still free), and the duplicate lands.
    #
    # Here-string, not `printf … | grep -q`, for the SIGPIPE reason documented at the
    # version check further down.
    if grep -q "^## \[v$VERSION\]" <<< "$CHANGELOG_AT_TARGET"; then
        echo "Cannot promote: CHANGELOG.md already documents v$VERSION at the target."
        echo ""
        grep -n "^## \[v$VERSION\]" <<< "$CHANGELOG_AT_TARGET" | sed 's/^/  /'
        echo ""
        echo "An earlier run promoted this version but never tagged it, and new entries"
        echo "have accumulated under [Unreleased] since. Promoting again would create a"
        echo "SECOND '## [v$VERSION]' heading and split the notes between them."
        echo ""
        echo "Either tag v$VERSION as it stands (clear [Unreleased] by folding those"
        echo "entries into the existing section by hand), or release them under a new"
        echo "version number."
        exit 1
    fi

    # Promotion adds a commit ON TOP of master. If the target is an earlier commit, that
    # commit still carries the unpromoted CHANGELOG, so the tag would ship notes under
    # [Unreleased] regardless — the exact outcome this guard exists to prevent. Refuse
    # rather than appear to fix it.
    MAIN_HEAD=$(git rev-parse master)
    if [[ "$TARGET_SHA" != "$MAIN_HEAD" ]]; then
        echo "Cannot promote automatically: the target is not master HEAD."
        echo "  target:    $(git log -1 --oneline "$TARGET_SHA")"
        echo "  master HEAD: $(git log -1 --oneline "$MAIN_HEAD")"
        echo ""
        echo "A promotion commit would land on top of master and leave the target"
        echo "untouched, so the tag would still ship notes under [Unreleased]."
        echo "Either release master HEAD, or promote by hand onto the commit you want."
        exit 1
    fi

    CAN_PROMOTE=1
    BLOCKER=""
    if ! command -v gh >/dev/null 2>&1; then
        echo "Cannot promote automatically: the gh CLI is not installed."
        CAN_PROMOTE=0
    elif ! gh auth status >/dev/null 2>&1; then
        echo "Cannot promote automatically: gh is not authenticated (run: gh auth login)."
        CAN_PROMOTE=0
    elif git rev-parse --verify "$PROMOTE_BRANCH" >/dev/null 2>&1 ||
         git ls-remote --exit-code --heads origin "$PROMOTE_BRANCH" >/dev/null 2>&1; then
        echo "Cannot promote automatically: branch $PROMOTE_BRANCH already exists."
        CAN_PROMOTE=0
        BLOCKER="branch-exists"
    fi

    if [[ "$CAN_PROMOTE" -eq 0 ]]; then
        echo ""
        # The recipe has to match the blocker. Printing "git checkout -b $PROMOTE_BRANCH"
        # when the refusal was *that the branch already exists* hands the user a first
        # step guaranteed to fail, which reads as the script being broken rather than as
        # the branch needing attention.
        if [[ "$BLOCKER" == "branch-exists" ]]; then
            echo "That branch already carries a promotion, or is left over from one."
            if git rev-parse --verify "$PROMOTE_BRANCH" >/dev/null 2>&1; then
                echo "  local:  $(git log -1 --oneline "$PROMOTE_BRANCH")"
            fi
            if git ls-remote --exit-code --heads origin "$PROMOTE_BRANCH" >/dev/null 2>&1; then
                echo "  remote: origin/$PROMOTE_BRANCH exists"
            fi
            echo ""
            echo "Finish it (merge its PR), or clear it and re-run ./release.sh:"
            echo "  git branch -D $PROMOTE_BRANCH"
            echo "  git push origin --delete $PROMOTE_BRANCH"
        else
            echo "Promote by hand instead:"
            echo "  1. git checkout -b $PROMOTE_BRANCH"
            echo "  2. ./scripts/promote-changelog.sh $VERSION $TODAY"
            echo "     (inserts '## [v$VERSION] - $TODAY' below [Unreleased] and folds the"
            echo "      changelog.d entries beneath it, deleting the folded files)"
            echo "  3. git add -A -- CHANGELOG.md changelog.d && commit"
            echo "  4. Open a PR, merge it, then re-run ./release.sh against the merged commit."
        fi
        echo ""
        exit 1
    fi

    echo "Promotion is mechanical: it inserts the line"
    echo "    ## [v$VERSION] - $TODAY"
    echo "below [Unreleased], folds the changelog.d entries verbatim beneath it"
    echo "(deleting the folded files), changes no entry text, and opens a"
    echo "docs-only PR, then merges it once CI is green and Codex has given a thumbs up."
    echo ""

    # From here the script owns a branch and a checkout. Any exit before the final
    # cleanup must put the user back on master rather than stranding them mid-promotion.
    #
    # INT and TERM as well as EXIT: the longest window here is a 15-minute poll, which is
    # exactly where a Ctrl-C is likely, and that is the worst moment to leave someone on a
    # promotion branch with a half-applied rewrite.
    #
    # Discarding CHANGELOG.md and changelog.d/ is safe and is what makes the checkout
    # reliable: step 2 guarantees a clean tree at start-up, so any modification present
    # here — the rewritten changelog, the deleted entry files — is the promotion script's
    # own mechanical work, and it is regenerable. Unstage first — after `git add`,
    # `git checkout --` restores from the index and would preserve it.
    restore_main() {
        git reset -q HEAD -- CHANGELOG.md changelog.d 2>/dev/null || true
        git checkout -- CHANGELOG.md changelog.d 2>/dev/null || true
        if [[ "$(git rev-parse --abbrev-ref HEAD)" != "master" ]]; then
            git checkout master --quiet 2>/dev/null || true
        fi
        return 0
    }
    trap restore_main EXIT INT TERM

    git checkout -b "$PROMOTE_BRANCH" --quiet

    # The rewrite, its validation and its prove-it-did-exactly-what-it-built check all
    # live in the helper (which exits non-zero having changed nothing on any failure) —
    # separate so it can be tested against fixtures without this script's orchestration.
    if ! ./scripts/promote-changelog.sh "$VERSION" "$TODAY"; then
        echo "Error: promotion rewrite failed — nothing committed. See the message above."
        exit 1
    fi

    # -A: the fold DELETES the changelog.d files it consumed, and deletions need -A to
    # stage through a pathspec.
    git add -A -- CHANGELOG.md changelog.d
    git commit --quiet -m "docs: promote [Unreleased] to v$VERSION" \
        -m "Mechanical: inserts the dated version heading and folds the changelog.d entries verbatim beneath it, deleting the folded files. No entry text is changed."
    git push --quiet -u origin "$PROMOTE_BRANCH"

    PR_URL=$(gh pr create --base master --head "$PROMOTE_BRANCH" \
        --title "docs: promote [Unreleased] to v$VERSION" \
        --body "Promotes ${ENTRY_COUNT:-0} entr(y/ies) — $PENDING_COUNT changelog.d file(s) folded verbatim + $DIRECT_COUNT direct — under a dated \`## [v$VERSION]\` heading so \`release.sh\` can tag. Opened automatically by \`release.sh\`; no entry text is changed.")
    echo "Opened $PR_URL"

    # The head commit the checks and the thumbs up must be about: the one just pushed. Everything below
    # is pinned to it, and the merge itself is pinned to it with --match-head-commit.
    HEAD_SHA=$(git rev-parse HEAD)
    echo "Waiting for 'Build, Test & Coverage' to succeed and Codex to give a thumbs up on ${HEAD_SHA:0:9}..."

    # Bounded. A promotion that has not merged in 20 minutes has hit something this script cannot
    # resolve (CI red, Codex raised a finding or has not looked), and silently waiting longer is worse
    # than saying so: the branch is already pushed, so finishing by hand costs one merge.
    POLL_SECONDS=15
    MAX_WAIT=1200
    WAITED=0
    while true; do
        PR_JSON=$(gh pr view "$PR_URL" --json state,headRefOid 2>/dev/null || echo '{}')
        PR_STATE=$(jq -r '.state // "UNKNOWN"' <<< "$PR_JSON")
        PR_HEAD=$(jq -r '.headRefOid // ""' <<< "$PR_JSON")
        if [[ "$PR_STATE" == "CLOSED" ]]; then
            echo ""
            echo "Error: $PR_URL was closed without merging. Nothing tagged."
            exit 1
        fi
        if [[ "$PR_STATE" == "MERGED" ]]; then
            # Someone merged it by hand while we waited: fine, that is the outcome we wanted.
            break
        fi
        if [[ -n "$PR_HEAD" && "$PR_HEAD" != "$HEAD_SHA" ]]; then
            echo ""
            echo "Error: the head of $PR_URL moved from ${HEAD_SHA:0:9} to ${PR_HEAD:0:9} while waiting."
            echo "Whatever was checked is not what would be merged. Review it, merge it by hand, then re-run ./release.sh."
            exit 1
        fi

        # CI: every check run named exactly 'Build, Test & Coverage' on the head SHA. None yet means
        # it has not started, which is NOT success. Any finished-but-not-success run is a hard stop.
        CI_CONCLUSIONS=$(gh api "repos/{owner}/{repo}/commits/$HEAD_SHA/check-runs?per_page=100" \
            --jq '[.check_runs[] | select(.name == "Build, Test & Coverage") | (.conclusion // "pending")] | join(" ")' \
            2>/dev/null || echo "")
        CI_OK=0
        if [[ -n "$CI_CONCLUSIONS" ]]; then
            CI_OK=1
            for c in $CI_CONCLUSIONS; do
                case "$c" in
                    success) ;;
                    pending) CI_OK=0 ;;
                    *)
                        echo ""
                        echo "Error: 'Build, Test & Coverage' finished '$c' on ${HEAD_SHA:0:9}. Nothing merged, nothing tagged."
                        echo "The branch is pushed and the PR is open: $PR_URL"
                        exit 1
                        ;;
                esac
            done
        fi

        # Codex: a thumbs up (the +1 reaction) from the Codex bot on the PR. An eyes reaction means it is
        # still reviewing and is never enough; a finding arrives as a comment, which this does not read.
        CODEX_OK=0
        CODEX_THUMBS=$(gh api "repos/{owner}/{repo}/issues/${PR_URL##*/}/reactions?per_page=100" \
            --jq '[.[] | select(.user.login == "chatgpt-codex-connector[bot]" and .content == "+1")] | length' \
            2>/dev/null || echo 0)
        if [[ "${CODEX_THUMBS:-0}" -gt 0 ]]; then
            CODEX_OK=1
        fi

        if [[ "$CI_OK" -eq 1 && "$CODEX_OK" -eq 1 ]]; then
            echo ""
            echo "CI is green and Codex gave a thumbs up on ${HEAD_SHA:0:9}. Merging..."
            if ! gh pr merge "$PR_URL" --squash --match-head-commit "$HEAD_SHA" >/dev/null 2>&1; then
                echo "Error: could not merge $PR_URL pinned to ${HEAD_SHA:0:9} (the head may have moved)."
                echo "The branch is pushed and the PR is open — merge it, then re-run ./release.sh."
                exit 1
            fi
            break
        fi

        if [[ "$WAITED" -ge "$MAX_WAIT" ]]; then
            echo ""
            echo "Error: $PR_URL has not been merged after $((MAX_WAIT / 60)) minutes"
            echo "  (CI: ${CI_CONCLUSIONS:-no 'Build, Test & Coverage' run yet}; Codex thumbs up: $([[ "$CODEX_OK" -eq 1 ]] && echo yes || echo no))."
            echo "The branch is pushed and the PR is open — merge it, then re-run ./release.sh."
            gh pr checks "$PR_URL" 2>&1 | head -10 || true
            exit 1
        fi
        sleep "$POLL_SECONDS"
        WAITED=$((WAITED + POLL_SECONDS))
        printf '  ...%ds\r' "$WAITED"
    done
    echo ""
    echo "Merged. Syncing master..."

    git checkout master --quiet
    trap - EXIT INT TERM
    git branch -D "$PROMOTE_BRANCH" --quiet 2>/dev/null || true
    git pull --ff-only origin master --quiet

    # Re-target onto the promotion commit. The tag must point at a commit that actually
    # contains the notes, which the originally-chosen target no longer is.
    TARGET_SHA=$(git rev-parse master)

    # The go-ahead given at the version prompt covered the commits listed then plus
    # the promotion commit. Anything else that merged during the wait was never shown,
    # so that case — and only that case — stops to ask.
    EXTRA=$(git rev-list --count "$MAIN_HEAD".."$TARGET_SHA")
    if [[ "$EXTRA" -ne 1 ]]; then
        echo ""
        echo "Warning: master gained $((EXTRA - 1)) commit(s) besides the promotion while waiting:"
        git log --oneline --reverse "$MAIN_HEAD".."$TARGET_SHA"
        read -r -p "Tag v$VERSION including these? (y/N): " CONFIRM_EXTRA
        if [[ "$CONFIRM_EXTRA" != "y" && "$CONFIRM_EXTRA" != "Y" ]]; then
            echo "Cancelled — the notes are promoted on master; re-run ./release.sh $VERSION to tag."
            exit 1
        fi
    fi
    CHANGELOG_AT_TARGET=$(git show "$TARGET_SHA:CHANGELOG.md" 2>/dev/null || true)

    if [[ -n "$(unreleased_body_at "$TARGET_SHA" || true)" ]]; then
        echo "Error: [Unreleased] is still non-empty after promotion."
        echo "Something landed on master mid-promotion. Nothing tagged."
        exit 1
    fi
    LEFTOVER=$(git ls-tree -r --name-only "$TARGET_SHA" -- changelog.d 2>/dev/null | grep -v 'README\.md$' || true)
    if [[ -n "$LEFTOVER" ]]; then
        echo "Error: changelog.d/ still holds pending entries after promotion:"
        printf '%s\n' "$LEFTOVER" | sed 's/^/  /'
        echo "Something landed on master mid-promotion. Nothing tagged."
        exit 1
    fi

    echo ""
    echo "Promoted. Target is now:"
    echo "  $(git log -1 --oneline "$TARGET_SHA")"
    echo ""
    if [[ "$CURRENT" != "none" ]]; then
        echo "Commits between $CURRENT and target ($(git rev-list --count "$CURRENT".."$TARGET_SHA") total):"
        git log "$CURRENT".."$TARGET_SHA" --oneline --reverse
        echo ""
    fi
fi

# NOTE: a here-string, deliberately NOT `printf ... | grep -q`.
#
# `grep -q` exits the moment it matches. With `set -o pipefail` (line 23) and a
# CHANGELOG this size, printf is still writing hundreds of KB when grep leaves, takes
# SIGPIPE, and exits 141 — so the pipeline reports failure even though grep MATCHED.
# The guard then insists the section is missing when it is present.
#
# The failure is inverted and self-concealing: the EARLIER the heading appears, the
# more data printf has left to write and the more reliably it breaks. A new release
# sits at the top of the file, so this fired on every correctly-promoted CHANGELOG and
# would have passed only if the notes were buried at the bottom. It cost a release
# cycle on v2.17.2. The `[Unreleased]` check above is immune only because awk reads its
# input to completion.
if ! grep -q "^## \[v$VERSION\]" <<< "$CHANGELOG_AT_TARGET"; then
    echo ""
    echo "Error: CHANGELOG.md has no '## [v$VERSION]' section at the target commit."
    echo ""
    echo "[Unreleased] is empty, so the notes were either never written or promoted"
    echo "under a different version number. Check the heading matches v$VERSION."
    echo ""
    exit 1
fi

echo "CHANGELOG: [Unreleased] is empty and v$VERSION is documented."

# 8. Tag and push (tag message from -m, else the default)
TAG_MESSAGE="${MESSAGE:-Release v$VERSION}"
echo ""
echo "Tagging v$VERSION at $(git log -1 --oneline "$TARGET_SHA")"
git tag -a "v$VERSION" "$TARGET_SHA" -m "$TAG_MESSAGE"
git push origin "v$VERSION"

echo ""
echo "Tag v$VERSION pushed — pipeline deploying to production."

# 9. Watch the Deploy workflow until it finishes. The tag is already pushed, so
# nothing here can change the release — Ctrl-C only stops the watching.
if ! command -v gh >/dev/null 2>&1 || ! gh auth status >/dev/null 2>&1; then
    echo "Watch progress at: https://github.com/gregochr/plex-webhooks/actions"
    exit 0
fi

notify() {
    printf '\a'
    if command -v osascript >/dev/null 2>&1; then
        osascript -e "display notification \"$1\" with title \"release.sh\"" 2>/dev/null || true
    fi
}

# The Deploy workflow's own "Deploy to Production" job runs scripts/prod-health.sh --deploy after
# `docker compose up` and prints one line per check plus a `prod-health: N checks, M failed`
# summary — but that only ever lived in a job log nobody opens, so the deploy could succeed
# while the health check quietly failed. The owner's ask
# was for the health check to be "properly articulated as the very end of the output" — a check
# nobody reads is not a check — so this pulls those lines back out of the job log and prints them
# as the last thing release.sh says, on both the success and the failure path. The log can lag
# the run's own completion by a few seconds, hence the short retry.
print_deploy_health() {
    local run_id="$1" run_url="$2"
    local job_id lines attempt

    job_id=$(gh run view "$run_id" --json jobs \
        --jq '.jobs[] | select(.name == "Deploy to Production") | .databaseId' 2>/dev/null || true)

    lines=""
    if [[ -n "$job_id" ]]; then
        for attempt in 1 2 3; do
            lines=$(gh run view --job "$job_id" --log 2>/dev/null \
                | awk -F'\t' '{print $3}' \
                | sed -E 's/^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:.]+Z //' \
                | grep -E '^(ok  |warn |FAIL |prod-health:)' || true)
            grep -q '^prod-health:' <<< "$lines" && break
            [[ "$attempt" -lt 3 ]] && sleep 5
        done
    fi

    if [[ -z "$lines" ]]; then
        echo "   (post-deploy health check output not found in the job log — see $run_url)"
        return 0
    fi

    echo ""
    echo "Post-deploy health check (on the host):"
    while IFS= read -r line; do
        echo "   $line"
    done <<< "$lines"
}

echo "Waiting for the Deploy run to start (Ctrl-C stops watching; the deploy carries on)..."
RUN_ID=""
for _ in $(seq 1 24); do
    RUN_ID=$(gh run list --workflow deploy.yml --limit 20 \
        --json databaseId,headBranch \
        --jq ".[] | select(.headBranch == \"v$VERSION\") | .databaseId" 2>/dev/null | head -n 1 || true)
    [[ -n "$RUN_ID" ]] && break
    sleep 5
done
if [[ -z "$RUN_ID" ]]; then
    echo "No Deploy run for v$VERSION appeared within 2 minutes."
    echo "Check: https://github.com/gregochr/plex-webhooks/actions/workflows/deploy.yml"
    exit 1
fi
RUN_URL="https://github.com/gregochr/plex-webhooks/actions/runs/$RUN_ID"
echo "Deploy run: $RUN_URL"

START=$(date +%s)
LAST_JOB=""
while true; do
    RUN_JSON=$(gh run view "$RUN_ID" --json status,conclusion,jobs 2>/dev/null || echo '{}')
    STATUS=$(jq -r '.status // "unknown"' <<< "$RUN_JSON")
    if [[ "$STATUS" == "completed" ]]; then
        break
    fi
    JOB=$(jq -r '[.jobs[]? | select(.status == "in_progress") | .name] | join(", ")' <<< "$RUN_JSON")
    ELAPSED=$(( $(date +%s) - START ))
    if [[ -n "$JOB" && "$JOB" != "$LAST_JOB" ]]; then
        printf '\n  %dm%02ds  %s' $((ELAPSED / 60)) $((ELAPSED % 60)) "$JOB"
        LAST_JOB="$JOB"
    else
        printf '.'
    fi
    if [[ "$ELAPSED" -ge 3600 ]]; then
        echo ""
        echo "Still running after an hour — stopped watching. $RUN_URL"
        exit 1
    fi
    sleep 20
done
echo ""

CONCLUSION=$(jq -r '.conclusion // "unknown"' <<< "$RUN_JSON")
ELAPSED=$(( $(date +%s) - START ))
if [[ "$CONCLUSION" == "success" ]]; then
    echo ""
    echo "✅ v$VERSION is deployed to production ($((ELAPSED / 60))m$(printf '%02d' $((ELAPSED % 60)))s)."
    echo "   CineLight: http://<host>:8080/"
    print_deploy_health "$RUN_ID" "$RUN_URL"
    notify "v$VERSION deployed to production"
else
    echo ""
    echo "❌ Deploy of v$VERSION finished: $CONCLUSION"
    echo "   $RUN_URL"
    gh run view "$RUN_ID" --json jobs \
        --jq '.jobs[] | select(.conclusion != "success" and .conclusion != "skipped") | "   - \(.name): \(.conclusion)"' 2>/dev/null || true
    print_deploy_health "$RUN_ID" "$RUN_URL"
    notify "v$VERSION deploy $CONCLUSION"
    exit 1
fi
