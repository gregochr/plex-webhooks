#!/bin/bash
# Fixture tests for scripts/promote-changelog.sh. Each case builds a throwaway repo layout (CHANGELOG.md and
# changelog.d/) in a mktemp directory, runs the script there, and checks the exact resulting files, so the
# rewrite is exercised without release.sh's branch, PR and merge orchestration.
#
# Run: scripts/tests/promote-changelog-test.sh   (exit 0 = all passed)

set -euo pipefail

SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/promote-changelog.sh"
ROOT="$(mktemp -d)"
trap 'rm -rf "$ROOT"' EXIT

PASSED=0
FAILED=0

pass() { PASSED=$((PASSED + 1)); echo "ok   $1"; }
fail() { FAILED=$((FAILED + 1)); echo "FAIL $1"; shift; printf '       %s\n' "$@"; }

# New fixture directory with the standard seed changelog; echoes its path.
new_fixture() {
    local dir
    dir="$(mktemp -d "$ROOT/case.XXXXXX")"
    mkdir "$dir/changelog.d"
    printf '# Changelog\n\n## [Unreleased]\n\n## [v0.9.0] - 2026-01-01\n\n### Added — old thing\n\nOld text.\n' > "$dir/CHANGELOG.md"
    printf '# changelog.d\n\nReadme, never folded.\n' > "$dir/changelog.d/README.md"
    echo "$dir"
}

# Runs the script in a fixture; sets OUT (combined output) and STATUS.
run_in() {
    local dir="$1"
    shift
    set +e
    OUT="$(cd "$dir" && "$SCRIPT" "$@" 2>&1)"
    STATUS=$?
    set -e
}

assert_file() {
    local name="$1" file="$2" expected="$3" actual
    actual="$(cat "$file")"
    if [[ "$actual" == "$expected" ]]; then
        pass "$name"
    else
        fail "$name" "expected:" "$expected" "actual:" "$actual"
    fi
}

assert_status() {
    local name="$1" want="$2"
    if [[ "$STATUS" -eq "$want" ]]; then
        pass "$name"
    else
        fail "$name" "exit status $STATUS, wanted $want" "$OUT"
    fi
}

assert_contains() {
    local name="$1" needle="$2"
    if grep -qF -- "$needle" <<< "$OUT"; then
        pass "$name"
    else
        fail "$name" "output did not contain: $needle" "$OUT"
    fi
}

assert_unchanged() {
    local name="$1" dir="$2" before="$3"
    if [[ "$(cd "$dir" && find . -type f | LC_ALL=C sort | xargs shasum)" == "$before" ]]; then
        pass "$name"
    else
        fail "$name" "the fixture changed although the script refused"
    fi
}

snapshot() { (cd "$1" && find . -type f | LC_ALL=C sort | xargs shasum); }

# --- 1. folds entries newest first, under one dated heading, and deletes them -----------------------
d="$(new_fixture)"
printf '### Added — older\n\nOlder body.\n' > "$d/changelog.d/20261101-older.md"
printf '\n\n### Fixed — newer\n\nNewer body.\n\n\n' > "$d/changelog.d/20261105-newer.md"
run_in "$d" 1.0.0 2026-11-07
assert_status "folds: exits 0" 0
assert_file "folds: newest entry first, blank lines trimmed, heading inserted below [Unreleased]" "$d/CHANGELOG.md" \
'# Changelog

## [Unreleased]

## [v1.0.0] - 2026-11-07

### Fixed — newer

Newer body.

### Added — older

Older body.

## [v0.9.0] - 2026-01-01

### Added — old thing

Old text.'
if [[ ! -e "$d/changelog.d/20261101-older.md" && ! -e "$d/changelog.d/20261105-newer.md" ]]; then
    pass "folds: deletes the folded entry files"
else
    fail "folds: deletes the folded entry files" "an entry file survived"
fi
if [[ -f "$d/changelog.d/README.md" ]]; then pass "folds: keeps changelog.d/README.md"; else fail "folds: keeps changelog.d/README.md"; fi
assert_contains "folds: reports the count" "folded 2 changelog.d entr(y/ies)"

# --- 2. a straggler written directly under [Unreleased] ends up under the same heading ----------------
d="$(new_fixture)"
printf '# Changelog\n\n## [Unreleased]\n\n### Fixed — straggler\n\nWritten by hand.\n\n## [v0.9.0] - 2026-01-01\n\n### Added — old thing\n\nOld text.\n' > "$d/CHANGELOG.md"
printf '### Added — new\n\nNew body.\n' > "$d/changelog.d/20261107-new.md"
run_in "$d" 1.0.0 2026-11-07
assert_status "straggler: exits 0" 0
assert_file "straggler: folded entry first, hand-written entry follows under the same heading" "$d/CHANGELOG.md" \
'# Changelog

## [Unreleased]

## [v1.0.0] - 2026-11-07

### Added — new

New body.

### Fixed — straggler

Written by hand.

## [v0.9.0] - 2026-01-01

### Added — old thing

Old text.'

