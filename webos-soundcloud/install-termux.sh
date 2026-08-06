#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-smartirtv}"

cd "$ROOT"
chmod +x webos-soundcloud/package-termux.sh
./webos-soundcloud/package-termux.sh

IPK="$(find "$ROOT" -maxdepth 1 -name 'com.skallahaze.soundcloudtv_1.0.0_all.ipk' -print -quit)"
if [[ -z "$IPK" ]]; then
  echo "FEHLER: SoundCloud TV IPK wurde nicht erzeugt."
  exit 1
fi

echo "Installiere: $IPK"
ares-install --device "$TARGET" "$IPK"
ares-launch --device "$TARGET" com.skallahaze.soundcloudtv
