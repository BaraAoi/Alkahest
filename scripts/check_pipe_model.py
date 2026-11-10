#!/usr/bin/env python3
"""Validate the self-routing pipe assets: multipart arms, working overlay and animation."""
import json
import struct
import zlib
from pathlib import Path

A = Path('src/main/resources/assets/alkahest')
SIDES = ("north", "south", "east", "west", "up", "down")


def load(path):
    return json.loads((A/path).read_text())


states = load('blockstates/alchemy_pipe.json')
assert 'multipart' in states and 'variants' not in states, 'the pipe is a multipart block'
parts = states['multipart']


def find(when):
    for part in parts:
        if part.get('when', {}) == when:
            return part['apply']['model']
    return None


assert find({}) == 'alkahest:block/alchemy_pipe_core', 'always draw the copper fitting'
active_core = find({'active': 'true'})
assert active_core == 'alkahest:block/alchemy_pipe_flow_core', 'the working pipe needs a glow core'

ARM_LOW, ARM_HIGH = 6, 10
for side in SIDES:
    arm = find({side: 'true'})
    assert arm == f'alkahest:block/pipe_{side}', (side, arm)
    glow = find({'active': 'true', side: 'true'})
    assert glow == f'alkahest:block/pipe_{side}_flow', (side, glow)

    arm_model = load(f'models/block/pipe_{side}.json')
    (low, high) = (arm_model['elements'][0]['from'], arm_model['elements'][0]['to'])
    assert {face['texture'] for face in arm_model['elements'][0]['faces'].values()} == {'#glass'}, side
    assert arm_model['textures']['glass'] == 'minecraft:block/glass', side
    axis = {"north": 2, "south": 2, "west": 0, "east": 0, "up": 1, "down": 1}[side]
    other = [i for i in range(3) if i != axis]
    for i in other:
        assert low[i] == ARM_LOW and high[i] == ARM_HIGH, (side, low, high)
    span = {low[axis], high[axis]}
    assert span in ({0, ARM_LOW}, {ARM_HIGH, 16}), (side, low, high)

    flow_model = load(f'models/block/pipe_{side}_flow.json')
    flow_textures = {face['texture'] for face in flow_model['elements'][0]['faces'].values()}
    assert flow_textures == {'#flow'}, side
    tints = {face.get('tintindex') for face in flow_model['elements'][0]['faces'].values()}
    assert tints == {0}, 'the moving essence is tinted with its own colour'
    flow_low, flow_high = flow_model['elements'][0]['from'], flow_model['elements'][0]['to']
    for i in range(3):
        assert 0 <= flow_low[i] < flow_high[i] <= 16, (side, flow_low, flow_high)
    for i in other:
        assert flow_low[i] > ARM_LOW and flow_high[i] < ARM_HIGH, 'the tube must run inside the glass arm'

core = load('models/block/alchemy_pipe_core.json')
assert core['textures']['copper'] == 'minecraft:block/copper_block'
item = load('models/item/alchemy_pipe.json')
assert len(item['elements']) >= 5, 'the item shows a fitting with arms'

flow = load('models/block/alchemy_pipe_flow_core.json')
assert flow['textures']['flow'] == 'alkahest:block/pipe_flow'
assert all(face.get('tintindex') == 0 for face in flow['elements'][0]['faces'].values())


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
    assert end and offset == len(data)
    return dims, zlib.decompress(raw)


dims, raw = validate_png(A/'textures/block/pipe_flow.png')
assert dims == (16, 64), dims
meta = load('textures/block/pipe_flow.png.mcmeta')['animation']
assert meta['frametime'] > 0, meta
frames = meta['frames']
assert len(frames) == 4, frames


def rows_of(pixels, width, frame_height, frame_count):
    stride = width * 4
    assert len(pixels) == (stride + 1) * frame_height * frame_count
    prior, out = [0] * stride, []
    for y in range(frame_height * frame_count):
        kind, line = pixels[y*(stride+1)], list(pixels[y*(stride+1)+1:(y+1)*(stride+1)])
        for x, value in enumerate(line):
            left = line[x-4] if x >= 4 else 0
            above = prior[x]
            upper_left = prior[x-4] if x >= 4 else 0
            if kind == 1: value += left
            elif kind == 2: value += above
            elif kind == 3: value += (left+above)//2
            elif kind == 4:
                guess = left+above-upper_left
                distances = (abs(guess-left), abs(guess-above), abs(guess-upper_left))
                value += (left, above, upper_left)[distances.index(min(distances))]
            else: assert kind == 0, kind
            line[x] = value & 255
        prior = line
        out.append(bytes(line))
    return {b''.join(out[i*frame_height:(i+1)*frame_height]) for i in range(frame_count)}


assert len(rows_of(raw, 16, 16, 4)) == 4, 'the four animation frames must differ'
print('Self-routing pipe block state, models and flow animation validated')
