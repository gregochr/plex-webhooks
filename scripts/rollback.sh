#!/bin/bash
# ============================================================================
# Roll production back to the image and settings captured before a given release.
#
# THIS IS PLAN B. Fixing forward is almost always better. Reach for this only when forward is not
# available quickly enough.
#
# Reads the manifest written by pre-release.sh (~/cinelight-data/releases/<version>/) and does three
# things that only work together:
#   1. saves the CURRENT settings file aside, so the rollback is itself reversible
#   2. restores the settings file captured before the release
#   3. starts the exact image DIGEST that was running before
#
# Step 3 uses a digest rather than :latest, because after a bad release :latest is the bad release.
#
# The settings are restored because a release can add keys to, or change the meaning of, the settings
# file, and the app rewrites that file on every dashboard save. NOTE that this DISCARDS any setting
# changed through the dashboard since the release started: the plan is shown, and the version must be
# typed to proceed.
#
# The first release has nothing to roll back to (its manifest says `image=none`) and is refused.
#
# Usage:
#   scripts/rollback.sh              # list what can be rolled back to
#   scripts/rollback.sh v1.2.0       # roll back the release v1.2.0 introduced
#   CINELIGHT_ROLLBACK_YES=1         # skip the typed confirmation (for tests only)
# ============================================================================

set -euo pipefail

DATA_DIR="${CINELIGHT_DATA_DIR:-${HOME}/cinelight-data}"
SETTINGS_FILE="${CINELIGHT_SETTINGS_FILE:-${DATA_DIR}/config/application.properties}"
RELEASES_DIR="${DATA_DIR}/releases"
COMPOSE_DIR="${COMPOSE_DIR:-${HOME}/cinelight}"
SERVICE="${SERVICE:-cinelight}"
CONTAINER="${CONTAINER:-cinelight}"
HEALTH_ATTEMPTS="${HEALTH_ATTEMPTS:-30}"
HEALTH_SLEEP="${HEALTH_SLEEP:-5}"
# Must be one of the names Compose auto-loads (docker-compose.override.yml / compose.override.yml).
# goldenhour first used docker-compose.rollback.yml, which Compose ignores unless passed with -f: the
# "pin" then existed only for the one command that named it, and the very next bare
# `docker compose up -d` quietly resolved :latest back to the bad release while the banner and the
# file on disk both still claimed production was pinned.
OVERRIDE_FILE="${COMPOSE_DIR}/docker-compose.override.yml"

# --- No argument: show what is available ---------------------------------------------
if [ $# -eq 0 ]; then
    echo "Releases with a rollback point:"
    echo ""
    found=false
    for m in "${RELEASES_DIR}"/*/manifest; do
        [ -f "$m" ] || continue
        found=true
        v="$(basename "$(dirname "$m")")"
        taken="$(grep '^taken_at=' "$m" | cut -d= -f2- || true)"
        image="$(grep '^image=' "$m" | cut -d= -f2- || true)"
        if [ "$image" = "none" ]; then
            echo "  ${v}  (captured ${taken}; the first release, nothing to roll back to)"
        else
            echo "  ${v}  (captured ${taken})"
        fi
    done
    if [ "$found" = false ]; then
        echo "  (none — manifests start from the next deploy)"
    fi
    echo ""
    echo "Usage: $0 <version>    — rolls back the state that <version> replaced"
    exit 0
fi

VERSION="$1"
case "$VERSION" in
    */* | *..* | .*) echo "ERROR: '${VERSION}' is not a usable version." >&2; exit 1 ;;
esac
RELEASE_DIR="${RELEASES_DIR}/${VERSION}"
MANIFEST="${RELEASE_DIR}/manifest"
[ -f "$MANIFEST" ] || { echo "ERROR: no manifest for ${VERSION} at ${MANIFEST}" >&2; echo "Run '$0' with no arguments to list what is available." >&2; exit 1; }

# The `|| true` is load-bearing, not defensive noise. Without it a MISSING key takes the whole script
# out silently: grep exits 1, pipefail promotes that to the pipeline's status, the bare command
# substitution makes it the assignment's status, and `set -e` kills the run before printing anything,
# so every explicit error below would be unreachable for the one input it was written for.
field() { grep "^$1=" "$MANIFEST" | head -1 | cut -d= -f2- || true; }
PREV_IMAGE="$(field image)"
TAKEN_AT="$(field taken_at)"
SETTINGS_COPY="${RELEASE_DIR}/$(field settings)"

if [ -z "$PREV_IMAGE" ]; then
    echo "ERROR: manifest has no image — cannot roll back safely." >&2
    exit 1
fi
if [ "$PREV_IMAGE" = "none" ]; then
    echo "ERROR: ${VERSION} was the first deploy: nothing was running before it, so there is no image to roll back to." >&2
    echo "       Fix forward, or stop the service with: cd ${COMPOSE_DIR} && docker compose down" >&2
    exit 1
