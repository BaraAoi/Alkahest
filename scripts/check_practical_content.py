#!/usr/bin/env python3
"""Validate the expanded progression, generated sprites and the content registries."""
from pathlib import Path
import json
import re
import struct
import subprocess

root = Path('.')
assets = root / 'src/main/resources/assets/alkahest'
data = root / 'src/main/resources/data/alkahest'
java = root / 'src/main/java/net/alkahest'
plan = (java / 'research/PracticalResearchPlan.java').read_text()
steps = re.findall(r'new Step\("([a-z0-9_]+)", "alkahest:([a-z0-9_]+)",', plan)
assert len(steps) == 34 and len(set(steps)) == len(steps), 'all 34 steps must exist and be unique'
# Every visible node must exist exactly once and have its own clickable area.
journal = (root / 'src/client/java/net/alkahest/client/gui/JournalScreen.java').read_text()
nodes = [(int(i), int(x), int(y)) for i, x, y in re.findall(r'node\((\d+),\s*(-?\d+),\s*(-?\d+)\)', journal)]
assert sorted(i for i, _, _ in nodes) == [i for i in range(len(steps)) if i not in {12, 15, 16, 18, 27, 28}], 'one visible node per grouped research'
for n, (i, x, y) in enumerate(nodes):
    for j, xx, yy in nodes[n + 1:]:
        assert abs(x - xx) >= 32 or abs(y - yy) >= 32, (i, j, 'overlapping nodes')
for item, material, count in [('jar', 'minecraft:glass_pane', 7), ('stabilizer_receiver', 'minecraft:copper_ingot', 4),
                              ('stabilizer_crest', 'alkahest:crystal_ordo', 4)]:
    recipe = json.loads((data / f'recipe/{item}.json').read_text())
    symbol = next(k for k, v in recipe['key'].items() if v.get('item') == material)
    assert ''.join(recipe['pattern']).count(symbol) == count and recipe['result']['id'] == f'alkahest:{item}'
for name in ('stabilizer', 'tier2', 'transmutator', 'matrix'):
    advancement = json.loads((data / f'advancement/practice/action_{name}.json').read_text())
    assert advancement['criteria']['done']['trigger'] == 'minecraft:impossible'
assert 'camera.zoomAt(' in journal and 'camera.radius()' in journal, 'zoom and hit detection must agree'
for name, item in steps:
    for kind in ('observed', 'completed'):
        advancement = json.loads((data / f'advancement/practice/{kind}_{name}.json').read_text())
        assert advancement['criteria']['done']['trigger'] == 'minecraft:impossible'
    for lang in ('ru_ru', 'en_us'):
        strings = json.loads((assets / f'lang/{lang}.json').read_text())
        for suffix in ('title', 'recipe'):
            assert strings.get(f'screen.alkahest.practice.{name}.{suffix}'), (lang, name, suffix)

for item in ('frost_flask', 'purifying_flask'):
    texture = assets / f'textures/item/{item}.png'
    original = texture.read_bytes()
    assert original[:8] == b'\x89PNG\r\n\x1a\n'
    assert struct.unpack('>IIBB', original[16:26]) == (32, 32, 8, 6)
    model = json.loads((assets / f'models/item/{item}.json').read_text())
    assert model['textures']['layer0'] == f'alkahest:item/{item}'
    for lang in ('ru_ru', 'en_us'):
        strings = json.loads((assets / f'lang/{lang}.json').read_text())
        assert strings.get(f'item.alkahest.{item}') and strings.get(f'tooltip.alkahest.{item}')
    subprocess.run(['python3', 'scripts/make_practical_flasks.py'], check=True)
    assert texture.read_bytes() == original, f'{item} texture generator is not reproducible'

source = (java / 'transmutation/TransmutationRecipe.java').read_text()
for key in ('GRAVEL, Items.FLINT', 'FLINT, Items.QUARTZ', 'REDSTONE, Items.GLOWSTONE_DUST',
            'NETHERRACK, Items.NETHER_BRICK', 'SOUL_SAND, Items.SOUL_SOIL', 'IRON_NUGGET, Items.GOLD_NUGGET'):
    assert key in source, key
matrix = (java / 'matrix/MatrixRecipe.java').read_text()
for key in ('Items.POWERED_RAIL', 'Items.SEA_LANTERN',
            'Items.MAGMA_BLOCK', 'Items.SOUL_LANTERN', 'Items.ARROW', 'Items.RAIL'):
    assert key in matrix, key
journal = (root / 'src/client/java/net/alkahest/client/gui/JournalScreen.java').read_text()
assert 'TransmutationRecipe.all()' in journal and 'child.parents() & parent.completed()' in journal
print('Practical research, both generated flask sprites and new machine recipe registries validated')

# The formerly Matrix-only items remain available without progression gates.
for output, inputs in {
    'frost_flask': ['minecraft:glass_bottle', 'minecraft:snowball', 'alkahest:crystal_gelum'],
    'purifying_flask': ['minecraft:honey_bottle', 'alkahest:purified_water', 'alkahest:crystal_ordo'],
    'midas_elixir': ['alkahest:purified_water', 'minecraft:golden_apple', 'minecraft:gold_ingot', 'alkahest:crystal_ordo'],
}.items():
    recipe = json.loads((data / f'recipe/{output}.json').read_text())
    assert recipe['type'] == 'minecraft:crafting_shapeless'
    assert [entry['item'] for entry in recipe['ingredients']] == inputs
    assert recipe['result'] == {'id': f'alkahest:{output}', 'count': 1}
subprocess.run(['python3', 'scripts/generate_journal_recipes.py', '--check'], check=True)

# All cauldron recipes must be illustrated in the notes, not a manually maintained subset.
assert 'CAULDRON_PAGE = 3' in journal and 'renderCauldronIndex(c, x, y, w, h, mouseX, mouseY)' in journal
assert 'BookPagination.pages(net.alkahest.recipe.CauldronEssenceRecipes.all().size(), 2)' in journal
assert 'Node preview = new Node("cauldron_index"' in journal
