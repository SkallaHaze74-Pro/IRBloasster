#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/webos-tv-lab"
MEDIA="$APP/media"
MEDIA_MARKER="$MEDIA/.smartir-media-v3"

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

if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg fehlt. Einmal ausführen: pkg install ffmpeg"
  exit 1
fi

has_encoder() {
  ffmpeg -hide_banner -encoders 2>/dev/null | grep -q "$1"
}

make_hevc() {
  local size="$1"
  local transfer_name="$2"
  local transfer_code="$3"
  local output="$4"
  local extra_params="$5"

  ffmpeg -y -hide_banner -loglevel error \
    -f lavfi -i "testsrc2=size=${size}:rate=12,format=yuv420p10le" \
    -t 4 -an \
    -c:v libx265 -preset ultrafast -crf 30 \
    -pix_fmt yuv420p10le -profile:v main10 -tag:v hvc1 \
    -color_primaries bt2020 -color_trc "$transfer_name" -colorspace bt2020nc \
    -x265-params "log-level=error:repeat-headers=1:colorprim=9:transfer=${transfer_code}:colormatrix=9${extra_params}" \
    -movflags +faststart \
    "$output"
}

make_vp9() {
  local transfer_name="$1"
  local output="$2"

  ffmpeg -y -hide_banner -loglevel error \
    -f lavfi -i "testsrc2=size=3840x2160:rate=12,format=yuv420p10le" \
    -t 4 -an \
    -c:v libvpx-vp9 -deadline realtime -cpu-used 8 -row-mt 1 \
    -tile-columns 2 -frame-parallel 1 -b:v 7000k -maxrate 9000k -bufsize 18000k \
    -g 24 -pix_fmt yuv420p10le -profile:v 2 \
    -color_primaries bt2020 -color_trc "$transfer_name" -colorspace bt2020nc \
    "$output"
}

make_h264() {
  local size="$1"
  local level="$2"
  local output="$3"

  ffmpeg -y -hide_banner -loglevel error \
    -f lavfi -i "testsrc2=size=${size}:rate=24,format=yuv420p" \
    -t 4 -an \
    -c:v libx264 -preset ultrafast -crf 20 \
    -pix_fmt yuv420p -profile:v high -level:v "$level" \
    -color_primaries bt709 -color_trc bt709 -colorspace bt709 \
    -movflags +faststart \
    "$output"
}

if [[ ! -f "$MEDIA_MARKER" ]]; then
  rm -f "$MEDIA"/SmartIR-*.mp4 "$MEDIA"/SmartIR-*.webm "$MEDIA"/SmartIR-*.ts

  if ! has_encoder 'libx265'; then
    echo "Die installierte ffmpeg-Version enthält keinen libx265-Encoder."
    exit 1
  fi

  if ! has_encoder 'libx264'; then
    echo "Die installierte ffmpeg-Version enthält keinen libx264-Encoder."
    exit 1
  fi

  echo "Erzeuge sichtbare 4K/1080p HLG-, HDR10- und SDR-Testvideos …"

  make_hevc \
    "3840x2160" \
    "arib-std-b67" \
    "18" \
    "$MEDIA/SmartIR-HLG-4K-HEVC.mp4" \
    ""

  make_hevc \
    "1920x1080" \
    "arib-std-b67" \
    "18" \
    "$MEDIA/SmartIR-HLG-1080-HEVC.mp4" \
    ""

  make_hevc \
    "3840x2160" \
    "smpte2084" \
    "16" \
    "$MEDIA/SmartIR-HDR10-4K-HEVC.mp4" \
    ":hdr10=1:hdr10-opt=1:master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1):max-cll=1000,400"

  make_hevc \
    "1920x1080" \
    "smpte2084" \
    "16" \
    "$MEDIA/SmartIR-HDR10-1080-HEVC.mp4" \
    ":hdr10=1:hdr10-opt=1:master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1):max-cll=1000,400"

  ffmpeg -y -hide_banner -loglevel error \
    -i "$MEDIA/SmartIR-HLG-4K-HEVC.mp4" \
    -c copy -bsf:v hevc_mp4toannexb -f mpegts \
    "$MEDIA/SmartIR-HLG-4K-HEVC.ts"

  ffmpeg -y -hide_banner -loglevel error \
    -i "$MEDIA/SmartIR-HDR10-4K-HEVC.mp4" \
    -c copy -bsf:v hevc_mp4toannexb -f mpegts \
    "$MEDIA/SmartIR-HDR10-4K-HEVC.ts"

  if has_encoder 'libvpx-vp9'; then
    make_vp9 "arib-std-b67" "$MEDIA/SmartIR-HLG-4K-VP9.webm"
    make_vp9 "smpte2084" "$MEDIA/SmartIR-HDR10-4K-VP9.webm"
  else
    echo "Hinweis: libvpx-vp9 fehlt. HEVC- und MPEG-TS-Varianten werden trotzdem gebaut."
  fi

  make_h264 "3840x2160" "5.1" "$MEDIA/SmartIR-SDR-4K-H264.mp4"
  make_h264 "1920x1080" "4.2" "$MEDIA/SmartIR-SDR-1080-H264.mp4"

  touch "$MEDIA_MARKER"
fi

echo
echo "Erzeugte Mediendateien:"
ls -lh "$MEDIA"/SmartIR-* 2>/dev/null || true

echo
echo "Video-Metadaten:"
for file in "$MEDIA"/SmartIR-*; do
  [[ -f "$file" ]] || continue
  echo "--- $(basename "$file")"
  ffprobe -v error \
    -select_streams v:0 \
    -show_entries stream=codec_name,profile,width,height,pix_fmt,color_space,color_transfer,color_primaries \
    -of default=noprint_wrappers=1 "$file" || true
done

cd "$ROOT"
rm -f com.skallahaze.smartir.tvlab_*.ipk
ares-package webos-tv-lab
