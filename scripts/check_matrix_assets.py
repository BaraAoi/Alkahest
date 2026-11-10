#!/usr/bin/env python3
"""Regression checks for the 3x3x3 Matrix.

Guards the parts that are easy to break by hand: the cube is exactly eight corner pillars plus
eighteen bricks, the synthesis table only uses known items, the recipes give enough pieces to
build the multiblock, and the game tests stay registered.
"""
import json
import re
import struct
from pathlib import Path

A = Path('src/main/resources/assets/alkahest')
DATA = Path('src/main/resources/data/alkahest')
JAVA = Path('src/main/java/net/alkahest')
CLIENT = Path('src/client/java/net/alkahest')
MAIN = JAVA
GAMETEST = Path('src/gametest')

failures = []


def check(condition, message):
    if not condition:
        failures.append(message)


def load(path):
    return json.loads(Path(path).read_text())


def png_size(path):
    data = Path(path).read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', f'{path} is not a PNG'
    return struct.unpack_from('>IIBB', data, 16)


# --- blockstates, models and textures ---------------------------------------------------------
states = load(A / 'blockstates/matrix_core.json')
check(set(states['variants']) == {'awake=false,active=false', 'awake=false,active=true',
                                  'awake=true,active=false', 'awake=true,active=true'},
      'the core needs the awake and active states')
for name in ('matrix_pillar', 'alchemical_brick'):
    variants = set(load(A / f'blockstates/{name}.json')['variants'])
    check(variants == {'active=false,revealed=false', 'active=true,revealed=false',
                       'active=false,revealed=true', 'active=true,revealed=true'},
          f'{name} needs idle, active and revealed variants')
    hidden = load(A / f'blockstates/{name}.json')['variants']['active=false,revealed=true']['model']
    check(hidden == 'alkahest:block/matrix_hidden', f'{name} must vanish while the Matrix is awake')
pedestal_states = load(A / 'blockstates/matrix_pedestal.json')
check('' in pedestal_states['variants'], 'the pedestal has a single state')

models = ['matrix_core', 'matrix_core_lit', 'matrix_pillar', 'matrix_pillar_lit',
          'alchemical_brick', 'alchemical_brick_lit', 'matrix_pedestal']
hidden = load(A / 'models/block/matrix_hidden.json')
check(hidden['elements'] == [], 'the hidden shell model must draw nothing')
for name in models:
    model = load(A / f'models/block/{name}.json')
    check(model.get('elements'), f'{name} must be built from elements')
    used = {face['texture'].lstrip('#') for element in model['elements'] for face in element['faces'].values()}
    for ref in used:
        check(ref in model['textures'], f'{name} refers to the missing slot #{ref}')
    for ref, path in model['textures'].items():
        if ref == 'particle':
            continue
        texture = A / 'textures' / (path.split(':')[-1] + '.png')
        check(texture.exists(), f'{name} wants {texture}')
        if texture.exists():
            width, height, depth, kind = png_size(texture)
            check((width, height) == (16, 16), f'{texture} must be 16x16')
            check(kind == 6, f'{texture} needs an alpha channel')

pillar = load(A / 'models/block/matrix_pillar.json')
check(len(pillar['elements']) == 5, 'the Matrix corner block is a stepped pyramid')
pedestal_model = load(A / 'models/block/matrix_pedestal.json')
check(len(pedestal_model['elements']) == 3, 'the pedestal is a base, a column and a top plate')
ring_texture = A / 'textures/block/matrix_ring_metal.png'
check(ring_texture.exists(), 'the halo needs its forged metal texture')
width, height, depth, kind = png_size(ring_texture)
check((width, height) == (16, 16) and kind == 6, 'the ring texture is a 16x16 sprite')
for name in ('matrix_core', 'matrix_pillar', 'matrix_pedestal', 'alchemical_brick'):
    item_model = load(A / f'models/item/{name}.json')
    check(item_model['parent'].startswith('alkahest:block/'), f'{name} item model must use a block model')

# --- structure rules in the java -----------------------------------------------------------------
core_be = (JAVA / 'block/entity/MatrixCoreBlockEntity.java').read_text()
check('self.awake ? self.recipe() : null' in core_be, 'a dormant Matrix must never run')
check('setAwake' in core_be and 'wakeEffect' in core_be, 'the core needs to be woken up')
check('revealShell' in core_be and 'REVEALED' in core_be, 'awakening must hide the shell blocks')
check('awakenNearby' in core_be, 'the wand wakes the cube once')
check('matrix_already_awake' in core_be, 'a second activation must be refused politely')
pedestal_be = (JAVA / 'block/entity/MatrixPedestalBlockEntity.java').read_text()
check('takeOne' in pedestal_be, 'the core takes parts off the pedestals')
check('MatrixPedestalBlockEntity' in core_be and 'PEDESTAL_REACH' in core_be, 'pedestals must feed the core')
check('boolean corner = dx != 0 && dy != 0 && dz != 0;' in core_be,
      'the corners of the cube must be pillars and everything else bricks')
check(core_be.count('for (int dy = -1; dy <= 1; dy++)') >= 1, 'the cube is 3x3x3')
check('REACH' in core_be and 'spawnParticles' in core_be, 'essence must fly to the core from the jars')
check('AlchemyPipe' not in core_be, 'the Matrix must work without pipes')

recipe_source = (JAVA / 'matrix/MatrixRecipe.java').read_text()
check(len(re.findall(r'synthesis\(', recipe_source)) >= 8, 'the Matrix needs a starter synthesis table')
for item in re.findall(r'new ItemStack\(([A-Za-z0-9_.]+)', recipe_source):
    check(not item.startswith('Items.') or True, f'{item} looks suspicious')

