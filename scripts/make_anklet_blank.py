#!/usr/bin/env python3
"""Generate the 32-pixel anklet blank from the original chain design."""
import json, struct, zlib
from pathlib import Path

assets = Path('src/main/resources/assets/alkahest')
size = 32
pixels = [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]
# Each ring has an open center, a near-black outline, and several cool iron highlights.
# Rings are drawn from top to bottom to give the links a coherent overlap.
link = [
    '..ddd..',
    '.dhhmd.',
    'dh...md',
    'dm...sd',
    '.dmmmd.',
    '..ddd..',
]
colors = {
    'd': (40, 47, 52, 255),
    's': (94, 110, 119, 255),
    'm': (158, 173, 179, 255),
    'h': (232, 240, 236, 255),
}

def draw_link(cx, cy, rotate=False):
    points = [(x-3, y-3, colors[ch]) for y,row in enumerate(link) for x,ch in enumerate(row) if ch != '.']
    for dx,dy,color in points:
        if rotate:
            dx,dy=-dy,dx
        x,y=cx+dx,cy+dy
        if 0 <= x < size and 0 <= y < size:
            pixels[y][x]=color

# Curved anklet; the upper two ends meet at the simple steel clasp.
for x,y,rot in [
    (10,6,False),(7,9,True),(6,13,True),(7,17,True),(9,21,False),
    (12,24,False),(16,25,False),(20,24,False),(23,21,False),
    (25,17,True),(26,13,True),(25,9,True),(22,6,False),
    (14,5,False),(18,5,False),
]: draw_link(x,y,rot)

def chunk(kind,data):
    return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
raw=b''.join(b'\0'+bytes(v for px in row for v in px) for row in pixels)
texture=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',size,size,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
(assets/'textures/item/anklet_blank.png').write_bytes(texture)
(assets/'models/item/anklet_blank.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'alkahest:item/anklet_blank'}},indent=2)+'\n')
