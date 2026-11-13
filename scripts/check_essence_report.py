#!/usr/bin/env python3
"""Check the real vanilla+Alkahest coverage report created by the server smoke run."""
import csv
import sys
from pathlib import Path

path = Path(sys.argv[1] if len(sys.argv) > 1 else 'run/world/alkahest/item-essences.tsv')
if not path.is_file():
    raise SystemExit(f'Essence coverage report not found: {path}')
with path.open(encoding='utf-8', newline='') as file:
    rows = list(csv.DictReader((line for line in file if not line.startswith('#')), delimiter='\t'))
ids = [row['id'] for row in rows]
assert len(ids) > 1000, f'Expected a full registry report, got {len(ids)} entries'
assert len(ids) == len(set(ids)) and ids == sorted(ids), 'IDs must be unique and sorted'
for row in rows:
    assert row['kind'] in ('block', 'item'), row
    assert row['status'] in ('manual', 'recipe', 'missing'), row
    assert (row['status'] != 'missing') == bool(row['essences']), row
by_id = {row['id']: row for row in rows}
required = ['minecraft:blast_furnace', 'alkahest:distillery_tier2', 'minecraft:cherry_sapling',
    'minecraft:bamboo_block', 'minecraft:stripped_bamboo_block', 'minecraft:cracked_stone_bricks',
    'minecraft:smooth_stone', 'minecraft:smooth_stone_slab', 'minecraft:mossy_cobblestone',
    'minecraft:mossy_stone_bricks', 'minecraft:deepslate', 'minecraft:cobbled_deepslate',
    'minecraft:polished_deepslate', 'minecraft:deepslate_bricks', 'minecraft:deepslate_tiles',
    'minecraft:cracked_deepslate_bricks', 'minecraft:cracked_deepslate_tiles',
    'minecraft:chiseled_deepslate', 'minecraft:deepslate_diamond_ore', 'minecraft:tuff',
    'minecraft:polished_tuff', 'minecraft:tuff_bricks', 'minecraft:mud_bricks',
    'minecraft:chiseled_sandstone', 'minecraft:smooth_sandstone', 'minecraft:cake', 'minecraft:honey_block',
    'minecraft:brown_dye', 'minecraft:brown_wool', 'minecraft:black_concrete', 'minecraft:shulker_box',
    'minecraft:exposed_copper', 'minecraft:weathered_copper', 'minecraft:oxidized_copper',
    'minecraft:waxed_oxidized_copper', 'minecraft:netherite_ingot', 'alkahest:purifying_flask', 'alkahest:iron_chain', 'alkahest:anklet_blank']
# Older and new infused materials retain the exact source plus three units; infusion now costs eight.
for kind, source in {'ignis':'blaze_powder', 'aqua':'lapis_lazuli', 'terra':'iron_ingot',
                     'aer':'diamond', 'ordo':'gold_ingot', 'perditio':'amethyst_shard'}.items():
    output = by_id['alkahest:infused_' + kind]
    assert output['status'] == 'manual'
    def composition(row):
        return {k: int(v) for k,v in (entry.split('=') for entry in row['essences'].split(','))}
    expected = composition(by_id['minecraft:' + source])
    expected[kind] = expected.get(kind, 0) + 3
    assert composition(output) == expected, f'Infusion composition mismatch: {kind}'
for name in required:
    assert name in by_id and by_id[name]['status'] != 'missing', f'Missing composition for {name}'
missing = [row for row in rows if row['status'] == 'missing']
# This smoke server contains vanilla and Alkahest only. Air is a registry sentinel, not an item.
unresolved = [row['id'] for row in missing if row['id'] != 'minecraft:air']
assert not unresolved, f'Unmapped vanilla/Alkahest IDs: {unresolved}'
print(f'Coverage report: {len(rows) - len(missing)}/{len(rows)} item IDs mapped; only minecraft:air is not an item')
