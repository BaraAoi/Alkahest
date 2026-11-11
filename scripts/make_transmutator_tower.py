#!/usr/bin/env python3
"""Generate the compact Transmutator's glass chamber and metallic crown, no external libraries."""
import json, struct, zlib
from pathlib import Path
A = Path('src/main/resources/assets/alkahest')

def png(path, pixels):
    raw = b''.join(b'\x00' + bytes(v for pixel in row for v in pixel) for row in pixels)
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))

def glass(lit):
    rows=[]
    for y in range(16):
        row=[]
        for x in range(16):
            color=(0,0,0,0)
            if x in (0,15) or y in (0,15): color=(53,104,114,255)
            elif x in (1,14) or y in (1,14): color=(119,188,194,255)
            elif (x == 3 and 3 <= y <= 11) or (x == 4 and 3 <= y <= 7) or (x == 12 and 9 <= y <= 12): color=(190,232,222,255)
            elif (x+y)%9==0: color=(66,125,129,255)
            row.append(color)
        rows.append(row)
    return rows

def gem(lit):
    p=[]
    for y in range(16):
        row=[]
        for x in range(16):
            edge=abs(x-7.5)+abs(y-7.5)
            if edge > 7: col=(37,64,68,255)
            elif edge > 5: col=(189,127,52,255) if lit else (57,118,129,255)
            elif x<7 and y<8: col=(255,235,149,255) if lit else (170,232,225,255)
            else: col=(217,158,56,255) if lit else (91,176,178,255)
            if 5<=x<=7 and 4<=y<=6: col=(255,250,219,255)
            row.append(col)
        p.append(row)
    return p
def metal(brass):
    rows=[]
    for y in range(16):
        row=[]
        for x in range(16):
            n=((x*17+y*31+x*y*3)%9)-4
            if brass:
                base=(141,99,51) if y>8 else (178,133,71)
                if y in (0,15): base=(70,48,28)
                elif y in (1,14): base=(220,174,101)
            else:
                base=(115,69,40) if y>8 else (155,95,55)
                if y in (0,15): base=(54,36,26)
                elif y in (1,14): base=(197,137,83)
            if x in (2,13) and y in (3,12): base=(233,199,140)
            row.append(tuple(max(0,min(255,c+n)) for c in base)+(255,))
        rows.append(row)
    return rows

def quartz():
    rows=[]
    for y in range(16):
        row=[]
        for x in range(16):
            n=((x*19+y*29+x*y)%7)-3
            base=(214,211,196)
            if x in (0,15) or y in (0,15): base=(129,117,99)
            elif x in (1,14) or y in (1,14): base=(237,231,212)
            elif (x==y or x+y==15) and 4<=x<=11: base=(180,168,144)
            row.append(tuple(max(0,min(255,c+n)) for c in base)+(255,))
        rows.append(row)
    return rows

png(A/'textures/block/transmutator_tower_copper.png', metal(False))
png(A/'textures/block/transmutator_tower_brass.png', metal(True))
png(A/'textures/block/transmutator_tower_quartz.png', quartz())

for active in (False, True):
    suffix='_awake' if active else ''
    png(A/'textures/block'/('transmutator_tower_glass'+suffix+'.png'), glass(active))
    png(A/'textures/block'/('transmutator_tower_gem'+suffix+'.png'), gem(active))

def element(a,b,tex):
    return {'from':a,'to':b,'faces':{face:{'texture':'#'+tex} for face in ('up','down','north','south','east','west')}}
def model(name, active):
    suffix='_awake' if active else ''
    textures={'metal':'alkahest:block/transmutator_tower_copper',
              'brass':'alkahest:block/transmutator_tower_brass',
              'quartz':'alkahest:block/transmutator_tower_quartz',
              'glass':'alkahest:block/transmutator_tower_glass'+suffix,
              'gem':'alkahest:block/transmutator_tower_gem'+suffix,
              'particle':'alkahest:block/transmutator_tower_copper'}
    if name=='transmutator_chamber':
        # The square instrument is supported by low quartz feet and a copper cage.
        # Its centre remains transparent so the compact moving rings can be seen.
        parts=[element([1,0,1],[15,2,15],'metal'), element([2,2,2],[14,3,14],'brass'),
               element([3,3,3],[13,13,13],'glass'), element([6,5,6],[10,11,10],'gem'),
               element([2,13,2],[14,14,14],'brass'), element([1,14,1],[15,16,15],'metal')]
        for x in (1,12):
            for z in (1,12):
                parts.append(element([x,2,z],[x+3,5,z+3],'quartz'))
                parts.append(element([x+1,5,z+1],[x+2,13,z+2],'metal'))
                parts.append(element([x,12,z],[x+3,14,z+3],'brass'))
    else:
        # A grounded machined crown rather than another floating pyramid.
        parts=[element([1,0,1],[15,2,15],'metal'), element([2,2,2],[14,3,14],'brass'),
               element([3,3,3],[13,6,13],'quartz'), element([3,6,3],[13,8,13],'metal'),
               element([5,8,5],[11,9,11],'brass'), element([6,9,6],[10,12,10],'gem'),
               element([7,12,7],[9,14,9],'brass')]
        for x in (1,12):
            for z in (1,12): parts.append(element([x,2,z],[x+3,5,z+3],'metal'))
    return {'parent':'minecraft:block/block','textures':textures,'elements':parts}
for name in ('transmutator_chamber','transmutator_crown'):
    for active in (False,True):
        suffix='_awake' if active else ''
        (A/'models/block'/f'{name}{suffix}.json').write_text(json.dumps(model(name,active),indent=2)+'\n')
    (A/'blockstates'/f'{name}.json').write_text(json.dumps({'variants':{'awake=false':{'model':'alkahest:block/'+name}, 'awake=true':{'model':'alkahest:block/'+name+'_awake'}}},indent=2)+'\n')
    (A/'models/item'/f'{name}.json').write_text(json.dumps({'parent':'alkahest:block/'+name},indent=2)+'\n')
print('compact transmutator assets written')
