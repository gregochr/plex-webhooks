#!/bin/bash
# Fixture tests for ops/utils-pg/: backup.sh, verify.sh and install.sh. `docker`, `flock` and `rsync` are
# fakes on PATH that read their answers from FAKE_* variables and log what they were asked to do, so the
# real scripts run unmodified against a throwaway directory. Nothing here touches Docker or a database.
# The real thing is proven by ops/utils-pg/tests/e2e.sh (CI job "utils-pg backup end-to-end").
#
# The fake pg_dump emits "PGDMP <db> <tag> -END". A dump without "-END" is a TRUNCATED one: the fake
# `pg_restore -f /dev/null` (reads everything) rejects it, `pg_restore --list` (table of contents only)
# accepts it, exactly like the real tools. Failing fakes print "SECRETTABLE" / "SECRETROW" on stderr, and
# the tests assert those never reach the output or the log but do reach the mode-600 host file.
#
# Run: scripts/tests/utils-pg-backup-test.sh   (exit 0 = all passed)

set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OPS="$REPO/ops/utils-pg"
ROOT="$(mktemp -d)"
trap 'rm -rf "$ROOT"' EXIT

PASSED=0
FAILED=0
pass() { PASSED=$((PASSED + 1)); echo "ok   $1"; }
fail() { FAILED=$((FAILED + 1)); echo "FAIL $1"; shift; printf '       %s\n' "$@"; }
check() {   # check <name> <status> [detail...]
    local name="$1" rc="$2"
    shift 2
    if [[ "$rc" -eq 0 ]]; then pass "$name"; else fail "$name" "$@"; fi
}
cond() { if "$@" >/dev/null 2>&1; then echo 0; else echo 1; fi; }
cond_not() { if "$@" >/dev/null 2>&1; then echo 1; else echo 0; fi; }
contains() { grep -qF -- "$2" <<< "$1"; }
has() { contains "$OUT" "$1"; echo $?; }
lacks() { if contains "$OUT" "$1"; then echo 1; else echo 0; fi; }
# code_lacks <pattern> <file>...: 0 if no non-comment line matches.
code_lacks() { local pat="$1"; shift; if grep -hvE '^[[:space:]]*#' "$@" | grep -qE -- "$pat"; then echo 1; else echo 0; fi; }
perm() { stat -c %a "$1" 2>/dev/null || stat -f %Lp "$1"; }

# --- fakes --------------------------------------------------------------------------------------------
BIN="$ROOT/bin"
mkdir "$BIN"
cat > "$BIN/docker" <<'FAKE'
#!/bin/bash
echo "docker $*" >> "${FAKE_LOG:-/dev/null}"
kv() {   # kv "<k=v k=v>" <key> <default>
    local e
    for e in $1; do
        if [[ "${e%%=*}" == "$2" ]]; then echo "${e#*=}"; return; fi
    done
    echo "$3"
}
in_list() { [[ -n "$2" && " $1 " == *" $2 "* ]]; }
cmd="$1"; shift
case "$cmd" in
    inspect) echo "${FAKE_IMAGE_ID-sha256:fakeimg}"; [[ -n "${FAKE_IMAGE_ID-x}" ]] || exit 1; exit 0 ;;
    rm) exit 0 ;;
    run) [[ "${FAKE_RUN_FAIL:-0}" != 1 ]] || exit 1; echo fakecontainerid; exit 0 ;;
    exec) ;;
    *) exit 0 ;;
esac
while [[ "${1:-}" == -* ]]; do
    if [[ "$1" == *t* ]]; then echo "the input device is not a TTY" >&2; exit 1; fi
    shift
done
ctr="$1"; tool="$2"; shift 2
all="$*"
db=""; prev=""
for a in "$@"; do
    if [[ "$prev" == "-d" ]]; then db="$a"; fi
    prev="$a"
