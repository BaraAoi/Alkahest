#!/usr/bin/env python3
"""Procedural placeholder textures for the 0.1.32 tier-2 distillery update.

No PIL in the build environment, so this writes RGBA PNG files directly.
Every texture is deterministic: re-running the script produces identical pixels.
"""
import struct
import zlib
from pathlib import Path

BLOCK = Path('src/main/resources/assets/alkahest/textures/block')
GUI = Path('src/client/resources/assets/alkahest/textures/gui')


def write_png(path, width, height, pixels):
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        return (struct.pack('>I', len(data)) + tag + data
                + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF))

    header = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header)
                     + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b''))


def noise(x, y, seed=0):
    """Deterministic 0..255 hash noise."""
    h = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFF


def shade(color, delta):
    return tuple(max(0, min(255, c + delta)) for c in color[:3])


def canvas(width, height, color=(0, 0, 0, 0)):
    return [[tuple(color) for _ in range(width)] for _ in range(height)]


def glass_tile(seed, tint=(150, 232, 236), alpha=(28, 96)):
    img = canvas(16, 16, (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            n = noise(x, y, seed)
            border = x in (0, 15) or y in (0, 15)
            a = alpha[1] if border else alpha[0] + (n % 26)
            if (x + y) % 11 == 0 and not border:
                a = min(150, a + 46)
            img[y][x] = (*tint, a)
    for x in range(2, 14):
        img[2][x] = (240, 255, 255, 150)
        img[3][x] = (255, 255, 255, 96)
    for y in range(4, 13):
        img[y][2] = (235, 255, 255, 110)
    return img


def metal_tile(seed, base=(184, 108, 62)):
    img = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            n = noise(x, y, seed)
            c = shade(base, (n % 26) - 13)
            if x in (0, 15) or y in (0, 15):
                c = shade(base, -34)
            if (x in (3, 12) and y in (3, 12)):
                c = shade(base, 36)
            img[y][x] = (*c, 255)
    # patina specks
    for i in range(14):
        x, y = noise(i, 7, seed) % 16, noise(i, 13, seed) % 16
        img[y][x] = (86 + noise(i, 3, seed) % 30, 154 + noise(i, 5, seed) % 30, 120, 255)
    return img


def essence_tile(seed, base=(128, 62, 196)):
    img = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            n = noise(x, y, seed)
            c = shade(base, (n % 34) - 17 + (6 if y < 6 else 0))
            img[y][x] = (*c, 236)
    for i in range(9):
        x, y = 1 + noise(i, 2, seed) % 14, 1 + noise(i, 9, seed) % 14
        img[y][x] = (226, 196, 255, 250)
        if y + 1 < 16:
            img[y + 1][x] = (176, 128, 236, 240)
    return img


def crude_copper(seed):
    """Dented, patched copper for the crude tier-1 boiler."""
    img = metal_tile(seed, base=(150, 92, 58))
    for y in range(16):
        for x in range(16):
            n = noise(x, y, seed + 5)
            if n % 17 == 0:
                img[y][x] = (104, 74, 54, 255)
            if (x * 3 + y * 5) % 23 == 0:
                img[y][x] = (170, 126, 82, 255)
    for i in range(5):
        x, y = noise(i, 1, seed) % 14 + 1, noise(i, 4, seed) % 14 + 1
        img[y][x] = (58, 42, 34, 255)
        img[y][x - 1] = (196, 160, 112, 255)
    # rivets along the top and bottom edges
    for x in (2, 7, 13):
        img[1][x] = (206, 172, 120, 255)
        img[14][x] = (206, 172, 120, 255)
    return img


def crude_panel(seed):
    img = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            n = noise(x, y, seed)
            base = (66, 60, 54) if (y // 5) % 2 == 0 else (78, 68, 58)
            img[y][x] = (*shade(base, (n % 18) - 9), 255)
    for x in range(2, 14):
        img[5][x] = (128, 96, 62, 255)
        img[11][x] = (128, 96, 62, 255)
    for y in range(1, 15):
        img[y][1] = (96, 82, 66, 255)
        img[y][14] = (96, 82, 66, 255)
    return img


def crude_glass(seed):
    img = canvas(16, 16, (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            n = noise(x, y, seed)
            a = 40 + n % 30
            if x in (0, 15) or y in (0, 15):
                a = 96
            img[y][x] = (126, 146, 140, a)
    img[4][6] = (220, 236, 226, 120)
    img[5][5] = (220, 236, 226, 100)
    return img


def wood_tile(seed):
    img = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            n = noise(x, y, seed)
            base = (124, 92, 56) if y < 8 else (112, 82, 50)
            c = shade(base, (n % 16) - 8)
            if y in (0, 7, 8, 15):
                c = shade(base, -30)
            img[y][x] = (*c, 255)
    for x in range(16):
        if x % 5 == 0:
            img[8][x] = (78, 58, 36, 255)
    for i in range(6):
        x, y = noise(i, 6, seed) % 16, noise(i, 2, seed) % 16
        img[y][x] = (150, 116, 74, 255)
    return img


def leather_tile(seed):
    img = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            n = noise(x, y, seed)
            c = shade((96, 62, 40), (n % 20) - 10)
            img[y][x] = (*c, 255)
    for x in range(2, 14):
        img[2][x] = (150, 112, 74, 255)
        img[13][x] = (150, 112, 74, 255)
    for y in range(3, 13):
        img[y][3] = (150, 112, 74, 255)
        img[y][12] = (150, 112, 74, 255)
    for x in range(3, 13, 3):
        img[2][x] = (58, 40, 26, 255)
        img[13][x] = (58, 40, 26, 255)
    return img


def crude_gui(width=472, height=568):
    """Dark patched planks with iron nails: the crude distillery interface backdrop."""
    img = canvas(width, height)
    plank_h = 38
    for y in range(height):
        row = y // plank_h
        for x in range(width):
            n = noise(x, y, 11)
            base = (74, 54, 36) if row % 2 == 0 else (66, 48, 32)
            c = shade(base, (n % 14) - 7)
            if y % plank_h == 0:
                c = (38, 28, 20)
            if x % 118 == 0:
                c = (44, 32, 22)
            if (x < 3 or x > width - 4 or y < 3 or y > height - 4):
                c = (48, 36, 26)
            img[y][x] = (*c, 255)
    # iron nails on the plank grid
    for py in range(0, height, plank_h):
        for px in range(0, width, 118):
            for dx in range(3):
                for dy in range(3):
                    if 0 <= px + 8 + dx < width and 0 <= py + 10 + dy < height:
                        img[py + 10 + dy][px + 8 + dx] = (96, 96, 92, 255) if dx != 1 else (150, 150, 144, 255)
    # soot towards the middle, where the machine artwork used to be
    for y in range(40, height - 30):
        for x in range(24, width - 24):
            if noise(x, y, 23) % 37 == 0:
                r, g, b, a = img[y][x]
                img[y][x] = (max(0, r - 26), max(0, g - 20), max(0, b - 16), a)
    return img


def main():
    write_png(BLOCK / 'stabilizer_glass.png', 16, 16, glass_tile(3))
    write_png(BLOCK / 'stabilizer_frame.png', 16, 16, metal_tile(4))
    write_png(BLOCK / 'stabilizer_essence.png', 16, 16, essence_tile(5))
    write_png(BLOCK / 'distillery_crude.png', 16, 16, crude_copper(6))
    write_png(BLOCK / 'distillery_crude_panel.png', 16, 16, crude_panel(7))
    write_png(BLOCK / 'distillery_crude_glass.png', 16, 16, crude_glass(8))
    write_png(BLOCK / 'bellows_wood.png', 16, 16, wood_tile(9))
    write_png(BLOCK / 'bellows_leather.png', 16, 16, leather_tile(10))
    write_png(GUI / 'distillery_backdrop.png', 472, 568, crude_gui())
    print('textures written')


if __name__ == '__main__':
    main()
