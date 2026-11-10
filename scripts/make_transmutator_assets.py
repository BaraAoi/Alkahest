#!/usr/bin/env python3
"""Assets for the 3x1x3 Transmutator.

The core is a copper drum with a golden arcane sigil on its lid; the eight pillars are quartz
columns with a crystal channel. Both have an extra "working"/"charged" variant whose sigil and
channel burn bright, so the platform tells at a glance whether essence is flowing.
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
FACES = ("north", "south", "east", "west", "up", "down")

# --- palette ---------------------------------------------------------------------------------
COPPER = (168, 104, 58)
COPPER_DARK = (118, 68, 36)
COPPER_LIGHT = (214, 152, 92)
COPPER_EDGE = (86, 48, 26)
GOLD = (206, 158, 62)
GOLD_BRIGHT = (255, 226, 140)
AMBER = (196, 124, 48)
AMBER_LIT = (255, 214, 118)
QUARTZ = (224, 218, 203)
QUARTZ_DARK = (196, 189, 172)
QUARTZ_LIGHT = (242, 238, 228)
CRYSTAL = (132, 186, 198)
CRYSTAL_LIT = (236, 252, 255)


def noise(x, y, spread=7):
    """Deterministic speckle so the textures are stable between runs."""
    value = (x * 73856093) ^ (y * 19349663)
    return (value % (2 * spread + 1)) - spread


def shift(color, delta):
    return tuple(max(0, min(255, channel + delta)) for channel in color)


def blank(fill=(0, 0, 0, 0)):
    return [[fill for _ in range(SIZE)] for _ in range(SIZE)]


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


# --- core: copper drum ----------------------------------------------------------------------
def core_side(lit):
    rows = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            base = COPPER
            if y in (0, 15) or x in (0, 15):
                base = COPPER_EDGE
            elif y in (1, 14) or x in (1, 14):
                base = COPPER_DARK
            elif y in (4, 11):
                base = COPPER_DARK
            rows[y][x] = (*shift(base, noise(x, y)), 255)
    for y in (2, 13):
        for x in (2, 13):
            rows[y][x] = (*COPPER_LIGHT, 255)
    # The sigil: an amber arcane eye set into the drum.
    glow = (AMBER_LIT if lit else AMBER)
    for y in range(4, 12):
        for x in range(6, 10):
            if (x in (6, 9)) and (y in (4, 11)):
                continue
            rows[y][x] = (*glow, 255)
    for y in (5, 10):
        rows[y][7] = (*GOLD_BRIGHT, 255)
        rows[y][8] = (*GOLD_BRIGHT, 255)
    if lit:
        for y in range(3, 13):
            for x in range(4, 12):
                if rows[y][x][:3] in (AMBER, AMBER_LIT) or rows[y][x][:3] == GOLD_BRIGHT:
                    continue
                if (x + y) % 3 == 0:
                    rows[y][x] = (*AMBER, 255)
    return rows


def core_top(lit):
    rows = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            base = COPPER
            if x in (0, 15) or y in (0, 15):
                base = COPPER_EDGE
            elif x in (1, 14) or y in (1, 14):
                base = COPPER_DARK
            rows[y][x] = (*shift(base, noise(x, y)), 255)
    ring(rows, 7.5, 7.5, 6.2, COPPER_DARK, 1.4)
    ring(rows, 7.5, 7.5, 4.2, GOLD if not lit else GOLD_BRIGHT)
    disc(rows, 7.5, 7.5, 2.6, GOLD_BRIGHT if lit else GOLD)
    disc(rows, 7.5, 7.5, 1.2, (255, 248, 214) if lit else AMBER)
    for dx, dy in ((0, -5), (0, 5), (-5, 0), (5, 0)):
        rows[7 + dy][7 + dx] = (*GOLD_BRIGHT, 255)
        rows[8 + dy // 2][8 + dx // 2] = (*GOLD_BRIGHT, 255)
    return rows


def core_bottom():
    rows = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            base = COPPER_DARK if (x + y) % 2 else shift(COPPER_DARK, -12)
            if x in (0, 15) or y in (0, 15):
                base = COPPER_EDGE
            rows[y][x] = (*shift(base, noise(x, y, 4)), 255)
    disc(rows, 7.5, 7.5, 2.2, COPPER)
    return rows


# --- pillar: an obelisk with a crystal channel ----------------------------------------------
def pillar_shaft(lit):
    rows = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            base = QUARTZ
            if x in (0, 15):
                base = COPPER_DARK
            elif x in (1, 14):
                base = QUARTZ_LIGHT
            elif x in (6, 9):
                base = (120, 176, 190) if not lit else GOLD_BRIGHT
            elif x in (7, 8):
                base = (86, 138, 154) if not lit else (255, 240, 176)
            rows[y][x] = (*shift(base, noise(x, y, 4)), 255)
    for y in (4, 9, 13):
        for x in (6, 7, 8, 9):
            rows[y][x] = (*((255, 250, 220) if lit else GOLD), 255)
    if lit:
        for y in range(SIZE):
            for x in (5, 10):
                rows[y][x] = (*AMBER, 255)
    return rows


def pillar_plinth(lit):
    rows = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            base = COPPER
            if x in (0, 15) or y in (0, 15):
                base = COPPER_EDGE
            elif x in (1, 14) or y in (1, 14):
                base = COPPER_DARK
            rows[y][x] = (*shift(base, noise(x, y)), 255)
    for y in (4, 11):
        for x in (4, 11):
            rows[y][x] = (*COPPER_LIGHT, 255)
    ring(rows, 7.5, 7.5, 4.4, AMBER_LIT if lit else COPPER_DARK, 1.1)
    disc(rows, 7.5, 7.5, 1.6, GOLD_BRIGHT if lit else AMBER)
    return rows


def pillar_cap(lit):
    rows = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            base = QUARTZ
            if x in (0, 15) or y in (0, 15):
                base = QUARTZ_DARK
            rows[y][x] = (*shift(base, noise(x, y, 5)), 255)
    ring(rows, 7.5, 7.5, 6.6, COPPER_DARK, 1.1)
    ring(rows, 7.5, 7.5, 5.0, COPPER if not lit else AMBER)
    core_color = CRYSTAL_LIT if not lit else GOLD_BRIGHT
    for step, color in ((4, CRYSTAL if not lit else AMBER), (3, core_color), (2, core_color),
                        (1, (255, 252, 236)), (0, (255, 252, 236))):
        for x, y in ((7 - step, 7), (8 + step, 8), (7, 8 + step), (8, 7 - step),
                     (7 - step, 8), (8 + step, 7), (8, 8 + step), (7, 7 - step)):
            rows[y][x] = (*color, 255)
    disc(rows, 7.5, 7.5, 1.0, (255, 255, 255) if lit else GOLD_BRIGHT)
    return rows


def pillar_cap_side(lit):
    rows = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            base = COPPER if y < 6 else (COPPER_DARK if (x + y) % 2 else shift(COPPER_DARK, -10))
            if x in (0, 15) or y in (0, 15):
                base = COPPER_EDGE
            rows[y][x] = (*shift(base, noise(x, y, 4)), 255)
    for x in range(SIZE):
        rows[7][x] = (*((255, 232, 160) if lit else AMBER), 255)
        rows[8][x] = (*((255, 226, 140) if lit else GOLD), 255)
    return rows


write_png(BLOCK / 'transmutator_core_side.png', core_side(False))
write_png(BLOCK / 'transmutator_core_side_lit.png', core_side(True))
write_png(BLOCK / 'transmutator_core_top.png', core_top(False))
write_png(BLOCK / 'transmutator_core_top_lit.png', core_top(True))
write_png(BLOCK / 'transmutator_core_bottom.png', core_bottom())
write_png(BLOCK / 'transmutator_pillar_shaft.png', pillar_shaft(False))
write_png(BLOCK / 'transmutator_pillar_shaft_lit.png', pillar_shaft(True))
write_png(BLOCK / 'transmutator_pillar_plinth.png', pillar_plinth(False))
write_png(BLOCK / 'transmutator_pillar_plinth_lit.png', pillar_plinth(True))
write_png(BLOCK / 'transmutator_pillar_cap.png', pillar_cap(False))
write_png(BLOCK / 'transmutator_pillar_cap_lit.png', pillar_cap(True))
write_png(BLOCK / 'transmutator_pillar_cap_side.png', pillar_cap_side(False))
write_png(BLOCK / 'transmutator_pillar_cap_side_lit.png', pillar_cap_side(True))


# --- models ---------------------------------------------------------------------------------
def cube_model(top, bottom, side):
    """Full cube with explicit elements so the preview renderer and the game agree."""
    faces = {}
    for face in ("up", "down", "north", "south", "east", "west"):
        ref = "top" if face == "up" else ("bottom" if face == "down" else "side")
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


MODELS.mkdir(parents=True, exist_ok=True)
(MODELS / 'transmutator_core.json').write_text(json.dumps(
    cube_model('transmutator_core_top', 'transmutator_core_bottom', 'transmutator_core_side'), indent=2) + "\n")
(MODELS / 'transmutator_core_lit.json').write_text(json.dumps(
    cube_model('transmutator_core_top_lit', 'transmutator_core_bottom', 'transmutator_core_side_lit'), indent=2) + "\n")
def pillar_model(lit):
    shaft = 'transmutator_pillar_shaft_lit' if lit else 'transmutator_pillar_shaft'
    plinth = 'transmutator_pillar_plinth_lit' if lit else 'transmutator_pillar_plinth'
    cap = 'transmutator_pillar_cap_lit' if lit else 'transmutator_pillar_cap'
    cap_side = 'transmutator_pillar_cap_side_lit' if lit else 'transmutator_pillar_cap_side'

    def element(frm, to, mapping):
        faces = {}
        for face in ("up", "down", "north", "south", "east", "west"):
            faces[face] = {"texture": f"#{mapping[face]}", "uv": [0, 0, 16, 16]}
            if face in ("up", "down"):
                faces[face]["cullface"] = face
        return {"from": list(frm), "to": list(to), "faces": faces}

    return {
        "parent": "minecraft:block/block",
        "textures": {
            "shaft": f"alkahest:block/{shaft}",
            "plinth": f"alkahest:block/{plinth}",
            "cap": f"alkahest:block/{cap}",
            "cap_side": f"alkahest:block/{cap_side}",
            "particle": f"alkahest:block/{shaft}",
        },
        "elements": [
            element((0, 0, 0), (16, 3, 16),
                    {"up": "plinth", "down": "plinth", "north": "plinth", "south": "plinth",
                     "east": "plinth", "west": "plinth"}),
            element((2, 3, 2), (14, 14, 14),
                    {"up": "cap", "down": "plinth", "north": "shaft", "south": "shaft",
                     "east": "shaft", "west": "shaft"}),
            element((0, 14, 0), (16, 16, 16),
                    {"up": "cap", "down": "plinth", "north": "cap_side", "south": "cap_side",
                     "east": "cap_side", "west": "cap_side"}),
        ],
    }


(MODELS / 'transmutator_pillar.json').write_text(json.dumps(pillar_model(False), indent=2) + "\n")
(MODELS / 'transmutator_pillar_charged.json').write_text(json.dumps(pillar_model(True), indent=2) + "\n")

(STATES / 'transmutator_core.json').write_text(json.dumps({
    "variants": {
        "working=false": {"model": "alkahest:block/transmutator_core"},
        "working=true": {"model": "alkahest:block/transmutator_core_lit"},
    }
}, indent=2) + "\n")
(STATES / 'transmutator_pillar.json').write_text(json.dumps({
    "variants": {
        "charged=false": {"model": "alkahest:block/transmutator_pillar"},
        "charged=true": {"model": "alkahest:block/transmutator_pillar_charged"},
    }
}, indent=2) + "\n")

ITEM_MODELS.mkdir(parents=True, exist_ok=True)
for name, parent in (('transmutator_core', 'transmutator_core'), ('transmutator_pillar', 'transmutator_pillar')):
    (ITEM_MODELS / f'{name}.json').write_text(json.dumps(
        {"parent": f"alkahest:block/{parent}"}, indent=2) + "\n")

print('transmutator assets written')
