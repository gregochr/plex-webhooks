#!/bin/bash
# Fixture tests for the deploy-side scripts: pre-release.sh, record-deployed-images.sh, rollback.sh, prod-health.sh
# and utils-pg-bootstrap.sh. `docker` and `curl` are replaced by fakes on PATH that read their answers from FAKE_*
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
        if [[ "$*" == *utils-pg* ]]; then
            if [[ "${FAKE_PG_UP:-1}" == "1" ]]; then echo "utils-pg"; fi
        elif [[ "${FAKE_CONTAINER_EXISTS:-0}" == "1" ]]; then
            echo "cinelight"
        fi
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
        if [[ " $* " == *" utils-pg "* ]]; then
            # Postgres in utils-pg. Stdin is consumed (and kept) where the real command would read it.
            [[ -n "${CINELIGHT_DB_PASSWORD:-}" ]] && echo "password-length=${#CINELIGHT_DB_PASSWORD}" >> "${FAKE_LOG}.env"
            case "$*" in
                *pg_database*)   echo "${FAKE_DB_EXISTS-1}"; exit "${FAKE_DB_QUERY_RC:-0}" ;;
                *pg_dump*)       printf '%s' "${FAKE_DUMP_CONTENT-PGDMP-fake-dump}"; exit "${FAKE_DUMP_RC:-0}" ;;
                *"pg_restore -l"*) cat > /dev/null; exit "${FAKE_RESTORE_LIST_RC:-0}" ;;
                *pg_restore*)    cat > "${FAKE_LOG}.restore-stdin"; exit "${FAKE_RESTORE_RC:-0}" ;;
                *"DROP DATABASE"*) exit "${FAKE_DROP_RC:-0}" ;;
                *psql*)          cat >> "${FAKE_LOG}.psql-stdin"; exit "${FAKE_PSQL_RC:-0}" ;;
            esac
            exit 0
        fi
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
elif [[ "$args" == *"/actuator/health/database"* ]]; then
    echo "${FAKE_DB_HEALTH_BODY:-}"
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

# --- the database dump ---
h="$(new_host)"
run "$h" env FAKE_PG_UP=1 FAKE_DB_EXISTS=1 "$SCRIPTS/pre-release.sh" v2.0.0
dump="$h/data/releases/v2.0.0/cinelight.dump"
manifest="$h/data/releases/v2.0.0/manifest"
check "pre-release db: a database that exists is dumped (exit 0)" "$STATUS" "$OUT"
check "pre-release db: the manifest says db=dump and names the file" \
    "$(grep -qx 'db=dump' "$manifest" && grep -qx 'db_dump=cinelight.dump' "$manifest"; echo $?)" "$(cat "$manifest")"
check "pre-release db: the dump has the pg_dump output" "$(cond test "$(cat "$dump")" = "PGDMP-fake-dump")"
dperms="$(stat -c '%a' "$dump" 2>/dev/null || stat -f '%Lp' "$dump")"
check "pre-release db: the dump is mode 0600" "$(cond test "$dperms" = "600")" "mode was $dperms"
check "pre-release db: dumps with docker exec -i pg_dump -U postgres -Fc -d cinelight" \
    "$(grep -qxF 'docker exec -i utils-pg pg_dump -U postgres -Fc -d cinelight' "$h/log"; echo $?)" "$(cat "$h/log")"
check "pre-release db: the dump is checked with pg_restore -l" \
    "$(grep -qxF 'docker exec -i utils-pg pg_restore -l' "$h/log"; echo $?)" "$(cat "$h/log")"
check "pre-release db: no docker call for the database ever allocates a TTY (-t / -it)" \
    "$(! grep -E 'exec .*utils-pg' "$h/log" | grep -qE ' -[a-z]*t[a-z]* '; echo $?)" "$(cat "$h/log")"
check "pre-release db: the dump is not in the output" "$(! contains "$OUT" "PGDMP"; echo $?)"

