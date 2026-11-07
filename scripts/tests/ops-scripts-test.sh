#!/bin/bash
# Fixture tests for the deploy-side scripts: pre-release.sh, record-deployed-images.sh, rollback.sh and
# prod-health.sh. `docker` and `curl` are replaced by fakes on PATH that read their answers from FAKE_*
# environment variables and log what they were asked to do, so the real scripts run unmodified against a
# throwaway home directory. Nothing here touches Docker, the network or the real ~/cinelight*.
#
# Run: scripts/tests/ops-scripts-test.sh   (exit 0 = all passed)

set -euo pipefail

SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT="$(mktemp -d)"
trap 'rm -rf "$ROOT"' EXIT

PASSED=0
FAILED=0
pass() { PASSED=$((PASSED + 1)); echo "ok   $1"; }
fail() { FAILED=$((FAILED + 1)); echo "FAIL $1"; shift; printf '       %s\n' "$@"; }

check() {   # check <name> <condition-exit-status> [detail...]
    local name="$1" rc="$2"
    shift 2
    if [[ "$rc" -eq 0 ]]; then pass "$name"; else fail "$name" "$@"; fi
}

# Prints 0 if the command succeeds, 1 if it fails (cond_not: the reverse), for use as check's status argument.
cond() { if "$@" >/dev/null 2>&1; then echo 0; else echo 1; fi; }
cond_not() { if "$@" >/dev/null 2>&1; then echo 1; else echo 0; fi; }

# --- fakes --------------------------------------------------------------------------------------------
BIN="$ROOT/bin"
mkdir "$BIN"
cat > "$BIN/docker" <<'FAKE'
#!/bin/bash
# Fake docker. Every call is appended to $FAKE_LOG.
echo "docker $*" >> "${FAKE_LOG:-/dev/null}"
case "$1" in
    ps)
        if [[ "${FAKE_CONTAINER_EXISTS:-0}" == "1" ]]; then echo "cinelight"; fi
        ;;
    inspect)
        if [[ "$*" == *"{{.Image}}"* ]]; then
            [[ -n "${FAKE_IMAGE_ID:-}" ]] || exit 1
            echo "$FAKE_IMAGE_ID"
        else
            echo "${FAKE_DIGEST:-}"
        fi
        ;;
    compose)
        if [[ "$2" == "ps" && "$*" == *"--format json"* ]]; then
            echo "${FAKE_PS_JSON:-}"
        fi
        ;;
    exec)
        exit "${FAKE_EXEC_RC:-0}"
        ;;
esac
exit 0
FAKE
cat > "$BIN/curl" <<'FAKE'
#!/bin/bash
# Fake curl: a call with -w asks for the status code; any other call asks for a body, chosen by URL.
args="$*"
if [[ "$args" == *"-w"* ]]; then
    echo "${FAKE_ROOT_CODE:-200}"
elif [[ "$args" == *"/actuator/health/container"* ]]; then
    echo "${FAKE_HEALTH_BODY:-}"
elif [[ "$args" == *"/api/hue/status"* ]]; then
    echo "${FAKE_HUE_BODY:-}"
fi
exit 0
FAKE
chmod +x "$BIN/docker" "$BIN/curl"
export PATH="$BIN:$PATH"

# A fresh fake host: a data dir with a settings file, a compose dir, and a call log.
new_host() {
    local h
    h="$(mktemp -d "$ROOT/host.XXXXXX")"
    mkdir -p "$h/data/config" "$h/compose"
    printf '# Hue\nhue.start-scene=live-scene\nplex.movie-play-enabled=true\n' > "$h/data/config/application.properties"
    chmod 640 "$h/data/config/application.properties"
    : > "$h/log"
    echo "$h"
}

# Runs a script against host $1; sets OUT and STATUS. Remaining args: the command line.
run() {
    local h="$1"
    shift
    set +e
    OUT="$(CINELIGHT_DATA_DIR="$h/data" COMPOSE_DIR="$h/compose" FAKE_LOG="$h/log" "$@" 2>&1)"
    STATUS=$?
    set -e
}

