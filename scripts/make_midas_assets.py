#!/usr/bin/env python3
"""Generate the temporary Midas elixir and effect sprites without external libraries."""
from make_era3_textures import BASE, blank, write_png

img = blank(16, 16)
for y in range(1, 4):
    for x in range(6, 10):
        img[y][x] = (111, 65, 37, 255) if y > 1 else (238, 192, 104, 255)
for y in range(3, 6):
    for x in range(6, 10): img[y][x] = (193, 232, 223, 255)
for y in range(5, 14):
    for x in range(3, 13):
        if (y == 5 and x in (3, 4, 11, 12)) or (y == 13 and x in (3, 12)): continue
        edge = x in (3, 12) or y in (5, 13)
        img[y][x] = (117, 91, 50, 255) if edge else ((248, 192, 50, 255) if y >= 8 else (206, 228, 209, 255))
for x, y in [(5, 6), (5, 7), (5, 8), (6, 6), (8, 9), (9, 10)]: img[y][x] = (255, 242, 170, 255)
write_png(BASE / 'item/midas_elixir.png', 16, 16, img)
icon = blank(18, 18)
for y in range(3, 15):
    for x in range(3, 15):
        d = abs(x - 8.5) + abs(y - 8.5)
        if d < 7: icon[y][x] = (235, 177, 46, 255) if d < 5 else (142, 93, 29, 255)
for x, y in [(7, 5), (8, 5), (6, 6), (7, 6), (6, 7)]: icon[y][x] = (255, 242, 163, 255)
write_png(BASE / 'mob_effect/midas_touch.png', 18, 18, icon)