h="$(new_host)"
run "$h" env FAKE_PG_UP=1 FAKE_DB_EXISTS= "$SCRIPTS/pre-release.sh" v2.0.0
manifest="$h/data/releases/v2.0.0/manifest"
check "pre-release db: utils-pg up but no cinelight database warns and exits 0" "$STATUS" "$OUT"
check "pre-release db: ...records db=none" "$(grep -qx 'db=none' "$manifest"; echo $?)" "$(cat "$manifest")"
check "pre-release db: ...with no dump file and no db_dump line" \
    "$(if [[ ! -e "$h/data/releases/v2.0.0/cinelight.dump" ]] && ! grep -q '^db_dump=' "$manifest"; then echo 0; else echo 1; fi)"
check "pre-release db: ...and tells the owner to run the bootstrap" "$(contains "$OUT" "utils-pg-bootstrap.sh"; echo $?)" "$OUT"

h="$(new_host)"
run "$h" env FAKE_PG_UP=0 "$SCRIPTS/pre-release.sh" v2.0.0
manifest="$h/data/releases/v2.0.0/manifest"
check "pre-release db: utils-pg down warns and exits 0" "$STATUS" "$OUT"
check "pre-release db: ...records db=unavailable" "$(grep -qx 'db=unavailable' "$manifest"; echo $?)" "$(cat "$manifest")"
check "pre-release db: ...and never tried to dump" "$(! grep -q 'pg_dump' "$h/log"; echo $?)" "$(cat "$h/log")"

h="$(new_host)"
run "$h" env FAKE_PG_UP=1 FAKE_DB_QUERY_RC=2 "$SCRIPTS/pre-release.sh" v2.0.0
check "pre-release db: a failing existence query is db=unavailable, release continues" \
    "$(grep -qx 'db=unavailable' "$h/data/releases/v2.0.0/manifest" && echo 0 || echo 1)" "$OUT"

h="$(new_host)"
run "$h" env FAKE_PG_UP=1 FAKE_DUMP_RC=1 "$SCRIPTS/pre-release.sh" v2.0.0
check "pre-release db: a failing pg_dump is db=unavailable, release continues (exit 0)" "$STATUS" "$OUT"
check "pre-release db: ...with the partial dump removed" "$(cond test ! -e "$h/data/releases/v2.0.0/cinelight.dump")"
check "pre-release db: ...and the manifest still written" "$(grep -qx 'db=unavailable' "$h/data/releases/v2.0.0/manifest"; echo $?)"

h="$(new_host)"
run "$h" env FAKE_PG_UP=1 FAKE_DUMP_CONTENT= "$SCRIPTS/pre-release.sh" v2.0.0
check "pre-release db: an empty dump is not a backup (db=unavailable)" \
    "$(grep -qx 'db=unavailable' "$h/data/releases/v2.0.0/manifest" && test ! -e "$h/data/releases/v2.0.0/cinelight.dump" && echo 0 || echo 1)" "$OUT"

h="$(new_host)"
run "$h" env FAKE_PG_UP=1 FAKE_RESTORE_LIST_RC=1 "$SCRIPTS/pre-release.sh" v2.0.0
check "pre-release db: a dump pg_restore -l cannot read is not recorded (db=unavailable, file removed)" \
    "$(grep -qx 'db=unavailable' "$h/data/releases/v2.0.0/manifest" && test ! -e "$h/data/releases/v2.0.0/cinelight.dump" && echo 0 || echo 1)" "$OUT"
check "pre-release db: ...and the release still exits 0" "$STATUS" "$OUT"

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
    run "$h" env HEALTH_TIMEOUT=0 POLL_SECONDS=0 FAKE_DB_HEALTH_BODY='{"status":"UP"}' "$@" "$SCRIPTS/prod-health.sh" --deploy
}

