#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if ! command -v aapt2 >/dev/null 2>&1; then
  echo "[MusicCapsule] Termux aapt2 fehlt – installiere Paket …"
  pkg install -y aapt2
fi

if ! command -v keytool >/dev/null 2>&1; then
  echo "[MusicCapsule] keytool fehlt – installiere OpenJDK 17 …"
  pkg install -y openjdk-17
fi

if ! command -v unzip >/dev/null 2>&1; then
  echo "[MusicCapsule] unzip fehlt – installiere Paket …"
  pkg install -y unzip
fi

AAPT2_BIN="$(command -v aapt2)"
mkdir -p "$HOME/.gradle"
GLOBAL_PROPS="$HOME/.gradle/gradle.properties"
touch "$GLOBAL_PROPS"
sed -i '/^android\.aapt2FromMavenOverride=/d' "$GLOBAL_PROPS"
printf '\nandroid.aapt2FromMavenOverride=%s\n' "$AAPT2_BIN" >> "$GLOBAL_PROPS"

echo "[MusicCapsule] AAPT2 Override: $AAPT2_BIN"
"$AAPT2_BIN" version || true

SIGN_DIR="$HOME/.musiccapsule"
KEYSTORE="$SIGN_DIR/music-capsule-release.jks"
SIGN_PROPS="$SIGN_DIR/signing.properties"
mkdir -p "$SIGN_DIR"
chmod 700 "$SIGN_DIR"

if [ ! -f "$KEYSTORE" ] || [ ! -f "$SIGN_PROPS" ]; then
  echo
  echo "[MusicCapsule] Erzeuge einmalig den dauerhaften Signaturschlüssel …"
  PASS="$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')"
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storetype PKCS12 \
    -storepass "$PASS" \
    -alias musiccapsule \
    -keypass "$PASS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -dname "CN=Music Capsule,O=SkallaHaze74-Pro,C=DE" \
    -noprompt >/dev/null

  cat > "$SIGN_PROPS" <<EOF
storePassword=$PASS
keyAlias=musiccapsule
keyPassword=$PASS
EOF
  chmod 600 "$KEYSTORE" "$SIGN_PROPS"
  echo "[MusicCapsule] Permanenter Schlüssel erstellt: $KEYSTORE"
  echo "[MusicCapsule] ~/.musiccapsule niemals löschen; sonst sind spätere Updates nicht mehr möglich."
else
  echo "[MusicCapsule] Permanenter Signaturschlüssel gefunden."
fi

STORE_PASS="$(sed -n 's/^storePassword=//p' "$SIGN_PROPS" | head -n1)"
echo "[MusicCapsule] Signatur-Fingerabdruck:"
keytool -list -v -keystore "$KEYSTORE" -storepass "$STORE_PASS" -alias musiccapsule 2>/dev/null \
  | grep -E 'SHA256:|SHA-256:' | head -n1 || true

./gradlew --stop >/dev/null 2>&1 || true
rm -rf .gradle musiccapsule/build
./gradlew --no-daemon :musiccapsule:clean :musiccapsule:assembleRelease

VERSION="$(sed -n "s/.*versionName '\([^']*\)'.*/\1/p" musiccapsule/build.gradle | head -n1)"
SOURCE_APK="$ROOT/musiccapsule/build/outputs/apk/release/musiccapsule-release.apk"
OUT="$ROOT/MusicCapsule-v${VERSION:-stable}-STABLE.apk"

if [ ! -s "$SOURCE_APK" ]; then
  echo "[MusicCapsule] FEHLER: Release-APK fehlt: $SOURCE_APK"
  exit 10
fi

cp "$SOURCE_APK" "$OUT"
echo "[MusicCapsule] Prüfe APK-ZIP-Struktur …"
unzip -t "$OUT" >/dev/null

SDK_DIR=""
if [ -f "$ROOT/local.properties" ]; then
  SDK_DIR="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | head -n1 | sed 's/\\:/:/g')"
fi
for CANDIDATE in "$SDK_DIR" "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}" "$HOME/android-sdk"; do
  [ -n "$CANDIDATE" ] && [ -d "$CANDIDATE" ] || continue
  APKSIGNER="$(find "$CANDIDATE" -type f -path '*/build-tools/*/apksigner' 2>/dev/null | sort -V | tail -n1)"
  if [ -n "$APKSIGNER" ]; then
    echo "[MusicCapsule] Prüfe APK-Signatur mit apksigner …"
    "$APKSIGNER" verify --verbose --print-certs "$OUT"
    break
  fi
done

SHA256="$(sha256sum "$OUT" | awk '{print $1}')"
SIZE="$(wc -c < "$OUT" | tr -d ' ')"

echo
echo "[MusicCapsule] INSTALLIERBARE STANDALONE APK: $OUT"
echo "[MusicCapsule] Größe: $SIZE Bytes"
echo "[MusicCapsule] SHA-256: $SHA256"
echo "[MusicCapsule] Eigene Paket-ID: com.skallahaze.musiccapsule"

DOWNLOAD_DIR="$HOME/storage/downloads"
if [ -d "$DOWNLOAD_DIR" ]; then
  DOWNLOAD_APK="$DOWNLOAD_DIR/MusicCapsule-v${VERSION:-stable}-STABLE.apk"
  cp "$OUT" "$DOWNLOAD_APK"
  echo "[MusicCapsule] Kopiert nach Downloads: $DOWNLOAD_APK"
  echo "[MusicCapsule] Über die Dateien-App installieren."
else
  echo "[MusicCapsule] Tipp: einmal 'termux-setup-storage' ausführen."
fi
