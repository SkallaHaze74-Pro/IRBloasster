#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_DIR="$ROOT/webos-audio-bridge"
OUT_DIR="$ROOT/build-webos"
DEVICE="${1:-smartirtv}"
APP_ID="com.skallahaze.smartir.audiobridge"
VERSION="$(sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$APP_DIR/appinfo.json" | head -n1)"

command -v ares-package >/dev/null 2>&1 || {
  echo "Fehlt: ares-package (webOS CLI)"
  exit 1
}
command -v ares-install >/dev/null 2>&1 || {
  echo "Fehlt: ares-install (webOS CLI)"
  exit 1
}

printf '\n== Alte SmartIR Audio Bridge sauber beenden/entfernen ==\n'
if command -v ares-launch >/dev/null 2>&1; then
  ares-launch -d "$DEVICE" --close "$APP_ID" >/dev/null 2>&1 || true
fi
if ares-install -d "$DEVICE" --list 2>/dev/null | grep -Fq "$APP_ID"; then
  ares-install -d "$DEVICE" --remove "$APP_ID" || true
fi

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

printf '\n== SmartIR Audio Bridge %s paketieren ==\n' "${VERSION:-unbekannt}"
ares-package "$APP_DIR" -o "$OUT_DIR"

IPK="$(find "$OUT_DIR" -maxdepth 1 -type f -name '*.ipk' | sort | tail -n1)"
[ -n "$IPK" ] || {
  echo "Kein .ipk erzeugt"
  exit 1
}

printf '\n== Installiere frische Bridge auf %s ==\n' "$DEVICE"
ares-install -d "$DEVICE" "$IPK"

printf '\n== Kontrolle ==\n'
ares-install -d "$DEVICE" --list | grep -F "$APP_ID" || true

printf '\nFertig. App-ID: %s\n' "$APP_ID"
printf 'Bridge-Version: %s · Live PCM/WebSocket + Neon Spectrum\n' "${VERSION:-unbekannt}"
printf 'Jetzt SmartIR Audio Mix auf dem Handy öffnen und LIVE Mix starten.\n'
