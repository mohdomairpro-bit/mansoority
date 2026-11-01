"""
the Pale Ward: a heater shield of black-violet plate in a frame of bone. its face is his skull - violet eyes, a
hinged jaw - over a ribcage, horns curling out over the top, bone spikes along the rim. leather straps and a grip
behind. same space as the vanilla shield (face toward +z, grip behind), so the vanilla shield poses fit.
"""
import json, math, os, sys
import numpy as np
from boxes import Box, Sheet, element, fbm, vnoise, border, top_edge
from materials import BONE, PLATE, GLOW, LEATHER, rgba, bone, leather, glow

OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
PZ0, PZ1 = 0.6, 2.2                                  # plate thickness
# rows of the plate from the top: (y0, y1, half-width) - a heater shape, square shoulders, a long taper to a point
ROWS = [(9, 11, 7.0), (7, 9, 7.0), (5, 7, 7.0), (3, 5, 6.9), (1, 3, 6.7), (-1, 1, 6.4), (-3, -1, 6.0), (-5, -3, 5.4),
        (-7, -5, 4.7), (-9, -7, 3.8), (-11, -9, 2.7), (-12.5, -11, 1.5)]


def hw_at(y):
    for a, b, hw in ROWS:
        if a <= y <= b: return hw
    return 0.5


def plate_sh(f, P, U, V, w, h):
    x, y = P[..., 0], P[..., 1]
    hw = np.vectorize(hw_at)(np.clip(y, -12.5, 11))
    edge = hw - np.abs(x)                                                  # distance in from the rim
    n = fbm(P * [1, 0.5, 1], 2.4, 2, 3)
    t = 0.34 + (n - 0.5) * 0.16 + np.clip((y + 12) / 23, 0, 1) * 0.12      # a little lighter toward the top
    t = t - np.clip(1.2 - edge, 0, 1.2) * 0.18                              # shadow against the rim
    ring = (np.abs(edge - 2.6) < 0.18)                                     # an engraved line following the rim
    t = np.where(ring & (f == "south"), 0.62, t)
    if f in ("east", "west", "up", "down"): t = np.full(U.shape, 0.22)
    if f == "north": t = 0.2 + (n - 0.5) * 0.1
    return rgba(PLATE, t)


def skull_face(base):
    def sh(f, P, U, V, w, h):
        c = base(f, P, U, V, w, h)
        if f != "south": return c
        x, y = P[..., 0], P[..., 1]
        dark = np.zeros(U.shape, bool)
        for ex in (-1.6, 1.6):                                                                 # sockets, slanted: it's angry
            sock = ((x - ex) / 1.25) ** 2 + ((y - 5.9) / 1.05) ** 2 < 1
            sock &= y < 6.75 + (np.abs(x) - 1.6) * 0.75
            dark |= sock
        brow = (y > 6.75 + (np.abs(x) - 1.6) * 0.75) & (y < 7.35 + (np.abs(x) - 1.6) * 0.75) & (np.abs(x) < 3.0)
        c[brow] = rgba(BONE, np.full(brow.sum(), 0.4))
        cheek = (y > 4.1) & (y < 4.7) & (np.abs(x) > 2.2)
        c[cheek] = rgba(BONE, np.full(cheek.sum(), 0.45))
        dark |= (np.abs(x) < 0.55 - (y - 4.2) * 0.4) & (y > 3.4) & (y < 4.6)                  # nose
        crack = (np.abs(x - 0.4 - (y - 7) * 0.5) < 0.12) & (y > 7.0) & (y < 8.6)              # a crack down the brow
        c[dark] = rgba(BONE, np.full(dark.sum(), 0.02))
        c[crack] = rgba(BONE, np.full(crack.sum(), 0.25))
        return c
    return sh


def jaw_face(base):
    def sh(f, P, U, V, w, h):
        c = base(f, P, U, V, w, h)
        if f != "south": return c
        x, y = P[..., 0], P[..., 1]
        teeth = (y > 2.0) & (y < 2.75)
        gaps = teeth & (np.floor((x + 3) * 1.6) % 2 == 0)
        c[gaps] = rgba(BONE, np.full(gaps.sum(), 0.1))
        return c
    return sh


