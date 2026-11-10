#!/usr/bin/env python3
"""Generates 64x64 3D entity textures + emissive glow maps + 32x32 bolt textures for all 6 Elementals."""
import struct
import zlib
from pathlib import Path

def write_png(path: Path, width: int, height: int, pixels):
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            r, g, b, a = pixels[y][x]
            raw.extend((r, g, b, a))
    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)

def clamp(v):
    return max(0, min(255, int(round(v))))

def mix(c1, c2, t):
    return (
        clamp(c1[0] * (1 - t) + c2[0] * t),
        clamp(c1[1] * (1 - t) + c2[1] * t),
        clamp(c1[2] * (1 - t) + c2[2] * t),
        255,
    )

def shade(c, factor):
    return (clamp(c[0] * factor), clamp(c[1] * factor), clamp(c[2] * factor), 255)

def paint_box(img, u, v, w, h, d, base_color, edge_color, noise_seed=0):
    """Paints all 6 faces of a Minecraft cuboid UV layout with beveled armor shading."""
    faces = [
        ("top",    u + d,         v,     w, d, 1.14),
        ("bottom", u + d + w,     v,     w, d, 0.68),
        ("right",  u,             v + d, d, h, 0.84),
        ("front",  u + d,         v + d, w, h, 1.02),
        ("left",   u + d + w,     v + d, d, h, 0.88),
        ("back",   u + 2 * d + w, v + d, w, h, 0.78),
    ]
    for name, fx, fy, fw, fh, mult in faces:
        for dy in range(fh):
            for dx in range(fw):
                border = (dx == 0 or dy == 0 or dx == fw - 1 or dy == fh - 1)
                hval = ((dx * 37 + dy * 53 + noise_seed * 17) % 11 - 5) / 80.0
                col = edge_color if border else base_color
                img[fy + dy][fx + dx] = shade(col, mult + hval)

# Palettes: (armor_dark, armor_base, trim, glow_mid, glow_bright, antipode_glow)
THEMES = {
    "ignis": (
        (46, 22, 18), (78, 34, 24), (168, 62, 22),
        (255, 108, 18), (255, 234, 128), (64, 190, 255)
    ),
    "aqua": (
        (16, 48, 74), (28, 86, 124), (48, 152, 186),
        (36, 196, 255), (196, 250, 255), (255, 130, 42)
    ),
    "terra": (
        (48, 36, 24), (88, 66, 42), (124, 108, 62),
        (168, 214, 72), (236, 255, 164), (210, 240, 255)
    ),
    "aer": (
        (92, 110, 134), (156, 178, 204), (206, 224, 244),
        (170, 232, 255), (250, 254, 255), (186, 132, 74)
    ),
    "ordo": (
        (74, 68, 62), (196, 188, 174), (218, 172, 52),
        (255, 212, 54), (255, 252, 204), (158, 64, 228)
    ),
    "perditio": (
        (24, 12, 38), (52, 24, 78), (104, 38, 148),
        (178, 56, 245), (242, 176, 255), (255, 218, 76)
    ),
}

out_dir = Path("src/main/resources/assets/alkahest/textures/entity/elemental")