h="$(new_host)"
phealth FAKE_PS_JSON="$HEALTHY_PS" FAKE_HEALTH_BODY='{"status":"UP"}' FAKE_ROOT_CODE=200 FAKE_HUE_BODY='{"connected":true}'
check "prod-health: all good exits 0" "$STATUS" "$OUT"
check "prod-health: summary line" "$(contains "$OUT" "prod-health: 3 checks, 0 failed, 0 warning(s) (deploy)"; echo $?)" "$OUT"
check "prod-health: reports an ok line for compose, container health, dashboard, the bridge and the database" \
    "$(cond test "$(grep -c '^ok  ' <<< "$OUT")" -eq 5)" "$OUT"

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

phealth FAKE_PS_JSON="$HEALTHY_PS" FAKE_HEALTH_BODY='{"status":"UP"}' FAKE_ROOT_CODE=200 FAKE_HUE_BODY='{"connected":true}' FAKE_DB_HEALTH_BODY='{"status":"DOWN"}'
check "prod-health: a database that is not UP only warns (exit 0)" "$STATUS" "$OUT"
check "prod-health: ...as a warn line naming the database" "$(contains "$OUT" "warn database"; echo $?)" "$OUT"
check "prod-health: ...counted in the summary" "$(contains "$OUT" "0 failed, 1 warning(s)"; echo $?)" "$OUT"

phealth FAKE_PS_JSON="$HEALTHY_PS" FAKE_HEALTH_BODY='{"status":"UP"}' FAKE_ROOT_CODE=200 FAKE_HUE_BODY='{"connected":true}' FAKE_DB_HEALTH_BODY=''
check "prod-health: no answer from the database health group (never set up, or an old image) only warns" "$STATUS" "$OUT"
check "prod-health: ...as a warn line" "$(contains "$OUT" "warn database"; echo $?)" "$OUT"

phealth FAKE_PS_JSON="$HEALTHY_PS" FAKE_HEALTH_BODY='{"status":"UP"}' FAKE_ROOT_CODE=200 FAKE_HUE_BODY='{"connected":true}' FAKE_DB_HEALTH_BODY='{"status":"DOWN","note":"UP"}'
check "prod-health: a DOWN body that merely contains UP is not a healthy database" "$(contains "$OUT" "warn database"; echo $?)" "$OUT"

run "$h" "$SCRIPTS/prod-health.sh" --bogus
check "prod-health: an unknown flag is refused with status 2" "$(cond test "$STATUS" -eq 2)" "$OUT"

check "prod-health: has no host checks (no reboot, apt, tailscale or cloudflared)" \
    "$(grep -v '^[[:space:]]*#' "$SCRIPTS/prod-health.sh" | cond_not grep -qiE 'reboot-required|apt-get|tailscale|cloudflared|systemctl')"


# ---- rollback.sh --restore-db ------------------------------------------------------------------------
# A rollback host whose manifest records a database state; with `dump` the checked dump file is there too.
rollback_host_db() {   # rollback_host_db <db-state|nokey> -> prints the host dir
    local state="$1" h
    h="$(rollback_host ghcr.io/gregochr/cinelight@sha256:old)"
    if [[ "$state" != "nokey" ]]; then printf 'db=%s\n' "$state" >> "$h/data/releases/v1.1.0/manifest"; fi
    if [[ "$state" == "dump" ]]; then
        printf 'db_dump=cinelight.dump\n' >> "$h/data/releases/v1.1.0/manifest"
        printf 'PGDMP-from-the-release' > "$h/data/releases/v1.1.0/cinelight.dump"
        chmod 600 "$h/data/releases/v1.1.0/cinelight.dump"
    fi
    echo "$h"
}
rb() {   # rb <host> <extra env...> -- <args...>
    local h="$1"
    shift
    local envs=()
    while [[ "$1" != "--" ]]; do envs+=("$1"); shift; done
    shift
    run "$h" env CINELIGHT_ROLLBACK_YES=1 HEALTH_ATTEMPTS=1 HEALTH_SLEEP=0 "${envs[@]}" "$SCRIPTS/rollback.sh" "$@"
}
line_of() { grep -nF -- "$2" "$1/log" | head -1 | cut -d: -f1; }   # line_of <host> <text>
count_of() { grep -cF -- "$2" "$1/log" || true; }                   # count_of <host> <text>

