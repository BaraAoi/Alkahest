#!/usr/bin/env python3
import json
from pathlib import Path
base=Path('src/main/resources/assets/alkahest')
for name in ('lightning_in_a_bottle','miasma'):
    model=json.loads((base/f'models/item/{name}.json').read_text())
    assert model['textures']['layer0']==f'alkahest:item/{name}'
    assert (base/f'textures/item/{name}.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
for locale in ('ru_ru','en_us'):
    data=json.loads((base/f'lang/{locale}.json').read_text())
    for name in ('jar','empty','more','pipe','direction','filter','no_buffer'):
        assert data.get('screen.alkahest.monocle.'+name), (locale,name)
    for name in ('lightning_in_a_bottle','miasma'):
        assert data.get('item.alkahest.'+name)
    for name in ('jar_bulk_full','jar_bulk_added'):
        assert data.get('message.alkahest.'+name)
print('New reagents, monocle and jar translations validated')
