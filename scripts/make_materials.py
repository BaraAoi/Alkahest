#!/usr/bin/env python3
"""Deterministic 16px sprites for intermediate materials, without external libraries."""
import json, struct, zlib
from pathlib import Path
A=Path('src/main/resources/assets/alkahest')
def png(rows):
    def chunk(kind,data): return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    raw=b''.join(b'\0'+bytes(c for px in row for c in px) for row in rows)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
def draw(name, pattern, palette):
    assert len(pattern)==16 and all(len(row)==16 for row in pattern)
    pixels=[[(*palette[ch],255) if ch in palette else (0,0,0,0) for ch in row] for row in pattern]
    (A/'textures/item'/f'{name}.png').write_bytes(png(pixels))
    (A/'models/item'/f'{name}.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':f'alkahest:item/{name}'}},indent=2)+'\n')
cloth=['................','................','.....dddddd.....','....dlllmmmd....','...dlmmllmmd....','..dlmmllmmd.....','..dmlmmllld.....','..dmllmmllmd....','..dlmllmmlmd....','..dmmllmllmd....','...dmmllmllmd...','....dmllmmlmd...','.....dmmlmmd....','......dddddd....','................','................']
draw('cloth',cloth,{'d':(93,83,72),'m':(180,167,143),'l':(223,214,191)})
draw('spirit_cloth',cloth,{'d':(95,83,137),'m':(184,175,224),'l':(236,234,252)})
draw('stabilizing_reagent',['................','................','......dddd......','.....dllllD.....','....dlllllDd....','...dlllllDmmD...','..dlllllDmmmmD..','..dllllDmmmmmd..','..dlllDmmmum.md.','..dllllDmmum.md.','...dllllDmmmmD..','....ddlllDmmD...','......ddddDD....','................','................','................'],{'d':(75,63,60),'D':(119,96,71),'l':(231,218,167),'m':(181,145,83),'u':(255,246,211)})
draw('stable_material',['................','................','.........ddd....','......dddllld...','...dddrraallld..','..drrrraaaallld.','.drrrrtaaaoollld','.drrrtttaaooolld','.daattzzaaoooold','.daattzzppoooold','..dtttzzppppood.','...dttzzppppod..','....dzzpppddd...','.....ddddd......','................','................'],{'d':(51,49,64),'l':(219,231,230),'r':(220,68,47),'a':(100,192,218),'t':(155,112,71),'z':(221,238,249),'o':(225,191,61),'p':(119,65,166)})
