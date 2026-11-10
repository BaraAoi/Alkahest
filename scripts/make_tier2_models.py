#!/usr/bin/env python3
"""Block models for the laboratory updates.

* Crude tier-1 distillery: the dented copper boiler the player asked to keep.
* Stabilizer receiver: copper-plated vessel with a real glass tank and a tinted
  essence column (the client tints faces with tintindex 0 from the block entity).
* Tier-2 distillery: reuses the original brass distillery artwork.
* Essence bellows: a small glass flask on a metal foot with a central straw; the
  liquid inside is tinted with the colour of the stored essence.
"""
import json
from pathlib import Path

A = Path('src/main/resources/assets/alkahest')
MODELS = A / 'models/block'
ITEM_MODELS = A / 'models/item'
STATES = A / 'blockstates'

FACES = ("north", "south", "east", "west", "up", "down")

copper = "alkahest:block/distillery_copper"
panel = "alkahest:block/distillery_panel"
crude_copper = "alkahest:block/distillery_crude"
crude_panel = "alkahest:block/distillery_crude_panel"
crude_glass = "alkahest:block/distillery_crude_glass"
glass = "minecraft:block/glass"
essence = "alkahest:block/jar_essence"
iron = "alkahest:block/bellows_iron"


def box(frm, to, texture, tint=False):
    faces = {face: {"texture": texture} for face in FACES}
    if tint:
        for face in faces.values():
            face["tintindex"] = 0
    return {"from": list(frm), "to": list(to), "faces": faces}


def model(textures, elements):
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def write(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n")


# --- Crude tier-1 distillery: patched copper, sooty window, crooked chimney --------------------
crude_common = [
    box((1, 0, 1), (15, 3, 15), "#panel"),
    box((2, 3, 2), (14, 11, 14), "#copper"),
    box((1, 4, 1), (15, 5, 15), "#panel"),
    box((1, 9, 1), (15, 10, 15), "#panel"),
    box((5, 5, 1), (11, 8, 2), "#glass"),
    box((2, 11, 11), (5, 16, 14), "#copper"),
    box((11, 11, 2), (14, 14, 5), "#copper"),
    box((3, 11, 3), (5, 12, 5), "#panel"),
    box((12, 6, 12), (15, 9, 15), "#panel"),
]
write(STATES / 'distillery.json', {"variants": {
    "working=false": {"model": "alkahest:block/distillery_crude"},
    "working=true": {"model": "alkahest:block/distillery_crude_working"},
}})
write(ITEM_MODELS / 'distillery.json', {"parent": "alkahest:block/distillery_crude"})
write(MODELS / 'distillery_crude.json',
      model({"copper": crude_copper, "panel": crude_panel, "glass": crude_glass,
             "particle": crude_copper}, crude_common))
write(MODELS / 'distillery_crude_working.json',
      model({"copper": crude_copper, "panel": crude_panel, "glass": crude_glass,
             "essence": essence, "particle": crude_copper},
            crude_common + [box((5, 5, 1), (11, 8, 2), "#essence")]))

# --- Stabilizer receiver: visible liquid behind real glass -------------------------------------
vessel = [
    box((1, 0, 1), (15, 3, 15), "#panel"),
    box((2, 3, 2), (14, 14, 14), "#glass"),
    # open crown, so the liquid is visible from above as well
    box((1, 14, 1), (15, 16, 3), "#copper"),
    box((1, 14, 13), (15, 16, 15), "#copper"),
    box((1, 14, 3), (3, 16, 13), "#copper"),
    box((13, 14, 3), (15, 16, 13), "#copper"),
    box((1, 3, 1), (3, 14, 3), "#copper"),
    box((13, 3, 1), (15, 14, 3), "#copper"),
    box((1, 3, 13), (3, 14, 15), "#copper"),
    box((13, 3, 13), (15, 14, 15), "#copper"),
]
textures = {"copper": copper, "panel": panel, "glass": glass, "essence": essence, "particle": copper}
write(MODELS / 'stabilizer_receiver.json', model(textures, vessel))
write(MODELS / 'stabilizer_receiver_occupied.json',
      model(textures, vessel + [box((3, 3.2, 3), (13, 10, 13), "#essence", tint=True)]))

# --- Tier-2 distillery: the original, detailed brass cube --------------------------------------
write(STATES / 'distillery_tier2.json', {"variants": {
    "working=false": {"model": "alkahest:block/distillery"},
    "working=true": {"model": "alkahest:block/distillery_working"},
}})
write(ITEM_MODELS / 'distillery_tier2.json', {"parent": "alkahest:block/distillery"})

# --- Essence bellows: a glass bulb flask that shows the essence it holds -----------------------
# The flask is a little smaller than a full block, stands on an iron foot and carries a
# copper straw; the stored liquid rises inside the belly and is tinted with its own colour.
feet = [
    box((1, 0, 1), (15, 2, 15), "#iron"),
    box((2, 2, 2), (14, 3, 14), "#copper"),
    # one glass body: a single box keeps the flask free of internal seams
    box((3, 3, 3), (13, 12, 13), "#glass"),
    # open neck of four copper strips, so the liquid stays visible from above
    box((2.5, 12, 2.5), (13.5, 13, 5), "#copper"),
    box((2.5, 12, 11), (13.5, 13, 13.5), "#copper"),
    box((2.5, 12, 5), (5, 13, 11), "#copper"),
    box((11, 12, 5), (13.5, 13, 11), "#copper"),
    box((6.5, 12.4, 6.5), (9.5, 13.6, 9.5), "#copper"),
    box((7, 13.6, 7), (9, 16, 9), "#iron"),
    box((7.6, 3.2, 7.6), (8.4, 13, 8.4), "#copper"),
]

for level in range(4):
    elements = list(feet)
    if level > 0:
        top = (5.6, 8.2, 11.2)[level - 1]
        elements.append(box((3.4, 3.4, 3.4), (12.6, top, 12.6), "#essence", tint=True))
    write(MODELS / f'distillery_bellows_{level}.json',
          model({"iron": iron, "copper": copper, "glass": glass, "essence": essence,
                 "particle": glass}, elements))

write(STATES / 'distillery_bellows.json', {"variants": {
    f"level={level}": {"model": f"alkahest:block/distillery_bellows_{level}"} for level in range(4)
}})
write(ITEM_MODELS / 'distillery_bellows.json', {"parent": "alkahest:block/distillery_bellows_2"})
print('models written')
