#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-smartirtv}"
APP_ID="com.skallahaze.smartir.tvlab"

cd "$ROOT"
chmod +x webos-tv-lab/package-termux.sh
./webos-tv-lab/package-termux.sh

IPK="$(find "$ROOT" -maxdepth 1 -type f -name 'com.skallahaze.smartir.tvlab_*_all.ipk' | sort | tail -n1)"
if [[ -z "$IPK" ]]; then
  echo "FEHLER: TV-Lab-IPK wurde nicht erzeugt."
  exit 1
fi

# Remove the old media-heavy version first so generated HDR/4K files are not
# left behind in the developer app directory.
if command -v ares-launch >/dev/null 2>&1; then
  ares-launch --device "$TARGET" --close "$APP_ID" >/dev/null 2>&1 || true
fi
if ares-install --device "$TARGET" --list 2>/dev/null | grep -Fq "$APP_ID"; then
  ares-install --device "$TARGET" --remove "$APP_ID" || true
fi

echo "Installiere TV Lab Lite: $IPK"
ares-install --device "$TARGET" "$IPK"
ares-launch --device "$TARGET" "$APP_ID"
