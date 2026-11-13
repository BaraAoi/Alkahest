#!/usr/bin/env python3
"""Generate the journal's vanilla crafting diagrams from the shipped JSON recipes."""
import json
from pathlib import Path
import sys

mapping = {
 'cauldron':['alchemy_cauldron'], 'distillery':['distillery'],
 'ignis_wand':['crystal_wand_ignis','crystal_wand_terra','crystal_wand_aer','crystal_wand_ordo','crystal_wand_perditio'], 'aqua_wand':['crystal_wand_aqua'],
 'water_faucet':['water_faucet'],
 'stabilizer':['stabilizer_core','stabilizer_receiver','stabilizer_crest'],
 'pipe':['alchemy_pipe'], 'charger':['essence_charger'], 'tier2':['distillery_tier2','distillery_bellows'],
 'amulet':['iron_chain','anklet_blank'], 'transmutator':['transmutator_core','transmutator_pillar_aqua','transmutator_chamber','transmutator_crown'],
 'matrix':['matrix_core','matrix_pillar_recipe','alchemical_brick','matrix_pedestal'],
 'midas':['midas_elixir'], 'frost':['frost_flask'], 'purification':['purifying_flask'],
 'jar':['jar'], 'separator':['essence_separator'], 'crystallization':['crystallization_powder'],
}
root=Path('src/main/resources/data/alkahest/recipe')
q=lambda s: json.dumps(s,ensure_ascii=False)
lines=['package net.alkahest.client.gui;', '', 'import java.util.List;', 'import java.util.Map;', '',
       '/** Generated from the bundled crafting recipes by scripts/generate_journal_recipes.py. */',
       'final class JournalCraftingRecipes {',
       '    record Diagram(boolean shapeless, List<String> grid, String output, int count) { }',
       '    static final Map<String, List<Diagram>> BY_RESEARCH = Map.ofEntries(']
for key,names in mapping.items():
 diagrams=[]
 for name in names:
  data=json.loads((root/(name+'.json')).read_text()); grid=['']*9
  if data['type']=='minecraft:crafting_shaped':
   for y,row in enumerate(data['pattern']):
    for x,char in enumerate(row):
     if char!=' ': grid[y*3+x]=data['key'][char]['item']
  else:
   assert data['type']=='minecraft:crafting_shapeless'
   for i,item in enumerate(data['ingredients']): grid[i]=item['item']
  assert len(grid)==9
  result=data['result']
  diagrams.append('new Diagram('+str(data['type'].endswith('shapeless')).lower()+', List.of('+', '.join(map(q,grid))+'), '+q(result['id'])+', '+str(result.get('count',1))+')')
 lines.append('        Map.entry('+q(key)+', List.of('+', '.join(diagrams)+')),')
lines[-1]=lines[-1].removesuffix(',')
lines += ['    );', '    private JournalCraftingRecipes() { }', '}', '']
target=Path('src/client/java/net/alkahest/client/gui/JournalCraftingRecipes.java'); out='\n'.join(lines)
if '--check' in sys.argv:
 if not target.exists() or target.read_text()!=out: sys.exit('Generated journal recipes are stale')
else: target.write_text(out)
