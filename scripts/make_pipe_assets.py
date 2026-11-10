#!/usr/bin/env python3
"""Assets for the self-routing essence pipes.

The pipe is a small copper fitting with glass arms; every arm is shown when the matching
connection property is true. When the network is moving essence the `active` property adds a
tinted inner tube that animates (four-frame scrolling highlight), so a working pipe is
visible at a glance.
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

FACES = ("north", "south", "east", "west", "up", "down")

# --- animated flow texture: a bright band travels along the tube ------------------------------
WIDTH, HEIGHT, FRAMES = 16, 16, 4


def flow_pixels():
    pixels = []
    for frame in range(FRAMES):
        rows = []
        centre = frame * (HEIGHT / FRAMES)
        for y in range(HEIGHT):
            row = []
            distance = min(abs(y - centre), HEIGHT - abs(y - centre))
            glow = max(0.0, 1.0 - distance / 4.0)
            value = int(170 + 85 * glow)
            for x in range(WIDTH):
                shimmer = 6 if (x * 7 + y * 3 + frame) % 11 == 0 else 0
                level = min(255, value + shimmer)
                row.append((level, level, level, 255))
            rows.append(row)
        pixels.append(rows)
    return pixels


def write_png(path, rows):
    height, width = len(rows), len(rows[0])
    body = b''.join(b'\x00' + b''.join(bytes(px) for px in row) for row in rows)

    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

    header = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header)
                     + chunk(b'IDAT', zlib.compress(body)) + chunk(b'IEND', b''))


frames = flow_pixels()
write_png(BLOCK / 'pipe_flow.png', [row for frame in frames for row in frame])
(BLOCK / 'pipe_flow.png.mcmeta').write_text(json.dumps({
    "animation": {"frametime": 2, "interpolate": True, "frames": list(range(FRAMES))}
}, indent=2) + "\n")

# --- models ----------------------------------------------------------------------------------
GLASS = "minecraft:block/glass"
COPPER = "minecraft:block/copper_block"
FLOW = "alkahest:block/pipe_flow"
ARM = 6        # arms occupy 6..10, so the pipe is a little smaller than a full block
INNER, GLOW = 6.5, 9.5   # the animated tube runs inside the glass at 6.5..9.5


def box(frm, to, texture, tint=False):
    faces = {face: {"texture": texture} for face in FACES}
    if tint:
        for face in faces.values():
            face["tintindex"] = 0
    return {"from": list(frm), "to": list(to), "faces": faces}


def model(textures, elements):
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def write(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n")


SIDES = {
    "north": ((ARM, ARM, 0), (16 - ARM, 16 - ARM, ARM)),
    "south": ((ARM, ARM, 16 - ARM), (16 - ARM, 16 - ARM, 16)),
    "west": ((0, ARM, ARM), (ARM, 16 - ARM, 16 - ARM)),
    "east": ((16 - ARM, ARM, ARM), (16, 16 - ARM, 16 - ARM)),
    "down": ((ARM, 0, ARM), (16 - ARM, ARM, 16 - ARM)),
    "up": ((ARM, 16 - ARM, ARM), (16 - ARM, 16, 16 - ARM)),
}
FLOW_SIDES = {
    "north": ((INNER, INNER, 0), (GLOW, GLOW, INNER)),
    "south": ((INNER, INNER, GLOW), (GLOW, GLOW, 16)),
    "west": ((0, INNER, INNER), (INNER, GLOW, GLOW)),
    "east": ((GLOW, INNER, INNER), (16, GLOW, GLOW)),
    "down": ((INNER, 0, INNER), (GLOW, INNER, GLOW)),
    "up": ((INNER, GLOW, INNER), (GLOW, 16, GLOW)),
}

pipe_textures = {"glass": GLASS, "copper": COPPER, "particle": COPPER}
write(MODELS / 'alchemy_pipe_core.json', model(pipe_textures, [box((ARM, ARM, ARM), (16 - ARM, 16 - ARM, 16 - ARM), "#copper")]))
for side, (frm, to) in SIDES.items():
    write(MODELS / f'pipe_{side}.json', model(pipe_textures, [box(frm, to, "#glass")]))

flow_textures = {"flow": FLOW, "particle": COPPER}
write(MODELS / 'alchemy_pipe_flow_core.json',
      model(flow_textures, [box((INNER, INNER, INNER), (GLOW, GLOW, GLOW), "#flow", tint=True)]))
for side, (frm, to) in FLOW_SIDES.items():
    write(MODELS / f'pipe_{side}_flow.json', model(flow_textures, [box(frm, to, "#flow", tint=True)]))

write(ITEM_MODELS / 'alchemy_pipe.json', model(pipe_textures, [
    box((ARM, ARM, ARM), (16 - ARM, 16 - ARM, 16 - ARM), "#copper"),
    box(*SIDES["north"], "#glass"),
    box(*SIDES["south"], "#glass"),
    box(*SIDES["up"], "#glass"),
    box(*SIDES["down"], "#glass"),
]))

# --- block state: arms follow the connection properties, the glow follows `active` ------------
multipart = [{"apply": {"model": "alkahest:block/alchemy_pipe_core"}}]
for side in SIDES:
    multipart.append({"when": {side: "true"}, "apply": {"model": f"alkahest:block/pipe_{side}"}})
multipart.append({"when": {"active": "true"}, "apply": {"model": "alkahest:block/alchemy_pipe_flow_core"}})
for side in FLOW_SIDES:
    multipart.append({"when": {"active": "true", side: "true"},
                      "apply": {"model": f"alkahest:block/pipe_{side}_flow"}})
write(STATES / 'alchemy_pipe.json', {"multipart": multipart})
print('pipe assets written')
