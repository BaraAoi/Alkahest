#!/usr/bin/env python3
"""Validate distillery, bellows and receiver models, PNG integrity and animation layout."""
import json
import struct
import zlib
from pathlib import Path

assets = Path('src/main/resources/assets/alkahest')


def load(path):
    return json.loads((assets/path).read_text())


def model(name):
    data = load(f'models/block/{name}.json')
    if 'elements' not in data:
        parent = data['parent'].split('/')[-1]
        base = model(parent)
        textures = dict(base['textures'])
        textures.update(data.get('textures', {}))
        return {'textures': textures, 'elements': base['elements']}
    return data


# The crude tier-1 cube keeps the dented copper artwork the player asked to retain.
states = load('blockstates/distillery.json')['variants']
assert states['working=false']['model'] == 'alkahest:block/distillery_crude'
assert states['working=true']['model'] == 'alkahest:block/distillery_crude_working'
assert load('models/item/distillery.json')['parent'] == 'alkahest:block/distillery_crude'
crude_idle, crude_active = model('distillery_crude'), model('distillery_crude_working')
assert crude_idle['textures']['copper'] == 'alkahest:block/distillery_crude'
assert crude_active['textures']['essence'] == 'alkahest:block/jar_essence'
assert crude_idle['textures'] != crude_active['textures']
assert len(crude_idle['elements']) >= 8
for candidate in (crude_idle, crude_active):
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


textures = set(crude_idle['textures'].values()) | set(crude_active['textures'].values())
for texture in textures:
    assert texture.startswith('alkahest:block/')
    name = texture.split('/')[-1]
    dimensions, raw = validate_png(assets/f'textures/block/{name}.png')
    if name.endswith('_active'):
        assert dimensions == (32, 256), (name, dimensions)
        assert len(decode_rgb_frames(raw, 32, 32, 8)) == 8, 'Animation must have eight visually distinct frames'
    else:
        assert dimensions in ((16, 16), (32, 32)), (name, dimensions)
brass = load('models/block/distillery.json')
assert brass['textures']['chamber'] == 'alkahest:block/distillery_chamber_idle'
assert load('models/block/distillery_working.json')['textures']['chamber'] == 'alkahest:block/distillery_chamber_active'
meta = load('textures/block/distillery_chamber_active.png.mcmeta')['animation']
assert meta['frametime'] > 0 and 256//32 >= 2

# Tier-2 reuses the brass cube: only the blockstate and the item differ.
tier2 = load('blockstates/distillery_tier2.json')['variants']
assert tier2['working=false']['model'] == 'alkahest:block/distillery'
assert tier2['working=true']['model'] == 'alkahest:block/distillery_working'
assert load('models/item/distillery_tier2.json')['parent'] == 'alkahest:block/distillery'

# Bellows: a glass flask smaller than a block with a tinted essence column inside.
bellows_states = load('blockstates/distillery_bellows.json')['variants']
tops = []
for level in range(4):
    name = bellows_states[f'level={level}']['model'].split('/')[-1]
    assert name == f'distillery_bellows_{level}', name
    bellows = load(f'models/block/{name}.json')
    assert bellows['textures']['glass'] == 'minecraft:block/glass', name
    assert bellows['textures']['essence'] == 'alkahest:block/jar_essence', name
    assert bellows['textures']['iron'] == 'alkahest:block/bellows_iron', name
    liquid = [el for el in bellows['elements']
              if any(face.get('tintindex') == 0 for face in el['faces'].values())]
    for el in bellows['elements']:
        assert all(0 <= n <= 16 for n in el['from'] + el['to']), name
        assert all(a < b for a, b in zip(el['from'], el['to'])), name
        for face in el['faces'].values():
            assert face['texture'].startswith('#') and face['texture'][1:] in bellows['textures'], name
    if level == 0:
        assert not liquid, 'an empty flask must not render liquid'
        continue
    assert len(liquid) == 1, name
    flask = [el for el in bellows['elements'] if el['faces']['up']['texture'] == '#glass']
    assert flask, name
    body = max(flask, key=lambda el: (el['to'][1] - el['from'][1]) * (el['to'][0] - el['from'][0]))
    column = liquid[0]
    # the liquid stays inside the glass and never covers the neck
    assert body['from'][0] < column['from'][0] and body['from'][2] < column['from'][2], name
    assert body['to'][0] > column['to'][0] and body['to'][2] > column['to'][2], name
    assert body['to'][1] > column['to'][1] > 4, name
    tops.append(column['to'][1])
assert tops == sorted(tops) and len(set(tops)) == 3, tops
assert load('models/item/distillery_bellows.json')['parent'] == 'alkahest:block/distillery_bellows_2'
validate_png(assets/'textures/block/bellows_iron.png')

# Stabilizer receiver: real glass plus a tinted liquid that uses the jar essence art.
receiver_states = load('blockstates/stabilizer_receiver.json')['variants']
occupied = receiver_states['occupied=true']['model'].split('/')[-1]
empty = receiver_states['occupied=false']['model'].split('/')[-1]
assert (empty, occupied) == ('stabilizer_receiver', 'stabilizer_receiver_occupied')
for name in (empty, occupied):
    data = load(f'models/block/{name}.json')
    assert data['textures']['glass'] == 'minecraft:block/glass', name
    assert data['textures']['essence'] == 'alkahest:block/jar_essence', name
    tinted = [face for el in data['elements']
              for face in el['faces'].values()
              if face.get('tintindex') == 0]
    if name == occupied:
        assert tinted, 'the liquid must be tinted by the block color provider'
    else:
        assert not tinted, 'an empty receiver must not render liquid'
    for el in data['elements']:
        assert all(0 <= n <= 16 for n in el['from'] + el['to'])
        assert all(a <= b for a, b in zip(el['from'], el['to']))
assert load(f'models/block/{occupied}.json')['elements'] != load(f'models/block/{empty}.json')['elements']
assert load('models/item/stabilizer_receiver.json')['parent'] == 'alkahest:block/stabilizer_receiver'
print('Crude distillery, glass bellows, receiver and brass tier-2 models validated')
