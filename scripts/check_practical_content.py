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
assert len(steps) == 26 and len(set(steps)) == len(steps), 'all 26 steps must exist and be unique'
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
for key in ('ModItems.FROST_FLASK', 'ModItems.PURIFYING_FLASK', 'Items.POWERED_RAIL', 'Items.SEA_LANTERN',
            'Items.MAGMA_BLOCK', 'Items.SOUL_LANTERN', 'Items.ARROW', 'Items.RAIL'):
    assert key in matrix, key
journal = (root / 'src/client/java/net/alkahest/client/gui/JournalScreen.java').read_text()
assert 'TransmutationRecipe.all()' in journal and 'child.parents() & parent.completed()' in journal
print('Practical research, both generated flask sprites and new machine recipe registries validated')
