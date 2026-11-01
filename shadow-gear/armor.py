"""
Shade armour: a dark knight made of his remains. black-violet plate under bone. the helm is his face - an angry
skull mask with glowing violet eyes - with horns sweeping back off the temples and a ridge of spines over the top.
a ribcage over the chest with a violet core showing through, a high collar behind the head, a bone spine down the
back. pauldrons with bone spikes, clawed gauntlets, an elbow spur. bone knee guards with a spike, tassets, clawed
sabatons.

written as cubes on the vanilla humanoid parts (Minecraft box UV), the texture at twice the vanilla density.
writes armor/shade_armor.json (read by ShadeArmorModel) and textures/models/armor/shade_armor.png
"""
import json, math, os, sys
import numpy as np
from PIL import Image
from boxes import fbm, vnoise
from materials import BONE, PLATE, GLOW, LEATHER, rgba

OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
TEX = 128                     # declared texture size (model units)
DEN = 2                       # texels per unit -> a 256 png

# vanilla part pivots (model space: y down, front is -z)
PIVOT = {"head": (0, 0, 0), "body": (0, 0, 0), "right_arm": (-5, 2, 0), "left_arm": (5, 2, 0), "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0)}


def q(v): return round(v * 2) / 2


class Cube:
    def __init__(self, part, box, mat, pivot=(0, 0, 0), rot=(0, 0, 0), deco=None):
        self.part = part
        self.box = list(box[:3]) + [q(v) for v in box[3:]]   # x, y, z, w, h, d relative to pivot (sizes on the half-texel grid)
        self.grow = 0.0
        self.mat = mat; self.pivot = pivot; self.rot = rot; self.deco = deco
        self.uv = None


# ------------------------------------------------------------------------------------------ materials (3D shaders)
def m_plate(face, P, a, b, W, H, cube):
    n = fbm(P * [1, 0.6, 1], 1.8, 2, 3)
    t = 0.36 + (n - 0.5) * 0.14
    xi = np.floor(a * W); yi = np.floor(b * H)
    t = np.where(yi < 1, t + 0.2, t)                      # lit top edge
    t = np.where((xi < 1) | (xi >= W - 1), t - 0.1, t)
    t = np.where(yi >= H - 1, t - 0.16, t)
    if face == "top": t = t + 0.06
    if face == "bottom": t = t - 0.12
    return rgba(PLATE, t)


def m_bone(face, P, a, b, W, H, cube):
    n = fbm(P, 3.0, 2, 5)
    t = 0.66 + (n - 0.5) * 0.16
    cr = np.abs(fbm(P, 2.0, 2, 12) - 0.5) < 0.014
    t = np.where(cr, t - 0.2, t)
    xi = np.floor(a * W); yi = np.floor(b * H)
    if face in ("top",): t = t + 0.06
    if face == "bottom": t = t - 0.14
    if face not in ("top", "bottom"):
        t = np.where(yi < 1, t + 0.12, t)
        t = np.where((xi < 1) | (xi >= W - 1) | (yi >= H - 1), t - 0.14, t)
    return rgba(BONE, t)


def m_bone_tip(axis, start, rate):
    def sh(face, P, a, b, W, H, cube):
        c = m_bone(face, P, a, b, W, H, cube)
        t = np.clip((P[..., axis] - start) * rate, 0, 0.5)
        return c * (1 - t[..., None] * [1, 1, 1, 0]) + 0
    return sh


def m_leather(face, P, a, b, W, H, cube):
    n = fbm(P, 1.2, 2, 7)
    t = 0.45 + (n - 0.5) * 0.25
    yi = np.floor(b * H)
    t = np.where(yi % 4 == 0, t - 0.15, t)                # stitched bands
    return rgba(LEATHER, t)


def m_glow(face, P, a, b, W, H, cube):
    d = np.maximum(np.abs(a - 0.5), np.abs(b - 0.5)) * 2
    return rgba(GLOW, 0.95 - d * 0.45)


def m_trimmed(face, P, a, b, W, H, cube):
    """plate with a bone trim around its front and sides"""
    c = m_plate(face, P, a, b, W, H, cube)
    if face in ("top", "bottom"): return c
    xi = np.floor(a * W); yi = np.floor(b * H)
    trim = (yi >= H - 2) | (xi < 2) | (xi >= W - 2) if face in ("front", "back") else (yi >= H - 2)
    bc = m_bone(face, P, a, b, W, H, cube)
    return np.where(trim[..., None], bc, c)


# ------------------------------------------------------------------------------------------ decals
def skull_mask(face, P, a, b, W, H, c):
    """the helm's face: slanted sockets, nasal hole, a row of teeth; the eyes are their own little boxes"""
    if face != "front": return c
    x, y = P[..., 0], P[..., 1]                           # model space: y down; mask spans y -7.5..-0.5
    dark = np.zeros(x.shape, bool)
    bnd = -5.9 + 0.5 * (3.0 - np.abs(x))                  # the sockets' top edge: low at the middle, high outside (angry)
    for ex in (-1.9, 1.9):
        dark |= (((x - ex) / 1.6) ** 2 + ((y + 4.7) / 1.25) ** 2 < 1) & (y > bnd)
    brow = (y > bnd - 0.8) & (y <= bnd) & (np.abs(x) < 3.6)
    nose = (np.abs(x) < 0.7 - (y + 2.9) * -0.5) & (y > -3.3) & (y < -2.2)
    teeth = (y > -1.5) & (y < -0.6) & (np.abs(x) < 2.6)
    gaps = teeth & (np.floor((x + 3) * 1.4) % 2 == 0)
    c = c.copy()
    c[brow] = rgba(BONE, np.full(brow.sum(), 0.38)); c[dark | nose] = rgba(BONE, np.full((dark | nose).sum(), 0.02))
    c[gaps] = rgba(BONE, np.full(gaps.sum(), 0.08))
    return c


def ribs_decal(face, P, a, b, W, H, c):
    return c


# ------------------------------------------------------------------------------------------ the pieces
def helmet():
    C = []
    H = "head"
    C.append(Cube(H, (-4.5, -8.5, -4.5, 9, 9, 9), m_plate))                       # the helm
    C.append(Cube(H, (-4.0, -7.5, -5.5, 8, 7, 1), m_bone, deco=skull_mask))     # skull mask
    for s in (-1, 1):
        C.append(Cube(H, (s * 1.9 - 0.5, -5.0, -5.75, 1, 0.5, 0.5), m_glow))        # eyes
    C.append(Cube(H, (-4.5, -9.0, -5.0, 9, 1.5, 1.5), m_bone))                   # brow crest
    C.append(Cube(H, (-1.0, -2.5, -6.0, 2, 1.0, 1.0), m_bone))                   # chin
    C.append(Cube(H, (-4.5, -3.0, 4.0, 9, 3.0, 1.5), m_trimmed))                 # neck guard
    for i, z in enumerate((-3.0, -0.5, 2.0)):                                    # spines over the top
        hgt = (2.5, 3.0, 2.0)[i]
        C.append(Cube(H, (-0.5, -8.5 - hgt, z, 1, hgt, 1), m_bone_tip(1, -9.5, -0.25)))
    for s in (-1, 1):                                                            # horns: out from the temples, back and up
        side = "right" if s < 0 else "left"
        base = (s * 4.5, -6.5, -1.0)
        C.append(Cube(H, (0 if s > 0 else -3, -1, -1, 3, 2, 2), m_bone, pivot=base, rot=(0, 0, -s * 0.45)))
        tip1 = (s * 7.0, -8.0, -0.2)
        C.append(Cube(H, (0 if s > 0 else -2.5, -1, -0.75, 2.5, 1.5, 1.5), m_bone, pivot=tip1, rot=(0.35, 0, -s * 1.0)))
        tip2 = (s * 8.0, -10.0, 0.6)
        C.append(Cube(H, (0 if s > 0 else -2, -0.5, -0.5, 2, 1, 1), m_bone_tip(1, -10.0, -0.35), pivot=tip2, rot=(0.6, 0, -s * 1.45)))
    return C


def chestplate():
    C = []
    B = "body"
    C.append(Cube(B, (-4.5, -0.5, -2.5, 9, 12.5, 5), m_plate))                      # cuirass
    C.append(Cube(B, (-1.0, 4.0, -3.5, 2, 2, 1), m_glow))                           # the core, behind the ribs
    C.append(Cube(B, (-0.5, 1.5, -3.5, 1, 8.0, 1), m_bone))                         # sternum
    for i, y in enumerate((2.5, 4.5, 6.5, 8.5)):                                    # ribs
        w = (4.0, 4.0, 3.5, 3.0)[i]
        C.append(Cube(B, (-w, y, -3.0, w * 2, 1, 0.5), m_bone))
        for s in (-1, 1):                                                           # ...wrapping round the sides
            C.append(Cube(B, ((4.5 if s > 0 else -5.0), y, -2.5, 0.5, 1, 3), m_bone))
    C.append(Cube(B, (-5.0, -1.5, -3.0, 10, 2, 6), m_trimmed))                      # gorget
    C.append(Cube(B, (-5.0, -4.5, 2.5, 10, 3.5, 1.5), m_trimmed))                   # high collar behind the head
    for s in (-1, 1):
        C.append(Cube(B, (s * 4.0 - 0.5, -6.5, 2.75, 1, 2, 1), m_bone_tip(1, -5.5, -0.3)))   # collar spikes
    C.append(Cube(B, (-5.0, 10.5, -3.0, 10, 1.5, 6), m_leather))                    # belt
    C.append(Cube(B, (-1.5, 10.0, -3.5, 3, 2.5, 0.5), m_bone))                      # buckle
    for i, y in enumerate((1.5, 4.0, 6.5, 9.0)):                                    # vertebrae down the back
        C.append(Cube(B, (-0.75, y, 2.5, 1.5, 1.5, 1), m_bone))
    for arm, s in (("right_arm", -1), ("left_arm", 1)):
        def mx(x, w): return x if s < 0 else -(x + w)
        C.append(Cube(arm, (mx(-3.5, 5), -2.5, -2.5, 5, 8, 5), m_plate))             # upper arm
        C.append(Cube(arm, (mx(-4.5, 6.5), -3.5, -3.0, 6.5, 4, 6), m_trimmed))       # pauldron
        C.append(Cube(arm, (mx(-4.0, 5.5), 0.5, -2.75, 5.5, 2.5, 5.5), m_trimmed))   # its lower plate
        C.append(Cube(arm, (-0.5, -3, -0.5, 1, 3, 1), m_bone_tip(1, -1.5, -0.35), pivot=(s * 2.5, -3.5, -1.0), rot=(0, 0, s * 0.45)))
        C.append(Cube(arm, (-0.5, -2.5, -0.5, 1, 2.5, 1), m_bone_tip(1, -1.5, -0.35), pivot=(s * 2.5, -3.5, 1.5), rot=(0, 0, s * 0.6)))
        C.append(Cube(arm, (mx(-3.5, 5), 5.5, -2.5, 5, 5, 5), m_trimmed))            # bracer
        C.append(Cube(arm, (-0.5, -0.5, 0, 1, 1, 2), m_bone, pivot=(s * 1.0, 4.0, 2.5)))     # elbow spur
        for dx in (-1.25, 0.0, 1.25):                                               # claws, curling forward
            C.append(Cube(arm, (-0.25, 0, -0.25, 0.5, 2.0, 0.5), m_bone_tip(1, 11.0, 0.4), pivot=(s * 1.0 + dx, 10.0, -1.75), rot=(-0.4, 0, 0)))
    return C


def leggings():
    C = []
    C.append(Cube("body", (-4.5, 9.5, -2.75, 9, 3, 5.5), m_plate))                   # faulds
    for leg, s in (("right_leg", -1), ("left_leg", 1)):
        def mx(x, w): return x if s < 0 else -(x + w)
        C.append(Cube(leg, (-2.5, -0.5, -2.5, 5, 7, 5), m_plate))                     # cuisses
        C.append(Cube(leg, (-2.5, 0.0, -3.0, 5, 4.5, 0.5), m_trimmed))               # front tasset
        C.append(Cube(leg, (mx(-3.0, 0.5), 0.0, -2.5, 0.5, 4.5, 5), m_trimmed))       # side tasset
        C.append(Cube(leg, (-2.5, 5.0, -3.25, 5, 2.5, 1.25), m_bone))                 # knee guard
        C.append(Cube(leg, (-0.5, 5.5, -4.5, 1, 1, 1.5), m_bone_tip(2, -3.5, -0.4)))  # knee spike
        C.append(Cube(leg, (-2.25, 7.5, -2.25, 4.5, 3.5, 4.5), m_plate))              # greaves
    return C


def boots():
    C = []
    for leg, s in (("right_leg", -1), ("left_leg", 1)):
        def mx(x, w): return x if s < 0 else -(x + w)
        C.append(Cube(leg, (-2.5, 8.5, -2.5, 5, 3.5, 5), m_trimmed))                  # sabaton
        C.append(Cube(leg, (-2.5, 10.5, -3.5, 5, 1.5, 1), m_bone))                    # toe cap
        for x in (-2.0, -0.5, 1.0):                                                 # claws
            C.append(Cube(leg, (x, 11.0, -4.5, 1, 1, 1), m_bone_tip(2, -3.5, -0.5)))
        C.append(Cube(leg, (mx(-3.5, 1.0), 8.5, -0.5, 1.0, 1, 1), m_bone))           # ankle spur
    return C


# ------------------------------------------------------------------------------------------ box UV, packing, painting
def faces_of(c):
    """Minecraft box-UV regions for a cube at texOffs (u, v): (name, u0, v0, w, h, point(a, b) -> model xyz)"""
    x, y, z, w, h, d = c.box
    X0, Y0, Z0, X1, Y1, Z1 = x, y, z, x + w, y + h, z + d
    u, v = c.uv
    return [
        ("top", u + d, v, w, d, lambda a, b: (X0 + a * w, Y0 + 0 * a, Z1 - b * d)),          # minY face ("DOWN" polygon)
        ("bottom", u + d + w, v, w, d, lambda a, b: (X0 + a * w, Y1 + 0 * a, Z1 - b * d)),   # maxY face
        ("side", u, v + d, d, h, lambda a, b: (X0 + 0 * a, Y0 + b * h, Z1 - a * d)),          # west (-x)
        ("front", u + d, v + d, w, h, lambda a, b: (X0 + a * w, Y0 + b * h, Z0 + 0 * a)),     # north (-z)
        ("side", u + d + w, v + d, d, h, lambda a, b: (X1 + 0 * a, Y0 + b * h, Z0 + a * d)),  # east (+x)
        ("back", u + 2 * d + w, v + d, w, h, lambda a, b: (X1 - a * w, Y0 + b * h, Z1 + 0 * a)),  # south (+z)
    ]


def pack(cubes):
    items = sorted(cubes, key=lambda c: -(c.box[5] + c.box[4]))
    x = y = row = 0
    for c in items:
        _, _, _, w, h, d = c.box
        cw = int(math.ceil(2 * (d + w))) + 1; ch = int(math.ceil(d + h)) + 1
        if x + cw > TEX: x = 0; y += row; row = 0
        if y + ch > TEX: raise RuntimeError("armour texture full")
        c.uv = (x, y); x += cw; row = max(row, ch)
    return y + row


def paint(cubes):
    img = np.zeros((TEX * DEN, TEX * DEN, 4))
    exact = []
    for c in cubes:
        for name, u0, v0, fw, fh, pt in faces_of(c):
            W = int(round(fw * DEN)); Hh = int(round(fh * DEN))
            if W == 0 or Hh == 0: continue
            a, b = np.meshgrid((np.arange(W) + 0.5) / W, (np.arange(Hh) + 0.5) / Hh)
            P = np.stack(pt(a, b), -1).astype(float)
            col = c.mat(name, P, a, b, W, Hh, c)
            if c.deco: col = c.deco(name, P, a, b, W, Hh, col)
            X = int(round(u0 * DEN)); Y = int(round(v0 * DEN))
            # bleed a texel all round first so no seam ever samples empty texture
            pad = np.pad(col, ((1, 1), (1, 1), (0, 0)), mode="edge")
            y0, x0 = max(0, Y - 1), max(0, X - 1)
            img[y0:Y + Hh + 1, x0:X + W + 1] = pad[y0 - (Y - 1):, x0 - (X - 1):][:img.shape[0] - y0, :img.shape[1] - x0]
            exact.append((X, Y, W, Hh, col))
    for X, Y, W, Hh, col in exact:
        img[Y:Y + Hh, X:X + W] = col
    return img


def main():
    os.makedirs(OUT + "/armor", exist_ok=True); os.makedirs(OUT + "/textures/models/armor", exist_ok=True)
    slots = {"head": helmet(), "chest": chestplate(), "legs": leggings(), "feet": boots()}
    for cs in slots.values():                     # no two faces ever coplanar (armour renders both sides: they'd z-fight)
        for i, c in enumerate(cs): c.grow = 0.0 if i == 0 else 0.012 + 0.011 * (i % 6)
    allc = [c for v in slots.values() for c in v]
    used = pack(allc)
    print("armour texture rows used:", used, "/", TEX)
    img = paint(allc)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(OUT + "/textures/models/armor/shade_armor.png")
    js = {"texture": [TEX, TEX], "slots": {}}
    for k, cs in slots.items():
        js["slots"][k] = [{"part": c.part, "box": c.box, "uv": list(c.uv), "pivot": list(c.pivot), "rot": list(c.rot), "grow": round(c.grow, 4)} for c in cs]
    json.dump(js, open(OUT + "/armor/shade_armor.json", "w"))
    # for the preview: every face as a quad in world space (standing player, feet at y=0, y up), with its uvs
    quads = []
    for c in allc:
        x, y, z, w, h, d = c.box
        for name, u0, v0, fw, fh, pt in faces_of(c):
            if fw == 0 or fh == 0: continue
            corners = [(0, 0), (1, 0), (1, 1), (0, 1)]
            vs = []
            for a, b in corners:
                p = np.array(pt(np.float64(a), np.float64(b)), float)
                ctr = np.array([x + w / 2, y + h / 2, z + d / 2])
                p = p + np.sign(p - ctr) * c.grow
                rx, ry, rz = c.rot                                         # ModelPart: rotate Z, then Y, then X (zyx)
                Rx = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
                Ry = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
                Rz = np.array([[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
                p = Rz @ Ry @ Rx @ p + np.array(c.pivot) + np.array(PIVOT[c.part])
                vs.append([float(-p[0]), float(24 - p[1]), float(p[2]), (u0 + a * fw) / TEX, (v0 + b * fh) / TEX])
            quads.append(vs)
    json.dump(quads, open(OUT + "/armor/preview_quads.json", "w"))


if __name__ == "__main__":
    main()