done
case "$tool" in
    pg_isready) [[ "${FAKE_NOT_READY:-0}" != 1 ]]; exit $? ;;
    psql)
        if [[ "$ctr" == utils-pg-verify* ]]; then
            case "$all" in
                *"select 1"*) echo 1 ;;
                *"create database"*) ;;
                *query_to_xml*)
                    live="$(kv "${FAKE_LIVE:-wainwrights=5 cinelight=0}" "$db" 5)"
                    echo "$(kv "${FAKE_RESTORED:-}" "$db" "2:$live")" | tr ':' '|' ;;
            esac
        else
            case "$all" in
                *"select 1"*)
                    if [[ "${FAKE_PRECOND_FAIL:-0}" == 1 ]]; then echo "psql: error SECRETTABLE" >&2; exit 2; fi
                    echo "${FAKE_PRECOND_OUT-1}" ;;
                *datname*)
                    [[ "${FAKE_LIST_FAIL:-0}" != 1 ]] || exit 3
                    printf '%b' "${FAKE_DBS-cinelight\nwainwrights\n}" ;;
                *query_to_xml*)
                    [[ "${FAKE_LIVE_FAIL:-0}" != 1 ]] || exit 3
                    kv "${FAKE_LIVE:-wainwrights=5 cinelight=0}" "$db" 5 ;;
            esac
        fi
        exit 0 ;;
    pg_dump)
        if [[ -n "${FAKE_PEEK_DIR:-}" ]]; then ls -a "$FAKE_PEEK_DIR/$db" >> "$FAKE_PEEK_LOG"; fi
        if in_list "${FAKE_DUMP_FAIL:-}" "$db"; then echo "pg_dump: error: SECRETTABLE SECRETROW" >&2; exit 1; fi
        if in_list "${FAKE_DUMP_TRUNC:-}" "$db"; then printf 'PGDMP %s TRUNC' "$db"; exit 0; fi
        printf 'PGDMP %s %s -END\n' "$db" "${FAKE_DUMP_TAG:-v1}"
        if in_list "${FAKE_DUMP_RC1:-}" "$db"; then exit 1; fi
        exit 0 ;;
    pg_restore)
        if [[ "$all" == *"--list"* || "$all" == *" -l"* ]]; then cat > /dev/null; exit 0; fi
        data="$(cat)"
        if [[ "$data" != *"-END"* ]] || in_list "${FAKE_RESTORE_FAIL:-}" "$db" || [[ "${FAKE_CHECK_FAIL:-0}" == 1 && "$ctr" == "utils-pg" ]]; then
            echo "pg_restore: error: COPY failed for table SECRETTABLE: SECRETROW" >&2
            exit 1
        fi
        exit 0 ;;
    pg_dumpall)
        [[ "${FAKE_GLOBALS_FAIL:-0}" != 1 ]] || exit 1
        [[ -z "${FAKE_GLOBALS-CREATE ROLE postgres;}" ]] || printf '%s\n' "${FAKE_GLOBALS-CREATE ROLE postgres;}"
        exit 0 ;;
esac
exit 0
FAKE
cat > "$BIN/flock" <<'FAKE'
#!/bin/bash
echo "flock $*" >> "${FAKE_LOG:-/dev/null}"
exit "${FAKE_FLOCK_RC:-0}"
FAKE
cat > "$BIN/rsync" <<'FAKE'
#!/bin/bash
echo "rsync $*" >> "${FAKE_LOG:-/dev/null}"
exit "${FAKE_RSYNC_RC:-0}"
FAKE
chmod +x "$BIN/docker" "$BIN/flock" "$BIN/rsync"
export PATH="$BIN:$PATH"

# A fresh fake host: backup root, an install dir holding the repo's scripts, and a call log.
new_host() {
    local h
    h="$(mktemp -d "$ROOT/host.XXXXXX")"
    mkdir -p "$h/home" "$h/installed"
    cp "$OPS/backup.sh" "$OPS/verify.sh" "$h/installed/"
    : > "$h/log"
    echo "$h"
}

# runb <host> [VAR=value...]: runs backup.sh. Sets OUT and STATUS.
runb() {
    local h="$1"
    shift
    set +e
    OUT="$(env BACKUP_ROOT="$h/backups" HOME="$h/home" FAKE_LOG="$h/log" BACKUP_DATE=2026-11-07 BACKUP_DOW=3 "$@" "$OPS/backup.sh" 2>&1)"
    STATUS=$?
    set -e
}

# runv <host> [VAR=value...]: runs verify.sh. Sets OUT and STATUS.
runv() {
    local h="$1"
    shift
    set +e
    OUT="$(env BACKUP_ROOT="$h/backups" INSTALLED_DIR="$h/installed" HOME="$h/home" FAKE_LOG="$h/log" RUN_ID=t1 VERIFY_POLL_SLEEP=0 "$@" "$OPS/verify.sh" 2>&1)"
    STATUS=$?
    set -e
}

# A host with one good backup already taken (wainwrights + cinelight).
good_host() {
    H="$(new_host)"
    runb "$H"
    if [[ "$STATUS" -ne 0 ]]; then echo "setup: backup failed: $OUT" >&2; exit 1; fi
}
DUMP_W() { echo "$H/backups/daily/wainwrights/wainwrights_2026-11-07.dump"; }
status_nonzero() { if [[ "$STATUS" -ne 0 ]]; then echo 0; else echo 1; fi; }
exists() { if [[ -e "$1" ]]; then echo 0; else echo 1; fi; }
absent() { if [[ -e "$1" ]]; then echo 1; else echo 0; fi; }

# ============================ backup.sh =================================================================
good_host
check "backup: success exits 0" "$STATUS" "$OUT"
check "backup: wainwrights dump written" "$(exists "$(DUMP_W)")"
check "backup: cinelight (empty) dump written" "$(exists "$H/backups/daily/cinelight/cinelight_2026-11-07.dump")"
check "backup: globals written" "$(exists "$H/backups/globals/globals_2026-11-07.sql")"
check "backup: marker written" "$(exists "$H/backups/marker")"
check "backup: marker lists every database with size and live total" \
    "$(grep -qE "^wainwrights$(printf '\t')[0-9]+$(printf '\t')5\$" "$H/backups/marker"; echo $?)" "$(cat "$H/backups/marker")"
check "backup: marker lists the empty database with live total 0" \
    "$(grep -qE "^cinelight$(printf '\t')[0-9]+$(printf '\t')0\$" "$H/backups/marker"; echo $?)"
