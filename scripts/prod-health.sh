#!/bin/bash
# ============================================================================
# CineLight production health check: at the end of every deploy, and every morning.
#
# WHY THIS EXISTS
#
# "The container came up" and "CineLight answers, and the app inside is alive" are different claims,
# and only the second matters. A release that starts but serves nothing is a failed release, and
# nothing else would say so: Plex posts to it on the LAN and the first sign of a dead app would be a
# film starting with the lights still on. This runs at the end of deploy.yml (--deploy), where a
# failure fails the job, and daily on the self-hosted runner (--daily, prod-health.yml), where a
# failure opens a GitHub issue.
#
# WHAT IT CHECKS (each FAILS the run)
#
#   1. compose service  `cinelight` reports Health == healthy (polled: it runs seconds after
#                       `docker compose up`, and the container's healthcheck has a start period)
#   2. container health GET /actuator/health/container answers status UP. That group is `ping` only.
#   3. dashboard        GET / answers 200 (the public dashboard, which renders the whole config)
#
# WHAT ONLY WARNS
#
#   - GET /api/hue/status says the Hue bridge is unreachable. A bridge that is off, or has moved, is a
#     real problem for the lights but not a failed deploy: graceful degradation means CineLight carries
#     on without it, so it must not fail a release or open an issue every morning for the same thing.
#     (DIRIGERA is not checked at all, for the same reason.)
#   - GET /actuator/health/database is not UP: the playback-history database (utils-pg) is down, was never set up
#     (scripts/utils-pg-bootstrap.sh), or the `database` health group is missing. CineLight carries on with in-memory history
#     and keeps trying, so it must not fail a deploy or restart the container; but the owner should know that
#     history is not being saved. The `database` group exists only in docker-compose.yml, not in the `container` group
#     that Docker's HEALTHCHECK reads.
#
# WHAT IS DELIBERATELY NOT HERE
#
#   - No host checks (reboot-required, pending apt updates, Tailscale, cloudflared). This box also runs
#     goldenhour, whose prod-health.sh already checks the host; running them twice would open two
#     issues for one reboot.
#   - It says nothing about whether a release behaves correctly, only that it is up.
#   - `docker compose ps --format json` is read against Compose v2's field names (`Service`, `State`,
#     `Health`). A current Compose prints one JSON object per line; an older one prints one array. The
#     jq filter below handles both.
#
# Usage: scripts/prod-health.sh [--deploy|--daily]
#   Both modes run the same checks today; the flag is kept so a daily-only check can be added later
#   without changing the callers, and so the output names the mode.
#   COMPOSE_DIR     Directory holding docker-compose.yml (default: $HOME/cinelight)
#   HEALTH_TIMEOUT  Seconds to poll compose service health (default: 180)
#   APP_URL         Base URL of the app (default: http://localhost:8080)
# ============================================================================

set -euo pipefail

COMPOSE_DIR="${COMPOSE_DIR:-${HOME}/cinelight}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"
APP_URL="${APP_URL:-http://localhost:8080}"
SERVICE="${SERVICE:-cinelight}"
POLL_SECONDS="${POLL_SECONDS:-5}"

MODE="daily"
case "${1:-}" in
    --deploy) MODE="deploy" ;;
    --daily|"") MODE="daily" ;;
    *)
        echo "Usage: $0 [--deploy|--daily]" >&2
        exit 2
        ;;
esac

FAILURES=0
WARNINGS=0

report_ok() {
    printf 'ok  %s: %s\n' "$1" "$2"
}

report_fail() {
    FAILURES=$((FAILURES + 1))
    printf 'FAIL %s: %s\n' "$1" "$2"
    # Parsed as a workflow annotation when GITHUB_ACTIONS is set (it is, on a runner).
    if [ -n "${GITHUB_ACTIONS:-}" ]; then
        printf '::error::%s: %s\n' "$1" "$2"
    fi
}

report_warn() {
    WARNINGS=$((WARNINGS + 1))
    printf 'warn %s: %s\n' "$1" "$2"
    if [ -n "${GITHUB_ACTIONS:-}" ]; then
        printf '::warning::%s: %s\n' "$1" "$2"
    fi
}

