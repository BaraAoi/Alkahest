#!/usr/bin/env python3
"""Generate a faceted, solid 3D Alkahest focus using only Python's standard library."""
import json, struct, zlib
from pathlib import Path
A = Path('src/main/resources/assets/alkahest')

def png(path, colors):
    rows=[]
    for y in range(16):
        row=[]
        for x in range(16):
            base=colors[0] if x+y < 13 else colors[1]
            if x in (0,15) or y in (0,15): base=colors[2]
            if x==y and 2 < x < 8: base=colors[3]
            row.extend((*base,255))
        rows.append(b'\0'+bytes(row))
    def chunk(kind, data):
        return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows),9))+chunk(b'IEND',b''))

png(A/'textures/item/amulet_gold_3d.png', [(233,193,98),(165,111,42),(95,58,28),(255,229,151)])
png(A/'textures/item/amulet_alka_3d.png', [(108,237,223),(28,143,151),(16,77,89),(209,255,242)])
png(A/'textures/item/amulet_dark_3d.png', [(60,98,91),(34,61,59),(22,42,42),(91,143,124)])

def cuboid(a,b,material):
    return {'from':a,'to':b,'faces':{f:{'texture':'#'+material} for f in ('down','up','north','south','west','east')}}
# Layered settings on both sides, so it remains a physical focus from every angle.
parts=[
    cuboid([4,3,6],[12,13,10],'gold'),
    cuboid([3,4,6],[13,12,10],'gold'),
    cuboid([5,4,5],[11,12,11],'dark'),
    cuboid([4,5,5],[12,11,11],'dark'),
    cuboid([5,5,4.5],[11,11,11.5],'gem'),
    cuboid([6,4,4.75],[10,5,11.25],'gold'),
    cuboid([6,11,4.75],[10,12,11.25],'gold'),
    cuboid([4,6,4.75],[5,10,11.25],'gold'),
    cuboid([11,6,4.75],[12,10,11.25],'gold'),
    cuboid([6,6,4],[10,10,5],'gem'),
    cuboid([6,6,11],[10,10,12],'gem'),
    cuboid([7,7,3.75],[9,9,4.25],'gold'),
    cuboid([7,7,11.75],[9,9,12.25],'gold'),
    cuboid([7,12,6.5],[9,14,9.5],'gold'),
    cuboid([7,2,6.5],[9,4,9.5],'gold'),
]
model={'parent':'minecraft:block/block','textures':{'particle':'alkahest:item/amulet_gold_3d','gold':'alkahest:item/amulet_gold_3d','gem':'alkahest:item/amulet_alka_3d','dark':'alkahest:item/amulet_dark_3d'},'elements':parts,
       'display':{'gui':{'rotation':[20,35,0],'scale':[.9,.9,.9]},'ground':{'translation':[0,2,0],'scale':[.5,.5,.5]},'thirdperson_righthand':{'rotation':[0,90,0],'scale':[.5,.5,.5]},'firstperson_righthand':{'rotation':[0,-45,0],'scale':[.65,.65,.65]},'firstperson_lefthand':{'rotation':[0,45,0],'scale':[.65,.65,.65]}}}
(A/'models/item/alkahest_amulet.json').write_text(json.dumps(model,indent=2)+'\n')
print('3D amulet generated')