check "backup: folders are mode 700" "$(cond test "$(perm "$H/backups")$(perm "$H/backups/daily")$(perm "$H/backups/daily/wainwrights")$(perm "$H/backups/weekly")$(perm "$H/backups/globals")" = 700700700700700)"
check "backup: dump is mode 600" "$(cond test "$(perm "$(DUMP_W)")" = 600)"
check "backup: marker, log and error file are mode 600" \
    "$(cond test "$(perm "$H/backups/marker")$(perm "$H/backups/backup.log")$(perm "$H/backups/backup-errors.log")" = 600600600)"
check "backup: no temp file left behind" "$(cond test -z "$(find "$H/backups" -name '.partial-*')")"
check "backup: takes the lock" "$(grep -q '^flock -n 9' "$H/log"; echo $?)"
check "backup: never uses exec -t" "$(cond_not grep -E 'docker exec -[a-z]*t' "$H/log")" "$(cat "$H/log")"
check "backup: dumps with pg_dump -Fc --lock-wait-timeout as postgres" \
    "$(grep -q 'exec utils-pg pg_dump -U postgres -Fc --lock-wait-timeout=60s -d wainwrights' "$H/log"; echo $?)"
check "backup: checks the dump with pg_restore -f /dev/null" "$(grep -q 'exec -i utils-pg pg_restore -f /dev/null' "$H/log"; echo $?)"
check "backup: globals use --no-role-passwords" "$(grep -q 'pg_dumpall .*--globals-only --no-role-passwords' "$H/log"; echo $?)"
check "backup: precondition is select 1" "$(grep -q "psql -U postgres .*select 1" "$H/log"; echo $?)"
check "backup: output has no shell trace" "$(cond_not grep -E '^\++ ' <<< "$OUT")"

# Empty list, missing wainwrights, failed precondition: all fail closed, nothing is written.
h="$(new_host)"
runb "$h" FAKE_DBS=""
check "backup: an empty database list fails" "$(status_nonzero)" "$OUT"
check "backup: ...and says the list is empty" "$(has "database list is empty")" "$OUT"
check "backup: ...and writes no marker" "$(absent "$h/backups/marker")"
runb "$h" "FAKE_DBS=cinelight"
check "backup: a list without wainwrights fails" "$(status_nonzero)" "$OUT"
check "backup: ...and says which database is missing" "$(has "required database 'wainwrights'")" "$OUT"
runb "$h" FAKE_PRECOND_FAIL=1
check "backup: a failed select 1 fails" "$(status_nonzero)" "$OUT"
runb "$h" FAKE_PRECOND_OUT=0
check "backup: select 1 printing anything but 1 fails" "$(status_nonzero)" "$OUT"
runb "$h" FAKE_LIST_FAIL=1
check "backup: a failed database query fails" "$(status_nonzero)" "$OUT"
runb "$h" "FAKE_DBS=wainwrights\nbad name;x\n"
check "backup: an unsafe database name fails closed" "$(status_nonzero)" "$OUT"
check "backup: ...and no dump was written" "$(cond test -z "$(find "$h/backups/daily" -name '*.dump')")"
runb "$h" FAKE_FLOCK_RC=1
check "backup: a held lock fails" "$(status_nonzero)" "$OUT"
check "backup: ...and says so" "$(has "holds the lock")"

# A failing database fails the run, writes no marker and leaves no partial file; the others still back up.
good_host
cp "$H/backups/marker" "$ROOT/marker.old"
runb "$H" FAKE_DUMP_FAIL=cinelight FAKE_DUMP_TAG=v2
check "backup: a pg_dump failure fails the run" "$(status_nonzero)" "$OUT"
check "backup: ...the marker is not rewritten" "$(cond cmp -s "$H/backups/marker" "$ROOT/marker.old")"
check "backup: ...no temp file is left" "$(cond test -z "$(find "$H/backups" -name '.partial-*')")"
check "backup: ...the other database was still backed up" "$(cond grep -q v2 "$(DUMP_W)")"
check "backup: ...the log names the database" "$(has "FAILED cinelight")" "$OUT"

# pipefail: pg_dump exits non-zero after writing a complete-looking file.
good_host
runb "$H" FAKE_DUMP_RC1=wainwrights FAKE_DUMP_TAG=v2
check "backup: a non-zero pg_dump with complete-looking output still fails" "$(status_nonzero)" "$OUT"
check "backup: ...and does not replace the existing dump" "$(cond_not grep -q v2 "$(DUMP_W)")"

# Partial dump: pg_dump exits 0 with a truncated stream; only the full pg_restore read catches it.
good_host
cp "$(DUMP_W)" "$ROOT/good.dump"
runb "$H" FAKE_DUMP_TRUNC=wainwrights
check "backup: a truncated dump fails the run" "$(status_nonzero)" "$OUT"
check "backup: ...it fails on the pg_restore check" "$(has "did not pass pg_restore")" "$OUT"
check "backup: ...the previous good dump is untouched (temp-then-rename)" "$(cond cmp -s "$(DUMP_W)" "$ROOT/good.dump")"
check "backup: ...a truncated file is never installed" "$(cond_not grep -q TRUNC "$(DUMP_W)")"

