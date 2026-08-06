#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/webos-twitch-pro"

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
            dx = x - size / 2
            dy = y - size / 2
            radius = size * 0.46
            inside = dx * dx + dy * dy <= radius * radius

            t = y / max(1, size - 1)
            r = int(175 - 65 * t)
            g = int(112 - 75 * t)
            b = int(255 - 25 * t)

            tv = (
                size * 24 // 100 <= x <= size * 76 // 100
                and size * 28 // 100 <= y <= size * 65 // 100
            )
            border = max(1, size // 18)
            on_border = tv and (
                x <= size * 24 // 100 + border
                or x >= size * 76 // 100 - border
                or y <= size * 28 // 100 + border
                or y >= size * 65 // 100 - border
            )
            stand = (
                size * 46 // 100 <= x <= size * 54 // 100
                and size * 65 // 100 <= y <= size * 73 // 100
            ) or (
                size * 36 // 100 <= x <= size * 64 // 100
                and size * 72 // 100 <= y <= size * 76 // 100
            )

            if not inside:
                row.extend((0, 0, 0, 255))
            elif on_border or stand:
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
rm -f com.skallahaze.twitchtvpro_*.ipk
ares-package webos-twitch-pro
