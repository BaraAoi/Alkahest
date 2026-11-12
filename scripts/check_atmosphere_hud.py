#!/usr/bin/env python3
"""Guard the deliberately text-only, background-free atmosphere display."""
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parent.parent
hud = (root / 'src/client/java/net/alkahest/client/gui/AtmosphereHud.java').read_text(encoding='utf-8')
reading = (root / 'src/main/java/net/alkahest/atmosphere/AtmosphereReading.java').read_text(encoding='utf-8')
events = (root / 'src/main/java/net/alkahest/atmosphere/AtmosphereEvents.java').read_text(encoding='utf-8')
assert 'context.fill(' not in hud, 'No opaque HUD background or frame'
assert 'drawTexture' not in hud and 'drawTextWithShadow' in hud, 'Only the coloured numerals should render'
assert 'Text.translatable' not in hud and '.chance()' not in hud, 'No labels, coordinates or probability on HUD'
assert 'i % 3' in hud and 'i / 3' in hud and 'snapshot.levels()' in hud, 'Six values arranged in two rows'
colors = re.search(r'COLORS\s*=\s*\{([^}]+)\}', hud).group(1).split(',')
assert len(colors) == 6, 'Each essence has a legible colour'
assert 'chance' not in reading and 'message.alkahest.air_chance' not in events, 'Chance is not presented as a reading'
assert 'AtmosphereState.of(world).tick(world)' in events, 'Diffusion actually runs every world tick'
for lang in ('ru_ru', 'en_us'):
    values = json.loads((root / f'src/main/resources/assets/alkahest/lang/{lang}.json').read_text(encoding='utf-8'))
    assert 'key.alkahest.atmosphere' in values and 'key.categories.alkahest' in values
print('Atmosphere HUD is six background-free numeric values; diffusion tick and controls registered')
