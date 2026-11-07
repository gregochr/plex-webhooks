#!/bin/bash
# End-to-end proof against a REAL Postgres: the only test that proves authentication, a complete archive
# and a working restore. CI runs it as the job "utils-pg backup end-to-end"; run it locally with Docker:
#   ops/utils-pg/tests/e2e.sh
#
# It starts postgres:16 as a container named `utils-pg`, so it REFUSES to run if a container with that
# name already exists (it would be your real database). Set E2E_REPLACE_EXISTING=1 only on a throwaway
# machine. Everything it writes goes under BACKUP_ROOT (default: a temp directory); the container is
# removed on exit.
#
# Steps: load two databases (one named wainwrights, one empty), run backup.sh and verify.sh, then prove
# that a truncated dump is caught by BOTH backup.sh's own pg_restore check and by verify.sh.

set -euo pipefail

OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/utils-pg-e2e.XXXXXX")"
BACKUP_ROOT="${BACKUP_ROOT:-${ROOT}/backups}"
INSTALLED_DIR="${ROOT}/installed"
REAL_DOCKER="$(command -v docker)"
CONTAINER=utils-pg
export BACKUP_ROOT INSTALLED_DIR PG_CONTAINER="$CONTAINER"

if "$REAL_DOCKER" inspect "$CONTAINER" >/dev/null 2>&1; then
    if [ "${E2E_REPLACE_EXISTING:-0}" != "1" ]; then
        echo "ERROR: a container named ${CONTAINER} already exists. Refusing to touch it." >&2
        exit 2
    fi
    "$REAL_DOCKER" rm -fv "$CONTAINER" >/dev/null
fi

cleanup() {
    "$REAL_DOCKER" rm -fv "$CONTAINER" >/dev/null 2>&1 || true
    "$REAL_DOCKER" rm -fv "utils-pg-verify-e2e-$$" >/dev/null 2>&1 || true
    rm -rf "$ROOT"
}
trap cleanup EXIT

PASSED=0
ok() { PASSED=$((PASSED + 1)); echo "ok   $1"; }
bad() { echo "FAIL $1" >&2; exit 1; }
expect_ok() { local name="$1"; shift; if "$@" >"${ROOT}/out" 2>&1; then ok "$name"; else cat "${ROOT}/out" >&2; bad "$name"; fi; }
expect_fail() { local name="$1"; shift; if "$@" >"${ROOT}/out" 2>&1; then cat "${ROOT}/out" >&2; bad "$name"; else ok "$name"; fi; }

# --- Real Postgres -----------------------------------------------------------------
"$REAL_DOCKER" run -d --name "$CONTAINER" -e POSTGRES_HOST_AUTH_METHOD=trust postgres:16 >/dev/null
ready=0
for _ in $(seq 1 60); do
    if "$REAL_DOCKER" exec "$CONTAINER" pg_isready -h 127.0.0.1 -U postgres >/dev/null 2>&1; then
        ready=1
        break
    fi
    sleep 2
done
[ "$ready" -eq 1 ] || bad "postgres never became ready"

sql() { "$REAL_DOCKER" exec "$CONTAINER" psql -U postgres -v ON_ERROR_STOP=1 -Atq "$@" >/dev/null; }
sql -c 'create role wainwrights login'
sql -c 'create database wainwrights owner wainwrights'
sql -c 'create database cinelight'      # stays empty: a legitimately empty database must pass
sql -d wainwrights -c "create table fells (id serial primary key, name text, height int); create table walks (id serial primary key, fell_id int references fells(id), note text); insert into fells (name, height) select 'fell ' || g, 500 + g from generate_series(1, 200) g; insert into walks (fell_id, note) select g, repeat('x', 200) from generate_series(1, 200) g; analyze"

# The scripts as the host would have them installed (verify.sh checks the hashes).
mkdir -p "$INSTALLED_DIR"
cp "${OPS_DIR}/backup.sh" "${OPS_DIR}/verify.sh" "$INSTALLED_DIR/"

export RUN_ID="e2e-$$"
export VERIFY_POLL_SLEEP=1

# --- Backup and verify, the happy path -------------------------------------------------
expect_ok "backup.sh succeeds" "${OPS_DIR}/backup.sh"
[ -f "${BACKUP_ROOT}/marker" ] || bad "marker written"
ok "marker written"
grep -qE "^wainwrights$(printf '\t')[0-9]+$(printf '\t')400\$" "${BACKUP_ROOT}/marker" || bad "marker holds the exact live total (400 rows)"
ok "marker holds the exact live total (400 rows)"
first_of() { printf '%s' "$1"; }
GOOD="$(first_of "${BACKUP_ROOT}"/daily/wainwrights/wainwrights_????-??-??.dump)"
[ -s "$GOOD" ] || bad "wainwrights dump present"
ok "wainwrights dump present"
[ -s "$(first_of "${BACKUP_ROOT}"/daily/cinelight/cinelight_????-??-??.dump)" ] || bad "empty database still has a dump"
ok "empty database still has a dump"
grep -q 'CREATE ROLE' "${BACKUP_ROOT}"/globals/globals_*.sql || bad "globals have roles"
ok "globals have roles"
[ "$(stat -c %a "$BACKUP_ROOT/daily" 2>/dev/null || stat -f %Lp "$BACKUP_ROOT/daily")" = "700" ] || bad "folders are mode 700"
ok "folders are mode 700"

