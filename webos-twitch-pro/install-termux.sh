#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-smartirtv}"

cd "$ROOT"
chmod +x webos-twitch-pro/package-termux.sh
./webos-twitch-pro/package-termux.sh

IPK="$(find "$ROOT" -maxdepth 1 -name 'com.skallahaze.twitchtvpro_1.0.0_all.ipk' -print -quit)"
if [[ -z "$IPK" ]]; then
  echo "FEHLER: Twitch TV Pro 1.0.0 IPK wurde nicht erzeugt."
  exit 1
fi

echo "Installiere: $IPK"
ares-install --device "$TARGET" "$IPK"
ares-launch --device "$TARGET" com.skallahaze.twitchtvpro
