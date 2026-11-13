#!/usr/bin/env python3
"""Draw the necklace blank: a subtle cord and a clearly empty metal setting."""
import json, struct, zlib
from pathlib import Path

assets = Path('src/main/resources/assets/alkahest')
size = 32
pixels = [[(0,0,0,0) for _ in range(size)] for _ in range(size)]

def dot(x,y,color):
    if 0 <= x < size and 0 <= y < size: pixels[y][x] = color

def line(points,color):
    for (x0,y0),(x1,y1) in zip(points,points[1:]):
        dx,dy=abs(x1-x0),-abs(y1-y0)
        sx,sy=(1 if x0<x1 else -1),(1 if y0<y1 else -1)
        err=dx+dy
        while True:
            dot(x0,y0,color)
            if x0==x1 and y0==y1: break
            e=2*err
            if e>=dy: err+=dy; x0+=sx
            if e<=dx: err+=dx; y0+=sy

# A single-pixel, muted cord, deliberately subtler than the socket.
cord=(151,139,117,185)
line([(5,4),(5,9),(7,13),(10,16),(13,18)],cord)
line([(27,4),(27,9),(25,13),(22,16),(19,18)],cord)
# Socket: a raised bevel around a genuinely transparent, empty centre.
for y in range(16,31):
    for x in range(9,24):
        dx,dy=x-16,y-23
        d=dx*dx+dy*dy
        if 22 <= d <= 42:
            if d >= 35: color=(56,62,67,255)
            elif dx+dy < -2: color=(224,228,216,255)
            elif dx+dy > 4: color=(111,123,127,255)
            else: color=(165,177,177,255)
            dot(x,y,color)
# Small hanger at the top of the socket, no stone or coloured insert.
for x,y,col in [(15,16,(92,102,105,255)),(16,15,(188,197,192,255)),
                (17,16,(92,102,105,255)),(16,17,(221,228,221,255))]: dot(x,y,col)

def chunk(kind,data):
    return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
raw=b''.join(b'\0'+bytes(v for px in row for v in px) for row in pixels)
texture=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',size,size,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
(assets/'textures/item/iron_chain.png').write_bytes(texture)
(assets/'models/item/iron_chain.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'alkahest:item/iron_chain'}},indent=2)+'\n')
