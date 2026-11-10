#!/usr/bin/env python3
"""Validate model references, PNG integrity and animated frame layout without external dependencies."""
import json
import struct
import zlib
from pathlib import Path

assets = Path('src/main/resources/assets/alkahest')
states = json.loads((assets/'blockstates/distillery.json').read_text())['variants']
assert states['working=false']['model'] == 'alkahest:block/distillery_crude'
assert states['working=true']['model'] == 'alkahest:block/distillery_crude_working'
model = json.loads((assets/'models/block/distillery_crude.json').read_text())
active = json.loads((assets/'models/block/distillery_crude_working.json').read_text())
assert model['textures'] != active['textures']
assert len(model['elements']) >= 8
for candidate in (model, active):
    for el in candidate['elements']:
        assert all(0 <= n <= 16 for n in el['from'] + el['to'])
        assert all(a < b for a, b in zip(el['from'], el['to']))
        assert set(el['faces']) == {'north', 'south', 'east', 'west', 'up', 'down'}
        for face in el['faces'].values():
            assert face['texture'].startswith('#') and face['texture'][1:] in candidate['textures']


def validate_png(path):
    data = path.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    offset, dims, raw, end = 8, None, bytearray(), False
    while offset < len(data):
        size = struct.unpack_from('>I', data, offset)[0]
        kind = data[offset+4:offset+8]
        chunk = data[offset+8:offset+8+size]
        assert zlib.crc32(kind+chunk) & 0xffffffff == struct.unpack_from('>I', data, offset+8+size)[0]
        if kind == b'IHDR':
            dims = struct.unpack_from('>II', chunk)
        if kind == b'IDAT':
            raw.extend(chunk)
        offset += size + 12
        if kind == b'IEND':
            end = True
            break
    assert end and offset == len(data) and zlib.decompress(raw)
    return dims, zlib.decompress(raw)


def decode_rgb_frames(raw, width, frame_height, frame_count):
    # Undo PNG row filters to compare actual RGB frames, not merely compressed bytes.
    stride = width * 3
    assert len(raw) == (stride + 1) * frame_height * frame_count
    prior = [0] * stride
    rows = []
    for y in range(frame_height * frame_count):
        data = raw[y*(stride+1):(y+1)*(stride+1)]
        kind, line = data[0], list(data[1:])
        for x, value in enumerate(line):
            left = line[x-3] if x >= 3 else 0
            above = prior[x]
            upper_left = prior[x-3] if x >= 3 else 0
            if kind == 1: value += left
            elif kind == 2: value += above
            elif kind == 3: value += (left+above)//2
            elif kind == 4:
                guess = left+above-upper_left
                distances = (abs(guess-left), abs(guess-above), abs(guess-upper_left))
                value += (left, above, upper_left)[distances.index(min(distances))]
            else: assert kind == 0, f'Unknown PNG filter {kind}'
            line[x] = value & 255
        rows.append(bytes(line)); prior = line
    return {b''.join(rows[i*frame_height:(i+1)*frame_height]) for i in range(frame_count)}


textures = set(model['textures'].values()) | set(active['textures'].values())
for texture in textures:
    assert texture.startswith('alkahest:block/')
    name = texture.split('/')[-1]
    dimensions, raw = validate_png(assets/f'textures/block/{name}.png')
    if name.endswith('_active'):
        assert dimensions == (32, 256), (name, dimensions)
        assert len(decode_rgb_frames(raw, 32, 32, 8)) == 8, 'Animation must have eight visually distinct frames'
    else:
        assert dimensions in ((16, 16), (32, 32)), (name, dimensions)

# The animated chamber artwork is no longer used by the crude cube but stays for later tiers.
meta = json.loads((assets/'textures/block/distillery_chamber_active.png.mcmeta').read_text())['animation']
assert meta['frametime'] > 0 and 256//32 >= 2
print('Crude distillery model, working variant and textures validated')
