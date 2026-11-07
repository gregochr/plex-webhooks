#!/bin/bash
# ============================================================================
# Nightly backup of EVERY database in the shared `utils-pg` Postgres server
# (CineLight, wainwrights, and whatever is added later).
#
# Ported from goldenhour's backup-postgres.sh, which exists because goldenhour's
# first backup failed silently for 132 nights. The rules that came out of that:
#   - a job's own success claim is worthless, so verify.sh checks from outside;
#   - fail CLOSED: an empty database list, a partial dump or a failed precondition
#     is an error, never a quiet success;
#   - this repository is PUBLIC: no table name, row or dump content is ever
#     printed or logged. pg_dump/pg_restore/psql stderr goes to a mode-600 file
#     on the host only. The log carries database names, sizes and counts.
#
# Installed by .github/workflows/install-utils-pg-backup.yml into
# ~/services/utils-pg/backup/ and run by utils-pg-backup.timer as gregochr.
#
# Config (environment; the systemd unit also reads ~/.config/utils-pg-backup.env):
#   PG_CONTAINER  the Postgres container           (default utils-pg)
#   BACKUP_ROOT   where backups are written        (default ~/backups/utils-pg)
#   OFFBOX_DEST   optional rsync destination for a second copy. Host-only: it
#                 is set in the EnvironmentFile, never in this repository.
#   REQUIRED_DB   a database that must exist       (default wainwrights)
#   BACKUP_DATE / BACKUP_DOW  test hooks (date as YYYY-MM-DD, day of week 1-7)
#
# Layout under BACKUP_ROOT (every directory mode 700, every file mode 600):
#   daily/<db>/<db>_YYYY-MM-DD.dump   weekly/<db>/<db>_week_YYYY-MM-DD.dump
#   globals/globals_YYYY-MM-DD.sql    marker, backup.log, backup-errors.log
#
# Restore one database (to a scratch server, never over a live one by accident):
#   docker exec -i utils-pg pg_restore -U postgres -d <db> --no-owner \
#       < daily/<db>/<db>_<date>.dump
# ============================================================================

set -euo pipefail
umask 077
export LC_ALL=C

# A trace would print every command, including query text. Refuse, rather than leak.
[[ $- != *x* ]] || { echo "ERROR: refusing to run with xtrace enabled." >&2; exit 1; }

PG_CONTAINER="${PG_CONTAINER:-utils-pg}"
BACKUP_ROOT="${BACKUP_ROOT:-${HOME}/backups/utils-pg}"
OFFBOX_DEST="${OFFBOX_DEST:-}"
REQUIRED_DB="${REQUIRED_DB:-wainwrights}"
DATE="${BACKUP_DATE:-$(date +%Y-%m-%d)}"
DAY_OF_WEEK="${BACKUP_DOW:-$(date +%u)}"   # 7 = Sunday
DAILY_KEEP=7
WEEKLY_KEEP=4
GLOBALS_KEEP=7

DAILY_DIR="${BACKUP_ROOT}/daily"
WEEKLY_DIR="${BACKUP_ROOT}/weekly"
GLOBALS_DIR="${BACKUP_ROOT}/globals"
LOG_FILE="${BACKUP_ROOT}/backup.log"
ERR_FILE="${BACKUP_ROOT}/backup-errors.log"   # host only; never printed, never uploaded
MARKER_FILE="${BACKUP_ROOT}/marker"
OFFBOX_STATUS_FILE="${BACKUP_ROOT}/offbox-status"
OFFBOX_LAST_OK_FILE="${BACKUP_ROOT}/offbox-last-ok"

# --- Setup: private folders, then the lock --------------------------------------
for d in "$BACKUP_ROOT" "$DAILY_DIR" "$WEEKLY_DIR" "$GLOBALS_DIR"; do
    mkdir -p "$d"
    chmod 700 "$d"
done

TMPS=()
cleanup() {
    if [ "${#TMPS[@]}" -gt 0 ]; then
        rm -f "${TMPS[@]}"
    fi
}
trap cleanup EXIT

log() { printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" | tee -a "$LOG_FILE" >&2; }
die() { log "ERROR: $*"; exit 1; }

# A manual run and the timer must never overlap.
exec 9>"${BACKUP_ROOT}/.lock"
flock -n 9 || die "another backup run holds the lock."

: > "$ERR_FILE"
chmod 600 "$ERR_FILE" "$LOG_FILE" 2>/dev/null || true

# Never `docker exec -t`: a pseudo-terminal rewrites newlines and corrupts binary dumps.
psql_at() { docker exec "$PG_CONTAINER" psql -U postgres -v ON_ERROR_STOP=1 -Atq "$@"; }

# --- Preconditions --------------------------------------------------------------
command -v docker >/dev/null 2>&1 || die "docker not found on PATH."

PRE="$(psql_at -c 'select 1' 2>>"$ERR_FILE")" \
    || die "cannot run psql in ${PG_CONTAINER} (is it running? details in ${ERR_FILE})."
[ "$PRE" = "1" ] || die "precondition 'select 1' did not print 1."

# --- Enumerate databases --------------------------------------------------------
LIST="$(psql_at -c "select datname from pg_database where datallowconn and not datistemplate and datname <> 'postgres' order by datname" 2>>"$ERR_FILE")" \
    || die "could not list databases (details in ${ERR_FILE})."

DBS=()
while IFS= read -r line; do
    if [ -n "$line" ]; then
        DBS+=("$line")
    fi
done <<< "$LIST"

# An empty list must never be a success: it is exactly what a broken query or a
# wrong container returns, and it would "back up" nothing and say so happily.
[ "${#DBS[@]}" -gt 0 ] || die "database list is empty."

FOUND_REQUIRED=0
for db in "${DBS[@]}"; do
    # Names become file paths and SQL identifiers: accept only plain ones, fail closed otherwise.
    [[ "$db" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]*$ ]] || die "a database has a name this script will not handle safely."
    if [ "$db" = "$REQUIRED_DB" ]; then
        FOUND_REQUIRED=1
    fi
done
[ "$FOUND_REQUIRED" -eq 1 ] || die "required database '${REQUIRED_DB}' is not in the list."
log "Databases to back up: ${#DBS[@]} (${DBS[*]})"

# --- Rotation -------------------------------------------------------------------
# rotate_keep <keep> <file>...   (the caller passes a glob; an unmatched glob is filtered out)
rotate_keep() {
    local keep="$1" f i n
    shift
    local files=()
    for f in "$@"; do
        if [ -f "$f" ]; then
            files+=("$f")
        fi
    done
    n="${#files[@]}"
    for ((i = 0; i < n - keep; i++)); do
        rm -f "${files[$i]}"
        log "Rotated old backup: $(basename "${files[$i]}")"
    done
}

# --- One database ---------------------------------------------------------------
MARKER_LINES=()
FAILED=0

