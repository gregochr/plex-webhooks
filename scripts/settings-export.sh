#!/bin/bash
# ============================================================================
# Read CineLight's settings copy back out of the `cinelight` database in the shared `utils-pg` Postgres
# (deploy-plan.md, D3). The owner runs this on the host. NO workflow may ever run it: this repo's Actions logs are
# public, and the output is the owner's settings.
#
#   scripts/settings-export.sh                 print the settings as key=value lines, ordered by key
#   scripts/settings-export.sh --changes [N]   print the last N changes (default 20), newest first, tab-separated:
#                                              changed_at, key, old_value, new_value, source
#                                              (a missing value prints as (none); source is initial|dashboard|sync)
#
# WHAT IT IS, AND IS NOT
#
#   The database holds a COPY of the settings file, kept up to date by the app, plus a log of what changed. The file
#   is still what the app starts from. Credentials (SensitiveKeys) and spring.datasource.* are never copied, so an
#   export cannot rebuild a working file by itself: the output ends with a comment naming what must be added back.
#   The file's sections and comments are not kept either.
#
# HOW IT TALKS TO POSTGRES
#
#   `docker exec -i utils-pg psql -U postgres -d cinelight -X -q -v ON_ERROR_STOP=1 -At`, with the SQL on stdin: -i,
#   never -t (a TTY would corrupt the output). N is checked against ^[0-9]+$ and passed with `-v n=`, so nothing the
#   caller types is ever spliced into SQL text.
#
#   DB_CONTAINER   The Postgres container (default: utils-pg)
#
# Exit codes: 0 ok; 1 utils-pg, the cinelight database or the setting table is missing (or psql failed); 2 bad usage.
# ============================================================================

set -euo pipefail
# [0-9] must mean the ASCII digits only, whatever the caller's locale.
export LC_ALL=C

DB_CONTAINER="${DB_CONTAINER:-utils-pg}"

usage() {
    echo "Usage: $0 [--changes [N]]" >&2
    exit 2
}

mode="settings"
limit=20
if [ $# -gt 0 ]; then
    case "$1" in
        --changes)
            mode="changes"
            if [ $# -eq 2 ]; then
                limit="$2"
                if ! [[ "$limit" =~ ^[0-9]+$ ]]; then
                    echo "ERROR: --changes takes a whole number, not '${limit}'." >&2
                    usage
                fi
            elif [ $# -gt 2 ]; then
                usage
            fi
            ;;
        *) usage ;;
    esac
fi

# --- Preconditions ----------------------------------------------------------------------------
command -v docker >/dev/null 2>&1 \
    || { echo "ERROR: docker not found on PATH. This must run on the Docker host." >&2; exit 1; }
[ -n "$(docker ps --filter "name=^/${DB_CONTAINER}\$" --format '{{.Names}}' 2>/dev/null)" ] \
    || { echo "ERROR: the ${DB_CONTAINER} container is not running." >&2; exit 1; }

# One psql session: psql_in <database> [psql options...], with the SQL on stdin.
psql_in() {
    local database="$1"
    shift
    docker exec -i "$DB_CONTAINER" psql -U postgres -d "$database" -X -q -v ON_ERROR_STOP=1 -At "$@"
}

found="$(psql_in postgres <<'SQL' 2>/dev/null || true
SELECT 1 FROM pg_database WHERE datname = 'cinelight';
SQL
)"
[ "$found" = "1" ] \
    || { echo "ERROR: the cinelight database does not exist in ${DB_CONTAINER} (run scripts/utils-pg-bootstrap.sh)." >&2; exit 1; }

has_table="$(psql_in cinelight <<'SQL' 2>/dev/null || true
SELECT to_regclass('public.setting') IS NOT NULL;
SQL
)"
[ "$has_table" = "t" ] \
    || { echo "ERROR: the setting table does not exist in the cinelight database: CineLight has not run a release with the settings copy yet." >&2; exit 1; }

# --- The query ------------------------------------------------------------------------------------
if [ "$mode" = "changes" ]; then
    # Captured first, so a failure part-way prints an error and not half a table.
    if ! rows="$(psql_in cinelight -v "n=${limit}" <<'SQL'
\pset null '(none)'
\pset fieldsep '\t'
SELECT to_char(changed_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'), key, old_value, new_value, source
FROM setting_change ORDER BY changed_at DESC, id DESC LIMIT :n;
SQL
    )"; then
        echo "ERROR: reading the setting_change table failed." >&2
        exit 1
    fi
    printf '# changed_at\tkey\told_value\tnew_value\tsource\n'
    if [ -n "$rows" ]; then
        printf '%s\n' "$rows"
    fi
    exit 0
fi

if ! rows="$(psql_in cinelight <<'SQL'
SELECT key || '=' || value FROM setting ORDER BY key COLLATE "C";
SQL
)"; then
    echo "ERROR: reading the setting table failed." >&2
    exit 1
fi

echo "# CineLight settings, read from the cinelight database in ${DB_CONTAINER}."
echo "# The settings file's sections and comments are not kept. Credentials and spring.datasource.* are never stored."
if [ -n "$rows" ]; then
    printf '%s\n' "$rows"
fi
echo "# Before a rebuilt file will start the app, add these back by hand (they are not in the database):"
echo "#   hue.application-key=<your Hue application key>"
echo "#   app.admin.password=\${CINELIGHT_ADMIN_PASSWORD}"
