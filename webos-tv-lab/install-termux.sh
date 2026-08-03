#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-smartirtv}"

cd "$ROOT"
chmod +x webos-tv-lab/package-termux.sh
./webos-tv-lab/package-termux.sh

IPK="$(find "$ROOT" -maxdepth 1 -name 'com.skallahaze.smartir.tvlab_1.2.0_all.ipk' -print -quit)"
if [[ -z "$IPK" ]]; then
  echo "FEHLER: com.skallahaze.smartir.tvlab_1.2.0_all.ipk wurde nicht erzeugt."
  exit 1
fi

echo "Installiere: $IPK"
ares-install --device "$TARGET" "$IPK"
ares-launch --device "$TARGET" com.skallahaze.smartir.tvlab
