#!/usr/bin/env python3
"""Generate translucent elemental veins within the silhouettes of 1.21.1 items.

Models keep their vanilla layer0 so resource packs still provide the underlying material;
layer1 is a transparent, tinted mixture with irregular mineral veins, not an emblem.
The compact masks describe the default item silhouettes, so generation needs no game files.
"""
import json
import struct
import zlib
from pathlib import Path

assets = Path('src/main/resources/assets/alkahest')
# X is an opaque pixel in the vanilla item's default silhouette. Paths are coordinates of
# uneven, branching veins: they are intentionally different for every type of material.
masks = {
    'blaze_powder': ['................','......X.........','.....XXX........','...X..X....X....','..XXX.....XXX...','...X...X...X....','..X...XXXX......','.XXX...XXXX.....','.XXX.XXXXXX.X...','.XXXXXXXXXXXXX..','.XXXXXXXXXXXXX..','..XXXXXXXXXXXXX.','...XXXXXXXXXXX..','....X.XXXXXXX...','.......XXXXX....','................'],
    'lapis_lazuli': ['................','................','.......XXXXX....','......XXXXXXX...','.....XXXXXXXXX..','....XXXXXXXXXX..','....XXXXXXXXXX..','....XXXXXXXXXX..','...XXXXXXXXXX...','..XXXXXXXXXXX...','..XXXXXXXXXX....','..XXXXXXXXX.....','..XXXXXXXX......','...XXXX.........','................','................'],
    'iron_ingot': ['................','................','..........XX....','.......XXXXXX...','....XXXXXXXXXX..','.XXXXXXXXXXXXXX.','XXXXXXXXXXXXXXXX','XXXXXXXXXXXXXXXX','XXXXXXXXXXXXXXXX','XXXXXXXXXXXXXXXX','XXXXXXXXXXXXXXX.','.XXXXXXXXXXXX...','..XXXXXXXX......','...XXXX.........','................','................'],
    'diamond': ['................','................','......XXXX......','.....XXXXXX.....','....XXXXXXXX....','...XXXXXXXXXX...','...XXXXXXXXXX...','..XXXXXXXXXXXX..','..XXXXXXXXXXXX..','..XXXXXXXXXXXX..','..XXXXXXXXXXXX..','...XXXXXXXXXX...','...XXXXXXXXXX...','....XXXXXXXX....','.....XXXXXX.....','................'],
    'gold_ingot': ['................','................','..........XX....','.......XXXXXX...','....XXXXXXXXXX..','.XXXXXXXXXXXXXX.','XXXXXXXXXXXXXXXX','XXXXXXXXXXXXXXXX','XXXXXXXXXXXXXXXX','XXXXXXXXXXXXXXXX','XXXXXXXXXXXXXXX.','.XXXXXXXXXXXX...','..XXXXXXXX......','...XXXX.........','................','................'],
    'amethyst_shard': ['................','................','.........XXXXX..','........XXXXXX..','.......XXXXXXX..','......XXXXXXXX..','.....XXXXXXXXX..','....XXXXXXXXX...','...XXXXXXXXX....','..XXXXXXXXX.....','..XXXXXXXX......','..XXXXXXX.......','..XXXXXX........','...XXXX.........','....XX..........','................'],
}
# Base texture, translucent tint, vein shadow, vein colour and branching, non-symmetric paths.
materials = {
    'ignis': ('blaze_powder', (226,40,4), (128,13,2), (255,63,8), [
        [(2,10),(3,9),(4,10),(5,9),(6,9),(7,8),(8,7)], [(5,9),(6,10),(7,11),(8,11),(9,12),(10,12)], [(9,10),(10,9),(11,9),(12,10)]]),
    'aqua': ('lapis_lazuli', (12,169,207), (8,94,142), (143,246,255), [
        [(8,3),(8,4),(7,5),(7,6),(6,7),(6,8),(5,9),(5,10),(4,11)], [(7,6),(8,7),(9,7),(10,8),(11,8)], [(6,8),(7,9),(7,10),(8,11),(8,12)]]),
    'terra': ('iron_ingot', (145,90,43), (94,55,26), (203,154,82), [
        [(3,6),(4,6),(5,7),(6,7),(7,8),(8,8),(9,7),(10,7),(11,6),(12,6)], [(6,7),(5,8),(4,9),(3,9),(2,10)], [(8,8),(7,9),(7,10),(6,11),(5,11)]]),
    'aer': ('diamond', (118,191,234), (8,90,151), (168,226,255), [
        [(6,4),(7,5),(8,5),(9,6),(9,7),(10,8),(10,9),(11,10)], [(8,5),(8,6),(7,7),(6,7),(5,8),(5,9)], [(10,9),(9,10),(8,10),(7,11),(6,12)]]),
    'ordo': ('gold_ingot', (255,215,92), (122,62,7), (255,160,20), [
        [(3,6),(4,7),(5,7),(6,8),(7,8),(8,7),(9,7),(10,6),(11,6),(12,5)], [(5,7),(5,8),(4,9),(3,10)], [(8,7),(8,8),(9,9),(10,9),(11,10)]]),
    'perditio': ('amethyst_shard', (86,12,138), (45,1,77), (190,74,234), [
        [(12,3),(11,4),(10,5),(10,6),(9,7),(8,7),(8,8),(7,9),(6,9),(5,10),(4,11)], [(10,6),(11,6),(11,7),(10,8)], [(8,8),(8,9),(7,10),(7,11),(6,12)]]),
}

def png(rows):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    raw = b''.join(b'\0' + bytes(channel for pixel in row for channel in pixel) for row in rows)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')

for name, (base, tint, shadow, light, paths) in materials.items():
    mask = {(x,y) for y,line in enumerate(masks[base]) for x,value in enumerate(line) if value == 'X'}
    rows = [[(0,0,0,0) for _ in range(16)] for _ in range(16)]
    # A saturated coloured wash ties the fissures to the material. Preserve its outline.
    for x,y in mask:
        if all((x+dx,y+dy) in mask for dx,dy in ((-1,0),(1,0),(0,-1),(0,1))):
            rows[y][x] = (*tint, 65 + ((x*7+y*11) % 4)*10)
    vein = {point for path in paths for point in path if point in mask}
    for x,y in vein:
        # Dark irregular edging makes the coloured vein stand out on bright materials.
        for dx,dy in ((-1,0),(1,0),(0,-1),(0,1)):
            xx,yy = x+dx,y+dy
            if (xx,yy) in mask and (xx,yy) not in vein and rows[yy][xx][3]:
                rows[yy][xx] = (*shadow, 135)
    # Thicken selected sides of the branches; leave plenty of the original material visible.
    midpoint = tuple((2*highlight + shade)//3 for highlight, shade in zip(light, shadow))
    for x,y in vein:
        if (x+y) % 3 == 0:
            xx,yy = x+1,y
            if (xx,yy) in mask and (xx,yy) not in vein and rows[yy][xx][3]:
                rows[yy][xx] = (*midpoint, 205)
    for x,y in vein:
        rows[y][x] = (*light, 235 + ((x*3+y*5) % 3)*10)
    (assets / 'textures/item' / f'infused_{name}.png').write_bytes(png(rows))
    model = {'parent':'minecraft:item/generated', 'textures': {'layer0':f'minecraft:item/{base}', 'layer1':f'alkahest:item/infused_{name}'}}
    (assets / 'models/item' / f'infused_{name}.json').write_text(json.dumps(model, indent=2)+'\n')
