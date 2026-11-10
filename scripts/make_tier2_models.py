#!/usr/bin/env python3
"""Block models for the 0.1.33 update.

* Stabilizer receiver: copper-plated vessel with a real glass tank and a tinted
  essence column (the client tints faces with tintindex 0 from the block entity).
* Tier-2 distillery: reuses the original brass distillery artwork.
* Essence bellows: Thaumcraft-style wooden boards around a leather bag that
  inflates with the stored amount.
"""
import json
from pathlib import Path


A = Path('src/main/resources/assets/alkahest')
MODELS = A / 'models/block'
ITEM_MODELS = A / 'models/item'
STATES = A / 'blockstates'

FACES = ("north", "south", "east", "west", "up", "down")


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


# --- Stabilizer receiver: visible liquid behind real glass -------------------------------------
copper = "alkahest:block/distillery_copper"
panel = "alkahest:block/distillery_panel"
glass = "minecraft:block/glass"
essence = "alkahest:block/jar_essence"

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

# --- Essence bellows: Thaumcraft-inspired boards, leather bag, iron nozzle ---------------------
wood = "alkahest:block/bellows_wood"
leather = "alkahest:block/bellows_leather"
iron = "alkahest:block/bellows_iron"

frame = [
    box((0, 1, 2), (2, 15, 14), "#wood"),
    box((14, 1, 2), (16, 15, 14), "#wood"),
    box((2, 1, 2), (14, 3, 14), "#wood"),
    box((2, 13, 2), (14, 15, 14), "#wood"),
    box((2, 5, 2), (14, 6, 14), "#wood"),
    box((7, 0, 7), (9, 2, 9), "#iron"),
    box((6, 3, 6), (10, 4, 10), "#iron"),
    box((0, 7, 4), (2, 8, 12), "#iron"),
    box((14, 7, 4), (16, 8, 12), "#iron"),
]

for level in range(4):
    top = (5, 8, 11, 13)[level]
    elements = list(frame)
    if level > 0:
        elements.append(box((2, 3, 2), (14, top, 14), "#leather"))
        # accordion ribs, brighter on the upper edge
        for rib in range(5, top - 1, 3):
            elements.append(box((1.9, rib, 1.9), (14.1, rib + 1, 14.1), "#iron"))
    write(MODELS / f'distillery_bellows_{level}.json',
          model({"wood": wood, "leather": leather, "iron": iron, "particle": wood}, elements))

write(STATES / 'distillery_bellows.json', {"variants": {
    f"level={level}": {"model": f"alkahest:block/distillery_bellows_{level}"} for level in range(4)
}})
write(ITEM_MODELS / 'distillery_bellows.json', {"parent": "alkahest:block/distillery_bellows_2"})
print('models written')
