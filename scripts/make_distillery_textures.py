#!/usr/bin/env python3
"""Regenerate the distillery's hand-authored 32px pixel art. Requires Pillow."""
from PIL import Image, ImageDraw
import math
import random
from pathlib import Path

out = Path('src/main/resources/assets/alkahest/textures/block')
out.mkdir(parents=True, exist_ok=True)

def metal():
    rng = random.Random(3187)
    im = Image.new('RGB', (32,32)); p = im.load()
    for y in range(32):
        for x in range(32):
            noise = rng.randrange(-7, 8)
            shade = 9 if (x+y)%7==0 else 0
            p[x,y] = (max(0,151+noise+shade), max(0,91+noise+shade), max(0,55+noise+shade))
    d = ImageDraw.Draw(im)
    d.line([(0,0),(31,0)], fill=(216,156,89),width=2)
    d.line([(0,1),(0,31)], fill=(202,137,75))
    d.line([(0,31),(31,31),(31,0)], fill=(76,48,40),width=2)
    for x,y in [(3,3),(28,3),(3,28),(28,28)]:
        d.point((x,y), fill=(248,197,116));d.point((x+1,y+1), fill=(88,50,38))
    return im

metal().save(out/'distillery_copper.png', optimize=True)
# Elaborate brass panel: this is the lower boiler/foot of the apparatus.
im=metal(); d=ImageDraw.Draw(im)
d.rectangle((4,6,27,25), fill=(91,58,47),outline=(211,151,87),width=1)
d.rectangle((6,8,25,23), fill=(36,54,54),outline=(147,104,65),width=1)
# Tiny geometric sigil, also visible on the inventory model.
d.polygon([(16,9),(22,20),(10,20)],outline=(188,149,86))
d.line([(11,13),(21,13)],fill=(121,194,170));d.rectangle((15,15,17,17),fill=(132,221,195))
for x in (8,23):
    for y in (10,21):d.point((x,y),fill=(230,179,100))
im.save(out/'distillery_panel.png', optimize=True)
# Burnished cap with a black chimney mouth, seen from above.
im=metal();d=ImageDraw.Draw(im);d.ellipse((5,5,26,26), fill=(70,49,41),outline=(236,176,98),width=2)
d.ellipse((10,10,21,21), fill=(17,33,36),outline=(117,90,61));d.ellipse((13,12,17,16),fill=(53,112,102))
im.save(out/'distillery_vent.png', optimize=True)

def chamber(frame=None):
    active = frame is not None
    im = Image.new('RGB',(32,32),(32,47,49)); p=im.load()
    # Real painted glass with a deep teal vessel behind it, not an alpha overlay.
    for y in range(3,29):
        for x in range(3,29):
            center=max(0,1-abs(x-15.5)/16)
            glow=int(18*center) if active else int(6*center)
            grain=(x*11+y*7)%5
            if active:
                wave=int(5*math.sin((y+frame*2)*.45 + x*.12))
                p[x,y]=(13+grain, 56+glow+wave, 58+glow+wave)
            else:p[x,y]=(15+grain,36+glow,40+glow)
    d=ImageDraw.Draw(im)
    # Copper frame, shaped shoulders, bolts and thin glass reflections.
    d.rectangle((0,0,31,31),outline=(58,44,40),width=2)
    d.rectangle((2,2,29,29),outline=(177,119,69),width=2)
    d.rectangle((4,4,27,27),outline=(49,87,79),width=1)
    d.line([(6,5),(6,23)], fill=(91,172,160) if active else (56,104,100))
    d.line([(7,5),(12,5)], fill=(137,208,188) if active else (72,123,117))
    d.line([(25,9),(25,22)], fill=(41,109,101) if active else (38,72,70))
    # The circular arcane seal stays still while bubbles travel behind/around it.
    glow=(94,230,202) if active else (50,116,107)
    d.ellipse((10,9,22,21),outline=glow,width=1)
    d.polygon([(16,10),(21,19),(11,19)],outline=glow)
    d.line([(13,16),(19,16)],fill=glow)
    if active:
        # Each bubble rises across the eight vertically stacked animation frames.
        for x,offset in ((9,3),(13,11),(21,0),(23,7),(18,14)):
            y=25-((frame*3+offset)%19)
            d.point((x,y),fill=(166,255,225));
            if x in (9,21): d.point((x+1,y),fill=(72,181,160))
        d.line([(9,25),(23,25)],fill=(85,205,175))
    for x,y in ((3,3),(28,3),(3,28),(28,28)):
        d.point((x,y),fill=(240,191,109))
    return im

chamber().save(out/'distillery_chamber_idle.png', optimize=True)
strip=Image.new('RGB',(32,32*8))
for frame in range(8):strip.paste(chamber(frame),(0,frame*32))
strip.save(out/'distillery_chamber_active.png', optimize=True)
(out/'distillery_chamber_active.png.mcmeta').write_text('{"animation":{"frametime":4,"interpolate":true}}\n')
