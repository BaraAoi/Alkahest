#!/usr/bin/env python3
"""Assets for the 3x3x3 Matrix and its Alchemical Bricks.

The core is a golden drum with a violet crystal orb; the eight pillars are dark obelisks with
gold collars and a crystal cap; the eighteen bricks are deep slate tiles with copper mortar and
a rune. Every piece has an `active` variant whose runes burn violet, so the whole cube lights up
as one machine while a synthesis runs.
"""
import json
import struct
import zlib
from pathlib import Path

A = Path('src/main/resources/assets/alkahest')
BLOCK = A / 'textures/block'
MODELS = A / 'models/block'
ITEM_MODELS = A / 'models/item'
STATES = A / 'blockstates'

SIZE = 16
FACES = ("up", "down", "north", "south", "east", "west")

GOLD = (198, 158, 66)
GOLD_DARK = (146, 112, 44)
GOLD_LIGHT = (245, 214, 130)
GOLD_EDGE = (94, 68, 26)
VIOLET = (128, 84, 190)
VIOLET_DARK = (74, 44, 116)
VIOLET_LIGHT = (206, 168, 255)
VIOLET_GLOW = (240, 222, 255)
SLATE = (74, 74, 86)
SLATE_DARK = (52, 52, 62)
SLATE_LIGHT = (98, 98, 112)
COPPER = (168, 104, 58)
COPPER_DARK = (118, 68, 36)
QUARTZ = (224, 218, 203)


def noise(x, y, spread=6):
    value = (x * 73856093) ^ (y * 19349663)
    return (value % (2 * spread + 1)) - spread


def shift(color, delta):
    return tuple(max(0, min(255, channel + delta)) for channel in color)


def blank():
    return [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]


def ring(rows, cx, cy, radius, color, thickness=1):
    for y in range(SIZE):
        for x in range(SIZE):
            distance = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if radius - thickness < distance <= radius:
                rows[y][x] = (*color, 255)


def disc(rows, cx, cy, radius, color):
    for y in range(SIZE):
        for x in range(SIZE):
            if ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 <= radius:
                rows[y][x] = (*color, 255)


def write_png(path, rows):
    height, width = len(rows), len(rows[0])
    body = b''.join(b'\x00' + b''.join(bytes(px) for px in row) for row in rows)

    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

    header = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header)
                     + chunk(b'IDAT', zlib.compress(body)) + chunk(b'IEND', b''))


# --- core ------------------------------------------------------------------------------------
def core_side(lit):
    rows = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            base = GOLD
            if y in (0, 15) or x in (0, 15):
                base = GOLD_EDGE
            elif y in (1, 14) or x in (1, 14):
                base = GOLD_DARK
            elif y in (7, 8):
                base = VIOLET_GLOW if lit else VIOLET
            rows[y][x] = (*shift(base, noise(x, y)), 255)
    for y in range(3, 13):
        for x in range(5, 11):
            if rows[y][x][:3] in (GOLD, GOLD_DARK, GOLD_EDGE):
                continue
    # Runic diamond in the middle of the band.
    for step in range(3):
        for x, y in ((7, 3 + step), (8, 3 + step), (7, 12 - step), (8, 12 - step),
                     (7 - step, 7), (7 - step, 8), (8 + step, 7), (8 + step, 8)):
            rows[y][x] = (*(VIOLET_GLOW if lit else VIOLET_LIGHT), 255)
    for y in (2, 13):
        for x in (2, 13):
            rows[y][x] = (*GOLD_LIGHT, 255)
    if lit:
        for y in range(3, 13):
            rows[y][5] = (*VIOLET, 255)
            rows[y][10] = (*VIOLET, 255)
    return rows


def core_top(lit):
    rows = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            base = GOLD
            if x in (0, 15) or y in (0, 15):
                base = GOLD_EDGE
            elif x in (1, 14) or y in (1, 14):
                base = GOLD_DARK
            rows[y][x] = (*shift(base, noise(x, y)), 255)
    ring(rows, 7.5, 7.5, 6.2, GOLD_DARK, 1.3)
    ring(rows, 7.5, 7.5, 4.6, GOLD_EDGE if not lit else VIOLET, 1.0)
    disc(rows, 7.5, 7.5, 3.2, VIOLET_DARK)
    disc(rows, 7.5, 7.5, 2.3, VIOLET if not lit else VIOLET_LIGHT)
    disc(rows, 7.5, 7.5, 1.0, VIOLET_GLOW)
    for dx, dy in ((0, -5), (0, 5), (-5, 0), (5, 0)):
        rows[7 + dy][7 + dx] = (*GOLD_LIGHT, 255)
    return rows


