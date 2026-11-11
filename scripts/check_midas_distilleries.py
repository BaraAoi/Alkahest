#!/usr/bin/env python3
"""Protect Midas's opt-in speed/stability and the server GameTests for both distillery tiers."""
import json
from pathlib import Path
root=Path('src/main/java/net/alkahest')
effect=(root/'effect/MidasTouchEffect.java').read_text()
assert 'Math.min(1.0f, base + 0.20f)' in effect and ': base' in effect
for name in ('DistilleryBlockEntity', 'Tier2DistilleryBlockEntity'):
    code=(root/'block/entity'/f'{name}.java').read_text()
    assert 'MidasTouchEffect.nearby(world, pos)' in code, f'{name}: must use same-world radius'
    assert 'MidasTouchEffect.progressStep(midas, time)' in code, f'{name}: missing speed boost'
    assert 'MidasTouchEffect.outputStability(' in code, f'{name}: missing stability bonus'
    assert 'tickWithMidas' in code, f'{name}: deterministic game testing seam missing'
code=(root/'block/entity/DistilleryBlockEntity.java').read_text()
assert code.count('MidasTouchEffect.outputStability(')==2, 'both raw and Alkahest crystal outputs need the bonus'
tests=Path('src/gametest/java/net/alkahest/block/entity/MidasDistilleryGameTests.java').read_text()
for test in ('crudeSpeedAndNoEffectRegression','alkahestModeAddsStabilityAndOnlyOneCatalystCharge','tierTwoSpeedStabilityAndNoBellowsRegression','stabilityBonusIsFlatAndCapped'):
    assert f'void {test}(' in tests
mod=json.loads(Path('src/gametest/resources/fabric.mod.json').read_text())
assert 'net.alkahest.block.entity.MidasDistilleryGameTests' in mod['entrypoints']['fabric-gametest']
for lang in ('en_us','ru_ru'):
    data=json.loads(Path(f'src/main/resources/assets/alkahest/lang/{lang}.json').read_text())
    assert data.get('tooltip.alkahest.midas_elixir.distillery')
print('Midas distillery rules and server GameTests validated')
