#!/usr/bin/env python3
import json
from pathlib import Path
base = Path('src/main/resources/assets/alkahest')
recipes = Path('src/main/resources/data/alkahest/recipe')
for name in ('lightning_in_a_bottle', 'miasma', 'crystallization_powder'):
    model = json.loads((base / f'models/item/{name}.json').read_text())
    assert model['textures']['layer0'] == f'alkahest:item/{name}'
    assert (base / f'textures/item/{name}.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
powder_recipe = json.loads((recipes / 'crystallization_powder.json').read_text())
assert powder_recipe['result']['id'] == 'alkahest:crystallization_powder'
for locale in ('ru_ru', 'en_us'):
    data = json.loads((base / f'lang/{locale}.json').read_text())
    for name in ('jar', 'empty', 'more', 'pipe', 'direction', 'filter', 'no_buffer',
                 'cauldron', 'cauldron_dry', 'cauldron_cold'):
        assert data.get('screen.alkahest.monocle.' + name), (locale, name)
    for name in ('lightning_in_a_bottle', 'miasma', 'crystallization_powder'):
        assert data.get('item.alkahest.' + name)
    for name in ('jar_bulk_full', 'jar_bulk_added', 'cauldron_no_residue', 'cauldron_crystallized',
                 'cauldron_contents', 'cauldron_dissolved', 'cauldron_essence_full',
                 'cauldron_missing_essences', 'cauldron_cannot_dissolve'):
        assert data.get('message.alkahest.' + name), (locale, name)
print('Laboratory items, crystallization recipe, monocle and cauldron translations validated')
