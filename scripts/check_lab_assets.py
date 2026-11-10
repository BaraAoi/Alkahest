#!/usr/bin/env python3
import json
from pathlib import Path

base = Path('src/main/resources/assets/alkahest')
data_root = Path('src/main/resources/data/alkahest')
recipes = data_root / 'recipe'
adv = data_root / 'advancement/research'

prima = ('ignis', 'aqua', 'terra', 'aer', 'ordo', 'perditio')

for name in (
    'lightning_in_a_bottle', 'miasma', 'crystallization_powder',
    'alchemical_moss', 'alkahest_vial', 'alkahest_amulet',
    'binding_salt', *(f'binding_salt_{t}' for t in prima)
):
    model = json.loads((base / f'models/item/{name}.json').read_text())
    assert model['textures']['layer0'] == f'alkahest:item/{name}', name
    assert (base / f'textures/item/{name}.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n'), name

assert (base / 'textures/mob_effect/crystallized.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
assert (base / 'textures/block/alchemical_moss.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
assert json.loads((base / 'blockstates/alchemical_moss.json').read_text())['variants']['']['model'] == 'alkahest:block/alchemical_moss'

for tex in ('dissolved.png', 'dissolved_glow.png'):
    assert (base / f'textures/entity/dissolved/{tex}').read_bytes().startswith(b'\x89PNG\r\n\x1a\n'), tex
assert json.loads((base / 'models/item/dissolved_spawn_egg.json').read_text())['parent'] == 'minecraft:item/template_spawn_egg'

for t in prima:
    for tex in (f'{t}.png', f'{t}_glow.png', f'bolt_{t}.png'):
        assert (base / f'textures/entity/elemental/{tex}').read_bytes().startswith(b'\x89PNG\r\n\x1a\n'), tex
    egg_model = json.loads((base / f'models/item/elemental_{t}_spawn_egg.json').read_text())
    assert egg_model['parent'] == 'minecraft:item/template_spawn_egg', t
    salt_recipe = json.loads((recipes / f'binding_salt_{t}.json').read_text())
    assert salt_recipe['result']['id'] == f'alkahest:binding_salt_{t}', t

powder_recipe = json.loads((recipes / 'crystallization_powder.json').read_text())
assert powder_recipe['result']['id'] == 'alkahest:crystallization_powder'
amulet_recipe = json.loads((recipes / 'alkahest_amulet.json').read_text())
assert amulet_recipe['result']['id'] == 'alkahest:alkahest_amulet'

biome = json.loads((data_root / 'worldgen/biome/alchemical_wastes.json').read_text())
assert biome['effects']['grass_color'] == 6111187 and biome['effects']['fog_color'] == 9055202
assert len(biome['features']) == 11

for adv_name in (
    'chain_ignis_discovered', 'chain_ignis_cane', 'chain_ignis_feather', 'chain_ignis_complete',
    'chain_aqua_discovered', 'chain_aqua_sand', 'chain_aqua_ice', 'chain_aqua_complete',
    'binding_discovered', 'binding_slime', 'binding_bone_meal', 'binding_complete',
    'alkahest_discovered', 'alkahest_gold', 'alkahest_obsidian', 'alkahest_complete'
):
    data = json.loads((adv / f'{adv_name}.json').read_text())
    assert data['criteria']['done']['trigger'] == 'minecraft:impossible', adv_name

for locale in ('ru_ru', 'en_us'):
    data = json.loads((base / f'lang/{locale}.json').read_text())
    for name in ('jar', 'empty', 'more', 'pipe', 'direction', 'filter', 'no_buffer',
                 'cauldron', 'cauldron_dry', 'cauldron_cold', 'cauldron_stabilized'):
        assert data.get('screen.alkahest.monocle.' + name), (locale, name)
    for name in ('lightning_in_a_bottle', 'miasma', 'crystallization_powder',
                 'alkahest_vial', 'alkahest_amulet', 'dissolved_spawn_egg',
                 *(f'binding_salt_{t}' for t in prima),
                 *(f'elemental_{t}_spawn_egg' for t in prima)):
        assert data.get('item.alkahest.' + name), (locale, name)
    assert data.get('block.alkahest.alchemical_moss'), locale
    assert data.get('biome.alkahest.alchemical_wastes'), locale
    for t in prima:
        assert data.get(f'entity.alkahest.elemental_{t}'), (locale, t)
    assert data.get('entity.alkahest.elemental_bolt'), locale
    assert data.get('entity.alkahest.dissolved'), locale
    assert data.get('effect.alkahest.crystallized'), locale
    assert data.get('screen.alkahest.journal.page.7'), locale
    assert data.get('screen.alkahest.journal.page.8'), locale
    for node in ('chain_ignis', 'chain_aqua', 'binding', 'the_alkahest'):
        for suffix in ('title', 'hidden', 'locked', 'hypothesis', 'instructions', 'completed'):
            assert data.get(f'screen.alkahest.{node}.{suffix}'), (locale, node, suffix)
    for name in ('jar_bulk_full', 'jar_bulk_added', 'cauldron_no_residue', 'cauldron_crystallized',
                 'cauldron_contents', 'cauldron_dissolved', 'cauldron_essence_full',
                 'cauldron_missing_essences', 'cauldron_cannot_dissolve',
                 'elemental_bound', 'elemental_wrong_salt', 'elemental_evaporated',
                 'alkahest_brewed', 'cauldron_exploded', 'cauldron_annihilation',
                 'solvent_charges', 'amulet_charge'):
        assert data.get('message.alkahest.' + name), (locale, name)

print('Laboratory items, Elementals, Alkahest Era III assets, worldgen and translations validated')