h="$(rollback_host_db dump)"
rb "$h" -- v1.1.0
check "rollback db: without --restore-db the database is not touched (exit 0)" "$STATUS" "$OUT"
check "rollback db: ...no call to utils-pg at all" "$(! grep -q 'utils-pg' "$h/log"; echo $?)" "$(cat "$h/log")"
check "rollback db: ...and the output says it was left as it was" "$(contains "$OUT" "left as it was"; echo $?)" "$OUT"

h="$(rollback_host_db dump)"
rb "$h" -- --restore-db v1.1.0
check "rollback db: --restore-db with a checked dump exits 0" "$STATUS" "$OUT"
stop_l="$(line_of "$h" 'compose stop cinelight')"
drop_l="$(line_of "$h" 'DROP DATABASE IF EXISTS cinelight WITH (FORCE)')"
init_l="$(grep -nxF 'docker exec -i utils-pg psql -U postgres -v ON_ERROR_STOP=1' "$h/log" | head -1 | cut -d: -f1)"
rest_l="$(line_of "$h" 'pg_restore -U postgres -d cinelight --no-owner --role cinelight --exit-on-error --single-transaction')"
up_l="$(line_of "$h" 'compose -f docker-compose.yml -f')"
check "rollback db: the app is stopped, then the database dropped, recreated, restored, and only then is the old image started" \
    "$(cond test -n "$stop_l" -a -n "$drop_l" -a -n "$init_l" -a -n "$rest_l" -a -n "$up_l" -a "$stop_l" -lt "$drop_l" -a "$drop_l" -lt "$init_l" -a "$init_l" -lt "$rest_l" -a "$rest_l" -lt "$up_l")" \
    "stop=$stop_l drop=$drop_l init=$init_l restore=$rest_l up=$up_l"$'\n'"$(cat "$h/log")"
check "rollback db: DROP DATABASE uses WITH (FORCE) so open connections cannot block it" "$(grep -qF 'WITH (FORCE)' "$h/log"; echo $?)"
check "rollback db: recreates with the bootstrap SQL (database owned by the role, CONNECT revoked)" \
    "$(grep -qF 'CREATE DATABASE cinelight OWNER cinelight' "$h/log.psql-stdin" && grep -qF 'REVOKE CONNECT ON DATABASE cinelight FROM PUBLIC' "$h/log.psql-stdin"; echo $?)" "$(cat "$h/log.psql-stdin" 2>/dev/null)"
check "rollback db: ...and that SQL carries no password" "$(! grep -qE 'ALTER ROLE|PASSWORD|getenv' "$h/log.psql-stdin"; echo $?)"
check "rollback db: the release's dump is what pg_restore reads on stdin" \
    "$(cond test "$(cat "$h/log.restore-stdin")" = "PGDMP-from-the-release")"
check "rollback db: no docker call for the database allocates a TTY (-t / -it)" \
    "$(! grep -E 'exec .*utils-pg' "$h/log" | grep -qE ' -[a-z]*t[a-z]* '; echo $?)"
check "rollback db: the settings are restored as well" "$(grep -qx 'hue.start-scene=before-release' "$h/data/config/application.properties"; echo $?)"
check "rollback db: the plan warns that the history since the release is discarded" "$(contains "$OUT" "DATABASE LOSS"; echo $?)" "$OUT"
check "rollback db: the summary says the database was restored" "$(contains "$OUT" "restored from"; echo $?)" "$OUT"

