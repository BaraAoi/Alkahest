#!/usr/bin/env python3
"""Check the gust, complete bow draw assets, translations, and reproducible artwork."""
import json,struct,subprocess
from pathlib import Path
r=Path('src/main/resources/assets/alkahest')
paths=[r/f'textures/item/bow_aer_{i}.png' for i in range(4)]+[r/'textures/effect/wind_ribbon.png']
before=[p.read_bytes() for p in paths]
subprocess.run(['python3','scripts/make_wind_bow.py'],check=True)
assert before==[p.read_bytes() for p in paths]
for i,p in enumerate(paths):
 b=p.read_bytes(); expected=32 if i<4 else 64
 assert b[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>IIBB',b[16:26])==(expected,expected,8,6)
model=json.loads((r/'models/item/bow_aer.json').read_text())
assert len(model['overrides'])==3
for lang in ('en_us','ru_ru'):
 d=json.loads((r/f'lang/{lang}.json').read_text())
 for key in ('item.alkahest.bow_aer','tooltip.alkahest.bow_aer','screen.alkahest.practice.bow_aer.title','screen.alkahest.practice.bow_aer.recipe'): assert d.get(key),(lang,key)
renderer=Path('src/client/java/net/alkahest/client/render/WindGustRenderer.java').read_text()
assert 'SEGMENTS=24' in renderer and 'strand<3' in renderer and 'getEntityTranslucentEmissive' in renderer
projectile=Path('src/main/java/net/alkahest/entity/WindGustEntity.java').read_text()
assert 'i<2' in projectile and 'if (damaged && !child())' in projectile and 'excludedId' in projectile
assert 'RaycastContext.ShapeType.COLLIDER' in projectile and 'HostileEntity.class' in projectile
assert 'EnderDragonEntity.class' in projectile and 'EndCrystalEntity.class' in projectile and 'EnderDragonPart' in projectile
assert 'EndermanEntity' in projectile and 'WIND_BURST' in projectile and 'GUST_EMITTER_SMALL' in projectile
assert 'LIFETIME=200' in projectile and 'MAIN_MIN_SPEED=2.2' in projectile
assert 'MAIN_DAMAGE=16f, CHILD_DAMAGE=8f' in projectile and 'MAIN_RANGE=64' in projectile
print('Wind bow drawing stages, vortex, targeting and translations validated')
