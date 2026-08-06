#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/webos-soundcloud"

rm -rf "$APP/media"

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
    heights = [0.28, 0.46, 0.66, 0.86, 1.0, 0.86, 0.66, 0.46, 0.28]
    center_x = size / 2
    bar_width = max(2, size // 14)
    gap = max(1, size // 45)
    total = len(heights) * bar_width + (len(heights) - 1) * gap
    start_x = int(center_x - total / 2)

    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            r = int(255 - 35 * (y / max(1, size - 1)))
            g = int(92 + 55 * (1 - y / max(1, size - 1)))
            b = 0

            dx = x - size / 2
            dy = y - size / 2
            radius = size * 0.46
            inside = dx * dx + dy * dy <= radius * radius

            wave_bar = False
            for index, height_ratio in enumerate(heights):
                bx1 = start_x + index * (bar_width + gap)
                bx2 = bx1 + bar_width
                h = int(size * 0.52 * height_ratio)
                by1 = size // 2 - h // 2
                by2 = by1 + h
                if bx1 <= x <= bx2 and by1 <= y <= by2:
                    wave_bar = True
                    break

            if not inside:
                row.extend((0, 0, 0, 255))
            elif wave_bar:
                row.extend((255, 255, 255, 255))
            else:
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

cd "$ROOT"
rm -f com.skallahaze.soundcloudtv_*.ipk
ares-package webos-soundcloud
