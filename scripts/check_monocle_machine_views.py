#!/usr/bin/env python3
"""Keep monocle diagnostics linked to real machine lookups and both localizations."""
from pathlib import Path
import json
keys=['stabilizer_title','stabilizer_on','stabilizer_off','charger','charger_no_jar','charger_below','charger_above','charger_invalid']
for lang in ('ru_ru','en_us'):
    data=json.loads(Path(f'src/main/resources/assets/alkahest/lang/{lang}.json').read_text())
    for k in keys: assert data.get('screen.alkahest.monocle.'+k), (lang,k)
hud=Path('src/client/java/net/alkahest/client/gui/MonocleHud.java').read_text()
for s in ['StabilizerInspection.inspect(client.world, hit.getBlockPos())','EssenceChargerBlockEntity.findJar(client.world, charger.getPos())','JarItem.allowed(contents)','charger.getPos().down()']:
    assert s in hud,s
states=json.loads(Path('src/main/resources/assets/alkahest/blockstates/stabilizer_crest.json').read_text())['variants']
assert states=={'':{'model':'alkahest:block/stabilizer_crest'}},'user requested original static crest'
assert not Path('src/client/java/net/alkahest/client/render/StabilizerCoreRenderer.java').exists()
print('Monocle machine diagnostics and static stabilizer crest validated')
