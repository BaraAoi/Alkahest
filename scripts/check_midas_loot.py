#!/usr/bin/env python3
"""Guard the one-roll mob-loot scope and its integration tests."""
import json
from pathlib import Path
main=Path('src/main')
config=json.loads((main/'resources/alkahest.mixins.json').read_text())
for name in ('LivingEntityMidasLootMixin','EntityMidasDropMixin'):
    assert name in config['mixins']
living=(main/'java/net/alkahest/mixin/LivingEntityMidasLootMixin.java').read_text()
copy=(main/'java/net/alkahest/mixin/EntityMidasDropMixin.java').read_text()
assert 'victim instanceof MobEntity' in living and 'source.getAttacker() instanceof PlayerEntity' in living
assert 'player.hasStatusEffect(ModEffects.MIDAS_TOUCH)' in living and 'getRandom().nextBoolean()' in living
assert 'dropLoot(' in living and 'dropEquipment(' in living and 'At.Shift.AFTER' in living
assert 'dropped.getStack().copy()' in copy and 'state.alkahest$copyingDrop()' in copy and 'finally' in copy
assert 'generateLoot' not in copy and 'generateLoot' not in living, 'never reroll a loot table'
for lang in ('ru_ru','en_us'):
    text=json.loads((main/f'resources/assets/alkahest/lang/{lang}.json').read_text())
    assert text.get('tooltip.alkahest.midas_elixir.mob_loot')
tests=Path('src/gametest/java/net/alkahest/test/MidasLootGameTests.java').read_text()
for name in ('noEffectAndNonPlayerKillDoNotDouble','midasRollIsOncePerKillForLootAndEquipment'):
    assert f'void {name}(' in tests
assert 'super.dropLoot(source, playerKill)' in tests and 'dropInventory()' in tests
assert 'ESSENCE_STABILITY' in tests and 'mob.damage(source' in tests
mod=json.loads(Path('src/gametest/resources/fabric.mod.json').read_text())
assert 'net.alkahest.test.MidasLootGameTests' in mod['entrypoints']['fabric-gametest']
print('Midas loot single-roll scope, stack-copy guard and death integration tests validated')