def core_bottom():
    rows = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            base = GOLD_DARK if (x + y) % 2 else shift(GOLD_DARK, -12)
            if x in (0, 15) or y in (0, 15):
                base = GOLD_EDGE
            rows[y][x] = (*shift(base, noise(x, y, 4)), 255)
    disc(rows, 7.5, 7.5, 2.4, VIOLET_DARK)
    return rows


# --- pillar ----------------------------------------------------------------------------------
def pillar_plinth(lit):
    rows = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            base = GOLD
            if x in (0, 15) or y in (0, 15):
                base = GOLD_EDGE
            elif x in (1, 14) or y in (1, 14):
                base = GOLD_DARK
            rows[y][x] = (*shift(base, noise(x, y)), 255)
    for y in (4, 11):
        for x in (4, 11):
            rows[y][x] = (*GOLD_LIGHT, 255)
    ring(rows, 7.5, 7.5, 4.4, VIOLET_LIGHT if lit else GOLD_DARK, 1.2)
    disc(rows, 7.5, 7.5, 1.6, VIOLET_GLOW if lit else VIOLET)
    return rows


def pillar_cap(lit):
    rows = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            base = GOLD
            if x in (0, 15) or y in (0, 15):
                base = GOLD_EDGE
            rows[y][x] = (*shift(base, noise(x, y, 5)), 255)
    ring(rows, 7.5, 7.5, 6.6, GOLD_EDGE, 1.2)
    disc(rows, 7.5, 7.5, 4.6, VIOLET_DARK)
    disc(rows, 7.5, 7.5, 3.0, VIOLET_LIGHT if lit else VIOLET)
    for step, color in ((3, VIOLET_GLOW if lit else VIOLET_LIGHT), (2, (255, 250, 255))):
        for x, y in ((7 - step, 7), (8 + step, 7), (7, 7 + step), (7, 7 - step),
                     (8, 8 + step), (8, 8 - step), (7 - step, 8), (8 + step, 8)):
            rows[y][x] = (*color, 255)
    disc(rows, 7.5, 7.5, 1.0, (255, 255, 255))
    return rows


# --- brick -----------------------------------------------------------------------------------
def brick(lit):
    rows = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            row = y // 4
            offset = 0 if row % 2 == 0 else 4
            mortar = (y % 4 == 0) or ((x + offset) % 8 == 0)
            base = COPPER_DARK if mortar else (SLATE if row % 2 == 0 else SLATE_DARK)
            rows[y][x] = (*shift(base, noise(x, y, 5)), 255)
    # Copper rune in the middle tile.
    rune = VIOLET_GLOW if lit else COPPER
    for x, y in ((7, 5), (8, 5), (6, 6), (9, 6), (7, 7), (8, 7), (6, 8), (9, 8), (7, 9), (8, 9)):
        rows[y][x] = (*rune, 255)
    if lit:
        for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
            rows[y][x] = (*VIOLET_LIGHT, 255)
    return rows




# --- pyramid body (the corner blocks) --------------------------------------------------------
def pillar_body(lit):
    rows = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            band = (x % 8 == 0) or (y % 8 == 0)
            base = SLATE if not band else SLATE_DARK
            if x in (0, 15) or y in (0, 15):
                base = GOLD_EDGE
            elif x in (1, 14) or y in (1, 14):
                base = COPPER_DARK
            rows[y][x] = (*shift(base, noise(x, y, 4)), 255)
    for y in range(2, 14):
        for x in (7, 8):
            rows[y][x] = (*((VIOLET_GLOW) if lit else GOLD), 255)
    for y in (4, 11):
        for x in range(3, 13):
            rows[y][x] = (*((VIOLET_LIGHT) if lit else COPPER), 255)
    return rows


# --- pedestal --------------------------------------------------------------------------------
def pedestal_base(lit):
    rows = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            base = COPPER
            if x in (0, 15) or y in (0, 15):
                base = COPPER_DARK
            rows[y][x] = (*shift(base, noise(x, y, 4)), 255)
    ring(rows, 7.5, 7.5, 5.0, GOLD_DARK, 1.2)
    for y in (3, 12):
        for x in (3, 12):
            rows[y][x] = (*GOLD_LIGHT, 255)
    return rows


def pedestal_side(lit):
    rows = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            base = QUARTZ if x not in (0, 15) else GOLD_DARK
            if x in (7, 8):
                base = VIOLET_DARK
            rows[y][x] = (*shift(base, noise(x, y, 4)), 255)
    for y in range(4, 12):
        rows[y][7] = (*((VIOLET_GLOW) if lit else VIOLET), 255)
        rows[y][8] = (*((VIOLET_LIGHT) if lit else VIOLET_DARK), 255)
    return rows


