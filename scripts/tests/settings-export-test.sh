#!/bin/bash
# Fixture tests for scripts/settings-export.sh. `docker` is a fake on PATH that answers from FAKE_* variables and logs
# what it was asked to do, so the real script runs unmodified. Nothing here touches Docker or a database; the SQL
# itself is proven against embedded PostgreSQL by the Java tests (PostgresSettingsRepositoryTest and friends).
#
# The fake psql answers by database and by what arrives on stdin: the pg_database existence check, the
# to_regclass table check, and the real query (whose stdin is kept in $FAKE_LOG.sql).
#
# Run: scripts/tests/settings-export-test.sh   (exit 0 = all passed)

set -euo pipefail

SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
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
contains() { grep -qF -- "$2" <<< "$1"; }
has() { contains "$1" "$2"; echo $?; }
lacks() { if contains "$1" "$2"; then echo 1; else echo 0; fi; }
equals() { if [[ "$1" == "$2" ]]; then echo 0; else echo 1; fi; }

# --- fake docker --------------------------------------------------------------------------------------
BIN="$ROOT/bin"
mkdir "$BIN"
cat > "$BIN/docker" <<'FAKE'
#!/bin/bash
echo "docker $*" >> "${FAKE_LOG:-/dev/null}"
case "$1" in
    ps)
        if [[ "${FAKE_PG_UP:-1}" == "1" ]]; then echo "utils-pg"; fi
        exit 0
        ;;
    exec)
        sql="$(cat)"
        case "$*" in
            *"-d postgres"*)
                [[ "${FAKE_PG_QUERY_RC:-0}" == "0" ]] || exit "$FAKE_PG_QUERY_RC"
                [[ "$sql" == *pg_database* ]] && echo "${FAKE_DB_EXISTS-1}"
                exit 0
                ;;
            *"-d cinelight"*)
                if [[ "$sql" == *to_regclass* ]]; then
                    echo "${FAKE_TABLE-t}"
                    exit 0
                fi
                printf '%s\n' "$sql" >> "${FAKE_LOG}.sql"
                if [[ "${FAKE_QUERY_RC:-0}" != "0" ]]; then
                    echo "SECRETROW half a table"
                    echo "ERROR: boom" >&2
                    exit "$FAKE_QUERY_RC"
                fi
                if [[ -n "${FAKE_ROWS-}" ]]; then printf '%b\n' "$FAKE_ROWS"; fi
                exit 0
                ;;
        esac
        exit 3
        ;;
esac
exit 0
FAKE
chmod +x "$BIN/docker"
export PATH="$BIN:$PATH"

# run <args...>: runs the script with a fresh call log; sets OUT (stdout), ERR (stderr), STATUS and LOG.
run() {
    local log="$ROOT/log.$RANDOM"
    : > "$log"
    : > "$log.sql"
    set +e
    OUT="$(FAKE_LOG="$log" "$SCRIPTS/settings-export.sh" "$@" 2>"$ROOT/stderr")"
    STATUS=$?
    set -e
    ERR="$(cat "$ROOT/stderr")"
    LOG="$(cat "$log")"
    SQL="$(cat "$log.sql")"
}

TAB=$'\t'

# ============================ settings (no arguments) ======================================================
export FAKE_ROWS='hue.hub-address=192.0.2.1\nplex.movie-play-enabled=true\npostcode=DH1 4HU'
run
check "settings: exits 0" "$(equals "$STATUS" 0)" "status=$STATUS" "$ERR"
check "settings: prints every key=value line, in the order psql returned them" \
    "$(has "$OUT" $'hue.hub-address=192.0.2.1\nplex.movie-play-enabled=true\npostcode=DH1 4HU')" "$OUT"
check "settings: the header says the file's sections are not kept and credentials are not stored" \
    "$(has "$OUT" "sections and comments are not kept")" "$OUT"
check "settings: ends with the keys to re-add by hand" \
    "$(equals "$(tail -n 2 <<< "$OUT")" $'#   hue.application-key=<your Hue application key>\n#   app.admin.password=${CINELIGHT_ADMIN_PASSWORD}')" "$OUT"
check "settings: the data lines come between the header and the trailer" \
    "$(equals "$(grep -vc '^#' <<< "$OUT")" 3)" "$OUT"
check "settings: runs exactly docker exec -i utils-pg psql -U postgres -d cinelight -X -q -v ON_ERROR_STOP=1 -At" \
    "$(has "$LOG" "docker exec -i utils-pg psql -U postgres -d cinelight -X -q -v ON_ERROR_STOP=1 -At")" "$LOG"
check "settings: never uses -t or -it (a TTY corrupts the output)" \
    "$(if grep -E '^docker exec .* (-t|-it|-ti|--tty)( |$)' <<< "$LOG" >/dev/null; then echo 1; else echo 0; fi)" "$LOG"
check "settings: the SQL goes in on stdin and orders by key" "$(has "$SQL" "ORDER BY key")" "$SQL"
check "settings: reads only the setting table" "$(lacks "$SQL" "setting_change")" "$SQL"
check "settings: checks the database and the table exist first" \
    "$(has "$LOG" "-d postgres")" "$LOG"
unset FAKE_ROWS

FAKE_ROWS='' run
check "settings: an empty table prints the header and the trailer, exit 0" \
    "$(equals "$STATUS$(grep -vc '^#' <<< "$OUT" || true)" "00")" "status=$STATUS" "$OUT"

