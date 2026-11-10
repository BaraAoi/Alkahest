#!/usr/bin/env python3
"""Produce the early alchemical bottle sprites (requires Pillow)."""
from PIL import Image, ImageDraw
from pathlib import Path
out=Path('src/main/resources/assets/alkahest/textures/item');out.mkdir(parents=True, exist_ok=True)

def bottle(name, colors, bolt):
    im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
    # Stopper, neck and wide round-shouldered body, pixel-aligned for Minecraft.
    d.rectangle((12,2,19,5),fill=(111,66,43,255));d.line((13,2,19,2),fill=(225,173,105,255),width=2)
    d.rectangle((13,5,18,9),fill=(88,138,135,255),outline=(200,231,215,255))
    d.polygon([(10,8),(21,8),(21,10),(25,13),(26,16),(26,25),(24,28),(7,28),(5,25),(5,16),(6,13),(10,10)],fill=(28,56,62,255),outline=(188,220,209,255),width=1)
    d.polygon([(7,18),(25,18),(25,24),(23,26),(8,26),(7,24)],fill=colors[0])
    d.line((8,19,24,19),fill=colors[1],width=1)
    d.line((8,14,8,22),fill=(210,249,235,230),width=1)
    d.line((9,12,11,10),fill=(210,249,235,230),width=1)
    if bolt:
        d.polygon([(17,10),(12,18),(15,18),(13,25),(21,15),(17,15),(20,10)],fill=(255,222,104,255),outline=(255,250,179,255))
    else:
        for x,y in ((12,20),(18,22),(21,16),(14,13)):
            d.rectangle((x,y,x+2,y+2),fill=colors[1])
        d.line((11,24,22,24),fill=colors[1])
    im.save(out/(name+'.png'),optimize=True)

bottle('lightning_in_a_bottle',((19,111,156,255),(93,219,237,255)),True)
bottle('miasma',((72,39,89,255),(162,106,171,255)),False)
