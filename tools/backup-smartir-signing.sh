#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

SRC="$HOME/.smartir"
if [ ! -f "$SRC/smartir-release.jks" ] || [ ! -f "$SRC/signing.properties" ]; then
  echo "[SmartIR] Noch kein permanenter Signaturschlüssel vorhanden."
  echo "[SmartIR] Zuerst: bash tools/build-termux.sh"
  exit 1
fi

if [ ! -d "$HOME/storage/downloads" ]; then
  echo "[SmartIR] Termux-Speicherzugriff fehlt. Einmal ausführen: termux-setup-storage"
  exit 2
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$HOME/storage/downloads/SmartIR-signing-backup-$STAMP"
mkdir -p "$OUT"
cp "$SRC/smartir-release.jks" "$OUT/"
cp "$SRC/signing.properties" "$OUT/"
chmod 600 "$OUT/smartir-release.jks" "$OUT/signing.properties" 2>/dev/null || true

echo "[SmartIR] Backup erstellt: $OUT"
echo "[SmartIR] Privat aufbewahren. Wer diese beiden Dateien besitzt, kann SmartIR-Updates mit deiner Signatur erstellen."
