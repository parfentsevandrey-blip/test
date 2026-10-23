#!/usr/bin/env python3
"""Пишет тестовые субтитры Blu-ray (.sup, HDMV PGS) для самопроверки Lumen в CI.

Две реплики-картинки на холсте 1920×1080: «плашки» с полосами (имитация строки текста) появляются
на 1-й и 4-й секунде и гаснут на 3-й и 6-й. ffmpeg PGS не кодирует, поэтому поток собираем сами:
сегменты PCS, WDS, PDS, ODS (RLE) и END — ровно как на диске.
"""
import struct
import sys

WIDTH, HEIGHT = 1920, 1080


def segment(kind, pts, payload):
    return b"PG" + struct.pack(">IIBH", int(pts * 90000), 0, kind, len(payload)) + payload


def rle(pixels, width):
    out = bytearray()
    for row in range(len(pixels) // width):
        line = pixels[row * width:(row + 1) * width]
        i = 0
        while i < width:
            color = line[i]
            run = 1
            while i + run < width and line[i + run] == color and run < 16383:
                run += 1
            if color == 0:
                out += bytes([0, run]) if run < 64 else bytes([0, 0x40 | (run >> 8), run & 0xFF])
            elif run < 3:
                out += bytes([color]) * run
            elif run < 64:
                out += bytes([0, 0x80 | run, color])
            else:
                out += bytes([0, 0xC0 | (run >> 8), run & 0xFF, color])
            i += run
        out += b"\x00\x00"
    return bytes(out)


def bitmap(seed):
    w, h = 640, 72
    px = bytearray(w * h)
    for y in range(h):
        for x in range(w):
            edge = x < 6 or x >= w - 6 or y < 6 or y >= h - 6
            # «буквы»: вертикальные штрихи разной ширины внутри рамки
            stroke = 18 <= y < h - 18 and ((x // 9 + seed) % 3 != 0) and 24 <= x < w - 24
            px[y * w + x] = 2 if edge else (1 if stroke else 0)
    return w, h, bytes(px)


def display_set(pts, number, show, seed):
    x, y = (WIDTH - 640) // 2, HEIGHT - 72 - 80
    objects = 1 if show else 0
    pcs = struct.pack(">HHBHBBBB", WIDTH, HEIGHT, 0x10, number, 0x80 if show else 0x00, 0, 0, objects)
    if show:
        pcs += struct.pack(">HBBHH", 0, 0, 0, x, y)
    data = segment(0x16, pts, pcs)
    data += segment(0x17, pts, struct.pack(">BBHHHH", 1, 0, x, y, 640, 72))
    if show:
        # Палитра: 0 — прозрачный, 1 — белый, 2 — серая рамка (Y, Cr, Cb, A)
        palette = struct.pack(">BB", 0, 0) + bytes([0, 16, 128, 128, 0, 1, 235, 128, 128, 255, 2, 120, 128, 128, 200])
        data += segment(0x14, pts, palette)
        w, h, px = bitmap(seed)
        encoded = rle(px, w)
        ods = struct.pack(">HBB", 0, 0, 0xC0) + (len(encoded) + 4).to_bytes(3, "big") + struct.pack(">HH", w, h) + encoded
        data += segment(0x15, pts, ods)
    data += segment(0x80, pts, b"")
    return data


def main(path):
    stream = b""
    for i, (start, end) in enumerate([(1.0, 3.0), (4.0, 6.0)]):
        stream += display_set(start, i * 2, True, i)
        stream += display_set(end, i * 2 + 1, False, i)
    with open(path, "wb") as f:
        f.write(stream)


if __name__ == "__main__":
    main(sys.argv[1])
