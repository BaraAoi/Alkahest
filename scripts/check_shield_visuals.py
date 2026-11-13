#!/usr/bin/env python3
"""Validate the generated translucent shell and its explicit client/server wiring."""
from pathlib import Path
import struct, subprocess, zlib
p=Path('src/client/resources/assets/alkahest/textures/effect/shield_shell.png')
original=p.read_bytes()
subprocess.run(['python3','scripts/make_shield_texture.py'],check=True)
assert p.read_bytes()==original, 'shell generator must be reproducible'
assert original[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>IIBB',original[16:26])==(128,128,8,6)
cur=8; packed=bytearray()
while cur<len(original):
    n=struct.unpack_from('>I',original,cur)[0]; kind=original[cur+4:cur+8]
    if kind==b'IDAT': packed.extend(original[cur+8:cur+8+n])
    cur+=12+n
    if kind==b'IEND': break
raw=zlib.decompress(packed); stride=1+128*4
assert len(raw)==128*stride and all(raw[y*stride]==0 for y in range(128))
alpha=[raw[y*stride+1+x*4+3] for y in range(128) for x in range(128)]
assert min(alpha)==0 and 20<=max(alpha)<=60, 'shell must remain translucent'
assert len(set(alpha))>=15, 'ribbons should be softly shaded'
client=Path('src/client/java/net/alkahest/AlkahestClient.java').read_text()
visual=Path('src/client/java/net/alkahest/client/render/ShieldVisuals.java').read_text()
hud=Path('src/client/java/net/alkahest/client/gui/ShieldHud.java').read_text()
server=Path('src/main/java/net/alkahest/item/custom/ShieldRingItem.java').read_text()
assert 'WorldRenderEvents.AFTER_ENTITIES.register' in client and 'ShieldVisualPayload.ID' in client
assert 'PlayerLookup.tracking(player)' in server and 'ServerPlayNetworking.canSend' in server
assert 'LATITUDES=12, LONGITUDES=24' in visual and 'ShieldImpactEffect.opacity' in visual
assert 'ShieldImpactEffect.shouldTrigger(absorbed)' in server and 'sendVisual(player, true)' in server
assert 'sendVisual(entity, false)' in server and 'IMPACTS.remove(id)' in visual
assert 'ShieldBarLayout.calculate' in hud and 'drawTextWithShadow' not in hud
print('Shield shell, tracking, renderer and HUD checks passed')
