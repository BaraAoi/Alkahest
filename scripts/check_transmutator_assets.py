#!/usr/bin/env python3
"""Regression checks for the 3x1x3 Transmutator.

Verifies the assets, the recipes, the language entries and the Java-side rules that are easy to
break by hand: the platform is a ring of eight pillars, every essence used by a recipe exists,
and the game tests are registered.
"""
import json
import re
import struct
from pathlib import Path

ROOT = Path('.')
A = ROOT / 'src/main/resources/assets/alkahest'
DATA = ROOT / 'src/main/resources/data/alkahest'
JAVA = ROOT / 'src/main/java/net/alkahest'
CLIENT = ROOT / 'src/client/java/net/alkahest'
GAMETEST = ROOT / 'src/gametest'

failures = []


def check(condition, message):
    if not condition:
        failures.append(message)


def load(path):
    return json.loads(Path(path).read_text())


def png_size(path):
    data = Path(path).read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', f'{path} is not a PNG'
    width, height, depth, kind = struct.unpack_from('>IIBB', data, 16)
    return width, height, depth, kind


# --- blockstates and models ------------------------------------------------------------------
states = load(A / 'blockstates/transmutator_core.json')
check(set(states['variants']) == {'working=false', 'working=true'}, 'core needs idle and working variants')
states = load(A / 'blockstates/transmutator_pillar.json')
check(set(states['variants']) == {'charged=false', 'charged=true'}, 'pillar needs idle and charged variants')

models = ['transmutator_core', 'transmutator_core_lit', 'transmutator_pillar', 'transmutator_pillar_charged']
for name in models:
    model = load(A / f'models/block/{name}.json')
    check(model.get('elements'), f'{name} must be built from elements')
    for face in ('up', 'down', 'north', 'south', 'east', 'west'):
        used = {f['texture'].lstrip('#') for el in model['elements'] for f in el['faces'].values()}
    for ref in used:
        check(ref in model['textures'], f'{name} refers to the missing texture slot #{ref}')
    for ref, path in model['textures'].items():
        if ref == 'particle':
            continue
        texture = A / 'textures' / (path.split(':')[-1] + '.png')
        check(texture.exists(), f'{name} wants {texture}')
        if texture.exists():
            width, height, depth, kind = png_size(texture)
            check((width, height) == (16, 16), f'{texture} must be 16x16, got {width}x{height}')
            check(kind == 6, f'{texture} needs an alpha channel')

for name in ('transmutator_core', 'transmutator_pillar'):
    item_model = load(A / f'models/item/{name}.json')
    check(item_model['parent'] == f'alkahest:block/{name}', f'item model of {name} must use the block model')

# The pillar reads as an obelisk: plinth, shaft, cap.
pillar = load(A / 'models/block/transmutator_pillar.json')
check(len(pillar['elements']) == 3, 'the pillar is a plinth, a shaft and a cap')
shaft = pillar['elements'][1]
check(shaft['from'][1] > 0 and shaft['to'][0] < 16, 'the shaft must be inset in the plinth')

# --- recipes ---------------------------------------------------------------------------------
core_recipe = load(DATA / 'recipe/transmutator_core.json')
check(core_recipe['result']['id'] == 'alkahest:transmutator_core', 'the core recipe must give the core')
pillar_recipes = sorted((DATA / 'recipe').glob('transmutator_pillar_*.json'))
check(len(pillar_recipes) >= 6, 'the pillar has one recipe per primordial crystal')
for path in pillar_recipes:
    recipe = load(path)
    check(recipe['result']['id'] == 'alkahest:transmutator_pillar', f'{path.name} must give a pillar')
    check(recipe['result'].get('count', 1) >= 4, f'{path.name} should give at least four pillars')

# --- language --------------------------------------------------------------------------------
for lang in ('ru_ru', 'en_us'):
    strings = load(A / f'lang/{lang}.json')
    for key in ('block.alkahest.transmutator_core', 'block.alkahest.transmutator_pillar',
                'screen.alkahest.transmutator.title', 'screen.alkahest.transmutator.ready',
                'screen.alkahest.transmutator.working', 'screen.alkahest.transmutator.no_essence',
                'screen.alkahest.transmutator.blocked', 'screen.alkahest.transmutator.unformed',
                'screen.alkahest.transmutator.no_amulet', 'message.alkahest.transmutator_unformed',
                'message.alkahest.pillar_status', 'message.alkahest.pillar_empty'):
        check(key in strings, f'{lang} misses {key}')

# --- java rules ------------------------------------------------------------------------------
core_be = (JAVA / 'block/entity/TransmutatorCoreBlockEntity.java').read_text()
check('for (int dx = -1; dx <= 1; dx++)' in core_be, 'the platform is a 3x3 ring')
check(core_be.count('TransmutatorPillarBlockEntity') >= 2, 'the core talks to its pillars')
check('server.spawnParticles' in core_be, 'the core must show visible essence sparks')
check('DURATION' in core_be, 'the core needs a working duration')

pillar_be = (JAVA / 'block/entity/TransmutatorPillarBlockEntity.java').read_text()
check('ServerWorld' in pillar_be and 'spawnParticles' in pillar_be, 'essence flies through the air from jars')
check('REACH' in pillar_be, 'the pillar needs a reach to the jars')
check('AlchemyPipe' not in pillar_be and 'AlchemyPipe' not in core_be,
      'the transmutator must work without pipes')

recipe_source = (JAVA / 'transmutation/TransmutationRecipe.java').read_text()
essence_source = (JAVA / 'essence/EssenceType.java').read_text()
known = set(re.findall(r'^\s+(?:[A-Z_]+\()?([A-Z_]{3,})\(0x', essence_source, re.M))
known |= set(re.findall(r'\b([A-Z_]{3,})\(0x[0-9A-Fa-f]+, Tier\.', essence_source))
used = set(re.findall(r'EssenceType\.([A-Z_]+)', recipe_source))
check(used <= known, f'unknown essence types in the recipes: {sorted(used - known)}')
check(len(re.findall(r'recipe\(Items\.', recipe_source)) >= 8, 'the transmutator needs a decent recipe table')

block_source = (JAVA / 'block/custom/TransmutatorCoreBlock.java').read_text()
check('WORKING' in block_source and 'getTicker' in block_source, 'the core block must tick and switch state')

client_registration = (CLIENT / 'AlkahestClient.java').read_text()
check('TransmutatorScreen::new' in client_registration, 'the client must register the transmutator screen')

gametest_mod = load(GAMETEST / 'resources/fabric.mod.json')
check('net.alkahest.test.TransmutatorGameTests' in gametest_mod['entrypoints']['fabric-gametest'],
      'the transmutator game tests must be registered')

tests = (GAMETEST / 'java/net/alkahest/test/TransmutatorGameTests.java').read_text()
test_count = len(re.findall(r'@GameTest', tests))
check(test_count >= 7, f'expected at least seven transmutator game tests, found {test_count}')

# --- report ----------------------------------------------------------------------------------
if failures:
    print('transmutator checks FAILED:')
    for failure in failures:
        print('  -', failure)
    raise SystemExit(1)
print(f'transmutator checks passed: {len(models)} models, {len(pillar_recipes)} pillar recipes, {test_count} game tests')