def pedestal_top(lit):
    rows = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            base = GOLD
            if x in (0, 15) or y in (0, 15):
                base = GOLD_EDGE
            rows[y][x] = (*shift(base, noise(x, y, 4)), 255)
    ring(rows, 7.5, 7.5, 5.2, GOLD_EDGE, 1.2)
    ring(rows, 7.5, 7.5, 2.6, VIOLET_LIGHT if lit else VIOLET)
    disc(rows, 7.5, 7.5, 1.0, VIOLET_GLOW)
    return rows


def ring_metal():
    """A forged brass plate: warm metal bands, rivets and dark seams along the circumference."""
    rows = []
    for y in range(SIZE):
        row = []
        band = y % 8
        base = GOLD if band < 5 else GOLD_DARK
        if band in (0, 7):
            base = GOLD_EDGE
        for x in range(SIZE):
            value = shift(base, noise(x, y, 9))
            if x % 8 == 0:
                value = shift(GOLD_EDGE, noise(x, y, 3))
            if (x % 8 == 4) and band in (2, 5):
                value = GOLD_LIGHT
            row.append((*value, 255))
        rows.append(row)
    return rows


write_png(BLOCK / 'matrix_core_side.png', core_side(False))
write_png(BLOCK / 'matrix_core_side_lit.png', core_side(True))
write_png(BLOCK / 'matrix_core_top.png', core_top(False))
write_png(BLOCK / 'matrix_core_top_lit.png', core_top(True))
write_png(BLOCK / 'matrix_core_bottom.png', core_bottom())
write_png(BLOCK / 'matrix_pillar_plinth.png', pillar_plinth(False))
write_png(BLOCK / 'matrix_pillar_plinth_lit.png', pillar_plinth(True))
write_png(BLOCK / 'matrix_pillar_cap.png', pillar_cap(False))
write_png(BLOCK / 'matrix_pillar_cap_lit.png', pillar_cap(True))
write_png(BLOCK / 'alchemical_brick.png', brick(False))
write_png(BLOCK / 'alchemical_brick_lit.png', brick(True))
write_png(BLOCK / 'matrix_pillar_body.png', pillar_body(False))
write_png(BLOCK / 'matrix_pillar_body_lit.png', pillar_body(True))
write_png(BLOCK / 'matrix_pedestal_base.png', pedestal_base(False))
write_png(BLOCK / 'matrix_pedestal_side.png', pedestal_side(False))
write_png(BLOCK / 'matrix_pedestal_side_lit.png', pedestal_side(True))
write_png(BLOCK / 'matrix_pedestal_top.png', pedestal_top(False))
write_png(BLOCK / 'matrix_ring_metal.png', ring_metal())


# --- models ----------------------------------------------------------------------------------
def cube_model(top, bottom, side):
    faces = {}
    for face in FACES:
        ref = 'top' if face == 'up' else ('bottom' if face == 'down' else 'side')
        faces[face] = {"texture": f"#{ref}", "uv": [0, 0, 16, 16], "cullface": face}
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "top": f"alkahest:block/{top}",
            "bottom": f"alkahest:block/{bottom}",
            "side": f"alkahest:block/{side}",
            "particle": f"alkahest:block/{side}",
        },
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}],
    }


def pillar_model(lit):
    """A stepped pyramid: the corner block the rings rest on when the Matrix sleeps."""
    body = 'matrix_pillar_body_lit' if lit else 'matrix_pillar_body'
    plinth = 'matrix_pillar_plinth_lit' if lit else 'matrix_pillar_plinth'
    cap = 'matrix_pillar_cap_lit' if lit else 'matrix_pillar_cap'

    def element(frm, to, top, side, bottom=None):
        faces = {"up": {"texture": f"#{top}"}, "down": {"texture": f"#{bottom or 'plinth'}"}}
        for face in ("north", "south", "east", "west"):
            faces[face] = {"texture": f"#{side}"}
        for face, data in faces.items():
            data["uv"] = [0, 0, 16, 16]
            if face in ("up", "down"):
                data["cullface"] = face
        return {"from": list(frm), "to": list(to), "faces": faces}

    return {
        "parent": "minecraft:block/block",
        "textures": {
            "body": f"alkahest:block/{body}",
            "plinth": f"alkahest:block/{plinth}",
            "cap": f"alkahest:block/{cap}",
            "particle": f"alkahest:block/{body}",
        },
        "elements": [
            element((0, 0, 0), (16, 3, 16), "plinth", "plinth"),
            element((2, 3, 2), (14, 7, 14), "body", "body"),
            element((4, 7, 4), (12, 11, 12), "body", "body"),
            element((6, 11, 6), (10, 15, 10), "cap", "body"),
            element((7, 15, 7), (9, 16, 9), "cap", "cap"),
        ],
    }


