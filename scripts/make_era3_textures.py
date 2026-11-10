#!/usr/bin/env python3
"""Generate pixel-art textures for Alpha 0.1.29 (Alchemical Moss, Alkahest Vial/Amulet, Dissolved 3D mob)."""
import struct
import zlib
from pathlib import Path

BASE = Path("src/main/resources/assets/alkahest/textures")


def write_png(path: Path, w: int, h: int, pixels):
    path.parent.mkdir(parents=True, exist_ok=True)
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            r, g, b, a = pixels[y][x]
            raw.extend((r & 255, g & 255, b & 255, a & 255))

    def chunk(kind: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + kind
            + data
            + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
        )

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    path.write_bytes(png)


def blank(w: int, h: int):
    return [[(0, 0, 0, 0) for _ in range(w)] for _ in range(h)]


def make_alchemical_moss_block():
    img = blank(16, 16)
    # Bottom carpet (rows 12..15) + lush cross fronds (rows 4..15, cols 2..13) + full top/bottom pad
    palette = [
        (46, 28, 84, 255),
        (68, 42, 122, 255),
        (93, 63, 211, 255),
        (42, 112, 108, 255),
    ]
    for y in range(1, 16):
        for x in range(1, 15):
            dist = abs(x - 7.5) + abs(y - 8.5)
            if y >= 11 or dist < 8.5 or ((x + y * 3) % 5 == 0 and y >= 5):
                idx = (x * 7 + y * 13) % len(palette)
                img[y][x] = palette[idx]
    # Luminous spore buds (violet-magenta & turquoise)
    spores = [
        (4, 6, (214, 158, 255, 255)),
        (10, 5, (116, 242, 218, 255)),
        (7, 8, (232, 182, 255, 255)),
        (12, 9, (186, 120, 255, 255)),
        (5, 11, (116, 242, 218, 255)),
        (9, 12, (238, 198, 255, 255)),
        (3, 13, (176, 112, 252, 255)),
        (12, 14, (124, 245, 224, 255)),
    ]
    for sx, sy, col in spores:
        img[sy][sx] = col
    write_png(BASE / "block/alchemical_moss.png", 16, 16, img)


def make_alchemical_moss_item():
    img = blank(16, 16)
    for y in range(4, 14):
        for x in range(2, 14):
            dx = (x - 7.5) / 5.5
            dy = (y - 9.5) / 4.8
            if dx * dx + dy * dy <= 1.05:
                shade = (x * 5 + y * 9) % 3
                if shade == 0:
                    img[y][x] = (54, 32, 98, 255)
                elif shade == 1:
                    img[y][x] = (93, 63, 211, 255)
                else:
                    img[y][x] = (48, 124, 118, 255)
    # Outline & glowing spore tips
    for sx, sy, col in [
        (5, 6, (226, 176, 255, 255)),
        (9, 5, (128, 248, 224, 255)),
        (7, 8, (244, 212, 255, 255)),
        (11, 8, (196, 136, 255, 255)),
        (6, 10, (128, 248, 224, 255)),
        (9, 11, (226, 176, 255, 255)),
    ]:
        img[sy][sx] = col
    write_png(BASE / "item/alchemical_moss.png", 16, 16, img)


def make_alkahest_vial():
    img = blank(16, 16)
    gold_dark = (146, 104, 38, 255)
    gold_mid = (218, 174, 76, 255)
    gold_hi = (255, 232, 148, 255)
    glass = (182, 220, 238, 235)
    glass_dark = (68, 86, 114, 255)

    # Cork & gold collar (rows 1..5)
    for x in range(6, 10):
        img[1][x] = gold_hi if x < 8 else gold_mid
        img[2][x] = (148, 92, 58, 255)
    for x in range(5, 11):
        img[3][x] = gold_dark if x in (5, 10) else gold_hi
        img[4][x] = glass_dark if x in (5, 10) else glass

    # Faceted crystal bulb (rows 5..14)
    spans = {5: (4, 12), 6: (3, 13), 7: (3, 13), 8: (2, 14), 9: (2, 14), 10: (2, 14), 11: (3, 13), 12: (3, 13), 13: (4, 12), 14: (5, 11)}
    iridescent = [
        (168, 85, 247, 255),
        (216, 114, 255, 255),
        (96, 228, 216, 255),
        (246, 208, 116, 255),
        (124, 72, 228, 255),
    ]
    for y, (x0, x1) in spans.items():
        for x in range(x0, x1):
            if x in (x0, x1 - 1) or y == 14:
                img[y][x] = gold_mid if y in (8, 13, 14) else glass_dark
            elif y == 5:
                img[y][x] = glass
            else:
                col = iridescent[(x + y * 2) % len(iridescent)]
                if x == x0 + 1 and y in (7, 8, 9):
                    col = (255, 246, 255, 255)
                img[y][x] = col
    write_png(BASE / "item/alkahest_vial.png", 16, 16, img)


