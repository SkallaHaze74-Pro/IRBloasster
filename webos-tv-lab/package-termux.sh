#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/webos-tv-lab"
MEDIA="$APP/media"

# TV Lab Lite carries no generated HDR/4K videos. Remove leftovers from older
# installs/builds so they cannot accidentally make the new IPK large again.
rm -rf "$MEDIA"

python - "$APP" <<'PY'
import binascii
import pathlib
import struct
import sys
import zlib

root = pathlib.Path(sys.argv[1])

def chunk(name, data):
    return struct.pack(">I", len(data)) + name + data + struct.pack(
        ">I", binascii.crc32(name + data) & 0xFFFFFFFF
    )

def write_icon(path, size):
    rows = []
    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            t = (x + y) / max(1, 2 * (size - 1))
            r = int(24 + 125 * t)
            g = int(48 + 55 * t)
            b = int(190 + 45 * (1 - t))
            border = max(1, size // 18)
            x1, x2 = size * 22 // 100, size * 78 // 100
            y1, y2 = size * 24 // 100, size * 65 // 100
            white = (
                (x1 <= x <= x2 and (abs(y - y1) <= border or abs(y - y2) <= border))
                or (y1 <= y <= y2 and (abs(x - x1) <= border or abs(x - x2) <= border))
                or (size * 44 // 100 <= x <= size * 56 // 100 and size * 65 // 100 <= y <= size * 73 // 100)
                or (size * 36 // 100 <= x <= size * 64 // 100 and size * 72 // 100 <= y <= size * 76 // 100)
            )
            if white:
                r, g, b = 245, 250, 255
            row.extend((r, g, b, 255))
        rows.append(bytes(row))

    raw = b"".join(rows)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    path.write_bytes(png)

write_icon(root / "icon.png", 80)
write_icon(root / "largeicon.png", 130)
PY

command -v ares-package >/dev/null 2>&1 || {
  echo "ares-package fehlt. Bitte webOS TV CLI installieren."
  exit 1
}

cd "$ROOT"
rm -f com.skallahaze.smartir.tvlab_*.ipk

echo "Paketiere SmartIR TV Lab Lite 1.3.0 ohne Video-Medien …"
ares-package webos-tv-lab

IPK="$(find "$ROOT" -maxdepth 1 -type f -name 'com.skallahaze.smartir.tvlab_*_all.ipk' | sort | tail -n1)"
if [[ -z "$IPK" ]]; then
  echo "FEHLER: TV-Lab-IPK wurde nicht erzeugt."
  exit 1
fi

ls -lh "$IPK"
echo "Fertig: $IPK"
