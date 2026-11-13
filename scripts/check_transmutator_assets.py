#!/usr/bin/env python3
"""Regression checks for the 3x1x3 Transmutator.

Verifies the assets, the recipes, the language entries and the Java-side rules that are easy to
break by hand: the platform is a ring of eight pillars, every essence used by a recipe exists,
and the game tests are registered.
"""
import json
from amulet_geometry import first_near_coplanar, separate_faces
from copy import deepcopy
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

# The compact tower has generated glass/metal models, distinct dormant/awake states and
# multi-ingredient recipes. The old platform assets are intentionally retained for old worlds.
for name in ('transmutator_chamber', 'transmutator_crown'):
    state = load(A / f'blockstates/{name}.json')
    check(set(state['variants']) == {'awake=false', 'awake=true'}, f'{name} needs dormant and awake states')
    for suffix in ('', '_awake'):
        model = load(A / f'models/block/{name}{suffix}.json')
        check(len(model.get('elements', [])) >= 5, f'{name}{suffix} needs a detailed model')
        for ref, texture in model['textures'].items():
            file = A / 'textures' / (texture.split(':')[-1] + '.png')
            check(file.exists() and png_size(file)[:2] == (16, 16), f'{name} texture {ref} is missing or malformed')
    recipe = load(DATA / f'recipe/{name}.json')
    check(len(recipe['ingredients']) >= 3 and recipe['result']['id'] == 'alkahest:' + name, f'{name} requires a multi-ingredient recipe')
    check((DATA / f'loot_table/blocks/{name}.json').exists(), f'{name} needs a block drop')

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
check(len(re.findall(r'recipe\("[a-z0-9_]+", Items\.', recipe_source)) >= 8, 'the transmutator needs a decent recipe table')

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

collect = core_be.split('private boolean collect(', 1)[1].split('private static int jarsAround(', 1)[0]
check(collect.count('for (Map.Entry<EssenceType, Integer> entry : cost.entrySet())') >= 3,
      'collect must preflight every type, reserve every type, then commit payment in separate passes')
check('missingLaterEssenceNeverConsumesEarlierType' in tests and 'disappearingEssenceKeepsReservationsUntilPayment' in tests,
      'game tests must check repeated waiting and interrupted reservations')

pipe_be = (JAVA / 'block/entity/AlchemyPipeBlockEntity.java').read_text()
pipe_block = (JAVA / 'block/custom/AlchemyPipeBlock.java').read_text()
check('verticalFormed' in core_be and 'VerticalAwake' in core_be and 'pipeCost()' in core_be,
      'the new tower must be awakened, persistent, and request pipe payment')
check('payTransmutator' in pipe_be and 'finishPipedOperation' in pipe_be and 'TRANSMUTATOR_CORE' in pipe_block,
      'pipes must connect and pay the ready tower')
check('connectedTowerWaitsForWholePriceAndSpendsExactlyOnce' in tests and 'sharedNetworkTransfersBellowsToJarsAndFeedsTower' in tests,
      'the mixed network and missing essence need conservation tests')
check('dropBufferedEssence' in pillar_be, 'legacy pillar buffers must be recoverable when dismantling')

check('MidasTouchEffect.progressStep' in core_be and 'before / 10 != self.progress / 10' in core_be, 'Midas speed must not skip essence prefill boundaries')

# A compact Matrix-inspired instrument, never a renderer for the old platform or Matrix.
renderer = (CLIENT / 'client/render/TransmutatorCoreRenderer.java').read_text()
check('TRANSMUTATOR_CORE' in client_registration and 'TransmutatorCoreRenderer::new' in client_registration,
      'the new core renderer must be registered')
check('TRANSMUTATOR_CHAMBER' in renderer and 'TransmutatorAssemblyBlock.AWAKE' in renderer and 'TRANSMUTATOR_CROWN' in renderer,
      'animated rings may appear only in the awakened tower')
check('matrix_ring_metal.png' in renderer and 'getEntitySolid' in renderer and 'SEGMENTS' in renderer,
      'the tower needs compact real-metal rings on a quad layer')
for name in ('transmutator_chamber', 'transmutator_crown'):
    model = load(A / f'models/block/{name}.json')
    for el in model['elements']:
        check(all(0 <= n <= 16 for n in el['from'] + el['to']), f'{name} must remain within one block')
    for ref in ('metal', 'brass', 'quartz', 'gem'):
        check(ref in model['textures'], f'{name} needs visible {ref} materials')

check('core.getStack(TransmutatorCoreBlockEntity.AMULET)' in renderer and 'ModelTransformationMode.FIXED' in renderer,
      'the real installed amulet must render on the core')
