#!/bin/bash
# ============================================================================
# Prove the Docker image does not contain src/main/resources/application.properties or .env.
#
# WHY THIS EXISTS
#
# application.properties holds the Hue application key and the admin password, and is git-ignored. The
# image must never carry it: the settings file is mounted into the container at run time. Two things
# keep it out, `.dockerignore` and the Dockerfile's selective COPY, and either can be broken by a
# harmless-looking edit.
#
# A clean checkout never HAS that file, so building the image from one and finding no secret proves
# nothing. This script therefore writes a dummy application.properties and a dummy .env, each carrying a
# unique marker, builds the image, and asserts the markers are nowhere inside it:
#
#   1. the build stage (`--target builder`): neither file exists in /app. This is the sharp check, because
#      the builder stage does `COPY src src`, so a missing .dockerignore entry would put the file there.
#   2. the runtime image: the jar has no BOOT-INF/classes/application.properties entry, and no marker
#      appears in its classes or anywhere in /app or /config.
#
# It refuses to run if a real application.properties or .env already exists, so it can never overwrite or
# delete someone's settings. In CI (a clean checkout) they never exist.
#
# Usage: scripts/docker-build-test.sh
# ============================================================================

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

PROPS="src/main/resources/application.properties"
ENVFILE=".env"
IMAGE="cinelight-docker-build-test:$$"
BUILDER_IMAGE="cinelight-docker-build-test-builder:$$"
MARKER="CINELIGHT-MARKER-$(date +%s)-$$"

for f in "$PROPS" "$ENVFILE"; do
    if [[ -e "$f" ]]; then
        echo "Refusing to run: $f already exists, and this test would overwrite it." >&2
        echo "Move it aside first. CI runs on a clean checkout, where it is absent." >&2
        exit 1
    fi
done

cleanup() {
    rm -f -- "$PROPS" "$ENVFILE"
    docker image rm -f "$IMAGE" "$BUILDER_IMAGE" >/dev/null 2>&1 || true
}
trap cleanup EXIT

printf 'hue.application-key=%s\n' "$MARKER" > "$PROPS"
printf 'CINELIGHT_ADMIN_PASSWORD=%s\n' "$MARKER" > "$ENVFILE"

FAILURES=0
fail() {
    FAILURES=$((FAILURES + 1))
    echo "FAIL: $*" >&2
}

echo "==> Building the build stage with the marker files present"
docker build --target builder -t "$BUILDER_IMAGE" .

if docker run --rm --entrypoint sh "$BUILDER_IMAGE" -c 'test -e /app/src/main/resources/application.properties'; then
    fail "src/main/resources/application.properties is inside the build stage: .dockerignore is not excluding it"
fi
if docker run --rm --entrypoint sh "$BUILDER_IMAGE" -c 'test -e /app/.env'; then
    fail ".env is inside the build stage: .dockerignore is not excluding it"
fi

echo "==> Building the runtime image"
docker build -t "$IMAGE" .

# busybox unzip and grep are in the JRE alpine image. The jar is compressed, so grepping its bytes would
# find nothing whether or not the marker was there: extract the classes first.
# shellcheck disable=SC2016  # single quotes on purpose: $1 must reach the container's sh unexpanded
INSPECT='
set -eu
mkdir /tmp/jar
unzip -q /app/app.jar "BOOT-INF/classes/*" -d /tmp/jar
if [ -e /tmp/jar/BOOT-INF/classes/application.properties ]; then
    echo "application.properties is packaged in the jar"
    exit 1
fi
if grep -rqF "$1" /tmp/jar /app /config 2>/dev/null; then
    echo "the marker is inside the image"
    exit 1
fi
'
if ! docker run --rm --entrypoint sh "$IMAGE" -c "$INSPECT" sh "$MARKER"; then
    fail "the runtime image carries application.properties or its marker"
fi

if [[ "$FAILURES" -gt 0 ]]; then
    echo "docker-build-test: $FAILURES check(s) failed" >&2
    exit 1
fi
echo "docker-build-test: OK, no secret file or marker in the build stage or the runtime image"
