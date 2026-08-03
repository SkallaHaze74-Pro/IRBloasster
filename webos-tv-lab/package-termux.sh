#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/webos-tv-lab"
MEDIA="$APP/media"
FPS=6
DURATION=3

mkdir -p "$MEDIA"

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

command -v ffmpeg >/dev/null 2>&1 || {
  echo "ffmpeg fehlt. Einmal ausführen: pkg install ffmpeg"
  exit 1
}

has_encoder() {
  ffmpeg -hide_banner -encoders 2>/dev/null | grep -q "$1"
}

make_hevc_cli() {
  local size="$1"
  local transfer_name="$2"
  local transfer_code="$3"
  local output="$4"
  local hdr10="$5"
  local raw="${output%.mp4}.hevc"
  local params=(
    --y4m
    --input -
    --output "$raw"
    --preset ultrafast
    --crf 28
    --profile main10
    --repeat-headers
    --colorprim 9
    --transfer "$transfer_code"
    --colormatrix 9
    --fps "$FPS"
    --log-level error
  )

  if [[ "$hdr10" == "yes" ]]; then
    params+=(
      --hdr10
      --hdr10-opt
      --master-display "G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1)"
      --max-cll "1000,400"
    )
  fi

  ffmpeg -y -hide_banner -loglevel error \
    -f lavfi -i "testsrc2=size=${size}:rate=${FPS},format=yuv420p10le" \
    -t "$DURATION" -an -strict -1 -f yuv4mpegpipe - \
    | x265 "${params[@]}"

  ffmpeg -y -hide_banner -loglevel error \
    -r "$FPS" -f hevc -i "$raw" \
    -c:v copy -tag:v hvc1 \
    -color_primaries bt2020 -color_trc "$transfer_name" -colorspace bt2020nc \
    -movflags +faststart \
    "$output"

  rm -f "$raw"
}

make_hevc() {
  local size="$1"
  local transfer_name="$2"
  local transfer_code="$3"
  local output="$4"
  local hdr10="$5"

  [[ -s "$output" ]] && return 0

  if has_encoder 'libx265'; then
    local extra=""
    if [[ "$hdr10" == "yes" ]]; then
      extra=":hdr10=1:hdr10-opt=1:master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1):max-cll=1000,400"
    fi
    ffmpeg -y -hide_banner -loglevel error \
      -f lavfi -i "testsrc2=size=${size}:rate=${FPS},format=yuv420p10le" \
      -t "$DURATION" -an \
      -c:v libx265 -preset ultrafast -crf 28 \
      -pix_fmt yuv420p10le -profile:v main10 -tag:v hvc1 \
      -color_primaries bt2020 -color_trc "$transfer_name" -colorspace bt2020nc \
      -x265-params "log-level=error:repeat-headers=1:colorprim=9:transfer=${transfer_code}:colormatrix=9${extra}" \
      -movflags +faststart \
      "$output"
  elif command -v x265 >/dev/null 2>&1; then
    make_hevc_cli "$size" "$transfer_name" "$transfer_code" "$output" "$hdr10"
  else
    echo "HEVC kann nicht erzeugt werden. libx265/x265 fehlt."
    exit 1
  fi
}

make_sdr() {
  local size="$1"
  local fps="$2"
  local output="$3"

  [[ -s "$output" ]] && return 0

  if has_encoder 'libx264'; then
    ffmpeg -y -hide_banner -loglevel error \
      -f lavfi -i "testsrc2=size=${size}:rate=${fps},format=yuv420p" \
      -t "$DURATION" -an \
      -c:v libx264 -preset ultrafast -crf 20 \
      -pix_fmt yuv420p -profile:v high \
      -color_primaries bt709 -color_trc bt709 -colorspace bt709 \
      -movflags +faststart \
      "$output"
  else
    echo "Hinweis: libx264 fehlt. SDR wird mit dem eingebauten MPEG-4-Encoder erzeugt."
    ffmpeg -y -hide_banner -loglevel error \
      -f lavfi -i "testsrc2=size=${size}:rate=${fps},format=yuv420p" \
      -t "$DURATION" -an \
      -c:v mpeg4 -q:v 3 -pix_fmt yuv420p \
      -color_primaries bt709 -color_trc bt709 -colorspace bt709 \
      -movflags +faststart \
      "$output"
  fi
}