# Globals must hold a role.
h="$(new_host)"
runb "$h" "FAKE_GLOBALS=-- just a comment"
check "backup: globals without CREATE ROLE fail" "$(status_nonzero)" "$OUT"
runb "$h" FAKE_GLOBALS=
check "backup: empty globals fail" "$(status_nonzero)" "$OUT"
runb "$h" FAKE_GLOBALS_FAIL=1
check "backup: a failed pg_dumpall fails" "$(status_nonzero)" "$OUT"
check "backup: ...and no marker was written" "$(absent "$h/backups/marker")"

# Temp-then-rename: while pg_dump runs the final name does not exist and a .partial- file does.
h="$(new_host)"
runb "$h" "FAKE_PEEK_DIR=$h/backups/daily" "FAKE_PEEK_LOG=$h/peek"
check "backup: while dumping, only a .partial- file exists" \
    "$(grep -q '^\.partial-' "$h/peek"; echo $?)" "$(cat "$h/peek" 2>/dev/null)"
check "backup: while dumping, the final name does not exist yet" "$(cond_not grep -q 'wainwrights_2026' "$h/peek")"

# A same-day re-run replaces that day's file.
good_host
runb "$H" FAKE_DUMP_TAG=v2
check "backup: a same-day re-run succeeds" "$STATUS" "$OUT"
check "backup: ...and replaces that day's dump" "$(cond grep -q v2 "$(DUMP_W)")"

# Rotation: 7 daily kept per database, temp names untouched, a dropped database's dumps kept.
h="$(new_host)"
mkdir -p "$h/backups/daily/wainwrights" "$h/backups/daily/olddb"
for d in 01 02 03 04 05 06 07 08 09; do
    echo old > "$h/backups/daily/wainwrights/wainwrights_2026-10-$d.dump"
done
for d in 01 02 03 04 05 06 07 08 09 10; do
    echo old > "$h/backups/daily/olddb/olddb_2026-09-$d.dump"
done
echo half > "$h/backups/daily/wainwrights/.partial-ABCDEF"
runb "$h"
count="$(find "$h/backups/daily/wainwrights" -name 'wainwrights_*.dump' | wc -l | tr -d ' ')"
check "backup: rotation keeps 7 daily dumps" "$(cond test "$count" = 7)" "kept $count"
check "backup: rotation drops the oldest first" "$(absent "$h/backups/daily/wainwrights/wainwrights_2026-10-01.dump")"
check "backup: rotation keeps today's dump" "$(exists "$h/backups/daily/wainwrights/wainwrights_2026-11-07.dump")"
check "backup: a .partial- file never matches the rotation pattern" "$(exists "$h/backups/daily/wainwrights/.partial-ABCDEF")"
check "backup: a dropped database's dumps are never rotated" \
    "$(cond test "$(find "$h/backups/daily/olddb" -name '*.dump' | wc -l | tr -d ' ')" = 10)"
check "backup: a dropped database is warned about" "$(has "olddb is no longer a live database")" "$OUT"

# Weekly copy on Sunday, 4 kept.
h="$(new_host)"
mkdir -p "$h/backups/weekly/wainwrights"
for d in 01 02 03 04 05; do echo old > "$h/backups/weekly/wainwrights/wainwrights_week_2026-10-$d.dump"; done
runb "$h" BACKUP_DOW=7
check "backup: Sunday makes a weekly copy" "$(exists "$h/backups/weekly/wainwrights/wainwrights_week_2026-11-07.dump")"
check "backup: weekly rotation keeps 4" \
    "$(cond test "$(find "$h/backups/weekly/wainwrights" -name '*.dump' | wc -l | tr -d ' ')" = 4)"
runb "$h" BACKUP_DOW=2
check "backup: other days make no new weekly copy" "$(cond test "$(find "$h/backups/weekly/wainwrights" -name '*2026-11-0[1-6]*' | wc -l | tr -d ' ')" = 0)"

# Off-box: optional, no --delete, BatchMode, failure is a warning with a status file.
h="$(new_host)"
runb "$h"
check "backup: no OFFBOX_DEST means a warning and no status file" "$(absent "$h/backups/offbox-status")"
check "backup: ...the warning is printed" "$(has "OFFBOX_DEST is not set")"
runb "$h" OFFBOX_DEST=remote:/dest
check "backup: an off-box copy succeeds" "$STATUS" "$OUT"
check "backup: ...status is ok" "$(grep -q '^ok ' "$h/backups/offbox-status"; echo $?)"
check "backup: ...rsync uses BatchMode" "$(grep -q 'BatchMode=yes' "$h/log"; echo $?)"
check "backup: ...rsync never uses --delete" "$(cond_not grep -q -- '--delete' "$h/log")"
check "backup: ...rsync excludes temp files" "$(grep -q -- "--exclude .partial-\*" "$h/log"; echo $?)" "$(grep rsync "$h/log")"
runb "$h" OFFBOX_DEST=remote:/dest FAKE_RSYNC_RC=1
check "backup: a failed off-box copy is not fatal" "$STATUS" "$OUT"
check "backup: ...but is recorded as failed" "$(grep -q '^failed ' "$h/backups/offbox-status"; echo $?)"
check "backup: ...and warned about" "$(has "WARNING: off-box copy failed")"
runb "$h"
check "backup: unsetting OFFBOX_DEST removes a stale status file" "$(absent "$h/backups/offbox-status")"