# --- 1. Compose service ---------------------------------------------------------
check_compose_service() {
    local name="compose service"

    if ! command -v jq >/dev/null 2>&1; then
        report_fail "$name" "jq is not installed — cannot parse 'docker compose ps --format json'"
        return
    fi

    local deadline
    deadline=$(( $(date +%s) + HEALTH_TIMEOUT ))

    while true; do
        local raw
        if ! raw="$(cd "$COMPOSE_DIR" && docker compose ps --format json 2>&1)"; then
            if [ "$(date +%s)" -ge "$deadline" ]; then
                report_fail "$name" "'docker compose ps' failed in ${COMPOSE_DIR}: ${raw}"
                return
            fi
            sleep "$POLL_SECONDS"
            continue
        fi

        # Normalise both known shapes: NDJSON (one object per line, current Compose) and a single
        # top-level array (older Compose). A NDJSON line is already an object, so the "else ." branch
        # passes it through; an array value is unpacked by ".[]".
        local normalized health
        normalized="$(printf '%s' "$raw" | jq -c 'if type=="array" then .[] else . end' 2>/dev/null || true)"
        health="$(printf '%s\n' "$normalized" | jq -r --arg s "$SERVICE" 'select(.Service==$s) | .Health' 2>/dev/null | tail -1 || true)"

        if [ "$health" = "healthy" ]; then
            report_ok "$name" "${SERVICE}=healthy"
            return
        fi

        if [ "$(date +%s)" -ge "$deadline" ]; then
            report_fail "$name" "timed out after ${HEALTH_TIMEOUT}s (${SERVICE}=${health:-?})"
            echo "---- docker compose ps (${COMPOSE_DIR}) ----"
            (cd "$COMPOSE_DIR" && docker compose ps) || true
            echo "---------------------------------------------"
            return
        fi

        sleep "$POLL_SECONDS"
    done
}

# --- 2. The container health group -------------------------------------------------
# Compared as a whole value, not with an unanchored grep: a body that merely CONTAINS "UP" (say
# "status":"DOWN","note":"UP") must not pass.
check_container_health() {
    local name="container health"
    local url="${APP_URL}/actuator/health/container"
    local body status

    body="$(curl -sS --max-time 20 "$url" 2>&1 || true)"
    status="$(printf '%s' "$body" | jq -r '.status // empty' 2>/dev/null || true)"

    if [ "$status" = "UP" ]; then
        report_ok "$name" "${url} -> status UP"
    else
        report_fail "$name" "${url} did not answer status UP (got: ${body:-no response})"
    fi
}

# --- 3. The dashboard answers 200 --------------------------------------------------
# Retried: a request made seconds after a restart can hit a moment before the web server is listening.
check_dashboard() {
    local name="dashboard"
    local attempts=3
    local code=""
    local i

    for ((i = 1; i <= attempts; i++)); do
        code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 "${APP_URL}/" 2>&1 || true)"
        if [ "$code" = "200" ]; then
            report_ok "$name" "GET ${APP_URL}/ -> 200"
            return
        fi
        if [ "$i" -lt "$attempts" ]; then
            sleep "$POLL_SECONDS"
        fi
    done

    report_fail "$name" "GET ${APP_URL}/ -> '${code}', not 200, after ${attempts} attempts"
}

# --- warn: the Hue bridge ----------------------------------------------------------
# A public GET. `connected` is false when the bridge does not answer. Anything unreadable is a warning
# too, never a failure: this check must not be able to fail a deploy.
check_hue_bridge() {
    local name="hue bridge"
    local url="${APP_URL}/api/hue/status"
    local body connected

    body="$(curl -sS --max-time 20 "$url" 2>&1 || true)"
    connected="$(printf '%s' "$body" | jq -r '.connected' 2>/dev/null || true)"

    case "$connected" in
        true) report_ok "$name" "bridge reachable" ;;
        false) report_warn "$name" "${url} says the bridge is unreachable — lights will not change until it is back" ;;
        *) report_warn "$name" "${url} gave no readable status (got: ${body:-no response})" ;;
    esac
}

# --- warn: the playback database --------------------------------------------------------
check_database() {
    local name="database"
    local url="${APP_URL}/actuator/health/database"
    local body status

    body="$(curl -sS --max-time 20 "$url" 2>&1 || true)"
    status="$(printf '%s' "$body" | jq -r '.status // empty' 2>/dev/null || true)"

    if [ "$status" = "UP" ]; then
        report_ok "$name" "${url} -> status UP"
    else
        report_warn "$name" "${url} did not answer status UP (got: ${body:-no response}) — playback history is not being saved; run scripts/utils-pg-bootstrap.sh if the database was never set up"
    fi
}

check_compose_service
check_container_health
check_dashboard
check_hue_bridge
check_database

TOTAL_CHECKS=3
echo "prod-health: ${TOTAL_CHECKS} checks, ${FAILURES} failed, ${WARNINGS} warning(s) (${MODE})"

if [ "$FAILURES" -gt 0 ]; then
    exit 1
fi

exit 0
