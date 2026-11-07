#!/bin/bash
# ============================================================================
# Record the image digest of what is now running, immediately after a deploy.
#
# WHY THIS EXISTS
#
# pre-release.sh needs the RepoDigest of the outgoing image so a rollback can pin the exact build
# rather than :latest. It reads it off the running container's image, and that read can come back
# EMPTY: goldenhour (from which this is ported) had a deploy abort with an empty digest after a
# `docker compose pull` between releases moved :latest and the running container's image stopped
# reporting a RepoDigest.
#
# WHAT IS DELIBERATELY NOT CLAIMED HERE
#
# The exact condition under which Docker drops a RepoDigest is NOT asserted: it was observed once and
# could not be reproduced, and it depends on the image store (the containerd store makes the image ID
# and the repo digest the same value). This guards the SYMPTOM instead. Whatever the cause, an empty
# digest read would abort a release, and the digest is reliably knowable at exactly one moment: just
# after the deploy that started the container. Recording it then costs nothing.
#
# WHY THE LEDGER IS SAFE TO TRUST
#
# It records the image ID alongside the digest, and pre-release.sh uses the digest ONLY when the
# running container's image ID still equals the recorded one. That makes the fallback a checkable claim
# about the image actually running, not an assumption about the last one. On a mismatch the release
# aborts, because a manifest pinning the wrong build is worse than no manifest.
#
# Non-fatal throughout, deliberately: this runs AFTER the new image is live, the release has already
# succeeded, and failing here would report a healthy deploy as broken. A missing ledger only costs the
# NEXT release its fallback.
#
# Ledger format: one tab-separated line, `container  imageId  repoDigest  recordedAt`.
#
# Usage: scripts/record-deployed-images.sh
#   CONTAINER                Container to record (default: cinelight)
#   DEPLOYED_IMAGES_LEDGER   Ledger path (default: $HOME/cinelight-data/deployed-images.tsv)
# ============================================================================

set -euo pipefail

CONTAINER="${CONTAINER:-cinelight}"
LEDGER="${DEPLOYED_IMAGES_LEDGER:-${HOME}/cinelight-data/deployed-images.tsv}"

log() { echo "[record-deployed-images] $*"; }

image_id="$(docker inspect --format='{{.Image}}' "$CONTAINER" 2>/dev/null || true)"
if [ -z "$image_id" ]; then
    log "WARN: ${CONTAINER} is not running — no digest recorded"
    exit 0
fi

digest="$(docker inspect --format='{{if .RepoDigests}}{{index .RepoDigests 0}}{{end}}' \
    "$image_id" 2>/dev/null || true)"
if [ -z "$digest" ]; then
    log "WARN: ${CONTAINER} image ${image_id} carries no RepoDigest — nothing to record."
    log "      The next release falls back to reading the container directly."
    exit 0
fi

# A temp file in the ledger's own directory, then a rename: the ledger is never half-written.
mkdir -p "$(dirname "$LEDGER")"
tmp="$(mktemp "${LEDGER}.XXXXXX")"
if ! printf '%s\t%s\t%s\t%s\n' "$CONTAINER" "$image_id" "$digest" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$tmp" \
        || ! mv "$tmp" "$LEDGER"; then
    rm -f "$tmp"
    log "WARN: ledger write failed — the next release falls back to the direct read"
    exit 0
fi

log "ledger written: ${LEDGER} (${digest})"
