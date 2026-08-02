#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/webos-tv-lab"
MEDIA="$APP/media"

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
            border = size // 18
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

mkdir -p "$MEDIA"

if [[ ! -s "$MEDIA/SmartIR-HLG-HDR-Test.mp4" || ! -s "$MEDIA/SmartIR-HDR10-Test.mp4" ]]; then
  if ! command -v ffmpeg >/dev/null 2>&1; then
    echo "ffmpeg fehlt. Einmal ausführen: pkg install ffmpeg"
    exit 1
  fi

  if ! ffmpeg -hide_banner -encoders 2>/dev/null | grep 'libx265' >/dev/null; then
    echo "Die installierte ffmpeg-Version enthält keinen libx265-Encoder. Termux-Pakete aktualisieren und ffmpeg neu installieren."
    exit 1
  fi

  echo "Erzeuge lokale HLG- und HDR10-Testvideos …"

  ffmpeg -y -hide_banner -loglevel error \
    -f lavfi -i "smptehdbars=size=1920x1080:rate=24,format=yuv420p10le" \
    -f lavfi -i "anullsrc=channel_layout=stereo:sample_rate=48000" \
    -t 5 -map 0:v:0 -map 1:a:0 \
    -c:v libx265 -preset ultrafast -crf 30 -pix_fmt yuv420p10le -profile:v main10 -tag:v hvc1 \
    -color_primaries bt2020 -color_trc arib-std-b67 -colorspace bt2020nc \
    -x265-params "log-level=error:repeat-headers=1:colorprim=9:transfer=18:colormatrix=9" \
    -c:a aac -b:a 64k -movflags +faststart -shortest \
    "$MEDIA/SmartIR-HLG-HDR-Test.mp4"

  ffmpeg -y -hide_banner -loglevel error \
    -f lavfi -i "smptehdbars=size=1920x1080:rate=24,format=yuv420p10le" \
    -f lavfi -i "anullsrc=channel_layout=stereo:sample_rate=48000" \
    -t 5 -map 0:v:0 -map 1:a:0 \
    -c:v libx265 -preset ultrafast -crf 30 -pix_fmt yuv420p10le -profile:v main10 -tag:v hvc1 \
    -color_primaries bt2020 -color_trc smpte2084 -colorspace bt2020nc \
    -x265-params "log-level=error:hdr10=1:repeat-headers=1:colorprim=9:transfer=16:colormatrix=9:master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1):max-cll=1000,400" \
    -c:a aac -b:a 64k -movflags +faststart -shortest \
    "$MEDIA/SmartIR-HDR10-Test.mp4"
fi

cd "$ROOT"
rm -f com.skallahaze.smartir.tvlab_*.ipk
ares-package webos-tv-lab
