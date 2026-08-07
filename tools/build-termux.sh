#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if ! command -v aapt2 >/dev/null 2>&1; then
  echo "[SmartIR] Termux aapt2 fehlt – installiere Paket …"
  pkg install -y aapt2
fi

if ! command -v keytool >/dev/null 2>&1; then
  echo "[SmartIR] keytool fehlt – installiere OpenJDK 17 …"
  pkg install -y openjdk-17
fi

AAPT2_BIN="$(command -v aapt2)"
mkdir -p "$HOME/.gradle"
GLOBAL_PROPS="$HOME/.gradle/gradle.properties"
touch "$GLOBAL_PROPS"
sed -i '/^android\.aapt2FromMavenOverride=/d' "$GLOBAL_PROPS"
printf '\nandroid.aapt2FromMavenOverride=%s\n' "$AAPT2_BIN" >> "$GLOBAL_PROPS"

echo "[SmartIR] AAPT2 Override: $AAPT2_BIN"
"$AAPT2_BIN" version || true

SIGN_DIR="$HOME/.smartir"
KEYSTORE="$SIGN_DIR/smartir-release.jks"
SIGN_PROPS="$SIGN_DIR/signing.properties"
mkdir -p "$SIGN_DIR"
chmod 700 "$SIGN_DIR"

if [ ! -f "$KEYSTORE" ] || [ ! -f "$SIGN_PROPS" ]; then
  echo
  echo "[SmartIR] Erzeuge einmalig den dauerhaften privaten SmartIR-Signaturschlüssel …"
  PASS="$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')"
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storetype PKCS12 \
    -storepass "$PASS" \
    -alias smartir \
    -keypass "$PASS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -dname "CN=SmartIR,O=SkallaHaze74-Pro,C=DE" \
    -noprompt >/dev/null

  cat > "$SIGN_PROPS" <<EOF
storePassword=$PASS
keyAlias=smartir
keyPassword=$PASS
EOF
  chmod 600 "$KEYSTORE" "$SIGN_PROPS"
  echo "[SmartIR] Permanenter Signaturschlüssel erstellt: $KEYSTORE"
  echo "[SmartIR] Diesen Ordner niemals löschen; ohne Schlüssel sind spätere In-Place-Updates nicht möglich."
else
  echo "[SmartIR] Permanenter Signaturschlüssel gefunden."
fi

STORE_PASS="$(sed -n 's/^storePassword=//p' "$SIGN_PROPS" | head -n1)"
echo "[SmartIR] Signatur-Fingerabdruck:"
keytool -list -v -keystore "$KEYSTORE" -storepass "$STORE_PASS" -alias smartir 2>/dev/null \
  | grep -E 'SHA256:|SHA-256:' | head -n1 || true

./gradlew --stop >/dev/null 2>&1 || true
rm -rf .gradle app/build
./gradlew --no-daemon clean assembleDebug

VERSION="$(sed -n "s/.*versionName '\([^']*\)'.*/\1/p" app/build.gradle | head -n1)"
OUT="$ROOT/SmartIR-v${VERSION:-stable}-stable.apk"
cp app/build/outputs/apk/debug/app-debug.apk "$OUT"

echo
echo "[SmartIR] STABIL SIGNIERTE APK: $OUT"
echo "[SmartIR] Künftige Builds mit diesem Termux-Setup können die App ohne Deinstallation aktualisieren."
echo "[SmartIR] Wichtig: Von einer bereits anders signierten alten APK ist genau ein letzter Wechsel nötig."