h="$(rollback_host_db dump)"
rb "$h" -- v1.1.0 --restore-db
check "rollback db: the flag is accepted after the version too" "$STATUS" "$OUT"
check "rollback db: ...and restores" "$(grep -q 'pg_restore -U postgres' "$h/log"; echo $?)"

for state in none unavailable nokey; do
    h="$(rollback_host_db "$state")"
    rb "$h" -- --restore-db v1.1.0
    check "rollback db: --restore-db is refused when the manifest says db=${state}" "$(cond test "$STATUS" -ne 0)" "$OUT"
    check "rollback db: ...before anything is stopped (db=${state})" "$(cond test "$(count_of "$h" 'compose stop')" = "0")" "$(cat "$h/log")"
    check "rollback db: ...with the live settings untouched (db=${state})" "$(grep -qx 'hue.start-scene=CHANGED-AFTER' "$h/data/config/application.properties"; echo $?)"
done

h="$(rollback_host_db dump)"
rm "$h/data/releases/v1.1.0/cinelight.dump"
rb "$h" -- --restore-db v1.1.0
check "rollback db: a missing dump file is refused before anything is stopped" \
    "$(cond test "$STATUS" -ne 0 -a "$(count_of "$h" 'compose stop')" = "0")" "$OUT"

h="$(rollback_host_db dump)"
: > "$h/data/releases/v1.1.0/cinelight.dump"
rb "$h" -- --restore-db v1.1.0
check "rollback db: an empty dump file is refused before anything is stopped" \
    "$(cond test "$STATUS" -ne 0 -a "$(count_of "$h" 'compose stop')" = "0")" "$OUT"

h="$(rollback_host_db dump)"
rb "$h" FAKE_RESTORE_LIST_RC=1 -- --restore-db v1.1.0
check "rollback db: a dump that no longer passes pg_restore -l is refused before anything is stopped" \
    "$(cond test "$STATUS" -ne 0 -a "$(count_of "$h" 'compose stop')" = "0")" "$OUT"
check "rollback db: ...naming pg_restore -l" "$(contains "$OUT" "pg_restore -l"; echo $?)" "$OUT"
check "rollback db: ...and never dropped the database" "$(cond test "$(count_of "$h" 'DROP DATABASE')" = "0")"

h="$(rollback_host_db dump)"
rb "$h" FAKE_RESTORE_RC=1 -- --restore-db v1.1.0
check "rollback db: a failed pg_restore exits non-zero" "$(cond test "$STATUS" -ne 0)" "$OUT"
check "rollback db: ...says the old image was NOT started" "$(contains "$OUT" "old image was NOT started"; echo $?)" "$OUT"
check "rollback db: ...and really did not start it" "$(cond test "$(count_of "$h" 'compose -f docker-compose.yml -f')" = "0")" "$(cat "$h/log")"
check "rollback db: ...and wrote no pin" "$(cond test ! -e "$h/compose/docker-compose.override.yml")"
check "rollback db: ...and left the settings as they were" "$(grep -qx 'hue.start-scene=CHANGED-AFTER' "$h/data/config/application.properties"; echo $?)"

h="$(rollback_host_db dump)"
rb "$h" FAKE_DROP_RC=1 -- --restore-db v1.1.0
check "rollback db: a failed DROP DATABASE exits non-zero and goes no further" \
    "$(cond test "$STATUS" -ne 0 -a "$(count_of "$h" 'pg_restore -U')" = "0" -a "$(count_of "$h" 'compose -f docker-compose.yml -f')" = "0")" "$OUT"

h="$(rollback_host_db dump)"
rb "$h" FAKE_PSQL_RC=1 -- --restore-db v1.1.0
check "rollback db: a failed recreate exits non-zero and never restores or starts the old image" \
    "$(cond test "$STATUS" -ne 0 -a "$(count_of "$h" 'pg_restore -U')" = "0" -a "$(count_of "$h" 'compose -f docker-compose.yml -f')" = "0")" "$OUT"

