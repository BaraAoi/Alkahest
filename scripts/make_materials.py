#!/usr/bin/env python3
"""Deterministic 16px sprites for intermediate materials, without external libraries."""
import json, math, struct, zlib
from pathlib import Path
A=Path('src/main/resources/assets/alkahest')
def png(rows):
    def chunk(kind,data): return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    raw=b''.join(b'\0'+bytes(c for px in row for c in px) for row in rows)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,len(rows),8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
def draw(name, pattern, palette):
    assert len(pattern)==16 and all(len(row)==16 for row in pattern)
    pixels=[[(*palette[ch],255) if ch in palette else (0,0,0,0) for ch in row] for row in pattern]
    (A/'textures/item'/f'{name}.png').write_bytes(png(pixels))
    (A/'models/item'/f'{name}.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':f'alkahest:item/{name}'}},indent=2)+'\n')
cloth=['................','................','.....dddddd.....','....dlllmmmd....','...dlmmllmmd....','..dlmmllmmd.....','..dmlmmllld.....','..dmllmmllmd....','..dlmllmmlmd....','..dmmllmllmd....','...dmmllmllmd...','....dmllmmlmd...','.....dmmlmmd....','......dddddd....','................','................']
draw('cloth',cloth,{'d':(93,83,72),'m':(180,167,143),'l':(223,214,191)})
draw('spirit_cloth',cloth,{'d':(95,83,137),'m':(184,175,224),'l':(236,234,252)})
draw('stabilizing_reagent',['................','................','......dddd......','.....dllllD.....','....dlllllDd....','...dlllllDmmD...','..dlllllDmmmmD..','..dllllDmmmmmd..','..dlllDmmmum.md.','..dllllDmmum.md.','...dllllDmmmmD..','....ddlllDmmD...','......ddddDD....','................','................','................'],{'d':(75,63,60),'D':(119,96,71),'l':(231,218,167),'m':(181,145,83),'u':(255,246,211)})
# A polished, white alchemical ingot. The silhouette stays fixed; a soft highlight
# glides over the surface without changing the six-essence gameplay composition.
ingot = [
    '................',
    '................',
    '..........dd....',
    '.......dddhhd...',
    '....dddhwwwwwd..',
    '..ddhhwwwfffwwd.',
    '.dhhwwwfffffwhwd',
    '.dhwwwfffwwhhfmd',
    '.dhhwwwwhhhfmmsd',
    '.dmhhhhhfmmmsssd',
    '.dmmmfmmmmmmssd.',
    '..dmmfmmmmddd...',
    '...dmssddd......',
    '....ddd.........',
    '................',
    '................',
]
assert len(ingot)==16 and all(len(row)==16 for row in ingot)
palette = {'d': (107, 116, 126), 's': (158, 169, 180), 'm': (191, 201, 209),
           'f': (224, 232, 237), 'w': (242, 247, 249), 'h': (254, 255, 255)}
frames=[]
for frame in range(24):
    rows=[]
    for y, pattern_row in enumerate(ingot):
        row=[]
        for x, ch in enumerate(pattern_row):
            if ch == '.': row.append((0,0,0,0)); continue
            rgb=palette[ch]
            # A very subtle 24-frame sheen, visible on the top plane only.
            if ch in 'fw':
                phase = 2*math.pi*(frame/24 - (x+y)/24)
                amount = max(0, math.cos(phase)) * 8
                rgb=tuple(min(255, round(v+amount)) for v in rgb)
            row.append((*rgb,255))
        rows.append(row)
    frames.extend(rows)
(A/'textures/item/stable_material.png').write_bytes(png(frames))
(A/'textures/item/stable_material.png.mcmeta').write_text(json.dumps({'animation': {'frametime': 4, 'interpolate': True}},indent=2)+'\n')
(A/'models/item/stable_material.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'alkahest:item/stable_material'}},indent=2)+'\n')