def pedestal_model(lit):
    side = 'matrix_pedestal_side_lit' if lit else 'matrix_pedestal_side'

    def element(frm, to, top, side_texture, bottom):
        faces = {"up": {"texture": f"#{top}"}, "down": {"texture": f"#{bottom or 'base'}"}}
        for face in ("north", "south", "east", "west"):
            faces[face] = {"texture": f"#{side_texture}"}
        for face, data in faces.items():
            data["uv"] = [0, 0, 16, 16]
        return {"from": list(frm), "to": list(to), "faces": faces}

    return {
        "parent": "minecraft:block/block",
        "textures": {
            "base": "alkahest:block/matrix_pedestal_base",
            "side": f"alkahest:block/{side}",
            "top": "alkahest:block/matrix_pedestal_top",
            "particle": f"alkahest:block/{side}",
        },
        "elements": [
            element((3, 0, 3), (13, 2, 13), "base", "base", "base"),
            element((5, 2, 5), (11, 10, 11), "side", "side", "base"),
            element((3, 10, 3), (13, 12, 13), "top", "base", "base"),
        ],
    }


MODELS.mkdir(parents=True, exist_ok=True)
(MODELS / 'matrix_core.json').write_text(json.dumps(
    cube_model('matrix_core_top', 'matrix_core_bottom', 'matrix_core_side'), indent=2) + "\n")
(MODELS / 'matrix_core_lit.json').write_text(json.dumps(
    cube_model('matrix_core_top_lit', 'matrix_core_bottom', 'matrix_core_side_lit'), indent=2) + "\n")
(MODELS / 'matrix_pillar.json').write_text(json.dumps(pillar_model(False), indent=2) + "\n")
(MODELS / 'matrix_pillar_lit.json').write_text(json.dumps(pillar_model(True), indent=2) + "\n")
(MODELS / 'matrix_hidden.json').write_text(json.dumps({
    "parent": "minecraft:block/block",
    "textures": {"particle": "alkahest:block/matrix_pillar_body"},
    "elements": [],
}, indent=2) + "\n")
(MODELS / 'matrix_pedestal.json').write_text(json.dumps(pedestal_model(False), indent=2) + "\n")
(MODELS / 'matrix_pedestal_lit.json').write_text(json.dumps(pedestal_model(True), indent=2) + "\n")
(MODELS / 'alchemical_brick.json').write_text(json.dumps(
    cube_model('alchemical_brick', 'alchemical_brick', 'alchemical_brick'), indent=2) + "\n")
(MODELS / 'alchemical_brick_lit.json').write_text(json.dumps(
    cube_model('alchemical_brick_lit', 'alchemical_brick', 'alchemical_brick_lit'), indent=2) + "\n")

(STATES / 'matrix_core.json').write_text(json.dumps({
    "variants": {
        "awake=false,active=false": {"model": "alkahest:block/matrix_core"},
        "awake=false,active=true": {"model": "alkahest:block/matrix_core"},
        "awake=true,active=false": {"model": "alkahest:block/matrix_core_lit"},
        "awake=true,active=true": {"model": "alkahest:block/matrix_core_lit"},
    }
}, indent=2) + "\n")
(STATES / 'matrix_pedestal.json').write_text(json.dumps({
    "variants": {
        "": {"model": "alkahest:block/matrix_pedestal"},
    }
}, indent=2) + "\n")
(STATES / 'matrix_pillar.json').write_text(json.dumps({
    "variants": {
        "active=false,revealed=false": {"model": "alkahest:block/matrix_pillar"},
        "active=true,revealed=false": {"model": "alkahest:block/matrix_pillar_lit"},
        "active=false,revealed=true": {"model": "alkahest:block/matrix_hidden"},
        "active=true,revealed=true": {"model": "alkahest:block/matrix_hidden"},
    }
}, indent=2) + "\n")
(STATES / 'alchemical_brick.json').write_text(json.dumps({
    "variants": {
        "active=false,revealed=false": {"model": "alkahest:block/alchemical_brick"},
        "active=true,revealed=false": {"model": "alkahest:block/alchemical_brick_lit"},
        "active=false,revealed=true": {"model": "alkahest:block/matrix_hidden"},
        "active=true,revealed=true": {"model": "alkahest:block/matrix_hidden"},
    }
}, indent=2) + "\n")

ITEM_MODELS.mkdir(parents=True, exist_ok=True)
for name in ('matrix_core', 'matrix_pillar', 'matrix_pedestal', 'alchemical_brick'):
    parent = name
    (ITEM_MODELS / f'{name}.json').write_text(json.dumps(
        {"parent": f"alkahest:block/{parent}"}, indent=2) + "\n")

print('matrix assets written')
