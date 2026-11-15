#!/usr/bin/env python3
"""Generate the hand-drawn pixel bow stages and a softly edged wind ribbon texture."""
import json, math, struct, zlib
from pathlib import Path
root=Path('src/main/resources/assets/alkahest')
def png(path,w,h,pixels):
 def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
 raw=b''.join(b'\0'+bytes(x for pixel in row for x in pixel) for row in pixels)
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
def make(stage):
 p=[[(0,0,0,0) for x in range(32)] for y in range(32)]
 def dot(x,y,c,r=0):
  for yy in range(y-r,y+r+1):
   for xx in range(x-r,x+r+1):
    if 0<=xx<32 and 0<=yy<32: p[yy][xx]=c
 def line(a,b,c,r=0):
  x1,y1=a;x2,y2=b;n=max(abs(x2-x1),abs(y2-y1),1)
  for i in range(n+1): dot(round(x1+(x2-x1)*i/n),round(y1+(y2-y1)*i/n),c,r)
 # Metallic pale-blue recurved bow, viewed like a vanilla bow.
 path=[(5,28),(4,24),(5,19),(8,14),(12,10),(17,7),(22,6),(27,7)]
 for a,b in zip(path,path[1:]): line(a,b,(27,64,83,255),2);line(a,b,(105,184,207,255),1);line(a,b,(209,246,249,255),0)
 line((5,28),(8+stage*2,21-stage*2),(209,247,255,255))
 line((27,7),(8+stage*2,21-stage*2),(209,247,255,255))
 line((5,27),(8,28),(244,220,160,255),1);line((26,6),(28,9),(244,220,160,255),1)
 dot(10,14,(255,246,199,255),2);dot(10,14,(130,236,247,255),1);dot(10,14,(255,255,255,255))
 if stage:
  line((8+stage*2,21-stage*2),(21,8),(120,226,246,170),1)
  line((9+stage*2,20-stage*2),(21,8),(243,255,255,255))
 png(root/f'textures/item/bow_aer_{stage}.png',32,32,p)
for stage in range(4): make(stage)
w=h=64;p=[]
for y in range(h):
 row=[]
 for x in range(w):
  u=x/(w-1);v=y/(h-1);fade=math.sin(math.pi*u)**.45
  edge=math.sin(math.pi*v)**1.7
  alpha=round(150*fade*edge)
  row.append((225,250,255,alpha))
 p.append(row)
png(root/'textures/effect/wind_ribbon.png',w,h,p)
models=root/'models/item';models.mkdir(parents=True,exist_ok=True)
for i in range(1,4): (models/f'bow_aer_pulling_{i-1}.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':f'alkahest:item/bow_aer_{i}'}},indent=2)+'\n')
model={'parent':'minecraft:item/generated','textures':{'layer0':'alkahest:item/bow_aer_0'},'overrides':[
 {'predicate':{'pulling':1},'model':'alkahest:item/bow_aer_pulling_0'},
 {'predicate':{'pulling':1,'pull':.65},'model':'alkahest:item/bow_aer_pulling_1'},
 {'predicate':{'pulling':1,'pull':.9},'model':'alkahest:item/bow_aer_pulling_2'}]}
(models/'bow_aer.json').write_text(json.dumps(model,indent=2)+'\n')