contains() { grep -qF -- "$2" <<< "$1"; }

# ============================ pre-release.sh ===========================================================
h="$(new_host)"
run "$h" "$SCRIPTS/pre-release.sh" v1.0.0
check "pre-release: first deploy (no container) exits 0" "$STATUS" "$OUT"
manifest="$h/data/releases/v1.0.0/manifest"
check "pre-release: first deploy records image=none" "$(grep -qx 'image=none' "$manifest"; echo $?)" "$(cat "$manifest" 2>/dev/null)"
check "pre-release: manifest names the release" "$(grep -qx 'releasing=v1.0.0' "$manifest"; echo $?)"
check "pre-release: settings copy has the live content" "$(cmp -s "$h/data/config/application.properties" "$h/data/releases/v1.0.0/application.properties"; echo $?)"
perms="$(stat -c '%a' "$h/data/releases/v1.0.0/application.properties" 2>/dev/null || stat -f '%Lp' "$h/data/releases/v1.0.0/application.properties")"
check "pre-release: settings copy is mode 0600" "$(cond test "$perms" = "600")" "mode was $perms"
leftovers="$(find "$h/data/releases" -name '.staging.*' | wc -l | tr -d ' ')"
check "pre-release: no staging directory left behind" "$(cond test "$leftovers" = "0")" "$leftovers left"

h="$(new_host)"
FAKE_CONTAINER_EXISTS=1 FAKE_IMAGE_ID=sha256:imgid FAKE_DIGEST=ghcr.io/gregochr/cinelight@sha256:aaa \
    run "$h" env FAKE_CONTAINER_EXISTS=1 FAKE_IMAGE_ID=sha256:imgid FAKE_DIGEST=ghcr.io/gregochr/cinelight@sha256:aaa \
    "$SCRIPTS/pre-release.sh" v1.1.0
check "pre-release: running container exits 0" "$STATUS" "$OUT"
check "pre-release: records the outgoing digest, not a tag" \
    "$(grep -qx 'image=ghcr.io/gregochr/cinelight@sha256:aaa' "$h/data/releases/v1.1.0/manifest"; echo $?)" "$(cat "$h/data/releases/v1.1.0/manifest")"
check "pre-release: container lookup is anchored (name=^/cinelight\$)" \
    "$(grep -qF 'name=^/cinelight$' "$h/log"; echo $?)" "$(cat "$h/log")"

run "$h" env FAKE_CONTAINER_EXISTS=1 FAKE_IMAGE_ID=sha256:imgid FAKE_DIGEST=ghcr.io/gregochr/cinelight@sha256:BBB \
    "$SCRIPTS/pre-release.sh" v1.1.0
check "pre-release: a re-run keeps the first manifest (exit 0)" "$STATUS" "$OUT"
check "pre-release: a re-run does not overwrite the outgoing digest" \
    "$(grep -qx 'image=ghcr.io/gregochr/cinelight@sha256:aaa' "$h/data/releases/v1.1.0/manifest"; echo $?)" "$(cat "$h/data/releases/v1.1.0/manifest")"
check "pre-release: a re-run says it kept the manifest" "$(contains "$OUT" "keeping it"; echo $?)" "$OUT"

h="$(new_host)"
rm "$h/data/config/application.properties"
run "$h" "$SCRIPTS/pre-release.sh" v1.0.0
check "pre-release: a missing settings file aborts" "$(cond test "$STATUS" -ne 0)" "$OUT"
check "pre-release: a missing settings file names the path" "$(contains "$OUT" "application.properties is missing"; echo $?)" "$OUT"
check "pre-release: nothing written when it aborts" "$(cond test ! -e "$h/data/releases/v1.0.0")"

h="$(new_host)"
run "$h" "$SCRIPTS/pre-release.sh" ../escape
check "pre-release: a version that is a path is refused" "$(cond test "$STATUS" -ne 0)" "$OUT"
run "$h" "$SCRIPTS/pre-release.sh"
check "pre-release: no version is refused" "$(cond test "$STATUS" -ne 0)" "$OUT"