def make_alkahest_amulet():
    img = blank(16, 16)
    cord = (92, 68, 52, 255)
    gold_dark = (142, 98, 34, 255)
    gold_mid = (222, 178, 76, 255)
    gold_hi = (255, 236, 156, 255)

    # Necklace cord loop (rows 1..6)
    for x, y in [(5, 1), (6, 1), (9, 1), (10, 1), (4, 2), (11, 2), (3, 3), (12, 3), (3, 4), (12, 4), (4, 5), (11, 5), (5, 6), (10, 6)]:
        img[y][x] = cord
    img[6][7] = gold_hi
    img[6][8] = gold_mid

    # Ornate gold medallion setting + glowing Alkahest/Ordo gem (rows 7..14)
    spans = {7: (5, 11), 8: (4, 12), 9: (3, 13), 10: (3, 13), 11: (3, 13), 12: (4, 12), 13: (5, 11), 14: (6, 10)}
    for y, (x0, x1) in spans.items():
        for x in range(x0, x1):
            edge = x in (x0, x1 - 1) or y in (7, 13, 14)
            if edge:
                img[y][x] = gold_hi if (x + y) % 2 == 0 and x < 8 else (gold_mid if y < 13 else gold_dark)
            else:
                dist = abs(x - 7.5) + abs(y - 10.0)
                if dist <= 1.6:
                    img[y][x] = (255, 248, 224, 255)
                elif (x + y) % 2 == 0:
                    img[y][x] = (176, 92, 252, 255)
                else:
                    img[y][x] = (98, 232, 218, 255)
    write_png(BASE / "item/alkahest_amulet.png", 16, 16, img)


def fill_rect(img, x0, y0, w, h, fn):
    for dy in range(h):
        for dx in range(w):
            x, y = x0 + dx, y0 + dy
            if 0 <= x < len(img[0]) and 0 <= y < len(img):
                col = fn(dx, dy, x, y)
                if col is not None:
                    img[y][x] = col


def make_dissolved_mob():
    body = blank(64, 64)
    glow = blank(64, 64)

    def flesh_pixel(dx, dy, x, y):
        v = (x * 11 + y * 17) % 13
        if v in (0, 1):
            return (86, 178, 52, 255)   # dripping caustic slime vein
        if v in (2, 3):
            return (42, 22, 68, 255)    # deep corroded shadow
        if v in (4, 5, 6):
            return (78, 42, 116, 255)   # bruised arcane flesh
        return (58, 32, 88, 255)

    # Head cuboid (0, 0) 8x8x8 -> 32x16
    fill_rect(body, 0, 0, 32, 16, flesh_pixel)
    # Face sits at (8, 8) 8x8: asymmetric melting eyes & acidic mouth
    for ex, ey in [(9, 11), (10, 11), (13, 10), (14, 10), (14, 11)]:
        body[ey][ex] = (182, 255, 96, 255)
        glow[ey][ex] = (182, 255, 96, 255)
    for mx in range(10, 15):
        body[14][mx] = (112, 232, 68, 255)
        glow[14][mx] = (128, 244, 84, 220)

    # Unstable crystal shard on skull (32, 0) 3x5x3 -> 12x8
    fill_rect(body, 32, 0, 12, 8, lambda dx, dy, x, y: (192, 118, 255, 255) if (dx + dy) % 2 == 0 else (132, 68, 214, 255))
    fill_rect(glow, 32, 0, 12, 8, lambda dx, dy, x, y: (208, 142, 255, 235) if (dx + dy) % 2 == 0 else (142, 78, 228, 180))

    # Dripping jaw tendril (44, 0) 6x3x2 -> 16x5
    fill_rect(body, 44, 0, 16, 5, lambda dx, dy, x, y: (108, 218, 64, 255) if dy >= 3 else flesh_pixel(dx, dy, x, y))
    fill_rect(glow, 44, 3, 16, 2, lambda dx, dy, x, y: (146, 250, 92, 210))

    # Hunched torso (0, 16) 8x11x5 -> 26x16
    fill_rect(body, 0, 16, 26, 16, flesh_pixel)
    # Open chest cavity veins on torso front (5..13, 21..31)
    for y in range(23, 30):
        for x in range(7, 11):
            if (x + y) % 2 == 0:
                body[y][x] = (144, 246, 86, 255)
                glow[y][x] = (144, 246, 86, 220)

    # Pulsing inner core (26, 16) 4x4x4 -> 16x8
    fill_rect(body, 26, 16, 16, 8, lambda dx, dy, x, y: (186, 255, 112, 255) if (dx + dy) % 2 == 0 else (196, 112, 255, 255))
    fill_rect(glow, 26, 16, 16, 8, lambda dx, dy, x, y: (196, 255, 128, 255) if (dx + dy) % 2 == 0 else (210, 132, 255, 255))

    # Left acid claw (0, 32) 4x13x4 -> 16x17
    def claw_pixel(dx, dy, x, y):
        if dy >= 12:
            return (124, 238, 72, 255)
        return flesh_pixel(dx, dy, x, y)

    fill_rect(body, 0, 32, 16, 17, claw_pixel)
    fill_rect(glow, 0, 44, 16, 5, lambda dx, dy, x, y: (152, 252, 96, 225))

    # Melted right arm (16, 32) 3x10x3 -> 12x13
    fill_rect(body, 16, 32, 12, 13, flesh_pixel)

    # Shambling legs (0, 49) and (16, 49) 4x10x4 -> 16x14 each
    fill_rect(body, 0, 49, 32, 14, lambda dx, dy, x, y: (98, 198, 56, 255) if dy >= 11 else flesh_pixel(dx, dy, x, y))
    fill_rect(glow, 0, 61, 32, 2, lambda dx, dy, x, y: (132, 238, 82, 190))

    write_png(BASE / "entity/dissolved/dissolved.png", 64, 64, body)
    write_png(BASE / "entity/dissolved/dissolved_glow.png", 64, 64, glow)


if __name__ == "__main__":
    make_alchemical_moss_block()
    make_alchemical_moss_item()
    make_alkahest_vial()
    make_alkahest_amulet()
    make_dissolved_mob()
    print("Generated Era III textures successfully.")