h="$(rollback_host_db dump)"
rb "$h" -- --restore-db
check "rollback db: --restore-db without a version is a usage error (status 2)" "$(cond test "$STATUS" -eq 2)" "$OUT"
rb "$h" -- --bogus v1.1.0
check "rollback db: an unknown option is a usage error (status 2)" "$(cond test "$STATUS" -eq 2)" "$OUT"
rb "$h" -- v1.1.0 v1.2.0
check "rollback db: two versions is a usage error (status 2)" "$(cond test "$STATUS" -eq 2)" "$OUT"

# ============================ utils-pg-bootstrap.sh ====================================================
SECRET="s3cr3t'quote\$HOME \"x\""
write_env() {   # write_env <host> <line>
    printf 'CINELIGHT_UID=1000\n%s\nOTHER=1\n' "$2" > "$1/env"
}
boot() {   # boot <host> <extra env...> -- <args...>
    local h="$1"
    shift
    local envs=()
    while [[ "$1" != "--" ]]; do envs+=("$1"); shift; done
    shift
    run "$h" env -u CINELIGHT_DB_PASSWORD CINELIGHT_ENV_FILE="$h/env" "${envs[@]}" "$SCRIPTS/utils-pg-bootstrap.sh" "$@"
}

h="$(new_host)"
write_env "$h" "CINELIGHT_DB_PASSWORD=${SECRET}"
boot "$h" --
check "bootstrap: with the password in .env it exits 0" "$STATUS" "$OUT"
check "bootstrap: runs psql through docker exec -i -e CINELIGHT_DB_PASSWORD (value not on the command line)" \
    "$(grep -qxF 'docker exec -i -e CINELIGHT_DB_PASSWORD utils-pg psql -U postgres -v ON_ERROR_STOP=1' "$h/log"; echo $?)" "$(cat "$h/log")"
