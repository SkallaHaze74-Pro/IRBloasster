#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_DIR="$ROOT/webos-audio-bridge"
OUT_DIR="$ROOT/build-webos"
DEVICE="${1:-smartirtv}"

command -v ares-package >/dev/null 2>&1 || {
  echo "Fehlt: ares-package (webOS TV CLI)"
  exit 1
}
command -v ares-install >/dev/null 2>&1 || {
  echo "Fehlt: ares-install (webOS TV CLI)"
  exit 1
}

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

printf '\n== SmartIR Audio Bridge paketieren ==\n'
ares-package "$APP_DIR" -o "$OUT_DIR"

IPK="$(find "$OUT_DIR" -maxdepth 1 -type f -name '*.ipk' | sort | tail -n1)"
[ -n "$IPK" ] || {
  echo "Kein .ipk erzeugt"
  exit 1
}

printf '\n== Installiere auf %s ==\n' "$DEVICE"
ares-install -d "$DEVICE" "$IPK"

printf '\nFertig. App-ID: com.skallahaze.smartir.audiobridge\n'
printf 'Bridge-Version: 0.3.0 · Live PCM/WebSocket + Web Audio\n'
printf 'Jetzt SmartIR Audio Mix auf dem Handy öffnen.\n'