# No data in logs: failing fakes print SECRETTABLE/SECRETROW; they must reach only the host-only file.
h="$(new_host)"
runb "$h" FAKE_DUMP_FAIL=wainwrights
check "backup: stderr from pg_dump never reaches the output (table name)" "$(lacks SECRETTABLE)" "$OUT"
check "backup: ...nor row data" "$(lacks SECRETROW)" "$OUT"
check "backup: ...nor the log file" "$(cond_not grep -q SECRET "$h/backups/backup.log")"
check "backup: ...it goes to the mode-600 error file" \
    "$(grep -q SECRETTABLE "$h/backups/backup-errors.log"; echo $?)"
check "backup: ...which is mode 600" "$(cond test "$(perm "$h/backups/backup-errors.log")" = 600)"
runb "$h" FAKE_PRECOND_FAIL=1
check "backup: stderr from psql never reaches the output" "$(lacks SECRETTABLE)" "$OUT"
runb "$h" FAKE_CHECK_FAIL=1
check "backup: stderr from the pg_restore check never reaches the output" "$(lacks SECRETTABLE)" "$OUT"
check "backup: the script has no xtrace" "$(code_lacks '(^|[^-])set -[a-z]*x|set -o xtrace' "$OPS/backup.sh" "$OPS/verify.sh" "$OPS/install.sh")"
runb "$h" SHELLOPTS=xtrace
check "backup: refuses to run under xtrace" "$(status_nonzero)"

# ============================ verify.sh =================================================================
good_host
runv "$H"
check "verify: a good backup passes" "$STATUS" "$OUT"
check "verify: reports each database with errors, tables and rows" "$(has "Restored wainwrights: errors=0 tables=2 rows=5")" "$OUT"
check "verify: the output has no shell trace" "$(cond_not grep -E '^\++ ' <<< "$OUT")"
check "verify: starts the scratch server from utils-pg's own image id" \
    "$(grep -q "docker run .* sha256:fakeimg" "$H/log"; echo $?)" "$(grep 'docker run' "$H/log")"
RUNLINE="$(grep '^docker run' "$H/log")"
check "verify: scratch runs with --rm, --network none and a tmpfs data dir" \
    "$(contains "$RUNLINE" "--rm"; a=$?; contains "$RUNLINE" "--network none"; b=$?; contains "$RUNLINE" "--tmpfs /var/lib/postgresql/data"; c=$?; echo $((a + b + c)))" "$RUNLINE"
check "verify: scratch is named utils-pg-verify-<run id>" "$(contains "$RUNLINE" "--name utils-pg-verify-t1"; echo $?)"
check "verify: scratch publishes no port" "$(if [[ "$RUNLINE" == *" -p "* || "$RUNLINE" == *"--publish"* ]]; then echo 1; else echo 0; fi)"
check "verify: scratch is removed with docker rm -fv" "$(grep -q '^docker rm -fv utils-pg-verify-t1' "$H/log"; echo $?)"
check "verify: never touches goldenhour's container" "$(cond_not grep -qi goldenhour "$H/log")"
check "verify: never uses exec -t" "$(cond_not grep -E 'docker exec -[a-z]*t' "$H/log")"
check "verify: restores with --no-owner --no-acl --exit-on-error" "$(grep -q 'pg_restore -U postgres -d wainwrights --no-owner --no-acl --exit-on-error' "$H/log"; echo $?)"
check "verify: the error file is mode 600" "$(cond test "$(perm "$H/backups/verify-errors.log")" = 600)"
check "verify: no artifact or log tail is produced" "$(cond test -z "$(find "$H" -name '*.tar' -o -name '*.zip')")"

# An empty database passes only when it really was empty.
runv "$H" "FAKE_RESTORED=cinelight=0:0 wainwrights=2:5"
check "verify: a legitimately empty database passes" "$STATUS" "$OUT"
check "verify: ...and reports 0 tables, 0 rows" "$(has "Restored cinelight: errors=0 tables=0 rows=0")" "$OUT"

# Restored total below the live total, and zero when the live total was not zero.
runv "$H" "FAKE_RESTORED=wainwrights=2:3"
check "verify: a restored total below 90% of the marker's live total fails" "$(status_nonzero)" "$OUT"
check "verify: ...with the reason" "$(has "fewer rows")" "$OUT"
# Tolerance: the live total is counted just after the dump, so writes in between are allowed for.
H2="$(new_host)"
runb "$H2" "FAKE_LIVE=wainwrights=100 cinelight=0"
check "backup: exact live totals are recorded in the marker" \
    "$(grep -qE "^wainwrights$(printf '\t')[0-9]+$(printf '\t')100\$" "$H2/backups/marker"; echo $?)" "$(cat "$H2/backups/marker")"
