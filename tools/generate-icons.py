#!/usr/bin/env python3
"""Generate the extension icons (16/32/48/128 px) with the Python standard
library only. Design: #1A7F4B square with a white speech bubble.

    python3 tools/generate-icons.py

Writes extension/icons/icon{16,32,48,128}.png.
"""
import os
import struct
import zlib

GREEN = (0x1A, 0x7F, 0x4B, 255)
WHITE = (255, 255, 255, 255)
CLEAR = (0, 0, 0, 0)

def draw(size):
    s = size / 128.0
    px = [[CLEAR] * size for _ in range(size)]
    # opaque green square: x/y 21..106 at 128px, transparent margin outside
    g0, g1 = round(21 * s), round(106 * s)
    for y in range(g0, g1 + 1):
        for x in range(g0, g1 + 1):
            if 0 <= y < size and 0 <= x < size:
                px[y][x] = GREEN
    # bubble body: x 41..87, y 33..79 at 128px
    x0, x1 = round(41 * s), round(87 * s)
    y0, y1 = round(33 * s), round(79 * s)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if 0 <= y < size and 0 <= x < size:
                px[y][x] = WHITE
    # tail: rows below the body, tapering to a point at the center bottom
    ty0, ty1 = y1 + 1, round(95 * s)
    cx = round(64 * s)
    half = max(round(5 * s), 1)          # half-width at the top of the tail
    span = max(ty1 - ty0, 1)
    for y in range(ty0, ty1 + 1):
        t = (y - ty0) / span
        h = max(round(half * (1 - t)), 0)
        for x in range(cx - h, cx + h + 1):
            if 0 <= y < size and 0 <= x < size:
                px[y][x] = WHITE
    return px

def write_png(path, px):
    size = len(px)
    raw = bytearray()
    for row in px:
        raw.append(0)  # filter type 0
        for (r, g, b, a) in row:
            raw += bytes((r, g, b, a))
    def chunk(typ, data):
        c = struct.pack(">I", len(data)) + typ + data
        return c + struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)

def main():
    os.makedirs("extension/icons", exist_ok=True)
    for size in (16, 32, 48, 128):
        write_png(f"extension/icons/icon{size}.png", draw(size))
        print(f"icon{size}.png written")
    # Android launcher icons: the same design at 128px in each density
    # bucket (the manifest references @mipmap/ic_launcher).
    for density in ("hdpi", "xhdpi", "xxhdpi"):
        d = f"android-source/app/src/main/res/mipmap-{density}"
        os.makedirs(d, exist_ok=True)
        write_png(f"{d}/ic_launcher.png", draw(128))
        print(f"mipmap-{density}/ic_launcher.png written")

if __name__ == "__main__":
    main()