check "bootstrap: no docker call allocates a TTY (-t / -it)" "$(! grep -E '^docker exec' "$h/log" | grep -qE ' -[a-z]*t[a-z]* '; echo $?)"
check "bootstrap: the whole password reached psql's environment, quotes and \$ intact" \
    "$(grep -qx "password-length=${#SECRET}" "$h/log.env"; echo $?)" "$(cat "$h/log.env" 2>/dev/null)"
check "bootstrap: the password is nowhere in the docker calls" "$(! grep -qF -- "$SECRET" "$h/log"; echo $?)"
check "bootstrap: ...nor in the SQL sent to psql" "$(! grep -qF -- "$SECRET" "$h/log.psql-stdin"; echo $?)"
check "bootstrap: ...nor in anything the script printed" "$(! contains "$OUT" "$SECRET"; echo $?)"
sql="$h/log.psql-stdin"
set_line="$(grep -n "^SET log_statement = 'none';" "$sql" | cut -d: -f1)"
alter_line="$(grep -n '^ALTER ROLE cinelight PASSWORD' "$sql" | cut -d: -f1)"
check "bootstrap: silences statement logging before the password is set" \
    "$(cond test -n "$set_line" -a -n "$alter_line" -a "$set_line" -lt "$alter_line")" "$(cat "$sql")"
check "bootstrap: reads the password with \\getenv and quotes it with :'pw'" \
    "$(grep -qxF '\getenv pw CINELIGHT_DB_PASSWORD' "$sql" && grep -qxF "ALTER ROLE cinelight PASSWORD :'pw';" "$sql"; echo $?)" "$(cat "$sql")"
check "bootstrap: creates the role idempotently (WHERE NOT EXISTS), with no superuser, createdb or createrole" \
    "$(grep -qF "CREATE ROLE cinelight LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE" "$sql" && grep -qF 'FROM pg_roles WHERE rolname' "$sql" && grep -qF 'WHERE NOT EXISTS' "$sql"; echo $?)"
check "bootstrap: creates the database idempotently, owned by the role" \
    "$(grep -qF "CREATE DATABASE cinelight OWNER cinelight" "$sql" && grep -qF 'FROM pg_database WHERE datname' "$sql"; echo $?)"
check "bootstrap: both creations go through \\gexec (CREATE DATABASE cannot run inside a DO block)" \
    "$(cond test "$(grep -c '\\gexec' "$sql")" -eq 2)" "$(cat "$sql")"
check "bootstrap: revokes CONNECT from PUBLIC" "$(grep -qF 'REVOKE CONNECT ON DATABASE cinelight FROM PUBLIC;' "$sql"; echo $?)"
check "bootstrap: prints the init SQL for utils-pg/init/, without any password step" \
    "$(contains "$OUT" "CREATE DATABASE cinelight OWNER cinelight" && ! contains "$OUT" "ALTER ROLE"; echo $?)" "$OUT"

h="$(new_host)"
write_env "$h" 'CINELIGHT_DB_PASSWORD="two words"'
boot "$h" --
check "bootstrap: one pair of surrounding double quotes in .env is removed" "$(grep -qx 'password-length=9' "$h/log.env"; echo $?)" "$(cat "$h/log.env" 2>/dev/null)"
h="$(new_host)"
write_env "$h" "CINELIGHT_DB_PASSWORD='two words'"
boot "$h" --
check "bootstrap: ...and single quotes" "$(grep -qx 'password-length=9' "$h/log.env"; echo $?)"

h="$(new_host)"
write_env "$h" 'CINELIGHT_DB_PASSWORD=from-file'
boot "$h" CINELIGHT_DB_PASSWORD=from-env --
check "bootstrap: a password already in the environment wins over .env" "$(grep -qx 'password-length=8' "$h/log.env"; echo $?)" "$(cat "$h/log.env" 2>/dev/null)"

h="$(new_host)"
write_env "$h" 'SOMETHING_ELSE=1'
boot "$h" --
check "bootstrap: no password anywhere is refused" "$(cond test "$STATUS" -eq 1)" "$OUT"
check "bootstrap: ...before any docker exec" "$(! grep -q '^docker exec' "$h/log"; echo $?)"
check "bootstrap: ...naming the variable" "$(contains "$OUT" "CINELIGHT_DB_PASSWORD"; echo $?)" "$OUT"

h="$(new_host)"
write_env "$h" 'CINELIGHT_DB_PASSWORD=pw'
boot "$h" FAKE_PG_UP=0 --
check "bootstrap: a stopped utils-pg is refused" "$(cond test "$STATUS" -eq 1)" "$OUT"
check "bootstrap: ...without running psql" "$(! grep -q '^docker exec' "$h/log"; echo $?)"

h="$(new_host)"
write_env "$h" 'CINELIGHT_DB_PASSWORD=pw'
boot "$h" FAKE_PSQL_RC=1 --
check "bootstrap: a psql failure fails the script" "$(cond test "$STATUS" -ne 0)" "$OUT"

h="$(new_host)"
boot "$h" -- --print-init-sql
check "bootstrap: --print-init-sql needs no password and no docker (exit 0)" "$STATUS" "$OUT"
check "bootstrap: ...prints the role, database and revoke" \
    "$(contains "$OUT" "CREATE ROLE cinelight" && contains "$OUT" "CREATE DATABASE cinelight OWNER cinelight" && contains "$OUT" "REVOKE CONNECT"; echo $?)" "$OUT"
check "bootstrap: ...and nothing about a password" "$(! contains "$OUT" "ALTER ROLE"; echo $?)"
check "bootstrap: ...and made no docker call" "$(cond test ! -s "$h/log")"
boot "$h" -- --bogus
check "bootstrap: an unknown argument is a usage error (status 2)" "$(cond test "$STATUS" -eq 2)" "$OUT"

echo ""
echo "ops-scripts-test: ${PASSED} passed, ${FAILED} failed"
[[ "$FAILED" -eq 0 ]]