runv "$H2" "FAKE_LIVE=wainwrights=100 cinelight=0" "FAKE_RESTORED=wainwrights=2:95"
check "verify: live 100, restored 95 passes (within the 10% tolerance)" "$STATUS" "$OUT"
runv "$H2" "FAKE_LIVE=wainwrights=100 cinelight=0" "FAKE_RESTORED=wainwrights=2:90"
check "verify: live 100, restored 90 passes (the boundary)" "$STATUS" "$OUT"
runv "$H2" "FAKE_LIVE=wainwrights=100 cinelight=0" "FAKE_RESTORED=wainwrights=2:89"
check "verify: live 100, restored 89 fails (just under the boundary)" "$(status_nonzero)" "$OUT"
runv "$H2" "FAKE_LIVE=wainwrights=100 cinelight=0" "FAKE_RESTORED=wainwrights=2:80"
check "verify: live 100, restored 80 fails" "$(status_nonzero)" "$OUT"
check "verify: ...with the reason" "$(has "fewer rows")" "$OUT"
check "verify: the scratch tmpfs is capped at 2g by default" \
    "$(grep -qF -- '--tmpfs /var/lib/postgresql/data:rw,size=2g' "$H2/log"; echo $?)" "$(grep '^docker run' "$H2/log" | tail -1)"
runv "$H2" VERIFY_TMPFS_SIZE=512m
check "verify: VERIFY_TMPFS_SIZE overrides the cap" "$(grep -qF -- 'size=512m' "$H2/log"; echo $?)"
runv "$H" "FAKE_RESTORED=wainwrights=0:0"
check "verify: a zero restored total with a non-zero live total fails" "$(status_nonzero)" "$OUT"
runv "$H" "FAKE_RESTORED=wainwrights=2:9"
check "verify: a restored total above the live total passes" "$STATUS" "$OUT"
runv "$H" "FAKE_RESTORED=cinelight=1:3 wainwrights=2:5"
check "verify: an empty-at-dump database that now restores rows passes" "$STATUS" "$OUT"

# Restore errors: nothing from stderr reaches the output.
runv "$H" FAKE_RESTORE_FAIL=wainwrights
check "verify: a failed pg_restore fails" "$(status_nonzero)" "$OUT"
check "verify: ...the output has no table name" "$(lacks SECRETTABLE)" "$OUT"
check "verify: ...the output has no row data" "$(lacks SECRETROW)" "$OUT"
check "verify: ...the error file holds the detail" "$(grep -q SECRETTABLE "$H/backups/verify-errors.log"; echo $?)"
check "verify: ...only the database and an error count are shown" "$(has "Restore of wainwrights: 1 error(s)")" "$OUT"
check "verify: scratch is removed even after a failure" "$(grep -c '^docker rm -fv utils-pg-verify-t1' "$H/log" | awk '{ exit ($1 >= 2 ? 0 : 1) }'; echo $?)"

cp "$(DUMP_W)" "$ROOT/good.dump"
printf 'PGDMP wainwrights TRUNC' > "$(DUMP_W)"
runv "$H"
check "verify: a truncated dump on disk fails" "$(status_nonzero)" "$OUT"
check "verify: ...without leaking" "$(lacks SECRET)" "$OUT"
cp "$ROOT/good.dump" "$(DUMP_W)"

# Drift.
echo "# changed" >> "$H/installed/backup.sh"
runv "$H"
check "verify: a changed installed backup.sh fails (sha256 drift)" "$(status_nonzero)" "$OUT"
check "verify: ...and names the file" "$(has "Installed backup.sh differs")" "$OUT"
cp "$OPS/backup.sh" "$H/installed/backup.sh"
echo "# changed" >> "$H/installed/verify.sh"
runv "$H"
check "verify: a changed installed verify.sh fails" "$(status_nonzero)" "$OUT"
cp "$OPS/verify.sh" "$H/installed/verify.sh"
rm "$H/installed/verify.sh"
runv "$H"
check "verify: a missing installed script fails" "$(status_nonzero)" "$OUT"
cp "$OPS/verify.sh" "$H/installed/verify.sh"

# Live list and precondition.
runv "$H" FAKE_DBS=
check "verify: an empty live list fails" "$(status_nonzero)" "$OUT"
check "verify: ...and says so" "$(has "live database list is empty")" "$OUT"
runv "$H" FAKE_DBS=cinelight
check "verify: a live list without wainwrights fails" "$(status_nonzero)" "$OUT"
runv "$H" FAKE_PRECOND_FAIL=1
check "verify: a failed select 1 fails" "$(status_nonzero)" "$OUT"
runv "$H" FAKE_IMAGE_ID=
check "verify: an unreadable image fails" "$(status_nonzero)" "$OUT"
runv "$H" FAKE_RUN_FAIL=1
check "verify: a scratch server that will not start fails" "$(status_nonzero)" "$OUT"
runv "$H" FAKE_NOT_READY=1
check "verify: a scratch server that never becomes ready fails" "$(status_nonzero)" "$OUT"

