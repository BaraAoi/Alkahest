#!/usr/bin/env python3
import json
from pathlib import Path

base = Path('src/main/resources/assets/alkahest')
recipes = Path('src/main/resources/data/alkahest/recipe')
adv = Path('src/main/resources/data/alkahest/advancement/research')

prima = ('ignis', 'aqua', 'terra', 'aer', 'ordo', 'perditio')

for name in (
    'lightning_in_a_bottle', 'miasma', 'crystallization_powder',
    'binding_salt', *(f'binding_salt_{t}' for t in prima)
):
    model = json.loads((base / f'models/item/{name}.json').read_text())
    assert model['textures']['layer0'] == f'alkahest:item/{name}', name
    assert (base / f'textures/item/{name}.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n'), name

assert (base / 'textures/mob_effect/crystallized.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n')

for t in prima:
    egg_model = json.loads((base / f'models/item/elemental_{t}_spawn_egg.json').read_text())
    assert egg_model['parent'] == 'minecraft:item/template_spawn_egg', t
    salt_recipe = json.loads((recipes / f'binding_salt_{t}.json').read_text())
    assert salt_recipe['result']['id'] == f'alkahest:binding_salt_{t}', t

powder_recipe = json.loads((recipes / 'crystallization_powder.json').read_text())
assert powder_recipe['result']['id'] == 'alkahest:crystallization_powder'

for adv_name in (
    'chain_ignis_discovered', 'chain_ignis_cane', 'chain_ignis_feather', 'chain_ignis_complete',
    'chain_aqua_discovered', 'chain_aqua_sand', 'chain_aqua_ice', 'chain_aqua_complete',
    'binding_discovered', 'binding_slime', 'binding_bone_meal', 'binding_complete'
):
    data = json.loads((adv / f'{adv_name}.json').read_text())
    assert data['criteria']['done']['trigger'] == 'minecraft:impossible', adv_name

for locale in ('ru_ru', 'en_us'):
    data = json.loads((base / f'lang/{locale}.json').read_text())
    for name in ('jar', 'empty', 'more', 'pipe', 'direction', 'filter', 'no_buffer',
                 'cauldron', 'cauldron_dry', 'cauldron_cold'):
        assert data.get('screen.alkahest.monocle.' + name), (locale, name)
    for name in ('lightning_in_a_bottle', 'miasma', 'crystallization_powder',
                 *(f'binding_salt_{t}' for t in prima),
                 *(f'elemental_{t}_spawn_egg' for t in prima)):
        assert data.get('item.alkahest.' + name), (locale, name)
    for t in prima:
        assert data.get(f'entity.alkahest.elemental_{t}'), (locale, t)
    assert data.get('effect.alkahest.crystallized'), locale
    assert data.get('screen.alkahest.journal.page.7'), locale
    for node in ('chain_ignis', 'chain_aqua', 'binding'):
        for suffix in ('title', 'hidden', 'locked', 'hypothesis', 'instructions', 'completed'):
            assert data.get(f'screen.alkahest.{node}.{suffix}'), (locale, node, suffix)
    for name in ('jar_bulk_full', 'jar_bulk_added', 'cauldron_no_residue', 'cauldron_crystallized',
                 'cauldron_contents', 'cauldron_dissolved', 'cauldron_essence_full',
                 'cauldron_missing_essences', 'cauldron_cannot_dissolve',
                 'elemental_bound', 'elemental_wrong_salt', 'elemental_evaporated'):
        assert data.get('message.alkahest.' + name), (locale, name)

print('Laboratory items, Elementals, Charged Binding Salts, Era II research and translations validated')
