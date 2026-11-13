#!/usr/bin/env python3
"""Pixel art for ring blanks and enchanted rings."""
import json,struct,zlib
from pathlib import Path
A=Path('src/main/resources/assets/alkahest')
def write(name, rows, colors):
    assert len(rows)==16 and all(len(r)==16 for r in rows)
    raw=b''.join(b'\0'+bytes(v for k in row for v in ((*colors[k],255) if k in colors else (0,0,0,0))) for row in rows)
    def c(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
    png=b'\x89PNG\r\n\x1a\n'+c(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+c(b'IDAT',zlib.compress(raw,9))+c(b'IEND',b'')
    (A/'textures/item'/f'{name}.png').write_bytes(png)
    (A/'models/item'/f'{name}.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':f'alkahest:item/{name}'}},indent=2)+'\n')
base=['................','................','.....dddddd.....','...ddggllggdd...','..dgllgddgggd...','.dglgd....dggd..','.dggd......dgd..','.dggd......dgd..','.dggd......dgd..','.dggd......dgd..','..dggd....dgd...','...dggddddgd....','....ddggggd.....','......dddd......','................','................']
terra=['................','.....ddddd......','....dttlltd.....','...dtllllttd....','..dgtlltttgd....','.dglgddddgggd...','.dggd....dggd...','.dggd.....dgd...','.dggd.....dgd...','.dggd.....dgd...','..dggd...dgd....','...dggddggd.....','....ddgggd......','......ddd.......','................','................']
colors={'d':(83,59,35),'g':(191,143,58),'l':(255,226,141),'t':(156,99,51)}
write('ring_blank',base,colors)
write('ring_terra',terra,colors)

shield=['................','.....ddddd......','....dmmhhmd.....','...dmmhhhhmd....','..dgmhmmhmgd....','.dglgmhmmgggd...','.dggddmmddggd...','.dggd.dd..dgd...','.dggd.....dgd...','.dggd.....dgd...','..dggd...dgd....','...dggddggd.....','....ddgggd......','......ddd.......','................','................']
shield_colors={**colors, 'm':(173,185,206), 'h':(238,246,255)}
write('ring_shield',shield,shield_colors)

# Gems and small inlaid level marks distinguish upgrades at inventory size.
def marked(rows, count):
    out=rows.copy()
    positions={2:(6,8),3:(5,7,9),4:(4,6,8,10)}[count]
    line=list(out[14])
    for x in positions: line[x]='v'
    out[14]=''.join(line)
    return out
write('ring_terra_2', marked(terra,2), {**colors,'t':(178,111,49),'l':(255,236,168),'v':(228,183,84)})
for level,gem,light in [(2,(123,185,205),(218,249,255)),(3,(155,155,214),(237,232,255)),(4,(201,182,117),(255,252,216))]:
    write(f'ring_shield_{level}',marked(shield,level),{**colors,'m':gem,'h':light,'v':(235,205,125)})
