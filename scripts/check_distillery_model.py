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


# The crude tier-1 cube and the tier-2 cube share the original brass artwork.
states = load('blockstates/distillery.json')['variants']
assert states['working=false']['model'] == 'alkahest:block/distillery'
assert states['working=true']['model'] == 'alkahest:block/distillery_working'
model_idle = model('distillery')
model_active = model('distillery_working')
assert model_idle['textures']['chamber'] == 'alkahest:block/distillery_chamber_idle'
assert model_active['textures']['chamber'] == 'alkahest:block/distillery_chamber_active'
assert len(model_idle['elements']) >= 8
for candidate in (model_idle, model_active):
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


textures = set(model_idle['textures'].values()) | set(model_active['textures'].values())
for texture in textures:
    assert texture.startswith('alkahest:block/')
    name = texture.split('/')[-1]
    dimensions, raw = validate_png(assets/f'textures/block/{name}.png')
    if name.endswith('_active'):
        assert dimensions == (32, 256), (name, dimensions)
        assert len(decode_rgb_frames(raw, 32, 32, 8)) == 8, 'Animation must have eight visually distinct frames'
    else:
        assert dimensions in ((16, 16), (32, 32)), (name, dimensions)
meta = load('textures/block/distillery_chamber_active.png.mcmeta')['animation']
assert meta['frametime'] > 0 and 256//32 >= 2

# Tier-2 reuses the brass cube: only the blockstate and the item differ.
tier2 = load('blockstates/distillery_tier2.json')['variants']
assert tier2['working=false']['model'] == 'alkahest:block/distillery'
assert tier2['working=true']['model'] == 'alkahest:block/distillery_working'
assert load('models/item/distillery_tier2.json')['parent'] == 'alkahest:block/distillery'

# Bellows: wooden boards, a leather bag and iron ribs; each level must grow the bag.
bellows_states = load('blockstates/distillery_bellows.json')['variants']
heights, ribs = [], []
for level in range(4):
    name = bellows_states[f'level={level}']['model'].split('/')[-1]
    assert name == f'distillery_bellows_{level}', name
    bellows = load(f'models/block/{name}.json')
    assert bellows['textures']['wood'] == 'alkahest:block/bellows_wood'
    assert bellows['textures']['leather'] == 'alkahest:block/bellows_leather'
    assert bellows['textures']['iron'] == 'alkahest:block/bellows_iron'
    leather = [el for el in bellows['elements']
               if any(face['texture'] == '#leather' for face in el['faces'].values())]
    heights.append(max((el['to'][1] for el in leather), default=3))
    ribs.append(sum(1 for el in bellows['elements']
                    if all(face['texture'] == '#iron' for face in el['faces'].values())))
    for el in bellows['elements']:
        assert all(0 <= n <= 16 for n in el['from'] + el['to'])
        assert all(a <= b for a, b in zip(el['from'], el['to']))
assert heights == sorted(heights) and heights[-1] > heights[1] > heights[0], heights
assert ribs == sorted(ribs) and ribs[-1] > ribs[0], ribs
assert load('models/item/distillery_bellows.json')['parent'] == 'alkahest:block/distillery_bellows_2'
total = 0
for texture in ('bellows_wood', 'bellows_leather', 'bellows_iron'):
    validate_png(assets/f'textures/block/{texture}.png')
    total += 1
assert total == 3

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
print('Distillery, bellows and receiver models, working variants and textures validated')
