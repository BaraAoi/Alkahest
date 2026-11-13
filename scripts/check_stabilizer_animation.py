#!/usr/bin/env python3
"""Keep the inactive crest and its item unchanged; the live renderer only replaces its top."""
import json
from pathlib import Path
root=Path('src/main/resources/assets/alkahest')
load=lambda name: json.loads((root/name).read_text())
states=load('blockstates/stabilizer_crest.json')['variants']
assert states['rotating=false']['model']=='alkahest:block/stabilizer_crest'
assert states['rotating=true']['model']=='alkahest:block/stabilizer_crest_base'
whole=load('models/block/stabilizer_crest.json')
base=load('models/block/stabilizer_crest_base.json')
assert base['textures']==whole['textures'] and base['elements']==whole['elements'][:1]
assert len(whole['elements'])==3 and load('models/item/stabilizer_crest.json')['parent']=='alkahest:block/stabilizer_crest'
assert whole['elements'][1]['from']==[6,2,6] and whole['elements'][1]['to']==[10,10,10]
assert whole['elements'][2]['from']==[7,10,7] and whole['elements'][2]['to']==[9,14,9]
client=Path('src/client/java/net/alkahest/client/render/StabilizerCoreRenderer.java').read_text()
assert 'world.getTime()%240' in client and '*1.5f' in client
assert 'StabilizerCoreBlock.ACTIVE' in client and 'StabilizerCoreBlock.FORMED' in client
assert 'StabilizerCrestBlock.ROTATING' in client and 'rendersOutsideBoundingBox' in client
print('Stabilizer crystal animation assets and state wiring validated')