expect_ok "verify.sh passes on a good backup" "${OPS_DIR}/verify.sh"
if grep -qiE 'fells|walks|fell [0-9]' "${ROOT}/out" "${BACKUP_ROOT}/backup.log"; then bad "no table name or row reaches the output or the log"; fi
ok "no table name or row reaches the output or the log"
grep -q 'Restored cinelight: errors=0 tables=0 rows=0' "${ROOT}/out" || bad "empty database restores as 0 tables, 0 rows"
ok "empty database restores as 0 tables, 0 rows"
"$REAL_DOCKER" inspect "utils-pg-verify-${RUN_ID}" >/dev/null 2>&1 && bad "scratch container removed"
ok "scratch container removed"

# --- A restored total below the marker's live total must fail ---------------------------
cp "${BACKUP_ROOT}/marker" "${ROOT}/marker.good"
awk -F'\t' 'BEGIN{OFS="\t"} NR>1 && $1=="wainwrights"{$3=$3+1000} {print}' "${ROOT}/marker.good" > "${BACKUP_ROOT}/marker"
expect_fail "verify.sh fails when the restore has fewer rows than 90% of the live total" "${OPS_DIR}/verify.sh"
cp "${ROOT}/marker.good" "${BACKUP_ROOT}/marker"

# --- Truncated dump 1: backup.sh's own pg_restore check must catch it -------------------
# A wrapper makes `docker exec ... pg_dump` deliver only half the bytes while still exiting 0, which is
# what a dump cut off mid-stream looks like to the script. Only the pg_restore check can catch that.
SIZE="$(wc -c < "$GOOD" | tr -d ' ')"
mkdir "${ROOT}/wrap"
cat > "${ROOT}/wrap/docker" <<WRAP
#!/bin/bash
for a in "\$@"; do
    if [ "\$a" = "pg_dump" ]; then
        "$REAL_DOCKER" "\$@" | head -c $((SIZE / 2))
        exit 0
    fi
done
exec "$REAL_DOCKER" "\$@"
WRAP
chmod +x "${ROOT}/wrap/docker"
cp "$GOOD" "${ROOT}/good.dump"
cp "${BACKUP_ROOT}/marker" "${ROOT}/marker.before"
expect_fail "backup.sh fails on a truncated dump (its own pg_restore check)" env PATH="${ROOT}/wrap:${PATH}" "${OPS_DIR}/backup.sh"
cmp -s "$GOOD" "${ROOT}/good.dump" || bad "a failed run leaves the existing good dump untouched"
ok "a failed run leaves the existing good dump untouched"
cmp -s "${BACKUP_ROOT}/marker" "${ROOT}/marker.before" || bad "a failed run does not touch the marker"
ok "a failed run does not touch the marker"
[ -z "$(find "${BACKUP_ROOT}" -name '.partial-*' | head -1)" ] || bad "no temp file left behind"
ok "no temp file left behind"
grep -q 'did not pass pg_restore' "${BACKUP_ROOT}/backup.log" || bad "the failure was the pg_restore check"
ok "the failure was the pg_restore check"

# --- Truncated dump 2: verify.sh must catch a truncated file on disk --------------------
head -c $((SIZE / 2)) "${ROOT}/good.dump" > "$GOOD"
expect_fail "verify.sh fails on a truncated dump" "${OPS_DIR}/verify.sh"
if grep -qiE 'fells|walks|fell [0-9]' "${ROOT}/out"; then bad "a failed restore leaks no table name or row"; fi
ok "a failed restore leaks no table name or row"
[ "$(stat -c %a "${BACKUP_ROOT}/verify-errors.log" 2>/dev/null || stat -f %Lp "${BACKUP_ROOT}/verify-errors.log")" = "600" ] || bad "error file is mode 600"
ok "error file is mode 600"
cp "${ROOT}/good.dump" "$GOOD"

# --- And a good run again, same day: replaces the file and passes ------------------------
expect_ok "backup.sh succeeds again the same day" "${OPS_DIR}/backup.sh"
expect_ok "verify.sh passes again" "${OPS_DIR}/verify.sh"

echo "e2e: ${PASSED} checks passed"
