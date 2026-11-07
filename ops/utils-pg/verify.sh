#!/bin/bash
# ============================================================================
# Daily check, from OUTSIDE the backup job, that the utils-pg backups happened and
# really restore. Run by .github/workflows/utils-pg-backup-verify.yml on the
# self-hosted runner (from the checkout), and by hand.
#
# A file existing proves almost nothing: a truncated dump and a good one are both a
# plausibly sized file. Only restoring each dump into a scratch server and counting
# rows proves a backup.
#
# THIS REPOSITORY IS PUBLIC. Nothing here prints a table name, a row or any dump
# content. pg_restore/psql stderr (a failed COPY prints row data) goes to a mode-600
# file on the host and is never echoed. The output shows only database names, error
# counts, table counts and row totals. No set -x, no artifacts, no log tailing.
#
# Fails closed: every check below that cannot positively pass is a failure.
#
# Config (environment):
#   PG_CONTAINER   the live server                         (default utils-pg)
#   BACKUP_ROOT    where backup.sh wrote                   (default ~/backups/utils-pg)
#   INSTALLED_DIR  where backup.sh/verify.sh are installed (default ~/services/utils-pg/backup)
#   RUN_ID         makes the scratch container name unique (default $GITHUB_RUN_ID, else the PID)
#   MAX_AGE_HOURS  freshness limit for dumps and the marker (default 26)
#   REQUIRED_DB    a database that must exist              (default wainwrights)
# ============================================================================

set -euo pipefail
umask 077
export LC_ALL=C

[[ $- != *x* ]] || { echo "ERROR: refusing to run with xtrace enabled." >&2; exit 1; }

PG_CONTAINER="${PG_CONTAINER:-utils-pg}"
BACKUP_ROOT="${BACKUP_ROOT:-${HOME}/backups/utils-pg}"
INSTALLED_DIR="${INSTALLED_DIR:-${HOME}/services/utils-pg/backup}"
RUN_ID="${RUN_ID:-${GITHUB_RUN_ID:-$$}}"
MAX_AGE_HOURS="${MAX_AGE_HOURS:-26}"
OFFBOX_MAX_AGE_HOURS="${OFFBOX_MAX_AGE_HOURS:-48}"
REQUIRED_DB="${REQUIRED_DB:-wainwrights}"
POLL_SLEEP="${VERIFY_POLL_SLEEP:-2}"
POLL_TRIES=60

REPO_OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MARKER_FILE="${BACKUP_ROOT}/marker"
ERR_FILE="${BACKUP_ROOT}/verify-errors.log"   # host only; never printed, never uploaded

[[ "$RUN_ID" =~ ^[A-Za-z0-9_.-]+$ ]] || { echo "ERROR: RUN_ID has unsafe characters." >&2; exit 1; }
SCRATCH="utils-pg-verify-${RUN_ID}"

FAILURES=0
fail() { echo "::error::$*"; FAILURES=$((FAILURES + 1)); }
note() { echo "$*"; }

# --- Helpers --------------------------------------------------------------------
mtime() { stat -c %Y "$1" 2>/dev/null || stat -f %m "$1"; }
age_hours() { echo $(( ( $(date +%s) - $(mtime "$1") ) / 3600 )); }
sha256() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | cut -d' ' -f1
    else
        shasum -a 256 "$1" | cut -d' ' -f1
    fi
}
# Never `docker exec -t`: a pseudo-terminal corrupts binary output.
live_psql() { docker exec "$PG_CONTAINER" psql -U postgres -v ON_ERROR_STOP=1 -Atq "$@"; }
scratch_psql() { docker exec "$SCRATCH" psql -U postgres -v ON_ERROR_STOP=1 -Atq "$@"; }