for idx, (name, (dark, base, trim, glow, bright, anti_glow)) in enumerate(THEMES.items()):
    body = [[(0, 0, 0, 0) for _ in range(64)] for _ in range(64)]
    emissive = [[(0, 0, 0, 0) for _ in range(64)] for _ in range(64)]

    # 1. Head (8x8x8) @ (0, 0)
    paint_box(body, 0, 0, 8, 8, 8, base, dark, idx)
    # Brow crest & glowing eyes on front face x:[8..16), y:[8..16)
    for x in range(8, 16):
        body[8][x] = (*trim, 255)
        body[9][x] = (*dark, 255)
    # Left eye (9..10, 11..12) & Right eye (13..14, 11..12)
    for ex in (9, 10, 13, 14):
        body[11][ex] = (*bright, 255)
        body[12][ex] = (*glow, 255)
        emissive[11][ex] = (*bright, 255)
        emissive[12][ex] = (*glow, 255)
    # Glowing mouth slit / rune visor
    for mx in range(10, 14):
        body[14][mx] = (*glow, 255)
        emissive[14][mx] = (*glow, 220)

    # 2. Horns (2x5x4) @ (32, 0) and (44, 0) + Forehead gem (2x4x2) @ (56, 0)
    for hu in (32, 44):
        paint_box(body, hu, 0, 2, 5, 4, dark, trim, idx + 2)
        # Glowing horn tips (top rows of side/front faces y = 4, 5)
        for hx in range(hu, hu + 12):
            body[4][hx] = (*bright, 255)
            body[5][hx] = (*glow, 255)
            emissive[4][hx] = (*bright, 255)
            emissive[5][hx] = (*glow, 220)
    paint_box(body, 56, 0, 2, 4, 2, glow, bright, idx + 3)
    paint_box(emissive, 56, 0, 2, 4, 2, glow, bright, idx + 3)

    # 3. Mantle / chestplate (10x6x6) @ (0, 16)
    paint_box(body, 0, 16, 10, 6, 6, base, dark, idx + 4)
    # Trim collar & open ribcage window on front (x: 6..16, y: 22..28) and back (x: 22..32, y: 22..28)
    for x in range(6, 16):
        body[22][x] = (*trim, 255)
    for wy in range(24, 28):
        for wx in range(9, 13):
            body[wy][wx] = (0, 0, 0, 0)
        body[wy][8] = (*glow, 255)
        body[wy][13] = (*glow, 255)
        emissive[wy][8] = (*glow, 210)
        emissive[wy][13] = (*glow, 210)
    for wy in range(24, 27):
        for wx in range(25, 29):
            body[wy][wx] = (0, 0, 0, 0)

    # 4. Inner Core (4x4x4) @ (32, 16) — pure radiant energy
    paint_box(body, 32, 16, 4, 4, 4, glow, bright, idx + 5)
    paint_box(emissive, 32, 16, 4, 4, 4, glow, bright, idx + 5)
    for f_off in (0, 4, 8, 12):
        body[21][33 + f_off] = (*bright, 255)
        body[22][34 + f_off] = (*bright, 255)
        emissive[21][33 + f_off] = (*bright, 255)
        emissive[22][34 + f_off] = (*bright, 255)

    # 5. Shoulder (4x4x5) @ (0, 28) & Forearm (3x9x4) @ (18, 28)
    paint_box(body, 0, 28, 4, 4, 5, base, trim, idx + 6)
    # Glowing rune on outer/front shoulder
    for sy in (34, 35):
        body[sy][6] = (*glow, 255)
        body[sy][7] = (*bright, 255)
        emissive[sy][6] = (*glow, 230)
        emissive[sy][7] = (*bright, 255)

    paint_box(body, 18, 28, 3, 9, 4, dark, base, idx + 7)
    # Energy vein down forearm + glowing claws at bottom (y = 39, 40)
    for ay in range(34, 41):
        col = bright if ay >= 39 else glow
        body[ay][23] = (*col, 255)
        emissive[ay][23] = (*col, 240)
        if ay >= 39:
            for ax in range(18, 32):
                body[ay][ax] = (*col, 255)
                emissive[ay][ax] = (*col, 230)

    # 6. Orbiting shard (2x7x2) @ (32, 28)
    paint_box(body, 32, 28, 2, 7, 2, base, trim, idx + 8)
    for sy in (32, 33, 34):
        for sx in range(32, 40):
            col = bright if sy == 33 else glow
            body[sy][sx] = (*col, 255)
            emissive[sy][sx] = (*col, 235)

    # 7. Upper vortex (6x3x6) @ (0, 41) & Lower vortex (4x4x4) @ (24, 41)
    paint_box(body, 0, 41, 6, 3, 6, dark, glow, idx + 9)
    # Hollow center on top/bottom faces of upper vortex
    for hy in range(43, 45):
        for hx in (8, 9, 14, 15):
            body[hy][hx] = (0, 0, 0, 0)
    for vx in range(0, 24, 2):
        body[48][vx] = (*glow, 255)
        emissive[48][vx] = (*glow, 220)

    paint_box(body, 24, 41, 4, 4, 4, trim, glow, idx + 10)
    for vx in range(24, 40):
        body[48][vx] = (*bright, 255)
        emissive[48][vx] = (*bright, 240)

    # 8. Salt Shackles (12x2x12) @ (0, 50) — hollow crystal ring!
    salt_edge = (150, 232, 255)
    salt_bright = (232, 252, 255)
    paint_box(body, 0, 50, 12, 2, 12, salt_edge, salt_bright, idx + 11)
    paint_box(emissive, 0, 50, 12, 2, 12, anti_glow, salt_bright, idx + 11)
    # Cut out inner 8x8 of top (x: 12..24, y: 50..62) and bottom (x: 24..36, y: 50..62)
    for cy in range(52, 60):
        for cx in range(14, 22):
            body[cy][cx] = (0, 0, 0, 0)
            emissive[cy][cx] = (0, 0, 0, 0)
        for cx in range(26, 34):
            body[cy][cx] = (0, 0, 0, 0)
            emissive[cy][cx] = (0, 0, 0, 0)

    write_png(out_dir / f"{name}.png", 64, 64, body)
    write_png(out_dir / f"{name}_glow.png", 64, 64, emissive)

    # 9. Projectile Bolt texture (32x32)
    bolt = [[(0, 0, 0, 0) for _ in range(32)] for _ in range(32)]
    paint_box(bolt, 0, 0, 4, 4, 4, bright, glow, idx + 20)
    paint_box(bolt, 0, 8, 6, 2, 2, glow, trim, idx + 21)
    paint_box(bolt, 0, 12, 2, 6, 2, glow, trim, idx + 22)
    write_png(out_dir / f"bolt_{name}.png", 32, 32, bolt)

print("Generated 6 Elemental 64x64 textures, 6 emissive glow maps, and 6 32x32 bolt textures.")