# Running container, no RepoDigest: the ledger is used only if its image id matches.
h="$(new_host)"
printf 'cinelight\tsha256:imgid\tghcr.io/gregochr/cinelight@sha256:ledger\t2026-11-07T00:00:00Z\n' > "$h/data/deployed-images.tsv"
run "$h" env FAKE_CONTAINER_EXISTS=1 FAKE_IMAGE_ID=sha256:imgid FAKE_DIGEST= "$SCRIPTS/pre-release.sh" v1.2.0
check "pre-release: falls back to a ledger entry whose image id matches" "$STATUS" "$OUT"
check "pre-release: the ledger digest is recorded" \
    "$(grep -qx 'image=ghcr.io/gregochr/cinelight@sha256:ledger' "$h/data/releases/v1.2.0/manifest"; echo $?)" "$(cat "$h/data/releases/v1.2.0/manifest" 2>/dev/null)"

h="$(new_host)"
printf 'cinelight\tsha256:OTHER\tghcr.io/gregochr/cinelight@sha256:ledger\t2026-11-07T00:00:00Z\n' > "$h/data/deployed-images.tsv"
run "$h" env FAKE_CONTAINER_EXISTS=1 FAKE_IMAGE_ID=sha256:imgid FAKE_DIGEST= "$SCRIPTS/pre-release.sh" v1.2.0
check "pre-release: a ledger entry for a different image aborts the release" "$(cond test "$STATUS" -ne 0)" "$OUT"
check "pre-release: ...and says why" "$(contains "$OUT" "DIFFERENT image"; echo $?)" "$OUT"
check "pre-release: ...and writes no manifest" "$(cond test ! -e "$h/data/releases/v1.2.0")"

h="$(new_host)"
run "$h" env FAKE_CONTAINER_EXISTS=1 FAKE_IMAGE_ID=sha256:imgid FAKE_DIGEST= "$SCRIPTS/pre-release.sh" v1.2.0
check "pre-release: a running image with no digest and no ledger aborts" "$(cond test "$STATUS" -ne 0)" "$OUT"

# ============================ record-deployed-images.sh ===============================================
h="$(new_host)"
run "$h" env FAKE_IMAGE_ID=sha256:imgid FAKE_DIGEST=ghcr.io/gregochr/cinelight@sha256:ccc \
    DEPLOYED_IMAGES_LEDGER="$h/data/deployed-images.tsv" "$SCRIPTS/record-deployed-images.sh"
check "record: exits 0" "$STATUS" "$OUT"
line="$(cut -f1-3 "$h/data/deployed-images.tsv" 2>/dev/null || true)"
check "record: writes container, image id and digest" \
    "$(cond test "$line" = "$(printf 'cinelight\tsha256:imgid\tghcr.io/gregochr/cinelight@sha256:ccc')")" "$line"
leftovers="$(find "$h/data" -name 'deployed-images.tsv.*' | wc -l | tr -d ' ')"
check "record: no temp file left behind" "$(cond test "$leftovers" = "0")"

h="$(new_host)"
run "$h" env FAKE_IMAGE_ID= DEPLOYED_IMAGES_LEDGER="$h/data/deployed-images.tsv" "$SCRIPTS/record-deployed-images.sh"
check "record: a missing container is a warning, exit 0" "$STATUS" "$OUT"
check "record: ...and writes no ledger" "$(cond test ! -e "$h/data/deployed-images.tsv")"

h="$(new_host)"
run "$h" env FAKE_IMAGE_ID=sha256:imgid FAKE_DIGEST= DEPLOYED_IMAGES_LEDGER="$h/data/deployed-images.tsv" \
    "$SCRIPTS/record-deployed-images.sh"
check "record: an image with no digest is a warning, exit 0" "$STATUS" "$OUT"
check "record: ...and writes no ledger" "$(cond test ! -e "$h/data/deployed-images.tsv")"

