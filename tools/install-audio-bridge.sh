#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_DIR="$ROOT/webos-audio-bridge"
OUT_DIR="$ROOT/build-webos"
DEVICE="${1:-smartirtv}"
APP_ID="com.skallahaze.smartir.audiobridge"
VERSION="$(sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$APP_DIR/appinfo.json" | head -n1)"
MAIN_PAGE="$(sed -n 's/.*"main"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$APP_DIR/appinfo.json" | head -n1)"

command -v ares-package >/dev/null 2>&1 || {
  echo "Fehlt: ares-package (webOS CLI)"
  exit 1
}
command -v ares-install >/dev/null 2>&1 || {
  echo "Fehlt: ares-install (webOS CLI)"
  exit 1
}

if [ "$VERSION" != "0.4.2" ] || [ "$MAIN_PAGE" != "index-v042.html" ] || [ ! -f "$APP_DIR/index-v042.html" ]; then
  echo "STOP: Lokale Bridge ist nicht SmartIR 0.4.2."
  echo "Gefunden: Version=${VERSION:-leer}, Main=${MAIN_PAGE:-leer}"
  echo "Bitte zuerst den aktuellen main-Stand holen."
  exit 2
fi

if grep -Fq "ROOT-FREE AUDIO BRIDGE" "$APP_DIR/index-v042.html"; then
  echo "STOP: Veraltete Bridge-Oberfläche erkannt."
  exit 3
fi

printf '\n== SmartIR Audio Bridge Quelle ==\n'
printf 'Version: %s\n' "$VERSION"
printf 'Main:    %s\n' "$MAIN_PAGE"
grep -F "BRIDGE 0.4.2" "$APP_DIR/index-v042.html" >/dev/null

printf '\n== Alte SmartIR Audio Bridge sauber beenden/entfernen ==\n'
if command -v ares-launch >/dev/null 2>&1; then
  ares-launch -d "$DEVICE" --close "$APP_ID" >/dev/null 2>&1 || true
fi
if ares-install -d "$DEVICE" --list 2>/dev/null | grep -Fq "$APP_ID"; then
  ares-install -d "$DEVICE" --remove "$APP_ID" || true
fi

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

printf '\n== SmartIR Audio Bridge %s paketieren ==\n' "$VERSION"
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
printf 'Bridge-Version: %s · Cache-Bust Main + Live PCM/WebSocket + Neon Spectrum\n' "$VERSION"
printf 'Jetzt SmartIR Audio Mix auf dem Handy öffnen und LIVE Mix starten.\n'
