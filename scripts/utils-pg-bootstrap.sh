#!/bin/bash
# ============================================================================
# Create CineLight's role and database in the shared `utils-pg` Postgres. The owner runs this ONCE on the host,
# before the first release that has a database (deploy-plan.md, "Owner actions for D2"). It is idempotent:
# running it again repairs a missing role or database and re-sets the password, and changes nothing else.
#
# WHAT IT DOES
#
#   - creates the role `cinelight` (LOGIN; no superuser, no CREATEDB, no CREATEROLE) if it does not exist
#   - creates the database `cinelight`, owned by that role, if it does not exist
#   - revokes CONNECT on it from PUBLIC (the other apps' roles cannot open it)
#   - sets the role's password from CINELIGHT_DB_PASSWORD
#
# HOW THE PASSWORD TRAVELS
#
#   The password is read from CINELIGHT_DB_PASSWORD in the environment, or else from ~/cinelight/.env (read as
#   text, never sourced). It reaches psql through `docker exec -e CINELIGHT_DB_PASSWORD`, which takes the value from
#   this script's environment, so it is never on a command line (no `ps` exposure), never echoed, and never
#   spliced into SQL text (a quote in it cannot break the statement). psql reads it with `\getenv` and
#   interpolates it with :'pw', which quotes it. The session first sets log_statement = 'none' so the statement is
#   not written to Postgres's log. The SQL goes in on stdin: `docker exec -i`, never `-t`.
#
# REBUILDING utils-pg FROM SCRATCH
#
#   Postgres runs its init directory only when the data directory is first created, so this script runs
#   the SQL by hand instead. Afterwards it prints that same SQL (without the password) so the owner can drop it
#   into utils-pg/init/ for a future rebuild; run this script again then to set the password.
#   `rollback.sh --restore-db` reuses the same SQL (via --print-init-sql) to recreate the database.
#
# Usage:
#   scripts/utils-pg-bootstrap.sh                  create/repair the role and database, then print the init SQL
#   scripts/utils-pg-bootstrap.sh --print-init-sql only print the SQL (no password, no docker)
#   DB_CONTAINER      The Postgres container (default: utils-pg)
#   CINELIGHT_ENV_FILE  Where to read the password from (default: $HOME/cinelight/.env)
# ============================================================================

set -euo pipefail

DB_CONTAINER="${DB_CONTAINER:-utils-pg}"
ENV_FILE="${CINELIGHT_ENV_FILE:-${HOME}/cinelight/.env}"

# The part that has no secret in it: role, database, privileges. Also what rollback.sh runs to recreate the
# database after DROP DATABASE.
print_init_sql() {
    cat <<'INIT_SQL'
-- CineLight: role and database in utils-pg. Idempotent. Set the role's password separately
-- (scripts/utils-pg-bootstrap.sh); this file deliberately has no password in it.
SELECT 'CREATE ROLE cinelight LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE'
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'cinelight') \gexec

SELECT 'CREATE DATABASE cinelight OWNER cinelight'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cinelight') \gexec

REVOKE CONNECT ON DATABASE cinelight FROM PUBLIC;
INIT_SQL
}

if [ "${1:-}" = "--print-init-sql" ]; then
    print_init_sql
    exit 0
elif [ $# -gt 0 ]; then
    echo "Usage: $0 [--print-init-sql]" >&2
    exit 2
fi

# --- The password --------------------------------------------------------------------------
# From the environment if set; otherwise the last CINELIGHT_DB_PASSWORD= line of the .env file, with one pair of
# surrounding quotes removed. Never `source` it: that file is data, not code.
if [ -z "${CINELIGHT_DB_PASSWORD:-}" ] && [ -f "$ENV_FILE" ]; then
    line="$(grep '^CINELIGHT_DB_PASSWORD=' "$ENV_FILE" | tail -1 || true)"
    value="${line#CINELIGHT_DB_PASSWORD=}"
    case "$value" in
        \"*\") value="${value#\"}"; value="${value%\"}" ;;
        \'*\') value="${value#\'}"; value="${value%\'}" ;;
    esac
    CINELIGHT_DB_PASSWORD="$value"
fi
if [ -z "${CINELIGHT_DB_PASSWORD:-}" ]; then
    echo "ERROR: CINELIGHT_DB_PASSWORD is not set, and ${ENV_FILE} has no CINELIGHT_DB_PASSWORD= line." >&2
    echo "       Add it to ${ENV_FILE} first: the app and this script must use the same password." >&2
    exit 1
fi
export CINELIGHT_DB_PASSWORD

# --- Preconditions ----------------------------------------------------------------------------
command -v docker >/dev/null 2>&1 \
    || { echo "ERROR: docker not found on PATH. This must run on the Docker host." >&2; exit 1; }
[ -n "$(docker ps --filter "name=^/${DB_CONTAINER}\$" --format '{{.Names}}' 2>/dev/null)" ] \
    || { echo "ERROR: the ${DB_CONTAINER} container is not running." >&2; exit 1; }

# --- Run it ---------------------------------------------------------------------------------------
# One psql session on stdin. SET log_statement and log_min_error_statement come first (a failed ALTER ROLE would
# otherwise log its statement), then the secret-free SQL, then the password step.
echo "==> Creating the cinelight role and database in ${DB_CONTAINER}"
{
    cat <<'PRELUDE_SQL'
SET log_statement = 'none';
SET log_min_error_statement = 'panic';
PRELUDE_SQL
    print_init_sql
    cat <<'PASSWORD_SQL'
\getenv pw CINELIGHT_DB_PASSWORD
ALTER ROLE cinelight PASSWORD :'pw';
PASSWORD_SQL
} | docker exec -i -e CINELIGHT_DB_PASSWORD "$DB_CONTAINER" psql -U postgres -v ON_ERROR_STOP=1 >/dev/null

echo "==> Done. The cinelight role and database exist, and the role's password is set."
echo ""
echo "To have a rebuilt utils-pg create them too, save this as utils-pg/init/20-cinelight.sql"
echo "(it has no password: run this script again after a rebuild to set it):"
echo ""
print_init_sql
