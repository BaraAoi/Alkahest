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
steps = re.findall(r'new Step\("([a-z0-9_]+)", "([a-z0-9_]+:[a-z0-9_]+)",', plan)
assert len(steps) == 38 and len(set(steps)) == len(steps), 'all 38 steps must exist and be unique'
# Every visible node must exist exactly once and have its own clickable area.
journal = (root / 'src/client/java/net/alkahest/client/gui/JournalScreen.java').read_text()
nodes = [(int(i), int(x), int(y)) for i, x, y in re.findall(r'node\((\d+),\s*(-?\d+),\s*(-?\d+)\)', journal)]
assert sorted(i for i, _, _ in nodes) == [i for i in range(len(steps)) if i not in {12, 15, 16, 18, 27, 28, 34}], 'one visible node per grouped research'
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

# Catalogues and former guide chapters remain accessible from relevant research entries.
content = (java / 'research/ResearchJournalContent.java').read_text()
assert 'case "cauldron" -> Catalogue.CAULDRON' in content
assert 'case "matrix" -> Catalogue.MATRIX' in content and 'case "transmutator" -> Catalogue.TRANSMUTATOR' in content
assert 'ResearchJournalContent.chapters(node.key())' in journal
assert 'BookPagination.pages(net.alkahest.recipe.CauldronEssenceRecipes.all().size(), 2)' in journal
assert 'renderCauldronDiagram(c, brew, 0, 0,' in journal
assert 'screen.alkahest.journal.notes' not in journal and 'renderGuide(' not in journal
assert 'layout.detail().contains' in journal and 'camera.zoomAt(' in journal
assert 'layout.mapRegions(selected != null)' in journal

# The atlas has no book base; modal depth must exceed Minecraft's elevated item layer.
atlas = (root / 'src/client/resources/assets/alkahest/textures/gui/research_atlas.png').read_bytes()
assert atlas[:8] == b'\x89PNG\r\n\x1a\n'
assert struct.unpack('>IIBB', atlas[16:26]) == (1376, 768, 8, 2), 'opaque, high-resolution atlas texture'
render = journal.split('    @Override public void render(', 1)[1].split('    private void centered(', 1)[0]
assert 'context.drawTexture(BACKGROUND, 0, 0, width, height' in render
assert 'context.drawTexture(BOOK, 0, 0, width, height' not in render
assert render.index('renderMap(') < render.index('context.getMatrices().translate(0, 0, 300)') < render.index('context.drawTexture(BOOK, book.x()') < render.index('renderDetails(') < render.index('context.getMatrices().pop()')

# Transmutation branch uses the actual recipe registry rather than duplicated diagram prices.
assert 'node(36, 1100, 155)' in journal and 'node(37, 1240, 155)' in journal
assert 'TransmutationRecipe.find(new ItemStack(Items.IRON_INGOT), diamond)' in journal
assert 'entry.getKey().displayName()' in journal and 'recipe.duration() / 20' in journal
for name in ('transmutation_gold', 'transmutation_diamond'):
    assert f'"{name}"' in plan

# Both catalogues use the same filtered list for pagination, rows and hover details.
assert 'RecipeCatalogueSearch.matches(catalogueSearch.getText(), recipe)' in journal
assert 'cataloguePages(node, h, matrixRecipes.size(), transmutations.size())' in journal
assert 'page, mouseX, mouseY, matrixRecipes)' in journal
assert 'page, mouseX, mouseY, transmutations)' in journal
assert 'if (searchChanged) { detailPage = textSpreads;' in journal
assert 'catalogueSearch.charTyped(chr, modifiers)' in journal
assert 'recipe.cost().get(type)' in journal and 'recipe.duration() / 20' in journal
for lang in ('ru_ru', 'en_us'):
    strings = json.loads((assets / f'lang/{lang}.json').read_text())
    for key in ('search', 'search_hint', 'search_hint_more', 'search_empty'):
        assert strings.get(f'screen.alkahest.journal.{key}')

# Six elemental materials retain vanilla silhouettes and have reproducible translucent veins.
material_inputs = {'ignis':'blaze_powder','aqua':'lapis_lazuli','terra':'iron_ingot',
                   'aer':'diamond','ordo':'gold_ingot','perditio':'amethyst_shard'}
overlays = {}
for kind, base in material_inputs.items():
    texture = assets / f'textures/item/infused_{kind}.png'
    overlays[kind] = texture.read_bytes()
    assert overlays[kind][:8] == b'\x89PNG\r\n\x1a\n'
    assert struct.unpack('>IIBB', overlays[kind][16:26]) == (16,16,8,6)
    model = json.loads((assets / f'models/item/infused_{kind}.json').read_text())
    assert model['textures'] == {'layer0':f'minecraft:item/{base}', 'layer1':f'alkahest:item/infused_{kind}'}
    for lang in ('ru_ru','en_us'):
        assert json.loads((assets / f'lang/{lang}.json').read_text()).get(f'item.alkahest.infused_{kind}')
    assert f'{base}_to_infused_{kind}' in source
subprocess.run(['python3', 'scripts/make_infused_materials.py'], check=True)
assert all((assets / f'textures/item/infused_{k}.png').read_bytes() == v for k,v in overlays.items())
assert len(set(overlays.values())) == 6

# Every vein/mixture pixel stays inside the default material silhouette, not a floating symbol.
import runpy, zlib
generator = runpy.run_path('scripts/make_infused_materials.py')
for kind, base in material_inputs.items():
    encoded = overlays[kind]
    offset, packed = 8, bytearray()
    while offset < len(encoded):
        size = struct.unpack_from('>I', encoded, offset)[0]
        tag = encoded[offset+4:offset+8]
        if tag == b'IDAT': packed.extend(encoded[offset+8:offset+8+size])
        offset += 12 + size
        if tag == b'IEND': break
    raw = zlib.decompress(packed)
    assert len(raw) == 16 * 65
    pixels = [(x,y,raw[y*65+1+x*4+3]) for y in range(16) for x in range(16)]
    mask = generator['masks'][base]
    assert all(not a or mask[y][x] == 'X' for x,y,a in pixels), kind
    assert sum(1 for x,y,a in pixels if a >= 235) >= 15, f'{kind} needs opaque contrasting veins'
    assert sum(1 for x,y,a in pixels if a >= 200) >= 20, f'{kind} needs broad vein highlights'
    assert sum(1 for x,y,a in pixels if 0 < a < 160) >= 15, f'{kind} needs a blended tint'
