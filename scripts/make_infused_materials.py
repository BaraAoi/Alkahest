#!/usr/bin/env python3
"""Build transparent 16px elemental seals over vanilla material textures."""
import json, struct, zlib
from pathlib import Path

assets = Path('src/main/resources/assets/alkahest')
# Input, seal colour, light, and unique pixel rune. Vanilla texture remains visible beneath.
materials = {
    'ignis': ('blaze_powder', (210, 45, 8), (255, 218, 92), ['..X..','.X.X.','.X.X.','XX.XX','XXXXX','.XXX.','..X..']),
    'aqua': ('lapis_lazuli', (0, 83, 165), (148, 241, 255), ['..X..','..X..','.XXX.','.XXX.','XXXXX','.XXX.','..X..']),
    'terra': ('iron_ingot', (95, 51, 21), (221, 177, 106), ['..X..','.XXX.','XXXXX','XX.XX','XXXXX','.XXX.','..X..']),
    'aer': ('diamond', (45, 123, 153), (235, 255, 255), ['...X.','.XX.X','X....','.XXX.','....X','X.XX.','.X...']),
    'ordo': ('gold_ingot', (144, 91, 12), (255, 255, 192), ['..X..','.X.X.','X...X','X.X.X','X...X','.X.X.','..X..']),
    'perditio': ('amethyst_shard', (44, 4, 76), (237, 144, 255), ['X...X','.X.X.','..X..','...X.','..X..','.X.X.','X...X']),
}

def png(rows):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    raw = b''.join(b'\0' + bytes(channel for pixel in row for channel in pixel) for row in rows)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')

for name, (base, dark, light, glyph) in materials.items():
    rows = [[(0,0,0,0) for _ in range(16)] for _ in range(16)]
    dots = {(x + 5, y + 4) for y, line in enumerate(glyph) for x, char in enumerate(line) if char == 'X'}
    for x,y in dots:
        for dx,dy in ((0,-1),(0,1),(-1,0),(1,0)):
            xx,yy=x+dx,y+dy
            if (xx,yy) not in dots: rows[yy][xx]=(*dark, 205)
    for x,y in dots: rows[y][x]=(*light,255)
    (assets / 'textures/item' / f'infused_{name}.png').write_bytes(png(rows))
    model = {'parent':'minecraft:item/generated','textures': {'layer0':f'minecraft:item/{base}', 'layer1':f'alkahest:item/infused_{name}'}}
    (assets / 'models/item' / f'infused_{name}.json').write_text(json.dumps(model, indent=2)+'\n')
