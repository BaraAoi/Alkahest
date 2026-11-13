#!/usr/bin/env python3
"""Generate the small iron necklace crafting-material icon."""
import json, struct, zlib
from pathlib import Path

assets = Path('src/main/resources/assets/alkahest')
# Dark steel rims and bright, offset links make a chain rather than an amulet.
pattern = [
    '................','................','...DD......DD...',
    '..DHLD....DLHD..','..DHLD....DLHD..','..DHLD....DLHD..',
    '...DHLD..DLHD...','...DHLDDDLHD....','....DHLDLHD.....',
    '....DHLDLHD.....','.....DHLHD......','......DHD.......',
    '.......D........','................','................','................',
]
assert all(len(row) == 16 for row in pattern)
colors = {'.':(0,0,0,0), 'D':(57,62,68,255), 'L':(143,152,159,255), 'H':(222,228,229,255)}
def chunk(kind,data):
    return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
raw=b''.join(b'\0'+bytes(v for char in row for v in colors[char]) for row in pattern)
texture=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
(assets/'textures/item/iron_chain.png').write_bytes(texture)
(assets/'models/item/iron_chain.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'alkahest:item/iron_chain'}},indent=2)+'\n')