def build():
    B = []
    for a, b, hw in ROWS:
        B.append(Box((-hw, a, PZ0), (hw, b, PZ1), plate_sh, name="plate"))
    # the bone frame: rim pieces either side of every row, a top bar, a point at the bottom
    rim = bone(5, 0.9, seams="sides")
    for a, b, hw in ROWS:
        nxt = hw
        B.append(Box((-hw - 0.4, a, PZ0 - 0.2), (-hw + 1.1, b, PZ1 + 0.5), rim, name="rim"))
        B.append(Box((hw - 1.1, a, PZ0 - 0.2), (hw + 0.4, b, PZ1 + 0.5), rim, name="rim"))
    B.append(Box((-7.4, 10.6, PZ0 - 0.2), (7.4, 12.0, PZ1 + 0.5), rim, name="rim_top"))
    B.append(Box((-1.2, 12.0, PZ0 - 0.1), (1.2, 13.2, PZ1 + 0.4), bone(6, 0.9), name="crest"))
    B.append(Box((-1.0, -14.0, PZ0 - 0.1), (1.0, -12.5, PZ1 + 0.4), bone(7, 0.9, (1, -12.5, -0.25)), name="point"))
    # spikes along the rim, leaning up and out
    for s in (-1, 1):
        for y, ln in ((10.9, 3.4), (4.0, 2.8), (-3.0, 2.4)):
            hw = hw_at(y) + 0.4
            x0, x1 = (hw - 0.2, hw + ln) if s > 0 else (-hw - ln, -hw + 0.2)
            B.append(Box((x0, y - 0.6, PZ0 + 0.1), (x1, y + 0.6, PZ1 + 0.2), bone(8 + s, 0.8, (0, 0, 0)) if False else bone(8 + int(y), 0.8),
                         rot=(45 * s, "z", (s * hw, y, 1.4)), name="spike"))
    # the face: his skull, a hinged jaw, horns curling out over the top
    B.append(Box((-3.6, 3.2, PZ1), (3.6, 9.2, PZ1 + 1.3), skull_face(bone(12, 0.8, None, 0.7)), name="skull"))
    B.append(Box((-2.6, 1.6, PZ1), (2.6, 3.2, PZ1 + 1.0), jaw_face(bone(13, 0.8, None, 0.66)), name="jaw"))
    for s in (-1, 1):                                     # fangs hanging past the jaw
        B.append(Box((s * 1.6 - 0.35, 0.6, PZ1 + 0.3), (s * 1.6 + 0.35, 1.6, PZ1 + 0.8), bone(15 + s, 0.5, (1, 1.6, -0.3)), name="fang"))
    for ex in (-1.6, 1.6):
        B.append(Box((ex - 0.4, 5.5, PZ1 + 1.3), (ex + 0.4, 6.2, PZ1 + 1.35), glow(0.62, flicker=0.05), emissive=True, faces=("south",), name="eye"))
    # ribcage: a spine down the middle, ribs curving out, shrinking toward the point
    B.append(Box((-0.6, -10.5, PZ1), (0.6, 1.6, PZ1 + 0.7), bone(30, 0.6), name="spine"))
    for i, (y, half) in enumerate(((-0.4, 4.6), (-2.9, 4.2), (-5.4, 3.5), (-7.8, 2.6))):
        B.append(Box((-half, y - 0.55, PZ1), (half, y + 0.55, PZ1 + 0.5), bone(31 + i, 0.6), name="rib"))
    B.append(Box((-0.7, -1.9, PZ1 + 0.7), (0.7, -0.5, PZ1 + 1.05), glow(0.5, flicker=0.15), emissive=True, name="heart"))
    # behind: straps and the grip
    lth = leather(9)
    for y in (4.5, -4.5):
        B.append(Box((-5.0, y - 0.9, PZ0 - 0.5), (5.0, y + 0.9, PZ0), lth, name="strap"))
    B.append(Box((-1.0, -3.5, -4.0), (1.0, 3.5, -2.2), lth, name="grip"))
    B.append(Box((-1.0, 2.5, -2.2), (1.0, 3.5, PZ0 - 0.5), lth, name="grip_arm"))
    B.append(Box((-1.0, -3.5, -2.2), (1.0, -2.5, PZ0 - 0.5), lth, name="grip_arm"))
    return B


DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [10, 6, -4], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [10, 6, 12], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, 5], "translation": [-10, 2, -10], "scale": [1.05, 1.05, 1.05]},
    "firstperson_lefthand": {"rotation": [0, 180, 5], "translation": [10, 0, -10], "scale": [1.05, 1.05, 1.05]},
    "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.55, 0.55, 0.55]},
    "fixed": {"rotation": [0, 180, 0], "translation": [-4.5, 4.5, -5], "scale": [0.5, 0.5, 0.5]},
    "ground": {"rotation": [0, 0, 0], "translation": [2, 4, 2], "scale": [0.25, 0.25, 0.25]},
}
BLOCKING = {
    "thirdperson_righthand": {"rotation": [45, 135, 0], "translation": [3.51, 11, -2], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [45, 135, 0], "translation": [13.51, 3, 5], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, -5], "translation": [-15, 5, -11], "scale": [1.05, 1.05, 1.05]},
    "firstperson_lefthand": {"rotation": [0, 180, -5], "translation": [5, 5, -11], "scale": [1.05, 1.05, 1.05]},
    "gui": DISPLAY["gui"],
}


def main():
    os.makedirs(OUT + "/models/item", exist_ok=True); os.makedirs(OUT + "/textures/item", exist_ok=True)
    B = build()
    sheet = Sheet(256, 4)
    sheet.paint(B)
    sheet.save(OUT + "/textures/item/pale_ward_model.png")
    model = {"credit": "Mansoority - shadow-gear/shield.py", "texture_size": [256, 256],
             "textures": {"0": "theshadow:item/pale_ward_model", "particle": "theshadow:item/pale_ward_model"},
             "elements": [element(b, {"0": "0"}) for b in B], "gui_light": "front", "display": DISPLAY,
             "overrides": [{"predicate": {"minecraft:blocking": 1}, "model": "theshadow:item/pale_ward_blocking"}]}
    json.dump(model, open(OUT + "/models/item/pale_ward.json", "w"), indent=1)
    json.dump({"parent": "theshadow:item/pale_ward", "display": BLOCKING}, open(OUT + "/models/item/pale_ward_blocking.json", "w"), indent=1)
    print("shield:", len(model["elements"]), "elements")


if __name__ == "__main__":
    main()