# Marker versus live database list.
runv "$H" "FAKE_DBS=cinelight\nnewdb\nwainwrights\n"
check "verify: a live database missing from the marker fails" "$(status_nonzero)" "$OUT"
check "verify: ...and names it" "$(has "newdb is live but missing from the marker")" "$OUT"
runv "$H" "FAKE_DBS=wainwrights\n"
check "verify: a marker database no longer live fails" "$(status_nonzero)" "$OUT"
mv "$H/backups/marker" "$ROOT/marker.keep"
runv "$H"
check "verify: a missing marker fails" "$(status_nonzero)" "$OUT"
touch -t 202001010000 "$ROOT/marker.keep"
cp -p "$ROOT/marker.keep" "$H/backups/marker"
runv "$H"
check "verify: a stale marker fails" "$(status_nonzero)" "$OUT"
touch "$H/backups/marker"

# Freshness.
touch -t 202001010000 "$(DUMP_W)"
runv "$H"
check "verify: a stale dump fails" "$(status_nonzero)" "$OUT"
check "verify: ...and names the database" "$(has "newest dump for wainwrights is more than")" "$OUT"
touch "$(DUMP_W)"
rm "$H/backups/daily/cinelight/cinelight_2026-11-07.dump"
runv "$H"
check "verify: a database with no dump fails" "$(status_nonzero)" "$OUT"
check "verify: ...and names it" "$(has "No dump for cinelight")" "$OUT"
echo x > "$H/backups/daily/cinelight/cinelight_2026-11-07.dump"
printf 'PGDMP cinelight v1 -END\n' > "$H/backups/daily/cinelight/cinelight_2026-11-07.dump"
# A partial file newer than the good dump is never "newest".
echo "garbage" > "$H/backups/daily/wainwrights/.partial-ZZZZZZ"
runv "$H"
check "verify: a newer .partial- file is never the newest dump" "$STATUS" "$OUT"
rm "$H/backups/daily/wainwrights/.partial-ZZZZZZ"

# Globals.
mv "$H/backups/globals/globals_2026-11-07.sql" "$ROOT/globals.keep"
runv "$H"
check "verify: missing globals fail" "$(status_nonzero)" "$OUT"
echo "-- nothing" > "$H/backups/globals/globals_2026-11-07.sql"
runv "$H"
check "verify: globals without CREATE ROLE fail" "$(status_nonzero)" "$OUT"
: > "$H/backups/globals/globals_2026-11-07.sql"
runv "$H"
check "verify: empty globals fail" "$(status_nonzero)" "$OUT"
cp "$ROOT/globals.keep" "$H/backups/globals/globals_2026-11-07.sql"

# Off-box status.
runv "$H"
check "verify: no off-box copy is a warning, not a failure" "$STATUS" "$OUT"
check "verify: ...the warning is printed" "$(has "::warning::No off-box copy")" "$OUT"
echo "failed 2026-11-07T00:00:00Z" > "$H/backups/offbox-status"
runv "$H"
check "verify: a failed off-box copy (never succeeded) is a warning" "$STATUS" "$OUT"
date -u +%Y-%m-%dT%H:%M:%SZ > "$H/backups/offbox-last-ok"
touch -t 202001010000 "$H/backups/offbox-last-ok"
runv "$H"
check "verify: a failed off-box copy with no success for 48h fails" "$(status_nonzero)" "$OUT"
touch "$H/backups/offbox-last-ok"
runv "$H"
check "verify: a failed off-box copy right after a success is a warning" "$STATUS" "$OUT"
echo "ok 2026-11-07T00:00:00Z" > "$H/backups/offbox-status"
runv "$H"
check "verify: an ok off-box status passes" "$STATUS" "$OUT"
touch -t 202001010000 "$H/backups/offbox-status"
runv "$H"
check "verify: an off-box status older than 48h fails" "$(status_nonzero)" "$OUT"

# xtrace refusal.
runv "$H" SHELLOPTS=xtrace
check "verify: refuses to run under xtrace" "$(status_nonzero)"

# ============================ install.sh ================================================================
h="$(new_host)"
mkdir -p "$h/units"
cp "$OPS/utils-pg-backup.service" "$OPS/utils-pg-backup.timer" "$h/units/"
mkdir -p "$h/target"
echo "old script" > "$h/target/backup.sh"
chmod 755 "$h/target/backup.sh"
inode_of() { stat -c %i "$1" 2>/dev/null || stat -f %i "$1"; }
inode_before="$(inode_of "$h/target/backup.sh")"
set +e
OUT="$(INSTALL_DIR="$h/target" UNIT_DIR="$h/units" "$OPS/install.sh" 2>&1)"
STATUS=$?
set -e
check "install: matching units exit 0" "$STATUS" "$OUT"
check "install: backup.sh is the repo's" "$(cond cmp -s "$OPS/backup.sh" "$h/target/backup.sh")"
check "install: verify.sh is the repo's" "$(cond cmp -s "$OPS/verify.sh" "$h/target/verify.sh")"
check "install: scripts are mode 755" "$(cond test "$(perm "$h/target/backup.sh")$(perm "$h/target/verify.sh")" = 755755)"
check "install: the folder is mode 700" "$(cond test "$(perm "$h/target")" = 700)"
check "install: replaces the file by rename (new inode), never in place" \
    "$(cond test "$(inode_of "$h/target/backup.sh")" != "$inode_before")"
