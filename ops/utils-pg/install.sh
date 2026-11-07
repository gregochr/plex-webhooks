#!/bin/bash
# Installs backup.sh and verify.sh onto the host, then checks the systemd units.
# Run by .github/workflows/install-utils-pg-backup.yml from a checkout on the self-hosted runner.
#
# Scripts: each is written to a temp file INSIDE the target directory, chmod 755, then mv'd over
# the old one. `cp` over a script that is running corrupts it, because bash reads a script by byte
# offset; `mv` swaps the inode and leaves the running copy intact.
#
# Units: they need sudo, so they never update themselves. If /etc/systemd/system differs from this
# repository's copies the script FAILS and prints the sudo commands to run.
#
# Config (environment): INSTALL_DIR (default ~/services/utils-pg/backup), UNIT_DIR (default /etc/systemd/system)

set -euo pipefail
umask 077

SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_DIR="${INSTALL_DIR:-${HOME}/services/utils-pg/backup}"
UNIT_DIR="${UNIT_DIR:-/etc/systemd/system}"

mkdir -p "$INSTALL_DIR"
chmod 700 "$INSTALL_DIR"

for f in backup.sh verify.sh; do
    tmp="$(mktemp "${INSTALL_DIR}/.install-XXXXXX")"
    cp "${SRC_DIR}/${f}" "$tmp"
    chmod 755 "$tmp"
    mv -f "$tmp" "${INSTALL_DIR}/${f}"
    echo "Installed ${INSTALL_DIR}/${f}"
done

DRIFT=0
for u in utils-pg-backup.service utils-pg-backup.timer; do
    if ! cmp -s "${SRC_DIR}/${u}" "${UNIT_DIR}/${u}"; then
        echo "::error::${UNIT_DIR}/${u} is missing or differs from the repository."
        DRIFT=1
    fi
done

if [ "$DRIFT" -ne 0 ]; then
    echo "The units need sudo. The host has no clone, so copy from the runner's checkout. On the host run:"
    echo "  sudo cp ${SRC_DIR}/utils-pg-backup.service ${SRC_DIR}/utils-pg-backup.timer ${UNIT_DIR}/"
    echo "  sudo systemctl daemon-reload"
    echo "  sudo systemctl enable --now utils-pg-backup.timer"
    exit 1
fi
echo "Units match the repository."
