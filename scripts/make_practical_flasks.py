#!/usr/bin/env python3
"""Generate two 32x32 RGBA item icons with only the Python standard library."""
from pathlib import Path
import struct
import zlib
import json

ROOT = Path('src/main/resources/assets/alkahest')


def png(name, liquid, light, symbol):
    px = [[(0, 0, 0, 0) for _ in range(32)] for _ in range(32)]
    def dot(x, y, color):
        if 0 <= x < 32 and 0 <= y < 32: px[y][x] = color
    def box(x1, y1, x2, y2, color):
        for y in range(y1, y2 + 1):
            for x in range(x1, x2 + 1): dot(x, y, color)
    # Gold cork and shaded neck.
    box(12, 2, 19, 5, (100, 62, 40, 255))
    box(13, 2, 18, 3, (219, 174, 103, 255))
    box(13, 6, 18, 9, (99, 151, 151, 255))
    box(14, 6, 15, 8, (209, 242, 232, 255))
    # Stepped flask silhouette, dark glass, liquid and highlights.
    for y in range(9, 29):
        inset = 10 if y <= 10 else 8 if y <= 12 else 6 if y <= 15 else 5 if y <= 24 else 7
        for x in range(inset, 32 - inset):
            edge = x == inset or x == 31-inset or y == 28
            color = (160, 208, 204, 255) if edge else ((23, 53, 65, 255) if y < 17 else liquid)
            dot(x, y, color)
    box(8, 17, 23, 18, light)
    box(8, 14, 9, 23, (223, 249, 248, 230))
    box(10, 11, 12, 12, (223, 249, 248, 230))
    if symbol == 'snow':
        for d in range(-4, 5):
            dot(17+d, 22, (232, 255, 255, 255)); dot(17, 22+d, (232, 255, 255, 255))
        for dx,dy in [(-2,-2),(-1,-1),(1,1),(2,2),(-2,2),(-1,1),(1,-1),(2,-2)]:
            dot(17+dx,22+dy,(232,255,255,255))
    else:
        box(16, 19, 18, 25, (255, 249, 201, 255)); box(14, 21, 20, 23, (255, 249, 201, 255))
    raw = b''.join(b'\x00' + bytes(v for rgba in row for v in rgba) for row in px)
    def chunk(kind, data): return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB',32,32,8,6,0,0,0)) + chunk(b'IDAT', zlib.compress(raw,9)) + chunk(b'IEND',b'')
    path = ROOT / 'textures/item' / (name + '.png'); path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
    model = ROOT / 'models/item' / (name + '.json')
    model.write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'alkahest:item/'+name}}, indent=2) + '\n')

png('frost_flask', (24, 124, 177, 255), (94, 218, 243, 255), 'snow')
png('purifying_flask', (76, 160, 113, 255), (155, 236, 177, 255), 'cross')