check "install: no temp file left behind" "$(cond test -z "$(find "$h/target" -name '.install-*')")"
echo "# drift" >> "$h/units/utils-pg-backup.timer"
set +e
OUT="$(INSTALL_DIR="$h/target" UNIT_DIR="$h/units" "$OPS/install.sh" 2>&1)"
STATUS=$?
set -e
check "install: a differing unit fails" "$(status_nonzero)" "$OUT"
check "install: ...and prints the sudo commands" "$(has "sudo systemctl daemon-reload")" "$OUT"
check "install: ...including the cp of the units" "$(has "sudo cp ")" "$OUT"
rm "$h/units/utils-pg-backup.service"
set +e
OUT="$(INSTALL_DIR="$h/target" UNIT_DIR="$h/units" "$OPS/install.sh" 2>&1)"
STATUS=$?
set -e
check "install: a missing unit fails" "$(status_nonzero)" "$OUT"

# ============================ static rules ==============================================================
check "static: no script ever uses exec -t / -it" "$(code_lacks 'exec +-[a-zA-Z]*t' "$OPS"/*.sh "$OPS"/tests/*.sh)"
check "static: the scripts never name goldenhour's database container" "$(cond_not grep -rn 'goldenhour-db' "$OPS" "$REPO"/.github/workflows/*utils-pg*)"
check "static: no LAN address or off-box host in ops/ or the new workflows" \
    "$(cond_not grep -rnE '192\.168\.|10\.[0-9]+\.[0-9]+\.[0-9]+|172\.(1[6-9]|2[0-9]|3[01])\.|\.local\b|\.lan\b' "$OPS" "$REPO"/.github/workflows/*utils-pg*)"
check "static: dumps are written to a .partial- temp name before the rename" "$(grep -qF '.partial-XXXXXX' "$OPS/backup.sh"; echo $?)"
check "static: backup.sh uses flock and umask 077" "$(grep -q 'flock -n 9' "$OPS/backup.sh" && grep -q '^umask 077' "$OPS/backup.sh"; echo $?)"
for wf in install-utils-pg-backup utils-pg-backup-verify; do
    f="$REPO/.github/workflows/$wf.yml"
    check "static: $wf triggers on nothing but schedule/workflow_dispatch" \
        "$(cond_not grep -nE '^\s*(pull_request|pull_request_target|push|issue_comment|workflow_run|workflow_call):' "$f")"
    check "static: $wf is dispatchable by hand" "$(grep -q '^  workflow_dispatch:' "$f"; echo $?)"
done
check "static: the verify workflow runs at 07:30 UTC" "$(grep -q "cron: '30 7 \* \* \*'" "$REPO/.github/workflows/utils-pg-backup-verify.yml"; echo $?)"
check "static: the verify workflow's watchdog and notifiers run off the box" \
    "$(awk '/^  watchdog:/,/^  notify-failure:/' "$REPO/.github/workflows/utils-pg-backup-verify.yml" | grep -q 'runs-on: ubuntu-latest'; echo $?)"
check "static: the verify workflow notifies through ci-failure-issue.yml" \
    "$(grep -c 'uses: ./.github/workflows/ci-failure-issue.yml' "$REPO/.github/workflows/utils-pg-backup-verify.yml" | awk '{ exit ($1 == 2 ? 0 : 1) }'; echo $?)"
check "static: the verify workflow uploads no artifact" "$(cond_not grep -q 'upload-artifact' "$REPO/.github/workflows/utils-pg-backup-verify.yml")"
check "static: the verify workflow always removes the scratch container" \
    "$(grep -q 'docker rm -fv "utils-pg-verify-' "$REPO/.github/workflows/utils-pg-backup-verify.yml"; echo $?)"
check "static: the e2e job is a new job with its own name and the old check names are intact" \
    "$(grep -q 'name: utils-pg backup end-to-end' "$REPO/.github/workflows/ci.yml" && grep -q 'name: Build, Test & Coverage' "$REPO/.github/workflows/ci.yml" && grep -q 'name: Detect docs-only change' "$REPO/.github/workflows/ci.yml"; echo $?)"
check "static: the units run as gregochr, oneshot, 30 minute timeout" \
    "$(grep -q '^User=gregochr' "$OPS/utils-pg-backup.service" && grep -q '^Type=oneshot' "$OPS/utils-pg-backup.service" && grep -q '^TimeoutStartSec=30min' "$OPS/utils-pg-backup.service"; echo $?)"
check "static: the unit reads the host-only EnvironmentFile (optional)" "$(grep -q '^EnvironmentFile=-/home/gregochr/.config/utils-pg-backup.env' "$OPS/utils-pg-backup.service"; echo $?)"
check "static: the timer is 02:30 London, persistent, randomized" \
    "$(grep -q '^OnCalendar=\*-\*-\* 02:30:00 Europe/London' "$OPS/utils-pg-backup.timer" && grep -q '^Persistent=true' "$OPS/utils-pg-backup.timer" && grep -q '^RandomizedDelaySec=10min' "$OPS/utils-pg-backup.timer"; echo $?)"

echo
echo "$PASSED passed, $FAILED failed"
[[ "$FAILED" -eq 0 ]]
