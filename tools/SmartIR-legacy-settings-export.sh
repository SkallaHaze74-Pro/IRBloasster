#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PACKAGE="com.skallahaze.irbloasster"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="${1:-$HOME/storage/downloads/SmartIR-backup-legacy-$STAMP.json}"
GENERAL_XML="$(mktemp)"
SECURE_XML="$(mktemp)"
trap 'rm -f "$GENERAL_XML" "$SECURE_XML"' EXIT

command -v rish >/dev/null 2>&1 || {
  echo "FEHLER: rish fehlt. In Shizuku zuerst die Nutzung in Terminal-Apps einrichten."
  exit 1
}

command -v python >/dev/null 2>&1 || pkg install python -y

if ! rish -c "pm path $PACKAGE" | grep -q '^package:'; then
  echo "FEHLER: Die alte SmartIR-App ist nicht installiert."
  exit 1
fi

if ! rish -c "run-as $PACKAGE cat shared_prefs/smart_ir_settings.xml" > "$GENERAL_XML"; then
  echo "FEHLER: SmartIR-Einstellungen konnten nicht gelesen werden."
  echo "Shizuku muss laufen und die installierte SmartIR-Version muss eine Debug-APK sein."
  exit 1
fi

rish -c "run-as $PACKAGE cat shared_prefs/smart_ir_secure.xml" > "$SECURE_XML" 2>/dev/null || true

python - "$GENERAL_XML" "$SECURE_XML" "$OUT" <<'PY'
import json
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def read_xml(path: Path) -> dict:
    if not path.exists() or path.stat().st_size == 0:
        return {}
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        return {}
    values = {}
    for node in root:
        name = node.attrib.get("name")
        if not name:
            continue
        if node.tag == "string":
            values[name] = node.text or ""
        elif node.tag == "boolean":
            values[name] = node.attrib.get("value", "false").lower() == "true"
        elif node.tag in {"int", "long", "float"}:
            values[name] = node.attrib.get("value", "0")
    return values


general = read_xml(Path(sys.argv[1]))
secure = read_xml(Path(sys.argv[2]))
out = Path(sys.argv[3])

client_key_present = bool(
    secure.get("webos_client_key_secure")
    or secure.get("webos_client_key")
    or general.get("webos_client_key_secure")
    or general.get("webos_client_key")
)

fingerprint = (
    secure.get("webos_certificate_fingerprint")
    or general.get("webos_certificate_fingerprint")
    or ""
)

backup = {
    "format": "smartir-settings-backup",
    "schemaVersion": 1,
    "exportedAtEpochMillis": int(time.time() * 1000),
    "app": {
        "packageName": "com.skallahaze.irbloasster",
        "versionName": "legacy-export"
    },
    "settings": {
        "themePreference": general.get("theme", "SYSTEM"),
        "hapticsEnabled": bool(general.get("haptics", True)),
        "autoConnect": bool(general.get("auto_connect", True)),
        "webOsHost": general.get("webos_host", ""),
        "webOsMac": general.get("webos_mac", ""),
        "sonyMode": general.get("sony_mode", "AV1"),
        "webOsCertificateFingerprint": fingerprint
    },
    "security": {
        "webOsClientKeyIncluded": False,
        "webOsClientKeyWasPresent": client_key_present,
        "note": (
            "Der webOS-Client-Key wurde absichtlich nicht exportiert. "
            "Nach einer vollständigen Deinstallation den LG-TV neu koppeln."
        )
    }
}

out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(backup, ensure_ascii=False, indent=2), encoding="utf-8")
print(out)
PY

echo
echo "BACKUP ERFOLGREICH:"
echo "$OUT"
ls -lh "$OUT"
