#!/usr/bin/env python3
"""Block models for the 0.1.32 update: bigger Stabilizer vessel, crude tier-1 distillery,
tier-2 multiblock distillery and its essence bellows."""
import json
from pathlib import Path

A = Path('src/main/resources/assets/alkahest')
MODELS = A / 'models/block'
ITEM_MODELS = A / 'models/item'
STATES = A / 'blockstates'

FACES = ("north", "south", "east", "west", "up", "down")


def box(frm, to, texture, faces=None):
    return {
        "from": list(frm),
        "to": list(to),
        "faces": {f: {"texture": texture} for f in (faces or FACES)},
    }


def model(textures, elements, translucent=False):
    data = {"parent": "minecraft:block/block", "textures": textures, "elements": elements}
    if translucent:
        data["render_type"] = "minecraft:translucent"
    return data


def write(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n")


# --- Stabilizer receiver: a real vessel with a large glass tank ---------------------------------
frame = "alkahest:block/stabilizer_frame"
glass = "alkahest:block/stabilizer_glass"
essence = "alkahest:block/stabilizer_essence"

vessel_common = [
    box((1, 0, 1), (15, 3, 15), "#frame"),
    box((2, 3, 2), (14, 13, 14), "#glass"),
    box((1, 13, 1), (15, 16, 15), "#frame"),
    box((1, 3, 1), (2, 13, 2), "#frame"),
    box((14, 3, 1), (15, 13, 2), "#frame"),
    box((1, 3, 14), (2, 13, 15), "#frame"),
    box((14, 3, 14), (15, 13, 15), "#frame"),
    box((5, 16, 5), (8, 17, 8), "#frame"),
]
add_receiver = model({"frame": frame, "glass": glass, "particle": frame, "essence": essence},
                     vessel_common, translucent=True)
add_receiver_occupied = model({"frame": frame, "glass": glass, "particle": frame, "essence": essence},
                              vessel_common + [box((3, 3, 3), (13, 12, 13), "#essence")], translucent=True)
write(MODELS / 'stabilizer_receiver.json', add_receiver)
write(MODELS / 'stabilizer_receiver_occupied.json', add_receiver_occupied)

# --- Crude tier-1 distillery -------------------------------------------------------------------
crude_copper = "alkahest:block/distillery_crude"
crude_panel = "alkahest:block/distillery_crude_panel"
crude_glass = "alkahest:block/distillery_crude_glass"

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
write(MODELS / 'distillery_crude.json',
      model({"copper": crude_copper, "panel": crude_panel, "glass": crude_glass, "particle": crude_copper},
            crude_common))
write(MODELS / 'distillery_crude_working.json',
      model({"copper": crude_copper, "panel": crude_panel, "glass": crude_glass,
             "essence": essence, "particle": crude_copper},
            crude_common + [box((5, 5, 1), (11, 8, 2), "#essence")]))

# --- Tier-2 distillery: clean brass-boiler with a big window -----------------------------------
t2_copper = "alkahest:block/stabilizer_frame"
t2_panel = "alkahest:block/distillery_panel"
t2_glass = "alkahest:block/stabilizer_glass"

t2_common = [
    box((0, 0, 0), (16, 3, 16), "#panel"),
    box((1, 3, 1), (15, 12, 15), "#copper"),
    box((0, 12, 0), (16, 15, 16), "#panel"),
    box((2, 3, 2), (14, 12, 14), "#glass"),
    box((6, 15, 6), (10, 16, 10), "#copper"),
    box((0, 4, 0), (1, 11, 1), "#copper"),
    box((15, 4, 0), (16, 11, 1), "#copper"),
    box((0, 4, 15), (1, 11, 16), "#copper"),
    box((15, 4, 15), (16, 11, 16), "#copper"),
]
write(MODELS / 'distillery_tier2.json',
      model({"copper": t2_copper, "panel": t2_panel, "glass": t2_glass, "particle": t2_copper},
            t2_common, translucent=True))
write(MODELS / 'distillery_tier2_working.json',
      model({"copper": t2_copper, "panel": t2_panel, "glass": t2_glass,
             "essence": essence, "particle": t2_copper},
            t2_common + [box((3, 3, 3), (13, 11, 13), "#essence")], translucent=True))

# --- Essence bellows: the bag grows with the stored amount -------------------------------------
wood = "alkahest:block/bellows_wood"
leather = "alkahest:block/bellows_leather"
iron = "minecraft:block/iron_block"

bellows_frame = [
    box((2, 0, 2), (14, 2, 14), "#wood"),
    box((2, 14, 2), (14, 16, 14), "#wood"),
    box((2, 2, 2), (3, 14, 3), "#wood"),
    box((13, 2, 2), (14, 14, 3), "#wood"),
    box((2, 2, 13), (3, 14, 14), "#wood"),
    box((13, 2, 13), (14, 14, 14), "#wood"),
    box((3, 6, 3), (13, 8, 4), "#iron"),
]
for level, top in ((0, 12), (1, 12), (2, 11), (3, 10), (4, 8)):
    pass

bellows_models = {}
for level in range(4):
    bag_top = (4, 6, 9, 12)[level]
    elements = list(bellows_frame)
    if level > 0:
        elements.append(box((3, 3, 3), (13, bag_top, 13), "#leather"))
        elements.append(box((3, bag_top - 1, 3), (13, bag_top, 13), "#iron"))
    bellows_models[level] = model({"wood": wood, "leather": leather, "iron": iron, "particle": wood},
                                  elements)
    write(MODELS / f'distillery_bellows_{level}.json', bellows_models[level])

# --- blockstates and item models ---------------------------------------------------------------
write(STATES / 'distillery.json', {"variants": {
    "working=false": {"model": "alkahest:block/distillery_crude"},
    "working=true": {"model": "alkahest:block/distillery_crude_working"},
}})
write(STATES / 'distillery_tier2.json', {"variants": {
    "working=false": {"model": "alkahest:block/distillery_tier2"},
    "working=true": {"model": "alkahest:block/distillery_tier2_working"},
}})
write(STATES / 'distillery_bellows.json', {"variants": {
    f"level={level}": {"model": f"alkahest:block/distillery_bellows_{level}"} for level in range(4)
}})
write(ITEM_MODELS / 'distillery.json', {"parent": "alkahest:block/distillery_crude"})
write(ITEM_MODELS / 'distillery_tier2.json', {"parent": "alkahest:block/distillery_tier2"})
write(ITEM_MODELS / 'distillery_bellows.json', {"parent": "alkahest:block/distillery_bellows_2"})
print('models written')
