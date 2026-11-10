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