# ============================ rollback.sh ===============================================================
# A host that has gone through a release: a manifest for v1.1.0 and a changed live settings file.
rollback_host() {
    local image="$1" h
    h="$(new_host)"
    mkdir -p "$h/data/releases/v1.1.0"
    printf 'releasing=v1.1.0\ntaken_at=2026-11-07T10:00:00Z\nimage=%s\nsettings=application.properties\n' "$image" > "$h/data/releases/v1.1.0/manifest"
    printf '# Hue\nhue.start-scene=before-release\nplex.movie-play-enabled=true\n' > "$h/data/releases/v1.1.0/application.properties"
    chmod 600 "$h/data/releases/v1.1.0/application.properties"
    printf '# Hue\nhue.start-scene=CHANGED-AFTER\nplex.movie-play-enabled=false\n' > "$h/data/config/application.properties"
    chmod 640 "$h/data/config/application.properties"
    echo "$h"
}

h="$(rollback_host ghcr.io/gregochr/cinelight@sha256:old)"
run "$h" "$SCRIPTS/rollback.sh"
check "rollback: no argument lists the releases" "$(contains "$OUT" "v1.1.0  (captured 2026-11-07T10:00:00Z)"; echo $?)" "$OUT"

h="$(new_host)"
run "$h" "$SCRIPTS/rollback.sh"
check "rollback: no argument with no manifests says so" "$(contains "$OUT" "(none"; echo $?)" "$OUT"

h="$(rollback_host none)"
run "$h" env CINELIGHT_ROLLBACK_YES=1 "$SCRIPTS/rollback.sh" v1.1.0
check "rollback: the first release (image=none) is refused" "$(cond test "$STATUS" -ne 0)" "$OUT"
check "rollback: ...with a clear reason" "$(contains "$OUT" "first deploy"; echo $?)" "$OUT"
check "rollback: ...and nothing was stopped" "$(! grep -q 'compose stop' "$h/log"; echo $?)" "$(cat "$h/log")"

h="$(rollback_host ghcr.io/gregochr/cinelight:latest)"
run "$h" env CINELIGHT_ROLLBACK_YES=1 "$SCRIPTS/rollback.sh" v1.1.0
check "rollback: a tag (not a digest) is refused" "$(cond test "$STATUS" -ne 0)" "$OUT"

h="$(rollback_host ghcr.io/gregochr/cinelight@sha256:old)"
run "$h" env CINELIGHT_ROLLBACK_YES=1 "$SCRIPTS/rollback.sh" v9.9.9
check "rollback: an unknown version is refused" "$(cond test "$STATUS" -ne 0)" "$OUT"

h="$(rollback_host ghcr.io/gregochr/cinelight@sha256:old)"
rm "$h/data/releases/v1.1.0/application.properties"
run "$h" env CINELIGHT_ROLLBACK_YES=1 "$SCRIPTS/rollback.sh" v1.1.0
check "rollback: a missing settings copy is refused" "$(cond test "$STATUS" -ne 0)" "$OUT"
check "rollback: ...before anything is stopped" "$(! grep -q 'compose stop' "$h/log"; echo $?)" "$(cat "$h/log")"

h="$(rollback_host ghcr.io/gregochr/cinelight@sha256:old)"
run "$h" env CINELIGHT_ROLLBACK_YES=1 HEALTH_ATTEMPTS=1 HEALTH_SLEEP=0 "$SCRIPTS/rollback.sh" v1.1.0
check "rollback: full rollback exits 0 when healthy" "$STATUS" "$OUT"
live="$h/data/config/application.properties"
check "rollback: restores the pre-release settings" "$(grep -qx 'hue.start-scene=before-release' "$live"; echo $?)" "$(cat "$live")"
aside="$(find "$h/data/releases/v1.1.0" -name 'application.properties.pre-rollback-*' | head -1)"
check "rollback: the settings it replaced are saved aside" "$(grep -qx 'hue.start-scene=CHANGED-AFTER' "$aside" 2>/dev/null; echo $?)" "aside=$aside"
perms="$(stat -c '%a' "$live" 2>/dev/null || stat -f '%Lp' "$live")"
check "rollback: the live file keeps its permissions (0640)" "$(cond test "$perms" = "640")" "mode was $perms"
aperms="$(stat -c '%a' "$aside" 2>/dev/null || stat -f '%Lp' "$aside")"
check "rollback: the saved-aside copy is 0600" "$(cond test "$aperms" = "600")" "mode was $aperms"
left="$(find "$h/data/config" -type f | wc -l | tr -d ' ')"
check "rollback: no temp file left in the config directory" "$(cond test "$left" = "1")" "$left files"
override="$h/compose/docker-compose.override.yml"
check "rollback: pins the old digest in docker-compose.override.yml" "$(grep -qx '    image: ghcr.io/gregochr/cinelight@sha256:old' "$override"; echo $?)" "$(cat "$override" 2>/dev/null)"
stop_line="$(grep -n 'compose stop cinelight' "$h/log" | head -1 | cut -d: -f1)"
up_line="$(grep -n 'compose -f docker-compose.yml -f' "$h/log" | head -1 | cut -d: -f1)"
check "rollback: stops the service before it starts the pinned one" "$(cond test -n "$stop_line" -a -n "$up_line" -a "$stop_line" -lt "$up_line")" "$(cat "$h/log")"
check "rollback: starts only the cinelight service, without dependencies" "$(grep -q 'up -d --no-deps cinelight' "$h/log"; echo $?)" "$(cat "$h/log")"

h="$(rollback_host ghcr.io/gregochr/cinelight@sha256:old)"
run "$h" env CINELIGHT_ROLLBACK_YES=1 HEALTH_ATTEMPTS=1 HEALTH_SLEEP=0 FAKE_EXEC_RC=1 "$SCRIPTS/rollback.sh" v1.1.0
check "rollback: exits non-zero when the old image does not come up healthy" "$(cond test "$STATUS" -ne 0)" "$OUT"
check "rollback: ...and says so" "$(contains "$OUT" "NOT HEALTHY"; echo $?)" "$OUT"

# The typed confirmation: anything but the version aborts and changes nothing.
h="$(rollback_host ghcr.io/gregochr/cinelight@sha256:old)"
set +e
OUT="$(echo "wrong" | CINELIGHT_DATA_DIR="$h/data" COMPOSE_DIR="$h/compose" FAKE_LOG="$h/log" "$SCRIPTS/rollback.sh" v1.1.0 2>&1)"
STATUS=$?
set -e
check "rollback: a wrong confirmation aborts with exit 0" "$STATUS" "$OUT"
check "rollback: ...changes nothing" "$(grep -qx 'hue.start-scene=CHANGED-AFTER' "$h/data/config/application.properties"; echo $?)"
check "rollback: ...and stops nothing" "$(! grep -q 'compose stop' "$h/log"; echo $?)"

# ============================ prod-health.sh ============================================================
HEALTHY_PS='{"Service":"cinelight","State":"running","Health":"healthy"}'
UNHEALTHY_PS='{"Service":"cinelight","State":"running","Health":"unhealthy"}'
phealth() {   # phealth <extra env...> -- ; runs with fast timeouts
    run "$h" env HEALTH_TIMEOUT=0 POLL_SECONDS=0 "$@" "$SCRIPTS/prod-health.sh" --deploy
}

h="$(new_host)"
phealth FAKE_PS_JSON="$HEALTHY_PS" FAKE_HEALTH_BODY='{"status":"UP"}' FAKE_ROOT_CODE=200 FAKE_HUE_BODY='{"connected":true}'
check "prod-health: all good exits 0" "$STATUS" "$OUT"
check "prod-health: summary line" "$(contains "$OUT" "prod-health: 3 checks, 0 failed, 0 warning(s) (deploy)"; echo $?)" "$OUT"
check "prod-health: reports an ok line for compose, container health, dashboard and the bridge" \
    "$(cond test "$(grep -c '^ok  ' <<< "$OUT")" -eq 4)" "$OUT"

phealth FAKE_PS_JSON="$UNHEALTHY_PS" FAKE_HEALTH_BODY='{"status":"UP"}' FAKE_ROOT_CODE=200 FAKE_HUE_BODY='{"connected":true}'
check "prod-health: an unhealthy compose service fails" "$(cond test "$STATUS" -eq 1)" "$OUT"
check "prod-health: ...naming the service state" "$(contains "$OUT" "cinelight=unhealthy"; echo $?)" "$OUT"

phealth FAKE_PS_JSON='[{"Service":"cinelight","Health":"healthy"}]' FAKE_HEALTH_BODY='{"status":"UP"}' FAKE_ROOT_CODE=200 FAKE_HUE_BODY='{"connected":true}'
check "prod-health: reads the older array shape of compose ps" "$STATUS" "$OUT"

phealth FAKE_PS_JSON="$HEALTHY_PS" FAKE_HEALTH_BODY='{"status":"DOWN"}' FAKE_ROOT_CODE=200 FAKE_HUE_BODY='{"connected":true}'
check "prod-health: container health DOWN fails" "$(cond test "$STATUS" -eq 1)" "$OUT"

phealth FAKE_PS_JSON="$HEALTHY_PS" FAKE_HEALTH_BODY='{"status":"DOWN","note":"UP"}' FAKE_ROOT_CODE=200 FAKE_HUE_BODY='{"connected":true}'
check "prod-health: a DOWN body that merely contains UP still fails" "$(cond test "$STATUS" -eq 1)" "$OUT"

phealth FAKE_PS_JSON="$HEALTHY_PS" FAKE_HEALTH_BODY='' FAKE_ROOT_CODE=200 FAKE_HUE_BODY='{"connected":true}'
check "prod-health: no answer from the health endpoint fails" "$(cond test "$STATUS" -eq 1)" "$OUT"

phealth FAKE_PS_JSON="$HEALTHY_PS" FAKE_HEALTH_BODY='{"status":"UP"}' FAKE_ROOT_CODE=500 FAKE_HUE_BODY='{"connected":true}'
check "prod-health: GET / answering 500 fails" "$(cond test "$STATUS" -eq 1)" "$OUT"
check "prod-health: ...naming the code" "$(contains "$OUT" "'500', not 200"; echo $?)" "$OUT"

phealth FAKE_PS_JSON="$HEALTHY_PS" FAKE_HEALTH_BODY='{"status":"UP"}' FAKE_ROOT_CODE=200 FAKE_HUE_BODY='{"connected":false}'
check "prod-health: an unreachable Hue bridge only warns (exit 0)" "$STATUS" "$OUT"
check "prod-health: ...as a warn line and a counted warning" "$(contains "$OUT" "warn hue bridge"; echo $?)" "$OUT"
check "prod-health: ...in the summary" "$(contains "$OUT" "0 failed, 1 warning(s)"; echo $?)" "$OUT"

phealth FAKE_PS_JSON="$HEALTHY_PS" FAKE_HEALTH_BODY='{"status":"UP"}' FAKE_ROOT_CODE=200 FAKE_HUE_BODY=''
check "prod-health: an unreadable Hue status only warns (exit 0)" "$STATUS" "$OUT"

run "$h" "$SCRIPTS/prod-health.sh" --bogus
check "prod-health: an unknown flag is refused with status 2" "$(cond test "$STATUS" -eq 2)" "$OUT"

check "prod-health: has no host checks (no reboot, apt, tailscale or cloudflared)" \
    "$(grep -v '^[[:space:]]*#' "$SCRIPTS/prod-health.sh" | cond_not grep -qiE 'reboot-required|apt-get|tailscale|cloudflared|systemctl')"

echo ""
echo "ops-scripts-test: ${PASSED} passed, ${FAILED} failed"
[[ "$FAILED" -eq 0 ]]
