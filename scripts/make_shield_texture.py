#!/usr/bin/env python3
"""A subtle translucent cyan energy shell. Wrapped horizontally to avoid a UV seam."""
import math, struct, zlib
from pathlib import Path
size=128
rows=[]
for y in range(size):
    row=[]
    v=y/size
    for x in range(size):
        u=x/size
        # Three seamless, offset energy ribbons and a faint cellular lattice.
        wave=0.0
        for band in (0.22,0.50,0.78):
            center=band + .027*math.sin(2*math.pi*(u*3 + band))
            wave=max(wave, math.exp(-((v-center)/.008)**2))
        lattice=(math.sin(2*math.pi*u*8)*math.sin(2*math.pi*v*8))**12
        pole=min(1, max(0, min(v,1-v)*12))
        alpha=round((8 + 27*wave + 10*lattice)*pole)
        brightness=round(12*wave + 6*lattice)
        row.append((160+brightness, 221+brightness, 255, alpha))
    rows.append(row)
def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
raw=b''.join(b'\0'+bytes(c for pixel in row for c in pixel) for row in rows)
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',size,size,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
path=Path('src/client/resources/assets/alkahest/textures/effect/shield_shell.png')
path.parent.mkdir(parents=True,exist_ok=True)
path.write_bytes(png)
