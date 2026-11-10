#!/usr/bin/env python3
"""Render a block model to a small PNG so models can be checked without the game.

This is a development aid only: it draws the axis-aligned boxes of a model with an
isometric camera, samples the referenced 16x16 textures and applies tintindex 0 with
a colour supplied on the command line. Vanilla textures that are not part of this
repository are replaced with a stand-in (currently only minecraft:block/glass).
"""
import json
import struct
import sys
import zlib
from pathlib import Path

A = Path('src/main/resources/assets/alkahest')

FACES = ("north", "south", "east", "west", "up", "down")
SHADE = {"up": 1.0, "down": 0.55, "north": 0.86, "south": 0.72, "east": 0.66, "west": 0.8}


def read_png(path):
    data = path.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    offset, dims, kind, raw = 8, None, None, bytearray()
    while offset < len(data):
        size = struct.unpack_from('>I', data, offset)[0]
        tag = data[offset+4:offset+8]
        chunk = data[offset+8:offset+8+size]
        if tag == b'IHDR':
            width, height, depth, kind = struct.unpack_from('>IIBB', chunk)
            assert depth == 8 and kind in (2, 6), (depth, kind)
        elif tag == b'IDAT':
            raw.extend(chunk)
        offset += size + 12
        if tag == b'IEND':
            break
    raw = zlib.decompress(raw)
    channels = 4 if kind == 6 else 3
    stride = width * channels
    rows, prior = [], [0] * stride
    for y in range(height):
        line = list(raw[y*(stride+1)+1:(y+1)*(stride+1)])
        kind_byte = raw[y*(stride+1)]
        for x, value in enumerate(line):
            left = line[x-channels] if x >= channels else 0
            above = prior[x]
            upper_left = prior[x-channels] if x >= channels else 0
            if kind_byte == 1: value += left
            elif kind_byte == 2: value += above
            elif kind_byte == 3: value += (left+above)//2
            elif kind_byte == 4:
                guess = left+above-upper_left
                distances = (abs(guess-left), abs(guess-above), abs(guess-upper_left))
                value += (left, above, upper_left)[distances.index(min(distances))]
            else: assert kind_byte == 0, f'Unknown PNG filter {kind_byte}'
            line[x] = value & 255
        prior = line
        rows.append([tuple(line[i:i+channels]) for i in range(0, stride, channels)])
    return width, height, rows


def write_png(path, width, height, rows):
    body = b''.join(b'\x00' + b''.join(bytes(px) for px in row) for row in rows)

    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag+data) & 0xffffffff)

    header = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header) + chunk(b'IDAT', zlib.compress(body)) + chunk(b'IEND', b''))


def stand_in(name):
    """Vanilla textures are not shipped here; draw a believable substitute."""
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            row.append((196, 224, 224, 170 if edge else 28))
        rows.append(row)
    return 16, 16, rows


_cache = {}


def texture(name):
    if name in _cache:
        return _cache[name]
    if name.startswith('minecraft:'):
        result = stand_in(name)
    else:
        result = read_png(A / 'textures' / (name.split(':')[-1] + '.png'))
    _cache[name] = result
    return result


def load_model(path):
    data = json.loads((A / 'models' / (path.split(':')[-1] + '.json')).read_text())
    textures, elements = {}, []
    parent = data.get('parent')
    if parent and parent != 'minecraft:block/block':
        textures, elements = load_model(parent)
    textures.update(data.get('textures', {}))
    elements = data.get('elements', elements)
    return textures, elements


def face_quad(frm, to, face):
    x0, y0, z0 = frm
    x1, y1, z1 = to
    return {
        "north": [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)],
        "south": [(x1, y0, z1), (x0, y0, z1), (x0, y1, z1), (x1, y1, z1)],
        "west": [(x0, y0, z1), (x0, y0, z0), (x0, y1, z0), (x0, y1, z1)],
        "east": [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def render(model_name, out, size=280, tint=(120, 64, 190), background=(26, 26, 30)):
    textures, elements = load_model(model_name)
    scale = size / 34.0
    cx, cy = size / 2, size / 2 + 8
    faces = []
    for el in elements:
        for face in FACES:
            if face not in el['faces']:
                continue
            data = el['faces'][face]
            ref = data['texture'].lstrip('#')
            name = textures.get(ref, ref)
            quad = face_quad(el['from'], el['to'], face)
            projected = [((x - z) * 0.8660254 * scale + cx, ((x + z) * 0.5 - y) * scale + cy) for x, y, z in quad]
            depth = sum((x + y + z) / 3 for x, y, z in quad)
            faces.append((depth, projected, texture(name), data.get('tintindex') == 0, SHADE[face]))
    faces.sort(key=lambda item: item[0])

    pixels = [[(*background, 255) for _ in range(size)] for _ in range(size)]
    for _, quad, tex, tinted, shade in faces:
        width, height, rows = tex
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = (quad[i] for i in tri)
            uv = {0: (0, 0), 1: (1, 0), 2: (1, 1), 3: (0, 1)}
            (u0, v0), (u1, v1), (u2, v2) = (uv[i] for i in tri)
            det = (b[0]-a[0])*(c[1]-a[1]) - (c[0]-a[0])*(b[1]-a[1])
            if abs(det) < 1e-6:
                continue
            min_x = max(0, int(min(a[0], b[0], c[0]))-1)
            max_x = min(size-1, int(max(a[0], b[0], c[0]))+1)
            min_y = max(0, int(min(a[1], b[1], c[1]))-1)
            max_y = min(size-1, int(max(a[1], b[1], c[1]))+1)
            for py in range(min_y, max_y+1):
                for px in range(min_x, max_x+1):
                    wx, wy = px+0.5-a[0], py+0.5-a[1]
                    w1 = (wx*(c[1]-a[1]) - wy*(c[0]-a[0])) / det
                    w2 = (wy*(b[0]-a[0]) - wx*(b[1]-a[1])) / det
                    if w1 < 0 or w2 < 0 or w1+w2 > 1:
                        continue
                    u = u0 + w1*(u1-u0) + w2*(u2-u0)
                    v = v0 + w1*(v1-v0) + w2*(v2-v0)
                    sx = min(width-1, max(0, int(u*width)))
                    sy = min(height-1, max(0, int(v*height)))
                    sample = rows[sy][sx]
                    if len(sample) == 3:
                        sample = (*sample, 255)
                    alpha = sample[3] / 255.0
                    if alpha < 0.04:
                        continue
                    colour = [sample[c]*shade for c in range(3)]
                    if tinted:
                        colour = [colour[c]*tint[c]/255.0 for c in range(3)]
                    base = pixels[py][px]
                    pixels[py][px] = (
                        [min(255, int(colour[c]*alpha + base[c]*(1-alpha))) for c in range(3)]
                        + [255]
                    )
    write_png(Path(out), size, size, pixels)
    print('rendered', model_name, '->', out)


if __name__ == '__main__':
    model = sys.argv[1] if len(sys.argv) > 1 else 'alkahest:block/distillery_bellows_3'
    out = sys.argv[2] if len(sys.argv) > 2 else 'preview.png'
    tint = tuple(int(sys.argv[3].lstrip('#')[i:i+2], 16) for i in (0, 2, 4)) if len(sys.argv) > 3 else (120, 64, 190)
    render(model, out, tint=tint)
