#!/usr/bin/env python3
"""Check the distillery artwork and translations that the client UI references."""
import json
import struct
import zlib
from pathlib import Path

path = Path('src/client/resources/assets/alkahest/textures/gui/distillery_backdrop.png')
data = path.read_bytes()
assert data[:8] == b'\x89PNG\r\n\x1a\n', 'Invalid PNG signature'
offset, dimensions, idat, ended = 8, None, bytearray(), False
while offset < len(data):
    length = struct.unpack_from('>I', data, offset)[0]
    kind = data[offset + 4:offset + 8]
    chunk = data[offset + 8:offset + 8 + length]
    crc = struct.unpack_from('>I', data, offset + 8 + length)[0]
    assert zlib.crc32(kind + chunk) & 0xffffffff == crc, f'Invalid {kind!r} CRC'
    if kind == b'IHDR':
        dimensions = struct.unpack_from('>II', chunk)
    if kind == b'IDAT':
        idat.extend(chunk)
    offset += length + 12
    if kind == b'IEND':
        ended = True
        break
assert ended and offset == len(data) and dimensions == (472, 568), 'Invalid GUI texture dimensions or termination'
assert zlib.decompress(idat), 'Empty PNG image data'
for locale in ('ru_ru', 'en_us'):
    translations = json.loads(Path(f'src/main/resources/assets/alkahest/lang/{locale}.json').read_text())
    for name in ('title', 'input', 'output', 'ready', 'working', 'blocked', 'progress'):
        assert translations.get(f'screen.alkahest.distillery.{name}'), f'Missing {locale} distillery {name}'
    assert '%s' in translations['screen.alkahest.distillery.progress'], f'Missing progress placeholder: {locale}'
print('Distillery UI texture and translations validated')

# Tier-II indicator stays outside the input, tracks and inventory; tooltip lists server-provided types.
tier2 = Path('src/client/java/net/alkahest/client/gui/Tier2DistilleryScreen.java').read_text()
handler = Path('src/main/java/net/alkahest/screen/Tier2DistilleryScreenHandler.java').read_text()
assert 'QUEUE_X = 107, QUEUE_Y = 23, QUEUE_WIDTH = 63, QUEUE_HEIGHT = 24' in tier2
assert 'handler.pendingTotal()' in tier2 and 'handler.pendingAmount(type)' in tier2 and 'context.drawTooltip' in tier2
assert 'PENDING_START + EssenceType.values().length' in handler and 'addProperties(properties)' in handler
for locale in ('ru_ru', 'en_us'):
    translations = json.loads(Path(f'src/main/resources/assets/alkahest/lang/{locale}.json').read_text())
    assert translations.get('screen.alkahest.distillery_tier2.queue')
    assert '%s' in translations['screen.alkahest.distillery_tier2.queue_details']
print('Tier II queued-essence indicator, tooltip and property sync validated')

# The opt-in button has its own hitbox, separate from all slots and the progress and queue panels.
assert 'REDSTONE_X = 10, REDSTONE_Y = 48, REDSTONE_W = 44, REDSTONE_H = 13' in tier2
assert 'clickButton(handler.syncId, Tier2DistilleryScreenHandler.REDSTONE_BUTTON)' in tier2
assert 'REDSTONE_PROPERTY = PENDING_START + EssenceType.values().length' in handler
assert 'player.isSpectator() || !canUse(player)' in handler
for locale in ('ru_ru', 'en_us'):
    translations = json.loads(Path(f'src/main/resources/assets/alkahest/lang/{locale}.json').read_text())
    for key in ('paused', 'redstone_on', 'redstone_off', 'redstone_hint'):
        assert translations.get(f'screen.alkahest.distillery_tier2.{key}')
    assert translations.get('tooltip.alkahest.distillery_redstone')
loot = json.loads(Path('src/main/resources/data/alkahest/loot_table/blocks/distillery_tier2.json').read_text())
assert 'alkahest:distillery_redstone' in loot['pools'][0]['entries'][0]['functions'][0]['include']
