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
    'alchemical_moss', 'alkahest_vial',
    'binding_salt', *(f'binding_salt_{t}' for t in prima)
):
    model = json.loads((base / f'models/item/{name}.json').read_text())
    assert model['textures']['layer0'] == f'alkahest:item/{name}', name
    assert (base / f'textures/item/{name}.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n'), name

# The amulet is a full cuboid model now, not an item/generated layer0 sprite.
amulet_model = json.loads((base / 'models/item/alkahest_amulet.json').read_text())
assert len(amulet_model['elements']) >= 10
for ref in ('gold', 'gem', 'dark'):
    texture = amulet_model['textures'][ref].split(':', 1)[1]
    assert (base / f'textures/{texture}.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n'), ref

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
    for name in ('jar', 'empty', 'more', 'pipe', 'pipe_auto', 'pipe_flowing', 'no_buffer',
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


# --- Tier-2 distillery and its essence bellows (0.1.32) ---------------------------------------
bellows_state = json.loads((base / 'blockstates/distillery_bellows.json').read_text())['variants']
assert set(bellows_state) == {f'level={level}' for level in range(4)}
for level in range(4):
    model_name = bellows_state[f'level={level}']['model'].split('/')[-1]
    assert (base / f'models/block/{model_name}.json').is_file(), model_name

tier2_state = json.loads((base / 'blockstates/distillery_tier2.json').read_text())['variants']
assert tier2_state['working=false']['model'] == 'alkahest:block/distillery'
assert tier2_state['working=true']['model'] == 'alkahest:block/distillery_working'
crude_state = json.loads((base / 'blockstates/distillery.json').read_text())['variants']
assert crude_state['working=false']['model'] == 'alkahest:block/distillery_crude'
assert crude_state['working=true']['model'] == 'alkahest:block/distillery_crude_working'
assert json.loads((base / 'models/item/distillery.json').read_text())['parent'] == 'alkahest:block/distillery_crude'

for name in ('distillery_tier2', 'distillery_bellows'):
    assert (base / f'models/item/{name}.json').is_file(), name
    assert (data_root / f'loot_table/blocks/{name}.json').is_file(), name
    assert (recipes / f'{name}.json').is_file(), name

# The tier-2 cube must stay late-game: it is gated behind an Alkahest vial and copper blocks.
tier2_recipe = json.loads((recipes / 'distillery_tier2.json').read_text())
gate = {entry.get('item') for entry in tier2_recipe['key'].values()}
assert 'alkahest:alkahest_vial' in gate, 'tier-2 distillery must require an Alkahest vial'
assert 'minecraft:copper_block' in gate, 'tier-2 distillery must require copper blocks'
assert tier2_recipe['key']['D'] == {'item': 'alkahest:distillery'}

for tex in ('distillery_copper', 'distillery_panel', 'distillery_vent', 'bellows_iron',
            'distillery_crude', 'distillery_crude_panel', 'distillery_crude_glass'):
    assert (base / f'textures/block/{tex}.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n'), tex

for locale in ('ru_ru', 'en_us'):
    data = json.loads((base / f'lang/{locale}.json').read_text())
    for name in ('distillery_tier2', 'distillery_bellows', 'distillery'):
        assert data.get('block.alkahest.' + name), (locale, name)
    for suffix in ('title', 'input', 'ready', 'working', 'blocked'):
        assert data.get(f'screen.alkahest.distillery_tier2.{suffix}'), (locale, suffix)
    for name in ('bellows_instruction', 'bellows_empty', 'bellows_jar_full', 'bellows_poured',
                 'bellows_status', 'bellows_status_empty'):
        assert data.get('message.alkahest.' + name), (locale, name)
    for name in ('bellows', 'distillery_tier2', 'stabilizer', 'working', 'idle', 'blocked', 'progress'):
        assert data.get('screen.alkahest.monocle.' + name), (locale, name)

print('Laboratory items, Elementals, Alkahest Era III assets, worldgen and translations validated')