check('syncAmulet()' in core_be and 'sendToAround' in core_be and 'stacks.clear();\n        Inventories.readNbt' in core_be,
      'amulet changes must sync and clear omitted empty slots to avoid ghosts')
check('visibleAmuletUpdatesAndClearsOnClientReplicas' in tests, 'amulet insertion and removal need replication tests')

# 0.1.60: the chamber is empty unless the real focus is installed, and the same
# dimensional focus is used in the player's trinket renderer (the cord is separate).
amulet = load(A / 'models/item/alkahest_amulet.json')
check(len(amulet.get('elements', [])) >= 10, 'amulet must have an actual layered 3D model')
check(first_near_coplanar(amulet['elements']) is None, 'amulet layers must not have coplanar overlapping faces (z-fighting)')
# Verify the guard itself catches both identical and opposing face orientations.
a = {'from': [0, 0, 0], 'to': [2, 2, 2]}
b = {'from': [1, 1, 0], 'to': [3, 3, 2]}
c = {'from': [1, 1, 2], 'to': [3, 3, 4]}
check(first_near_coplanar([a, b]) is not None and first_near_coplanar([a, c]) is not None,
      'z-fighting guard must detect overlapping faces in both orientations')
check(first_near_coplanar([a, {'from':[3,3,3], 'to':[4,4,4]}]) is None, 'separate geometry must remain valid')
synthetic = deepcopy([a, b, c])
separate_faces(synthetic)
check(first_near_coplanar(synthetic) is None, 'generator must resolve conflicting faces deterministically')
for el in amulet['elements']:
    check(all(0 <= n <= 16 for n in el['from'] + el['to']), 'amulet cuboids must fit their item model')
    check(all(a < b for a, b in zip(el['from'], el['to'])), 'amulet pieces must have volume')
for ref, texture in amulet['textures'].items():
    file = A / 'textures' / (texture.split(':')[-1] + '.png')
    check(file.exists() and png_size(file)[:2] == (16,16), f'amulet texture {ref} is missing')
for suffix in ('', '_awake'):
    chamber = load(A / f'models/block/transmutator_chamber{suffix}.json')
    check(not any(any(face['texture'] == '#gem' for face in el['faces'].values()) for el in chamber['elements']),
          'the central decorative crystal must be absent in both chamber states')
check('matrices.translate(.5f, 1.5f, .5f)' in renderer and 'if (!tower) return;' in renderer and 'if (amulet.getItem()' in renderer,
      'the installed real focus must be rendered at the rings centre inside a complete tower')
check('matrices.translate(.5f, .52f, -.025f)' not in renderer, 'the old external amulet must be gone')
check('TrinketRendererRegistry.registerRenderer' in client_registration and 'AmuletTrinketRenderer' in client_registration,
      'the wearable amulet renderer must be registered')
worn = (CLIENT / 'client/render/AmuletTrinketRenderer.java').read_text()
check('biped.body.rotate(matrices)' in worn and 'ModelTransformationMode.FIXED' in worn and 'strap(' in worn,
      'the necklace needs a separate cord and follows the animated torso')
check('showWorkingEssences(world, pos, recipe)' in core_be and 'particleAspects(recipe)' in core_be
      and 'world.getTime() % 4 == 0' in core_be and 'world instanceof ServerWorld' in core_be,
      'work particles must use actual recipe aspects and be rate-limited')
check('workingAspectColoursFollowRecipeWithoutChangingCosts' in tests, 'test aspect selection and work preservation')
check(amulet['display']['firstperson_righthand']['rotation'][1] == -45
      and amulet['display']['firstperson_lefthand']['rotation'][1] == 45,
      'first-person poses must face the player instead of being edge-on')
check('animation.amuletAngle()' in renderer and 'animation.ringAngle()' in renderer
      and 'animation.outerRingAngle()' in renderer and 'new WeakHashMap<>' in renderer,
      'focus and both rings need independent, continuous per-core animation phases')
check('TransmutatorAnimation.shouldSync(before, self.progress)' in core_be and 'syncAnimationProgress()' in core_be,
      'world viewers need bounded actual progress checkpoints during vertical work')
animation = (JAVA / 'animation/TransmutatorAnimation.java').read_text()
check('Math.sin(Math.PI * fraction)' in animation and 'MAX_EXTRAPOLATION_TICKS' in animation,
      'ring speed must rise and fall with bounded visual progress')
check('animationProgressCheckpointsRoundTrip' in tests, 'test progress update and reconstruction')


# --- report ----------------------------------------------------------------------------------
if failures:
    print('transmutator checks FAILED:')
    for failure in failures:
        print('  -', failure)
    raise SystemExit(1)
print(f'transmutator checks passed: {len(models)} models, {len(pillar_recipes)} pillar recipes, {test_count} game tests')