echo "Prüfe/erzeuge HLG-, HDR10- und SDR-Testvideos …"

make_hevc "3840x2160" "arib-std-b67" "18" "$MEDIA/SmartIR-HLG-4K-HEVC.mp4" "no"
make_hevc "1920x1080" "arib-std-b67" "18" "$MEDIA/SmartIR-HLG-1080-HEVC.mp4" "no"
make_hevc "3840x2160" "smpte2084" "16" "$MEDIA/SmartIR-HDR10-4K-HEVC.mp4" "yes"
make_hevc "1920x1080" "smpte2084" "16" "$MEDIA/SmartIR-HDR10-1080-HEVC.mp4" "yes"

if [[ ! -s "$MEDIA/SmartIR-HLG-4K-HEVC.ts" ]]; then
  ffmpeg -y -hide_banner -loglevel error \
    -i "$MEDIA/SmartIR-HLG-4K-HEVC.mp4" \
    -c copy -bsf:v hevc_mp4toannexb -f mpegts \
    "$MEDIA/SmartIR-HLG-4K-HEVC.ts"
fi

if [[ ! -s "$MEDIA/SmartIR-HDR10-4K-HEVC.ts" ]]; then
  ffmpeg -y -hide_banner -loglevel error \
    -i "$MEDIA/SmartIR-HDR10-4K-HEVC.mp4" \
    -c copy -bsf:v hevc_mp4toannexb -f mpegts \
    "$MEDIA/SmartIR-HDR10-4K-HEVC.ts"
fi

if has_encoder 'libvpx-vp9'; then
  if [[ ! -s "$MEDIA/SmartIR-HLG-4K-VP9.webm" ]]; then
    ffmpeg -y -hide_banner -loglevel error \
      -f lavfi -i "testsrc2=size=3840x2160:rate=${FPS},format=yuv420p10le" \
      -t "$DURATION" -an \
      -c:v libvpx-vp9 -deadline realtime -cpu-used 8 -row-mt 1 \
      -tile-columns 2 -frame-parallel 1 -b:v 5000k -maxrate 7000k -bufsize 14000k \
      -g 18 -pix_fmt yuv420p10le -profile:v 2 \
      -color_primaries bt2020 -color_trc arib-std-b67 -colorspace bt2020nc \
      "$MEDIA/SmartIR-HLG-4K-VP9.webm"
  fi

  if [[ ! -s "$MEDIA/SmartIR-HDR10-4K-VP9.webm" ]]; then
    ffmpeg -y -hide_banner -loglevel error \
      -f lavfi -i "testsrc2=size=3840x2160:rate=${FPS},format=yuv420p10le" \
      -t "$DURATION" -an \
      -c:v libvpx-vp9 -deadline realtime -cpu-used 8 -row-mt 1 \
      -tile-columns 2 -frame-parallel 1 -b:v 5000k -maxrate 7000k -bufsize 14000k \
      -g 18 -pix_fmt yuv420p10le -profile:v 2 \
      -color_primaries bt2020 -color_trc smpte2084 -colorspace bt2020nc \
      "$MEDIA/SmartIR-HDR10-4K-VP9.webm"
  fi
else
  echo "Hinweis: VP9-Encoder fehlt. HEVC-Varianten werden trotzdem gebaut."
fi

make_sdr "3840x2160" "6" "$MEDIA/SmartIR-SDR-4K-H264.mp4"
make_sdr "1920x1080" "12" "$MEDIA/SmartIR-SDR-1080-H264.mp4"

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
