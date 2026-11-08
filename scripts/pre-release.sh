#!/bin/bash
# ============================================================================
# Pre-release manifest: what a rollback needs, captured before the new image is pulled.
#
# Runs on the production host from deploy.yml, immediately before `docker compose pull`. Writes
# ~/cinelight-data/releases/<version>/ containing
#
#   manifest                 key=value lines: releasing, taken_at, image, settings, db (and db_dump)
#   application.properties   a 0600 copy of the settings file as it is right now
#   cinelight.dump           a 0600 pg_dump -Fc of the cinelight database in utils-pg, when there is one
#
# `image` is the DIGEST of the image running now (never a tag: `docker compose pull` moves :latest, so
# after a bad release :latest IS the bad release, while a digest can never be re-pointed), or the word
# `none` on the very first deploy, when nothing is running yet and there is nothing to roll back to.
#
# The image and the settings only mean anything as a PAIR: a new release can change which keys the
# settings file holds, and the app rewrites that file on every dashboard save. Restoring one without
# the other gives a combination that was never run.
#
# `db` records what happened to the database, and is one of three states. Only the first can be restored by
# `rollback.sh --restore-db`; the other two WARN and never block the release (the app starts without the
# database, so a missing dump must not stop a deploy):
#
#   db=dump         the database was dumped and the dump passed `pg_restore -l`
#   db=none         utils-pg is up but has no `cinelight` database (scripts/utils-pg-bootstrap.sh has not been run)
#   db=unavailable  utils-pg is down or unreachable, or the dump failed or did not verify
#
# Failure to capture the image or the settings aborts the release. That is deliberate: this is the one moment
# the manifest is load-bearing, and deploying without one would leave nothing to roll back to.
#
# Re-running for a version that already has a manifest KEEPS the first one. A deploy that failed after
# `docker compose up` and is then retried would otherwise record the NEW, broken image as the outgoing
# one and overwrite the only correct manifest.
#
# This script is for the owner's host and is standalone on purpose: deploy.yml runs it from the
# checkout of the tag being released, so it can never be older than the release.
#
# Usage: scripts/pre-release.sh v1.0.0
#   CONTAINER                Container whose image is the outgoing release (default: cinelight)
#   CINELIGHT_DATA_DIR       Data directory (default: $HOME/cinelight-data)
#   CINELIGHT_SETTINGS_FILE  Settings file to copy (default: $CINELIGHT_DATA_DIR/config/application.properties)
#   DEPLOYED_IMAGES_LEDGER   Written by record-deployed-images.sh (default: $CINELIGHT_DATA_DIR/deployed-images.tsv)
#   DB_CONTAINER             The Postgres container (default: utils-pg)
#   DB_NAME                  The database to dump (default: cinelight)
# ============================================================================

set -euo pipefail

