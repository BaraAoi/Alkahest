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
# Equal RGB mix of the actual six primal EssenceType colours (A68072).
# Twenty-four subtle connected silhouettes form a cyclic, continuously morphing blob.
primal = (0xFF4500, 0x1E90FF, 0x8B5A2B, 0xF0F8FF, 0xFFD700, 0x4B0082)
blend = tuple(round(sum((color >> shift) & 255 for color in primal) / len(primal)) for shift in (16, 8, 0))
frames=[]
for frame in range(24):
    phase=2*math.pi*frame/24
    mask=set()
    for y in range(16):
        for x in range(16):
            dx=(x-7.5)/1.08; dy=(y-7.5)/.92
            angle=math.atan2(dy,dx)
            radius=5.0 + .85*math.sin(3*angle+phase) + .55*math.sin(2*angle-phase)
            if math.hypot(dx,dy) <= radius: mask.add((x,y))
    rows=[]
    for y in range(16):
        row=[]
        for x in range(16):
            if (x,y) not in mask: row.append((0,0,0,0)); continue
            edge=any((x+dx,y+dy) not in mask for dx,dy in ((-1,0),(1,0),(0,-1),(0,1)))
            shade=.60 if edge else 1.12 if x+y < 12 else 1.0 if x+y < 19 else .86
            row.append((*tuple(min(255,round(c*shade)) for c in blend),255))
        rows.append(row)
    frames.extend(rows)
(A/'textures/item/stable_material.png').write_bytes(png(frames))
(A/'textures/item/stable_material.png.mcmeta').write_text(json.dumps({'animation': {'frametime': 4, 'interpolate': True}},indent=2)+'\n')
(A/'models/item/stable_material.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'alkahest:item/stable_material'}},indent=2)+'\n')
