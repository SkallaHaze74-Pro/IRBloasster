#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/webos-soundcloud"

python - "$APP" <<'PY'
import binascii
import math
import pathlib
import struct
import sys
import wave
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

def write_background_test(path):
    sample_rate = 16000
    duration = 45
    note_length = 1.5
    notes = [261.63, 329.63, 392.00, 523.25, 392.00, 329.63]
    frames = bytearray()

    for index in range(sample_rate * duration):
        time = index / sample_rate
        frequency = notes[int(time / note_length) % len(notes)]
        local = (time % note_length) / note_length
        envelope = max(0.0, min(1.0, local / 0.12, (1.0 - local) / 0.18))
        global_fade = max(0.0, min(1.0, time / 0.8, (duration - time) / 1.5))
        sample = (
            0.48 * math.sin(2 * math.pi * frequency * time)
            + 0.22 * math.sin(2 * math.pi * frequency * 0.5 * time)
            + 0.12 * math.sin(2 * math.pi * frequency * 1.5 * time)
        )
        sample *= 0.83 + 0.17 * math.sin(2 * math.pi * 0.22 * time)
        value = int(max(-1.0, min(1.0, sample * 0.24 * envelope * global_fade)) * 32767)
        frames.extend(struct.pack('<h', value))

    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), 'wb') as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(sample_rate)
        audio.writeframes(frames)

write_icon(root / "icon.png", 80)
write_icon(root / "largeicon.png", 130)
write_background_test(root / "media" / "lg-background-test.wav")
PY

cd "$ROOT"
rm -f com.skallahaze.soundcloudtv_*.ipk
ares-package webos-soundcloud