RELEASE_VERSION="${1:-}"
[ -n "$RELEASE_VERSION" ] || { echo "ERROR: release version required, e.g. $0 v1.0.0" >&2; exit 1; }
case "$RELEASE_VERSION" in
    */* | *..* | .*) echo "ERROR: '${RELEASE_VERSION}' is not a usable version (it becomes a directory name)." >&2; exit 1 ;;
esac

CONTAINER="${CONTAINER:-cinelight}"
DATA_DIR="${CINELIGHT_DATA_DIR:-${HOME}/cinelight-data}"
SETTINGS_FILE="${CINELIGHT_SETTINGS_FILE:-${DATA_DIR}/config/application.properties}"
LEDGER="${DEPLOYED_IMAGES_LEDGER:-${DATA_DIR}/deployed-images.tsv}"
DB_CONTAINER="${DB_CONTAINER:-utils-pg}"
DB_NAME="${DB_NAME:-cinelight}"
RELEASE_DIR="${DATA_DIR}/releases/${RELEASE_VERSION}"
MANIFEST="${RELEASE_DIR}/manifest"

log() { echo "[pre-release] $*"; }

# --- Preconditions ----------------------------------------------------------------
# Checked separately from the container probe below so a missing docker binary reports the host, not
# the container.
command -v docker >/dev/null 2>&1 \
    || { echo "ERROR: docker not found on PATH. This must run on the Docker host." >&2; exit 1; }

[ -f "$SETTINGS_FILE" ] \
    || { echo "ERROR: settings file ${SETTINGS_FILE} is missing — refusing to release without it." >&2; exit 1; }

if [ -f "$MANIFEST" ]; then
    log "a manifest for ${RELEASE_VERSION} already exists (taken $(grep '^taken_at=' "$MANIFEST" | cut -d= -f2-)) — keeping it."
    exit 0
fi

# --- The outgoing image ------------------------------------------------------------
# `docker inspect <container>` returns a container object, which has NO RepoDigests field: that lives
# on the image. So two hops: the container's .Image is the image ID, which does carry RepoDigests.
# Asking the container directly makes the Go template error, and with 2>/dev/null the error is
# invisible and the output empty.
digest_of() {
    local image_id digest
    image_id="$(docker inspect --format='{{.Image}}' "$CONTAINER" 2>/dev/null)" || return 0
    [ -n "$image_id" ] || return 0
    digest="$(docker inspect --format='{{if .RepoDigests}}{{index .RepoDigests 0}}{{end}}' \
        "$image_id" 2>/dev/null || true)"
    if [ -n "$digest" ]; then
        printf '%s' "$digest"
        return 0
    fi
    # Running, but no RepoDigest. The container is fine and only the registry reference is missing,
    # so fall back to the ledger written by the deploy that started it.
    ledger_digest "$image_id"
}

# The recorded RepoDigest, but ONLY when the ledger's image ID still matches the one actually running.
# An image ID is a content hash, so that match is what makes the recorded digest a checkable claim
# about THIS image. On any mismatch it returns nothing and the caller aborts.
ledger_digest() {
    local running_id="$1" line container recorded_id digest
    [ -f "$LEDGER" ] || return 0
    line="$(awk -F'\t' -v c="$CONTAINER" '$1 == c { l = $0 } END { print l }' "$LEDGER" 2>/dev/null || true)"
    [ -n "$line" ] || { log "ledger has no record for ${CONTAINER}" >&2; return 0; }
    IFS=$'\t' read -r container recorded_id digest _ <<< "$line"
    if [ "$recorded_id" != "$running_id" ]; then
        log "ledger for ${container} records a DIFFERENT image than the one running — ignoring it rather than pinning the wrong build." >&2
        return 0
    fi
    log "${container}: image carries no RepoDigest; recovered from the deploy ledger." >&2
    printf '%s' "$digest"
}

# Whether the container exists at all (running or stopped). `docker ps -a --filter name=` matches as a
# substring, so the anchors matter: `cinelight` must not match `cinelight-old`.
container_exists() {
    [ -n "$(docker ps -a --filter "name=^/${CONTAINER}\$" --format '{{.Names}}' 2>/dev/null)" ]
}

if container_exists; then
    PREV_IMAGE="$(digest_of)"
    if [ -z "$PREV_IMAGE" ]; then
        # A locally built image has no RepoDigest. Better to stop than to write a manifest that looks
        # complete and cannot actually be rolled back to.
        echo "ERROR: could not read the digest of the image running as ${CONTAINER}." >&2
        echo "       A manifest without a digest cannot be rolled back to, so the release is stopping here." >&2
        exit 1
    fi
else
    # First deploy: nothing is running yet. Recorded as `none` so rollback.sh refuses clearly rather
    # than pinning an empty image.
    PREV_IMAGE="none"
    log "no ${CONTAINER} container exists: this is the first deploy, so there is nothing to roll back to."
fi

log "outgoing image: ${PREV_IMAGE}"

# --- Write the manifest and the settings copy -----------------------------------------
# Built in a temp directory beside the final one and renamed into place, so a crash never leaves a
# manifest without its settings copy (rollback would then restore nothing, silently).
mkdir -p "${DATA_DIR}/releases"
STAGING="$(mktemp -d "${DATA_DIR}/releases/.staging.XXXXXX")"
trap 'rm -rf "$STAGING"' EXIT

# 0600: the settings file holds the Hue application key.
( umask 077 && cp "$SETTINGS_FILE" "${STAGING}/application.properties" )
chmod 600 "${STAGING}/application.properties"
cmp -s "$SETTINGS_FILE" "${STAGING}/application.properties" \
    || { echo "ERROR: the settings copy differs from the original — aborting the release." >&2; exit 1; }

# --- The database -----------------------------------------------------------------------
# Every docker call here is `docker exec -i`, never `-t`: a TTY rewrites the binary dump stream (it turns
# newlines into CRLF) and corrupts it. Output of the pg tools goes to /dev/null: this runs in a workflow whose
# logs are public, and an error from pg_dump or pg_restore can name tables.
pg() { docker exec -i "$DB_CONTAINER" "$@"; }

db_container_running() {
    [ -n "$(docker ps --filter "name=^/${DB_CONTAINER}\$" --format '{{.Names}}' 2>/dev/null)" ]
}

# Prints dump | none | unavailable. On `dump` the file is ${STAGING}/cinelight.dump; otherwise nothing is left.
dump_database() {
    local dump="${STAGING}/${DB_NAME}.dump" exists
    db_container_running || { echo unavailable; return 0; }
    exists="$(pg psql -U postgres -v ON_ERROR_STOP=1 -At \
        -c "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'" 2>/dev/null)" || { echo unavailable; return 0; }
    [ "$exists" = "1" ] || { echo none; return 0; }
    # 0600 before the first byte is written: the dump holds the whole viewing history.
    if ! ( umask 077 && pg pg_dump -U postgres -Fc -d "$DB_NAME" > "$dump" 2>/dev/null ); then
        rm -f "$dump"; echo unavailable; return 0
    fi
    # A dump that is empty, or that pg_restore cannot list, is not a backup.
    if [ ! -s "$dump" ] || ! pg pg_restore -l < "$dump" >/dev/null 2>&1; then
        rm -f "$dump"; echo unavailable; return 0
    fi
    chmod 600 "$dump"
    echo dump
}

DB_STATE="$(dump_database)"
case "$DB_STATE" in
    dump) log "database: ${DB_NAME} dumped and checked (db=dump)" ;;
    none) log "WARNING: ${DB_CONTAINER} has no ${DB_NAME} database (db=none): run scripts/utils-pg-bootstrap.sh. The release continues; history stays in memory until then, and \`rollback.sh --restore-db\` will not be available." >&2 ;;
    *) log "WARNING: could not dump ${DB_NAME} from ${DB_CONTAINER} (db=unavailable). The release continues, but \`rollback.sh --restore-db\` will not be available for it." >&2 ;;
esac

{
    echo "releasing=${RELEASE_VERSION}"
    echo "taken_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "image=${PREV_IMAGE}"
    echo "settings=application.properties"
    echo "db=${DB_STATE}"
    if [ "$DB_STATE" = "dump" ]; then echo "db_dump=${DB_NAME}.dump"; fi
} > "${STAGING}/manifest"

mv "$STAGING" "$RELEASE_DIR"
trap - EXIT
# mktemp -d creates 0700; the directory only holds a manifest and a 0600 file, so keep it private.

log "manifest written: ${MANIFEST}"
log "rollback with: scripts/rollback.sh ${RELEASE_VERSION}"
