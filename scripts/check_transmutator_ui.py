#!/usr/bin/env python3
"""Check artwork integrity, panel geometry and server/client slot alignment for the tower GUI."""
import json, re, struct, zlib
from pathlib import Path

handler = Path('src/main/java/net/alkahest/screen/TransmutatorScreenHandler.java').read_text()
screen = Path('src/client/java/net/alkahest/client/gui/TransmutatorScreen.java').read_text()

def value(name):
    found = re.search(r'\b' + name + r'\s*=\s*(\d+)', handler)
    assert found, f'missing shared constant: {name}'
    return int(found.group(1))
W,H = value('WIDTH'),value('HEIGHT')
assert (W,H) == (236,220)
image = Path('src/client/resources/assets/alkahest/textures/gui/transmutator_panel.png').read_bytes()
assert image[:8] == b'\x89PNG\r\n\x1a\n'
pos, idat, dimensions, end = 8, bytearray(), None, False
while pos < len(image):
    n=struct.unpack_from('>I', image, pos)[0]
    kind=image[pos+4:pos+8]; data=image[pos+8:pos+8+n]
    crc=struct.unpack_from('>I',image,pos+8+n)[0]
    assert zlib.crc32(kind+data)&0xffffffff == crc, f'{kind!r} CRC mismatch'
    if kind==b'IHDR': dimensions=struct.unpack('>IIBBBBB',data)
    if kind==b'IDAT': idat.extend(data)
    pos += n+12
    if kind==b'IEND': end=True; break
assert end and pos==len(image) and dimensions[:4]==(W,H,8,6)
raw=zlib.decompress(idat)
assert len(raw)==(1+4*W)*H and len(set(raw))>35, 'panel must have actual artwork'

# A rectangle uses exclusive right/bottom edges, matching Minecraft's slot coordinates.
def rect(x,y,w,h): return (x,y,x+w,y+h)
def overlap(a,b): return a[0]<b[2] and b[0]<a[2] and a[1]<b[3] and b[1]<a[3]
def inside(a, b): return a[0]>=b[0] and a[1]>=b[1] and a[2]<=b[2] and a[3]<=b[3]
canvas=rect(0,0,W,H)
slots=[rect(value(n+'_X'),value(n+'_Y'),16,16) for n in ('INPUT','AMULET','OUTPUT')]
slots += [rect(value('PLAYER_X')+col*18,value('PLAYER_Y')+row*18,16,16) for row in range(3) for col in range(9)]
slots += [rect(value('PLAYER_X')+col*18,value('HOTBAR_Y'),16,16) for col in range(9)]
assert len(slots)==39 and all(inside(r,canvas) for r in slots)
for i,a in enumerate(slots):
    for b in slots[i+1:]: assert not overlap(a,b), f'overlapping slot hitboxes: {a} {b}'
reserved={
    'title':rect(13,9,205,9),
    'input label':rect(18,32,43,9),
    'output label':rect(96,32,38,9),
    'amulet label':rect(51,82,76,9),
    'amulet charge':rect(51,94,76,9),
    'amulet bar':rect(51,104,76,2),
    'progress track':rect(53,49,45,9),
    'progress percent':rect(54,63,43,9),
    'recipe selector':rect(value('CHOICE_X'),value('CHOICE_Y'),value('CHOICE_WIDTH'),value('CHOICE_HEIGHT')),
    'cost heading':rect(146,32,76,9),
    'status':rect(18,113,200,9),
    'inventory label':rect(13,130,206,9),
}
for i in range(5): reserved[f'cost {i}']=rect(146,45+i*11,74,9)
for name, r in reserved.items():
    assert inside(r,canvas), f'{name} overflows panel'
    for slot in slots: assert not overlap(r,slot), f'{name} overlaps slot {slot}'
for name,a in reserved.items():
    for other,b in reserved.items():
        if name < other: assert not overlap(a,b), f'{name} overlaps {other}'
for symbol in ('PLAYER_X + col * 18','PLAYER_Y + row * 18','HOTBAR_Y','INPUT_X','AMULET_X','OUTPUT_X'):
    assert symbol in handler and symbol in screen, f'slot geometry must be shared: {symbol}'
assert 'fit(statusText().getString(), 200)' in screen and 'c.drawTooltip(textRenderer, statusText()' in screen
assert 'amuletCharge()' in screen and 'ESSENCE_CHARGE' in screen and 'AlkahestAmuletItem.MAX_CHARGE' in screen
assert 'for (EssenceStack e : handler.requirements())' in screen, 'full cost tooltip must remain available'
for locale in ('ru_ru','en_us'):
    translations=json.loads(Path(f'src/main/resources/assets/alkahest/lang/{locale}.json').read_text())
    for field in ('input','output','amulet','cost','progress','amulet_hint','amulet_charge','no_recipe','recipe_choice','recipe_choice_hint','choose_button','change_button','choose_heading','choose_status','pick_hint','ready','working','no_essence','blocked','unformed','no_amulet','dormant'):
        assert translations.get('screen.alkahest.transmutator.'+field), f'{locale} missing {field}'
    assert '%s' in translations['screen.alkahest.transmutator.progress']
    assert translations['screen.alkahest.transmutator.amulet_charge'].count('%s') == 2
print(f'Transmutator UI validated: {W}x{H}, {len(slots)} slots, non-overlapping panels and localized tooltips')

assert 'canSelectRecipe()' in screen and 'canSelectIronRecipe' not in screen
assert 'TransmutationRecipe.choices(' in handler and 'core.selectRecipe(id - 1)' in handler
for locale in ('ru_ru', 'en_us'):
    data=json.loads(Path(f'src/main/resources/assets/alkahest/lang/{locale}.json').read_text())
    assert data['screen.alkahest.transmutator.recipe_choice'].count('%s') == 3

assert 'handler.needsRecipeChoice()' in screen and 'CHOICES_PER_PAGE' in screen
assert 'clickButton(handler.syncId, index + 1)' in screen

# The third iron recipe is reachable via the second page, with no overlap with slots.
assert 'CHOICES_PER_PAGE = 2' in screen and 'pages() > 1' in screen and 'Math.floorMod(page()' in screen
for r in (rect(146,45,74,18), rect(146,65,74,18), rect(146,87,74,10)):
    assert inside(r,canvas) and all(not overlap(r,slot) for slot in slots)