# --- 3. nothing in changelog.d: only the heading is inserted -------------------------------------------
d="$(new_fixture)"
run_in "$d" 1.0.0 2026-11-07
assert_status "empty: exits 0" 0
assert_file "empty: heading only, nothing folded" "$d/CHANGELOG.md" \
'# Changelog

## [Unreleased]

## [v1.0.0] - 2026-11-07

## [v0.9.0] - 2026-01-01

### Added — old thing

Old text.'
assert_contains "empty: reports zero folded" "folded 0 changelog.d entr(y/ies)"

# --- 4. refusals change nothing ------------------------------------------------------------------------
d="$(new_fixture)"
printf '### Added — fine\n\nBody.\n' > "$d/changelog.d/20261107-fine.md"
before="$(snapshot "$d")"

run_in "$d" v1.0.0 2026-11-07
assert_status "refuses a version with a v prefix" 1
assert_contains "version prefix message" "version must be x.y.z"
assert_unchanged "version prefix: nothing changed" "$d" "$before"

run_in "$d" 1.0 2026-11-07
assert_status "refuses a two-part version" 1

run_in "$d" 1.0.0 07-11-2026
assert_status "refuses a malformed date" 1
assert_contains "date message" "date must be YYYY-MM-DD"
assert_unchanged "date: nothing changed" "$d" "$before"

run_in "$d" 1.0.0
assert_status "refuses a missing date argument" 1
assert_contains "usage message" "Usage:"

# --- 5. no [Unreleased] heading -------------------------------------------------------------------------
d="$(new_fixture)"
printf '# Changelog\n\n## [v0.9.0] - 2026-01-01\n' > "$d/CHANGELOG.md"
printf '### Added — fine\n\nBody.\n' > "$d/changelog.d/20261107-fine.md"
before="$(snapshot "$d")"
run_in "$d" 1.0.0 2026-11-07
assert_status "refuses a changelog with no [Unreleased]" 1
assert_contains "no-unreleased message" "no '## [Unreleased]' heading"
assert_unchanged "no-unreleased: nothing changed" "$d" "$before"

# --- 6. no CHANGELOG.md -----------------------------------------------------------------------------------
d="$(new_fixture)"
rm "$d/CHANGELOG.md"
run_in "$d" 1.0.0 2026-11-07
assert_status "refuses when there is no CHANGELOG.md" 1
assert_contains "no-changelog message" "no CHANGELOG.md here"

# --- 7. an entry that does not open with a ### heading: nothing is changed or deleted -------------------
d="$(new_fixture)"
printf '### Added — fine\n\nBody.\n' > "$d/changelog.d/20261101-fine.md"
printf 'Just prose, no heading.\n' > "$d/changelog.d/20261105-bad.md"
before="$(snapshot "$d")"
run_in "$d" 1.0.0 2026-11-07
assert_status "refuses an entry without a ### heading" 1
assert_contains "bad-entry message names the file" "changelog.d/20261105-bad.md does not open with"
assert_unchanged "bad entry: CHANGELOG.md and every entry untouched" "$d" "$before"

d="$(new_fixture)"
: > "$d/changelog.d/20261105-empty.md"
run_in "$d" 1.0.0 2026-11-07
assert_status "refuses an empty entry file" 1
assert_contains "empty-entry message" "<empty file>"

# --- 8. files the fold cannot see are refused loudly, not skipped -----------------------------------------
d="$(new_fixture)"
printf '### Added — fine\n\nBody.\n' > "$d/changelog.d/20261107-fine.md"
printf '### Added — hidden\n\nBody.\n' > "$d/changelog.d/.fix.md"
before="$(snapshot "$d")"
run_in "$d" 1.0.0 2026-11-07
assert_status "refuses a dot-prefixed entry" 1
assert_contains "dotfile message" "not YYYYMMDD-<slug>.md entries"
assert_unchanged "dotfile: nothing changed" "$d" "$before"

d="$(new_fixture)"
printf '### Added — undated\n\nBody.\n' > "$d/changelog.d/undated.md"
run_in "$d" 1.0.0 2026-11-07
assert_status "refuses an undated entry name" 1

d="$(new_fixture)"
mkdir "$d/changelog.d/20261107-subdir.md"
run_in "$d" 1.0.0 2026-11-07
assert_status "refuses a directory that looks like an entry" 1

# --- 9. running it twice at the same version is not silently accepted by the script itself ----------------
# (release.sh refuses a duplicate heading before calling the script; the script's own guard is that it adds
# exactly one heading per run, so a second run adds a second one and the caller's guard is what stops it.)
d="$(new_fixture)"
printf '### Added — once\n\nBody.\n' > "$d/changelog.d/20261107-once.md"
run_in "$d" 1.0.0 2026-11-07
assert_status "first promotion succeeds" 0
count="$(grep -c '^## \[v1.0.0\]' "$d/CHANGELOG.md" || true)"
if [[ "$count" -eq 1 ]]; then pass "exactly one v1.0.0 heading after one promotion"; else fail "exactly one v1.0.0 heading after one promotion" "found $count"; fi

echo ""
echo "promote-changelog-test: ${PASSED} passed, ${FAILED} failed"
[[ "$FAILED" -eq 0 ]]