STARTED=0
cleanup() {
    # -v removes the anonymous volume a plain `rm -f` would leave behind.
    if [ "$STARTED" -eq 1 ]; then
        docker rm -fv "$SCRATCH" >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

mkdir -p "$BACKUP_ROOT"
chmod 700 "$BACKUP_ROOT"
: > "$ERR_FILE"
chmod 600 "$ERR_FILE"

# --- 1. Drift: the installed scripts must be this checkout's ---------------------
for f in backup.sh verify.sh; do
    want="$(sha256 "${REPO_OPS_DIR}/${f}")"
    if [ -f "${INSTALLED_DIR}/${f}" ]; then
        have="$(sha256 "${INSTALLED_DIR}/${f}")"
    else
        have="missing"
    fi
    if [ "$want" != "$have" ]; then
        fail "Installed ${f} differs from the repository (or is missing). Run the 'Install utils-pg backup' workflow."
    fi
done

# --- 2. The live server ----------------------------------------------------------
PRE="$(live_psql -c 'select 1' 2>>"$ERR_FILE")" || PRE=""
if [ "$PRE" != "1" ]; then
    fail "Cannot query ${PG_CONTAINER} (precondition 'select 1' failed)."
    exit 1
fi

LIST="$(live_psql -c "select datname from pg_database where datallowconn and not datistemplate and datname <> 'postgres' order by datname" 2>>"$ERR_FILE")" \
    || LIST=""
LIVE=()
while IFS= read -r line; do
    if [ -n "$line" ]; then
        LIVE+=("$line")
    fi
done <<< "$LIST"

if [ "${#LIVE[@]}" -eq 0 ]; then
    fail "The live database list is empty. That is never a pass."
    exit 1
fi

HAVE_REQUIRED=0
for db in "${LIVE[@]}"; do
    if ! [[ "$db" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]*$ ]]; then
        fail "A live database has a name this check will not handle safely."
        exit 1
    fi
    if [ "$db" = "$REQUIRED_DB" ]; then
        HAVE_REQUIRED=1
    fi
done
if [ "$HAVE_REQUIRED" -ne 1 ]; then
    fail "Required database '${REQUIRED_DB}' is not in the live list."
    exit 1
fi
note "Live databases: ${#LIVE[@]} (${LIVE[*]})"

# --- 3. Marker: fresh, and its database list equals the live list ----------------
MARK_DB=()
MARK_LIVE=()
if [ ! -f "$MARKER_FILE" ]; then
    fail "No marker file: the last backup did not complete."
else
    if [ "$(age_hours "$MARKER_FILE")" -gt "$MAX_AGE_HOURS" ]; then
        fail "The marker is more than ${MAX_AGE_HOURS}h old: the nightly backup has stopped."
    fi
    first=1
    while IFS=$'\t' read -r mdb msize mlive; do
        if [ "$first" -eq 1 ]; then
            first=0
            continue
        fi
        [ -n "$mdb" ] || continue
        if ! [[ "$msize" =~ ^[0-9]+$ && "$mlive" =~ ^[0-9]+$ ]]; then
            fail "Marker line for ${mdb} is malformed."
            continue
        fi
        MARK_DB+=("$mdb")
        MARK_LIVE+=("$mlive")
    done < "$MARKER_FILE"

    LIVE_SORTED="$(printf '%s\n' "${LIVE[@]}" | sort)"
    MARK_SORTED=""
    if [ "${#MARK_DB[@]}" -gt 0 ]; then
        MARK_SORTED="$(printf '%s\n' "${MARK_DB[@]}" | sort)"
    fi
    if [ "$LIVE_SORTED" != "$MARK_SORTED" ]; then
        for db in "${LIVE[@]}"; do
            if ! printf '%s\n' "$MARK_SORTED" | grep -qxF -- "$db"; then
                fail "Database ${db} is live but missing from the marker: it is not being backed up."
            fi
        done
        for db in ${MARK_DB[@]+"${MARK_DB[@]}"}; do
            if ! printf '%s\n' "$LIVE_SORTED" | grep -qxF -- "$db"; then
                fail "Database ${db} is in the marker but is no longer live."
            fi
        done
    fi
