#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if ! command -v aapt2 >/dev/null 2>&1; then
  echo "[SmartIR] Termux aapt2 fehlt – installiere Paket …"
  pkg install -y aapt2
fi

AAPT2_BIN="$(command -v aapt2)"
mkdir -p "$HOME/.gradle"
GLOBAL_PROPS="$HOME/.gradle/gradle.properties"
touch "$GLOBAL_PROPS"
sed -i '/^android\.aapt2FromMavenOverride=/d' "$GLOBAL_PROPS"
printf '\nandroid.aapt2FromMavenOverride=%s\n' "$AAPT2_BIN" >> "$GLOBAL_PROPS"

echo "[SmartIR] AAPT2 Override: $AAPT2_BIN"
"$AAPT2_BIN" version || true

./gradlew --stop >/dev/null 2>&1 || true
rm -rf .gradle app/build
./gradlew --no-daemon clean assembleDebug

echo
echo "[SmartIR] 1.4.0 APK: $ROOT/app/build/outputs/apk/debug/app-debug.apk"