backup_db() {
    local db="$1" dir="${DAILY_DIR}/$1" final live tmp size wdir wfinal wtmp
    final="${dir}/${db}_${DATE}.dump"
    mkdir -p "$dir" "${WEEKLY_DIR}/${db}" || return 1
    chmod 700 "$dir" "${WEEKLY_DIR}/${db}" || return 1

    tmp="$(mktemp "${dir}/.partial-XXXXXX")" || return 1
    TMPS+=("$tmp")

    # Written to a temp name that no rotation or "newest" glob matches, so an interrupted run
    # can never be mistaken for a backup. The exit code is the only success signal for the dump.
    if ! docker exec "$PG_CONTAINER" pg_dump -U postgres -Fc --lock-wait-timeout=60s -d "$db" \
            > "$tmp" 2>>"$ERR_FILE"; then
        log "FAILED ${db}: pg_dump exited non-zero (details in ${ERR_FILE})."
        return 1
    fi
    [ -s "$tmp" ] || { log "FAILED ${db}: pg_dump produced an empty file."; return 1; }

    # pg_restore -f /dev/null reads every data block. `--list` reads only the table of
    # contents, so a dump truncated mid-data would pass it.
    if ! docker exec -i "$PG_CONTAINER" pg_restore -f /dev/null < "$tmp" 2>>"$ERR_FILE"; then
        log "FAILED ${db}: the dump did not pass pg_restore (details in ${ERR_FILE})."
        return 1
    fi

    # Exact live row total (count(*) over every user table, built in one query from the catalog), taken
    # right after the dump. verify.sh compares the restored total against it, allowing for writes that
    # land between the dump and this count.
    live="$(psql_at -d "$db" -c "select coalesce(sum((xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I.%I', n.nspname, c.relname), false, true, '')))[1]::text::bigint), 0) from pg_class c join pg_namespace n on n.oid = c.relnamespace where c.relkind in ('r', 'm') and n.nspname not in ('pg_catalog', 'information_schema') and n.nspname not like 'pg_toast%'" 2>>"$ERR_FILE")" \
        || { log "FAILED ${db}: could not count the live rows."; return 1; }
    [[ "$live" =~ ^[0-9]+$ ]] || { log "FAILED ${db}: live row total was not a number."; return 1; }

    chmod 600 "$tmp" || return 1
    mv -f "$tmp" "$final" || return 1
    size="$(wc -c < "$final" | tr -d ' ')"
    MARKER_LINES+=("${db}"$'\t'"${size}"$'\t'"${live}")
    log "OK ${db}: ${size} bytes, ${live} live rows."

    if [ "$DAY_OF_WEEK" = "7" ]; then
        wdir="${WEEKLY_DIR}/${db}"
        wfinal="${wdir}/${db}_week_${DATE}.dump"
        wtmp="$(mktemp "${wdir}/.partial-XXXXXX")" || return 1
        TMPS+=("$wtmp")
        cp "$final" "$wtmp" || return 1
        mv -f "$wtmp" "$wfinal" || return 1
        log "Weekly copy for ${db} written."
    fi

    rotate_keep "$DAILY_KEEP" "${dir}/${db}_"????-??-??.dump
    rotate_keep "$WEEKLY_KEEP" "${WEEKLY_DIR}/${db}/${db}_week_"????-??-??.dump
    return 0
}

for db in "${DBS[@]}"; do
    if ! backup_db "$db"; then
        FAILED=1
    fi
done

# A database that no longer exists keeps its dumps: they may be the only copy.
for d in "${DAILY_DIR}"/*/; do
    [ -d "$d" ] || continue
    name="$(basename "$d")"
    known=0
    for db in "${DBS[@]}"; do
        if [ "$db" = "$name" ]; then
            known=1
        fi
    done
    if [ "$known" -eq 0 ]; then
        log "WARNING: ${name} is no longer a live database; its dumps are kept, not rotated."
    fi
done

# --- Globals (roles) ------------------------------------------------------------
# --no-role-passwords: the passwords live in each app's .env, not in a backup file.
backup_globals() {
    local gtmp
    gtmp="$(mktemp "${GLOBALS_DIR}/.partial-XXXXXX")" || return 1
    TMPS+=("$gtmp")
    if ! docker exec "$PG_CONTAINER" pg_dumpall -U postgres --globals-only --no-role-passwords \
            > "$gtmp" 2>>"$ERR_FILE"; then
        log "FAILED globals: pg_dumpall exited non-zero (details in ${ERR_FILE})."
        return 1
    fi
    if [ ! -s "$gtmp" ] || ! grep -q 'CREATE ROLE' "$gtmp"; then
        log "FAILED globals: the file is empty or has no role definitions."
        return 1
    fi
    chmod 600 "$gtmp" || return 1
    mv -f "$gtmp" "${GLOBALS_DIR}/globals_${DATE}.sql" || return 1
    rotate_keep "$GLOBALS_KEEP" "${GLOBALS_DIR}/globals_"????-??-??.sql
    log "Globals dumped."
}
if ! backup_globals; then
    FAILED=1
fi

# --- Off-box copy ---------------------------------------------------------------
# OFFBOX_DEST comes only from the host's EnvironmentFile. No --delete: the copy accumulates,
# and pruning it is the owner's job. A failure is a warning, not fatal: the machine on the
# other end may be asleep, and verify.sh reports stale replication separately.
if [ -n "$OFFBOX_DEST" ]; then
    if command -v rsync >/dev/null 2>&1 \
        && rsync -a --exclude '.partial-*' -e 'ssh -o BatchMode=yes -o ConnectTimeout=15' \
            "$DAILY_DIR" "$WEEKLY_DIR" "$GLOBALS_DIR" "$OFFBOX_DEST" >/dev/null 2>>"$ERR_FILE"; then
        log "Copied off-box."
        echo "ok $(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$OFFBOX_STATUS_FILE"
        date -u +%Y-%m-%dT%H:%M:%SZ > "$OFFBOX_LAST_OK_FILE"
    else
        log "WARNING: off-box copy failed; the local backup is intact but not redundant."
        echo "failed $(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$OFFBOX_STATUS_FILE"
    fi
else
    log "WARNING: OFFBOX_DEST is not set; backups exist only on this host."
    rm -f "$OFFBOX_STATUS_FILE" "$OFFBOX_LAST_OK_FILE"
fi

# --- Marker: only if EVERY database and the globals succeeded -------------------
[ "$FAILED" -eq 0 ] || die "backup incomplete: see the FAILED lines above. The marker was not updated."

MARKER_TMP="$(mktemp "${BACKUP_ROOT}/.partial-marker-XXXXXX")"
TMPS+=("$MARKER_TMP")
{
    date -u +%Y-%m-%dT%H:%M:%SZ
    printf '%s\n' "${MARKER_LINES[@]}"
} > "$MARKER_TMP"
chmod 600 "$MARKER_TMP"
mv -f "$MARKER_TMP" "$MARKER_FILE"

log "Backup complete: ${#DBS[@]} databases, marker written."