fi

marker_live() {   # marker_live <db>: prints the live total, or nothing if absent
    local i
    for ((i = 0; i < ${#MARK_DB[@]}; i++)); do
        if [ "${MARK_DB[$i]}" = "$1" ]; then
            echo "${MARK_LIVE[$i]}"
            return 0
        fi
    done
    return 1
}

# --- 4. Freshness: each database's newest COMPLETE dump --------------------------
DUMP_FOR=()   # parallel to LIVE; empty string = none usable
for db in "${LIVE[@]}"; do
    newest=""
    # .partial-* temp names never match this pattern, so a half-written file is never "newest".
    for f in "${BACKUP_ROOT}/daily/${db}/${db}_"????-??-??.dump; do
        if [ -f "$f" ]; then
            newest="$f"
        fi
    done
    if [ -z "$newest" ]; then
        fail "No dump for ${db}."
        DUMP_FOR+=("")
    elif [ ! -s "$newest" ]; then
        fail "The newest dump for ${db} is empty."
        DUMP_FOR+=("")
    elif [ "$(age_hours "$newest")" -gt "$MAX_AGE_HOURS" ]; then
        fail "The newest dump for ${db} is more than ${MAX_AGE_HOURS}h old."
        DUMP_FOR+=("")
    else
        note "Dump for ${db}: $(age_hours "$newest")h old."
        DUMP_FOR+=("$newest")
    fi
done

# --- 5. Restore each dump into a scratch server ---------------------------------
# Same image as the live server (by ID, not a floating tag), no network, data on tmpfs.
IMAGE="$(docker inspect -f '{{.Image}}' "$PG_CONTAINER" 2>>"$ERR_FILE")" || IMAGE=""
if [ -z "$IMAGE" ]; then
    fail "Could not read the image of ${PG_CONTAINER}."
    exit 1
fi

docker rm -fv "$SCRATCH" >/dev/null 2>&1 || true
STARTED=1
if ! docker run -d --rm --network none --tmpfs /var/lib/postgresql/data \
        -e POSTGRES_HOST_AUTH_METHOD=trust --name "$SCRATCH" "$IMAGE" >/dev/null 2>>"$ERR_FILE"; then
    fail "Could not start the scratch server."
    exit 1
fi

# Wait for the FINAL server: during initdb the entrypoint runs a temporary one that listens
# on the unix socket only, so a TCP check on loopback cannot be answered by it.
ready=0
for ((i = 0; i < POLL_TRIES; i++)); do
    if docker exec "$SCRATCH" pg_isready -h 127.0.0.1 -U postgres >/dev/null 2>&1 \
        && [ "$(scratch_psql -c 'select 1' 2>/dev/null || true)" = "1" ]; then
        ready=1
        break
    fi
    sleep "$POLL_SLEEP"
done
if [ "$ready" -ne 1 ]; then
    fail "The scratch server never became ready."
    exit 1
fi

for ((i = 0; i < ${#LIVE[@]}; i++)); do
    db="${LIVE[$i]}"
    dump="${DUMP_FOR[$i]}"
    [ -n "$dump" ] || continue

    before="$(wc -l < "$ERR_FILE" | tr -d ' ')"
    rc=0
    if scratch_psql -c "create database \"${db}\"" >/dev/null 2>>"$ERR_FILE"; then
        docker exec -i "$SCRATCH" pg_restore -U postgres -d "$db" --no-owner --no-acl --exit-on-error \
            < "$dump" >/dev/null 2>>"$ERR_FILE" || rc=$?
    else
        rc=1
    fi
    errors="$(tail -n +$((before + 1)) "$ERR_FILE" | grep -c -i 'error' || true)"
    if [ "$rc" -ne 0 ] && [ "$errors" -eq 0 ]; then
        errors=1
    fi

    # tables|rows, counted exactly on the restored copy. Only the two numbers leave the query.
    counts="$(scratch_psql -d "$db" -c "select count(*), coalesce(sum((xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I.%I', n.nspname, c.relname), false, true, '')))[1]::text::bigint), 0) from pg_class c join pg_namespace n on n.oid = c.relnamespace where c.relkind in ('r', 'm') and n.nspname not in ('pg_catalog', 'information_schema') and n.nspname not like 'pg_toast%'" 2>>"$ERR_FILE")" \
        || counts=""
    tables=""
    rows=""
    IFS='|' read -r tables rows <<< "$counts"

    if [ "$rc" -ne 0 ] || [ "$errors" -gt 0 ]; then
        fail "Restore of ${db}: ${errors} error(s). Details are in the host-only error file."
        continue
    fi
    if ! [[ "$tables" =~ ^[0-9]+$ && "$rows" =~ ^[0-9]+$ ]]; then
        fail "Restore of ${db}: could not count the restored data."
        continue
    fi
    if ! live_total="$(marker_live "$db")"; then
        fail "Restore of ${db}: no live total in the marker to compare against."
        continue
    fi
    note "Restored ${db}: errors=${errors} tables=${tables} rows=${rows} (live at dump time: ${live_total})"

    # A restore with fewer rows than the live database had at dump time lost data. A restore
    # with zero rows is only fine for a database that really was empty.
    if [ "$rows" -lt "$live_total" ]; then
        fail "Restore of ${db} has fewer rows (${rows}) than the live total at dump time (${live_total})."
    elif [ "$rows" -eq 0 ] && [ "$live_total" -ne 0 ]; then
        fail "Restore of ${db} has zero rows but the live total was ${live_total}."
    fi
done

# --- 6. Globals ------------------------------------------------------------------
gnewest=""
for f in "${BACKUP_ROOT}/globals/globals_"????-??-??.sql; do
    if [ -f "$f" ]; then
        gnewest="$f"
    fi
done
if [ -z "$gnewest" ]; then
    fail "No globals file."
elif [ ! -s "$gnewest" ] || ! grep -q 'CREATE ROLE' "$gnewest"; then
    fail "The newest globals file is empty or has no CREATE ROLE."
elif [ "$(age_hours "$gnewest")" -gt "$MAX_AGE_HOURS" ]; then
    fail "The newest globals file is more than ${MAX_AGE_HOURS}h old."
else
    note "Globals: present with role definitions."
fi

# --- 7. Off-box copy (a sleeping machine on the other end is a warning) ----------
OFFBOX_STATUS_FILE="${BACKUP_ROOT}/offbox-status"
OFFBOX_LAST_OK_FILE="${BACKUP_ROOT}/offbox-last-ok"
if [ ! -f "$OFFBOX_STATUS_FILE" ]; then
    echo "::warning::No off-box copy is configured: backups exist only on this host, on the disk that holds the databases."
else
    read -r state _ < "$OFFBOX_STATUS_FILE" || state=""
    if [ "$(age_hours "$OFFBOX_STATUS_FILE")" -gt "$OFFBOX_MAX_AGE_HOURS" ]; then
        fail "The off-box status is more than ${OFFBOX_MAX_AGE_HOURS}h old: the backup job has stopped."
    elif [ "$state" = "ok" ]; then
        note "Off-box copy: current."
    elif [ -f "$OFFBOX_LAST_OK_FILE" ] && [ "$(age_hours "$OFFBOX_LAST_OK_FILE")" -gt "$OFFBOX_MAX_AGE_HOURS" ]; then
        fail "No successful off-box copy for more than ${OFFBOX_MAX_AGE_HOURS}h."
    else
        echo "::warning::The latest off-box copy failed (the other machine may be asleep). Local backups are intact."
    fi
fi

# --- Result ----------------------------------------------------------------------
if [ "$FAILURES" -gt 0 ]; then
    echo "utils-pg backup verification FAILED: ${FAILURES} problem(s)."
    exit 1
fi
echo "utils-pg backup verification passed: every database restored cleanly."
