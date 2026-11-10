#!/usr/bin/env python3
"""Generate the temporary creative-only philosopher's stone focus texture."""
from make_era3_textures import blank, write_png, BASE

img = blank(16, 16)
for y in range(2, 14):
    for x in range(2, 14):
        d = abs(x - 7.5) + abs(y - 7.5)
        if d > 7: continue
        if d > 5.5: color = (78, 27, 47, 255)
        elif x + y < 13: color = (219, 88, 91, 255)
        else: color = (148, 38, 66, 255)
        img[y][x] = color
for x, y in [(6, 4), (7, 4), (5, 5), (6, 5), (5, 6)]:
    img[y][x] = (255, 201, 169, 255)
write_png(BASE / 'item/philosophers_stone.png', 16, 16, img)