# ============================ --changes ===================================================================
export FAKE_ROWS="2024-03-15T12:00:00Z${TAB}sse.heartbeat-interval${TAB}10000${TAB}25000${TAB}dashboard\n2024-03-15T11:00:00Z${TAB}postcode${TAB}(none)${TAB}DH1 4HU${TAB}initial"
run --changes
check "changes: default is 20, passed with -v n=" "$(has "$LOG" "-At -v n=20")" "$LOG"
check "changes: exits 0" "$(equals "$STATUS" 0)" "status=$STATUS" "$ERR"
check "changes: prints a header and the rows, tab-separated, as psql returned them" \
    "$(has "$OUT" "2024-03-15T12:00:00Z${TAB}sse.heartbeat-interval${TAB}10000${TAB}25000${TAB}dashboard")" "$OUT"
check "changes: the header names the five columns, tab-separated" \
    "$(equals "$(head -n 1 <<< "$OUT")" "# changed_at${TAB}key${TAB}old_value${TAB}new_value${TAB}source")" "$OUT"
check "changes: NULL prints as (none) through \\pset null" "$(has "$SQL" "\\pset null '(none)'")" "$SQL"
check "changes: tab-separated through \\pset fieldsep" "$(has "$SQL" "\\pset fieldsep '\\t'")" "$SQL"
check "changes: newest first, limited by the n variable" "$(has "$SQL" "ORDER BY changed_at DESC, id DESC LIMIT :n")" "$SQL"
check "changes: the number is never spliced into the SQL text" "$(lacks "$SQL" "20")" "$SQL"
check "changes: reads setting_change" "$(has "$SQL" "FROM setting_change")" "$SQL"
check "changes: never uses -t or -it" \
    "$(if grep -E '^docker exec .* (-t|-it|-ti|--tty)( |$)' <<< "$LOG" >/dev/null; then echo 1; else echo 0; fi)" "$LOG"
unset FAKE_ROWS

run --changes 5
check "changes N: --changes 5 passes n=5" "$(has "$LOG" "-At -v n=5")" "$LOG"
run --changes 0
check "changes N: --changes 0 is allowed (psql prints no rows)" "$(equals "$STATUS" 0)" "status=$STATUS" "$ERR"
check "changes N: --changes 0 passes n=0" "$(has "$LOG" "-v n=0")" "$LOG"
run --changes 100
check "changes N: a number with several digits is accepted" "$(has "$LOG" "-v n=100")" "$LOG"

FAKE_ROWS='' run --changes
check "changes: no rows prints the header only, exit 0" "$(equals "$STATUS$(wc -l <<< "$OUT" | tr -d ' ')" "01")" "status=$STATUS" "$OUT"

# ============================ bad usage ===================================================================
# shellcheck disable=SC2016 # '$(id)' is single-quoted on purpose: it must reach the script as literal text.
for bad in "abc" "-1" "5;DROP TABLE setting" "1.5" "" " 5" "5 " '$(id)' "0x10" "٣"; do
    run --changes "$bad"
    check "usage: --changes '$bad' is refused with exit 2" "$(equals "$STATUS" 2)" "status=$STATUS" "$ERR"
    check "usage: --changes '$bad' never reaches docker exec" "$(lacks "$LOG" "docker exec")" "$LOG"
    check "usage: --changes '$bad' says what is wrong" "$(has "$ERR" "whole number")" "$ERR"
done
run --changes 1 2
check "usage: --changes with two numbers is refused with exit 2" "$(equals "$STATUS" 2)" "status=$STATUS"
run --nonsense
check "usage: an unknown option is refused with exit 2" "$(equals "$STATUS" 2)" "status=$STATUS"
check "usage: it prints the usage line" "$(has "$ERR" "Usage:")" "$ERR"
run extra
check "usage: a stray argument is refused with exit 2" "$(equals "$STATUS" 2)" "status=$STATUS"

# ============================ failures ====================================================================
FAKE_PG_UP=0 run
check "missing: utils-pg not running -> exit 1" "$(equals "$STATUS" 1)" "status=$STATUS"
check "missing: utils-pg not running -> says so" "$(has "$ERR" "utils-pg container is not running")" "$ERR"
check "missing: utils-pg not running -> no output on stdout" "$(equals "$OUT" "")" "$OUT"

FAKE_DB_EXISTS='' run
check "missing: no cinelight database -> exit 1" "$(equals "$STATUS" 1)" "status=$STATUS"
check "missing: no cinelight database -> points at the bootstrap" "$(has "$ERR" "utils-pg-bootstrap.sh")" "$ERR"
check "missing: no cinelight database -> no output on stdout" "$(equals "$OUT" "")" "$OUT"

FAKE_PG_QUERY_RC=2 run
check "missing: psql cannot connect -> exit 1, as if the database is missing" "$(equals "$STATUS" 1)" "status=$STATUS"

FAKE_TABLE=f run
check "missing: no setting table -> exit 1" "$(equals "$STATUS" 1)" "status=$STATUS"
check "missing: no setting table -> says the release has not run yet" "$(has "$ERR" "setting table does not exist")" "$ERR"
FAKE_TABLE=f run --changes 3
check "missing: no setting table for --changes -> exit 1" "$(equals "$STATUS" 1)" "status=$STATUS"

FAKE_QUERY_RC=1 run
check "failure: a failing query -> exit 1" "$(equals "$STATUS" 1)" "status=$STATUS"
check "failure: a failing query -> prints no partial rows" "$(lacks "$OUT" "SECRETROW")" "$OUT"
check "failure: a failing query -> says reading failed" "$(has "$ERR" "reading the setting table failed")" "$ERR"
FAKE_QUERY_RC=1 run --changes
check "failure: a failing --changes query -> exit 1, no partial rows" \
    "$(equals "$STATUS$(grep -c SECRETROW <<< "$OUT" || true)" "10")" "status=$STATUS" "$OUT"

echo ""
echo "Passed: $PASSED, failed: $FAILED"
[[ "$FAILED" -eq 0 ]]