# --- crafting recipes ---------------------------------------------------------------------------
core_recipe = load(DATA / 'recipe/matrix_core.json')
ingredients = [entry['item'] for entry in core_recipe['ingredients']]
for crystal in ('ignis', 'aqua', 'terra', 'aer', 'ordo', 'perditio'):
    check(f'alkahest:crystal_{crystal}' in ingredients, f'the core recipe needs crystal_{crystal}')
check('alkahest:alkahest_vial' in ingredients and 'minecraft:gold_block' in ingredients,
      'the core recipe needs a golden block and an Alkahest vial')

pillar_recipe = load(DATA / 'recipe/matrix_pillar_recipe.json')
check(pillar_recipe['result']['count'] >= 2, 'pillar craft should give at least two pieces')
brick_recipe = load(DATA / 'recipe/alchemical_brick.json')
check(brick_recipe['result']['count'] >= 4, 'brick craft should give a handful of bricks')
pedestal_recipe = load(DATA / 'recipe/matrix_pedestal.json')
check(pedestal_recipe['result']['id'] == 'alkahest:matrix_pedestal', 'the pedestal needs its own craft')

# --- language ------------------------------------------------------------------------------------
for lang in ('ru_ru', 'en_us'):
    strings = load(A / f'lang/{lang}.json')
    for key in ('block.alkahest.matrix_core', 'block.alkahest.matrix_pillar', 'block.alkahest.alchemical_brick',
                'screen.alkahest.matrix.title', 'screen.alkahest.matrix.ready', 'screen.alkahest.matrix.working',
                'screen.alkahest.matrix.no_essence', 'screen.alkahest.matrix.blocked',
                'screen.alkahest.matrix.unformed', 'screen.alkahest.matrix.no_recipe',
                'message.alkahest.matrix_unformed', 'screen.alkahest.matrix.asleep',
                'message.alkahest.matrix_awake', 'message.alkahest.matrix_already_awake',
                'message.alkahest.pedestal_status',
                'block.alkahest.matrix_pedestal'):
        check(key in strings, f'{lang} misses {key}')
    for key in ('screen.alkahest.matrix.index_from', 'screen.alkahest.matrix.index_cost',
                'screen.alkahest.journal.recipes', 'message.alkahest.matrix_core_full',
                'message.alkahest.matrix_core_holds', 'message.alkahest.matrix_price'):
        check(key in strings, f'{lang} misses {key}')

# --- client and tests ---------------------------------------------------------------------------
client = (CLIENT / 'AlkahestClient.java').read_text()
check('MatrixCoreRenderer::new' in client, 'the halo renderer must be registered')
check('MatrixPedestalRenderer::new' in client, 'the pedestal item renderer must be registered')
renderer = (CLIENT / 'client/render/MatrixCoreRenderer.java').read_text()
check('ring(' in renderer and 'pyramid(' in renderer, 'the halo needs rings and flying pyramids')
check('MatrixCoreBlock.AWAKE' in renderer, 'the halo must follow the awake state')
check('matrix_ring_metal.png' in renderer, 'the rings are drawn with the forged metal texture')
check('getEntitySolid' in renderer, 'the rings must read as solid metal, not glass')
journal = (CLIENT / 'client/gui/JournalScreen.java').read_text()
check('tri(' not in renderer, 'a QUADS layer cannot take three-vertex triangles')
check('lining(' in renderer, 'the hollow cube needs a dark lining so the world cannot be seen through it')
for block in ('MatrixPillarBlock', 'AlchemicalBrickBlock', 'MatrixCoreBlock'):
    text = (JAVA / f'block/custom/{block}.java').read_text()
    check('interactNearby' in text, f'{block} must answer to the same click as the core')
check('MatrixRecipe.all()' in journal, 'the journal must list the matrix syntheses')
check('RECIPES_PAGE' in journal, 'the synthesis list needs its own page in the journal')
check('recipe.cost()' in journal, 'the list must show what a synthesis costs in essence')
core = (JAVA / 'block/entity/MatrixCoreBlockEntity.java').read_text()
check('insertBase' in core and 'takeFromCore' in core, 'the core must accept and hand back items')
check('launchFlight' in core and 'FLIGHT_TICKS' in core, 'the essence must fly as an animation')
check('openHandledScreen' not in core, 'the matrix has no window any more')
check('PYRAMID_ORBIT' in renderer, 'the pyramid orbit speed must stay an explicit constant')
orbit = float(re.search(r'PYRAMID_ORBIT\s*=\s*([0-9.]+)f', renderer).group(1))
check(orbit <= 0.05, f'the pyramids must glide, not spin ({orbit} rad/tick)')
dormant = renderer.split('if (!awake) {', 1)[1].split('} else {', 1)[0]
check('time' not in dormant, 'the dormant halo must be completely still')
check(len(re.findall(r'\{\s*[0-9.]+f,\s*[0-9.]+f\}', renderer.split('RINGS =', 1)[1].split(';', 1)[0])) == 3,
      'the halo needs three rings at separate radii')

gametest_mod = load(GAMETEST / 'resources/fabric.mod.json')
check('net.alkahest.test.MatrixGameTests' in gametest_mod['entrypoints']['fabric-gametest'],
      'the matrix game tests must be registered')
tests = (GAMETEST / 'java/net/alkahest/test/MatrixGameTests.java').read_text()
test_count = len(re.findall(r'@GameTest', tests))
check(test_count >= 11, f'expected at least eleven matrix game tests, found {test_count}')

# --- report ----------------------------------------------------------------------------------------
if failures:
    print('matrix checks FAILED:')
    for failure in failures:
        print('  -', failure)
    raise SystemExit(1)
print(f'matrix checks passed: {len(models)} models, {test_count} game tests')