fi
case "$PREV_IMAGE" in
    *@sha256:*) ;;
    *) echo "ERROR: the manifest image '${PREV_IMAGE}' is not a digest — refusing to pin a tag." >&2; exit 1 ;;
esac
[ -f "$SETTINGS_COPY" ] || { echo "ERROR: the settings copy referenced by the manifest is missing: ${SETTINGS_COPY}" >&2; exit 1; }
[ -f "$SETTINGS_FILE" ] || { echo "ERROR: the live settings file ${SETTINGS_FILE} is missing — nothing to save aside." >&2; exit 1; }

# --- Show the plan before doing any of it ----------------------------------------------
cat <<PLAN

────────────────────────────────────────────────────────────────────────
ROLLBACK PLAN — undoing release ${VERSION}
────────────────────────────────────────────────────────────────────────
  Captured:          ${TAKEN_AT}
  Image  ->          ${PREV_IMAGE}
  Settings ->        restored from ${SETTINGS_COPY}

  SETTINGS LOSS: any setting changed through the dashboard since
  ${TAKEN_AT} is discarded. The current file is saved aside first,
  so this is reversible.
────────────────────────────────────────────────────────────────────────

PLAN

if [ "${CINELIGHT_ROLLBACK_YES:-}" != "1" ]; then
    read -r -p "Type the version (${VERSION}) to proceed, anything else to abort: " CONFIRM
    [ "$CONFIRM" = "$VERSION" ] || { echo "Aborted. Nothing changed."; exit 0; }
fi

# --- 1. Stop the app, so nothing rewrites the settings while they are swapped ----------
echo "==> Stopping ${SERVICE}"
cd "$COMPOSE_DIR"
docker compose stop "$SERVICE"

# --- 2. Save the current settings aside, then restore --------------------------------------
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
ASIDE="${RELEASE_DIR}/application.properties.pre-rollback-${STAMP}"
echo "==> Saving the current settings: ${ASIDE}"
( umask 077 && cp "$SETTINGS_FILE" "$ASIDE" )
chmod 600 "$ASIDE"

# Restored the way the app writes it: a temp file in the SAME directory, the live file's permissions
# copied onto it, then a rename. The directory is bind-mounted into the container, so a rename onto
# the file is atomic and a crash leaves either the old file or the new, never half of one.
echo "==> Restoring ${SETTINGS_COPY}"
PERMS="$(stat -c '%a' "$SETTINGS_FILE" 2>/dev/null || stat -f '%Lp' "$SETTINGS_FILE")"
TMP_SETTINGS="$(mktemp "${SETTINGS_FILE}.XXXXXX")"
trap 'rm -f "$TMP_SETTINGS"' EXIT
cp "$SETTINGS_COPY" "$TMP_SETTINGS"
chmod "$PERMS" "$TMP_SETTINGS"
mv "$TMP_SETTINGS" "$SETTINGS_FILE"
trap - EXIT

# --- 3. Pin the old digest and start -----------------------------------------------------------
# Written as a compose override so the real docker-compose.yml is untouched. It stays on disk
# deliberately: while it exists, production is pinned to the rolled-back build and a stray
# `docker compose up` cannot silently restore the broken release (deploy.yml refuses to run while it
# exists). Delete it when you fix forward.
cat > "$OVERRIDE_FILE" <<OVERRIDE
# Written by scripts/rollback.sh — production is pinned to the build that
# preceded ${VERSION}. DELETE THIS FILE when rolling forward again.
services:
  ${SERVICE}:
    image: ${PREV_IMAGE}
OVERRIDE

echo "==> Starting the previous image"
docker compose -f docker-compose.yml -f "$OVERRIDE_FILE" up -d --no-deps "$SERVICE"

# --- 4. Confirm it came back ----------------------------------------------------------------------
echo "==> Waiting for health"
healthy=false
for _ in $(seq 1 "$HEALTH_ATTEMPTS"); do
    if docker exec "$CONTAINER" sh -c 'curl -sf http://localhost:8080/actuator/health/container' >/dev/null 2>&1; then
        healthy=true
        break
    fi
    sleep "$HEALTH_SLEEP"
done

if [ "$healthy" = true ]; then
    RESULT="ROLLBACK COMPLETE — healthy"
else
    RESULT="ROLLBACK APPLIED — NOT HEALTHY, check: docker compose logs ${SERVICE}"
fi

cat <<DONE

────────────────────────────────────────────────────────────────────────
${RESULT}
────────────────────────────────────────────────────────────────────────
  Running:     ${PREV_IMAGE}
  Undo the settings restore:   cp ${ASIDE} ${SETTINGS_FILE}   (then restart ${SERVICE})

  Production is PINNED by ${OVERRIDE_FILE}.
  When you fix forward, delete it or the pin will outlive the problem:
      rm ${OVERRIDE_FILE}
────────────────────────────────────────────────────────────────────────

DONE
[ "$healthy" = true ]
