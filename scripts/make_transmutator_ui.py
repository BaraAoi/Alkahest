#!/usr/bin/env python3
"""Draw a copper, quartz and glass Transmutator panel using only struct and zlib."""
import struct, zlib
from pathlib import Path
W,H=236,220
p=[[(0,0,0,255) for x in range(W)] for y in range(H)]

def fill(x0,y0,x1,y1,col):
    for y in range(max(0,y0),min(H,y1)):
        for x in range(max(0,x0),min(W,x1)): p[y][x]=(*col,255)
def frame(x0,y0,x1,y1,col):
    fill(x0,y0,x1,y0+1,col); fill(x0,y1-1,x1,y1,col)
    fill(x0,y0,x0+1,y1,col); fill(x1-1,y0,x1,y1,col)
def panel(x0,y0,x1,y1):
    fill(x0,y0,x1,y1,(15,25,29)); frame(x0,y0,x1,y1,(83,66,48))
    fill(x0+2,y0+2,x1-2,y0+3,(38,53,52)); fill(x0+2,y1-3,x1-2,y1-2,(35,33,30))
def diamond(cx,cy,r,col):
    for dy in range(-r,r+1): fill(cx-(r-abs(dy)),cy+dy,cx+(r-abs(dy))+1,cy+dy+1,col)
# low contrast brushed bronze; texture remains readable behind text
for y in range(H):
    for x in range(W):
        n=((x*31+y*19+x*y*7)%11)-5
        base=(43,35,28) if y>22 else (53,41,29)
        p[y][x]=tuple(max(0,min(255,c+n//2)) for c in base)+(255,)
frame(0,0,W,H,(22,18,15)); frame(1,1,W-1,H-1,(119,83,49)); frame(3,3,W-3,H-3,(182,137,77)); frame(4,4,W-4,H-4,(64,50,38))
fill(7,6,229,22,(30,26,22)); frame(7,6,229,22,(105,79,48)); fill(9,21,227,22,(189,147,91))
# riveted corner mounts and fine edge inlays
for x in (6,227):
    for y in (7,210):
        fill(x,y,x+3,y+3,(195,153,93)); fill(x+1,y+1,x+2,y+2,(68,46,30))
panel(8,25,132,107); panel(137,25,228,107); panel(8,109,228,125); panel(8,127,228,219)
# fine quartz/machined rails, separate status and inventory
fill(10,106,130,107,(136,105,67)); fill(139,106,226,107,(136,105,67))
fill(11,125,225,126,(119,90,58)); fill(11,141,225,142,(79,73,62))
# subdued engraved circuit marks, deliberately behind and away from controls
for y in (37,51,65,79,93):
    fill(12,y,14,y+1,(80,73,55)); fill(126,y,128,y+1,(80,73,55))
for cy in (36,96): diamond(135,cy,2,(177,133,75)); diamond(135,cy,1,(70,53,37))
# tiny corner studs, mirrored with an inset highlight
for x in (11,126,140,222):
    for y in (28,102):
        fill(x,y,x+2,y+2,(145,111,68)); fill(x,y,x+1,y+1,(221,177,104))
for x in (11,223):
    for y in (112,121): fill(x,y,x+2,y+2,(143,111,70))
# geometric arcane engraving around the machine, no glow or bright noise
for x in range(62,92):
    for y in range(62,96):
        d=((x-77)**2 + (y-79)**2)**.5
        if 14.3<d<15.3: p[y][x]=(42,58,57,255)
# PNG with unfiltered RGBA rows
raw=b''.join(b'\x00'+bytes(c for pixel in row for c in pixel) for row in p)
def chunk(kind,data): return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
out=Path('src/client/resources/assets/alkahest/textures/gui/transmutator_panel.png'); out.parent.mkdir(parents=True,exist_ok=True)
out.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',W,H,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
print('transmutator interface artwork written')
