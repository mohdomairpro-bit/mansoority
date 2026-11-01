"""
the Shadow Blade: a black-violet greatsword. bone serrations down one edge, a honed silver-violet edge down the
other, a glowing violet fuller, a bone guard with a skull at its heart and horns sweeping up, a wrapped grip and a
glowing gem in a bone claw for a pommel. wrapped in a flickering violet aura (crossed flame sheets, animated).

modelled upright along +y (centre line x = z = 8), then every element is turned -45 deg about z so it lies like a
sword sprite (grip bottom-left, tip top-right) and the vanilla handheld poses fit.
"""
import json, math, os, sys
import numpy as np
from PIL import Image
from boxes import Box, Sheet, element, fbm, vnoise, border, top_edge
from materials import BLADE, BONE, GLOW, rgba, bone, leather, glow, plate

OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
R = (-45, "z", (8, 8, 8))
Z0, Z1 = 7.4, 8.6                                   # blade thickness

# the blade's half-width as it runs up: wide at the guard, a long taper, a sharp point
SEG = [(4, 7, 3.0), (7, 10, 2.85), (10, 13, 2.7), (13, 16, 2.5), (16, 19, 2.3), (19, 21, 2.05), (21, 23, 1.75),
       (23, 24.5, 1.4), (24.5, 25.5, 1.05), (25.5, 26.5, 0.7), (26.5, 27.25, 0.4)]


def halfw(y):
    for a, b, hw in SEG:
        if a <= y < b: return hw
    return SEG[-1][2]


def blade_sh(f, P, U, V, w, h):
    x, y = P[..., 0], P[..., 1]
    hw = np.vectorize(halfw)(np.clip(y - 1e-4, 4, 27.2))
    d = np.abs(x - 8) / hw
    n = fbm(P * [1, 0.25, 1], 1.6, 3, 11)
    if f in ("north", "south"):
        t = 0.30 + (n - 0.5) * 0.22 + 0.05 * np.sin(y * 1.7 + n * 4)
        t = t - 0.12 * (d < 0.42)                                        # darker band either side of the fuller
        t = np.where((d > 0.62) & (d < 0.72), 0.06, t)                   # the bevel groove
        edge = d >= 0.72
        t = np.where(edge, 0.58 + (d - 0.72) / 0.28 * 0.42 + (n - 0.5) * 0.1, t)
        t = np.where(d > 0.93, 0.98, t)                                  # the honed line
    elif f in ("east", "west"):
        t = 0.82 + (n - 0.5) * 0.12
        zz = np.abs(P[..., 2] - 8) / ((Z1 - Z0) / 2)
        t = np.where(zz > 0.7, 0.6, t)
    else:
        t = np.full(U.shape, 0.75)
    return rgba(BLADE, t)


def skull_face(base_sh):
    """the guard's skull: sockets, a nose hole, a row of teeth (front and back)"""
    def sh(f, P, U, V, w, h):
        c = base_sh(f, P, U, V, w, h)
        if f not in ("north", "south"): return c
        x, y = P[..., 0], P[..., 1]
        dark = np.zeros(U.shape, bool)
        for ex in (6.85, 9.15):
            dark |= ((x - ex) / 0.85) ** 2 + ((y - 3.75) / 0.62) ** 2 < 1      # sockets
        dark |= (np.abs(x - 8) < 0.3 - (y - 2.7) * 0.25) & (y > 2.3) & (y < 2.95)      # nose
        teeth = (y > 1.2) & (y < 1.75) & (np.abs(x - 8) < 1.5)
        gaps = teeth & (np.floor((x - 6.5) * 2.0) % 2 == 0)
        c[dark] = rgba(BONE, np.full(dark.sum(), 0.02))
        c[gaps] = rgba(BONE, np.full(gaps.sum(), 0.12))
        return c
    return sh


def world_box(cx, cy, sx, sy, z0, z1, sh, **kw):
    """an element that is axis-aligned in the FINAL (rotated) frame, placed by its centre in the blade frame:
    a diamond relative to the blade"""
    a = math.radians(-45)
    dx, dy = cx - 8, cy - 8
    wx = 8 + dx * math.cos(a) - dy * math.sin(a); wy = 8 + dx * math.sin(a) + dy * math.cos(a)
    return Box((wx - sx / 2, wy - sy / 2, z0), (wx + sx / 2, wy + sy / 2, z1), sh, **kw)


def build():
    B = []
    # blade: a stack of tapering slabs
    for a, b, hw in SEG:
        B.append(Box((8 - hw, a, Z0), (8 + hw, b, Z1), blade_sh, rot=R, name="blade"))
    # the fuller: a glowing line down the middle, standing just proud of both faces
    B.append(Box((7.55, 5.0, Z0 - 0.08), (8.45, 21.5, Z1 + 0.08), glow(0.62, 0, 8.0, 0.45, 0.2), rot=R, emissive=True, name="fuller"))
    B.append(Box((7.75, 21.5, Z0 - 0.08), (8.25, 23.5, Z1 + 0.08), glow(0.7, 0, 8.0, 0.25, 0.1), rot=R, emissive=True, name="fuller_tip"))
    # serrations: bone teeth biting out of the left edge (diamonds relative to the blade)
    for i, ty in enumerate((6.6, 9.4, 12.2, 15.0)):
        s = 1.55 - i * 0.18
        hw = halfw(ty)
        B.append(world_box(8 - hw - 0.05, ty, s, s, Z0 + 0.12, Z1 - 0.12, bone(20 + i, 0.6), name="tooth"))
    # the guard: a bar of bone, horns sweeping up at each end
    g = bone(31, 0.8)
    B.append(Box((1.8, 2.2, 6.8), (14.2, 4.0, 9.2), g, rot=R, name="guard"))
    for s in (-1, 1):
        def X(a, b): return (8 + s * a, 8 + s * b) if s > 0 else (8 + s * b, 8 + s * a)
        x0, x1 = X(5.3, 7.2); B.append(Box((x0, 3.4, 7.1), (x1, 5.6, 8.9), bone(40 + s, 0.8, (1, 3.4, 0.08)), rot=R, name="horn"))
        x0, x1 = X(5.9, 7.4); B.append(Box((x0, 5.6, 7.3), (x1, 7.6, 8.7), bone(42 + s, 0.8, (1, 4.0, 0.12)), rot=R, name="horn"))
        x0, x1 = X(6.6, 7.6); B.append(Box((x0, 7.6, 7.5), (x1, 9.2, 8.5), bone(44 + s, 0.8, (1, 5.0, 0.2)), rot=R, name="horn"))
    # the skull at its heart
    B.append(Box((5.6, 0.9, 6.2), (10.4, 5.4, 9.8), skull_face(bone(50, 0.7, None, 0.68)), rot=R, name="skull"))
    for ex in (6.85, 9.15):
        for z0, z1 in ((6.1, 6.2), (9.8, 9.9)):
            B.append(Box((ex - 0.3, 3.5, z0), (ex + 0.3, 4.0, z1), glow(0.58, flicker=0.05), rot=R, emissive=True, faces=("north", "south"), name="eye"))
    # grip: wrapped, with bone collars
    B.append(Box((7.0, -5.0, 7.0), (9.0, 0.9, 9.0), leather(), rot=R, name="grip"))
    for y0 in (-1.2, -4.6):
        B.append(Box((6.75, y0, 6.75), (9.25, y0 + 0.7, 9.25), bone(60, 0.5), rot=R, name="collar"))
    # pommel: a bone claw cupping a glowing gem, a spike below
    B.append(Box((6.5, -7.0, 6.5), (9.5, -5.0, 9.5), bone(70, 0.6), rot=R, name="pommel"))
    B.append(Box((7.2, -8.3, 7.2), (8.8, -6.6, 8.8), glow(0.8, None, flicker=0.25), rot=R, emissive=True, name="gem"))
    B.append(Box((7.6, -9.6, 7.6), (8.4, -8.3, 8.4), bone(72, 0.4, (1, -8.3, -0.2)), rot=R, name="spike"))
    return B


# --------------------------------------------------------------------------------------- the aura (animated)
FRAMES, FS = 16, 64


def aura_frames(xw, y0, y1):
    """flame sheets: a frame is FS x FS covering x in [8-xw, 8+xw] (u) and y in [y1 .. y0] (v, top first).
    violet fire licking up off the blade's outline, white-hot right at the edge"""
    img = np.zeros((FS * FRAMES, FS, 4))
    us = (np.arange(FS) + 0.5) / FS; vs = (np.arange(FS) + 0.5) / FS
    U, V = np.meshgrid(us, vs)
    x = 8 - xw + U * 2 * xw; y = y1 - V * (y1 - y0)
    hw = np.vectorize(halfw)(np.clip(y, 4, 27.2)) * (y >= 4) + 1.0 * (y < 4) * np.clip((y - 0.5) / 3.5, 0.2, 1) * 2.4
    hw = np.where(y > 27.2, np.clip(0.4 - (y - 27.2) * 0.25, 0.01, 1), hw)
    dist = np.maximum(0, np.abs(x - 8) - hw)                              # how far out from the blade's outline
    period = 18.0
    for k in range(FRAMES):
        ph = k / FRAMES
        # flame noise scrolled up by exactly one period per loop, seamless
        Pn = np.stack([x * 1.3, (y - ph * period) % period, np.zeros_like(x)], -1)
        n1 = np.zeros_like(x)
        for sc, wgt in ((3.0, 0.6), (1.4, 0.3), (0.7, 0.1)):
            # tile in y: blend two samples a period apart
            a = Pn.copy(); b = Pn.copy(); b[..., 1] -= period
            m = (Pn[..., 1] / period)
            n1 += wgt * ((1 - m) * vnoise(a, sc, 9) + m * vnoise(b, sc, 9))
        reach = 0.6 + 1.7 * n1 ** 1.6 + 0.35 * np.clip((y - 4) / 23, 0, 1)
        heat = np.clip(1 - dist / reach, 0, 1)
        heat = heat ** 1.4 * (0.55 + 0.6 * n1)
        heat = np.where(dist < 0.05, np.maximum(heat, 0.25), heat)        # a thin rim right on the edge
        heat = np.where(y < 0.5, 0, heat)
        q = np.floor(heat * 6) / 6                                       # pixel-art steps
        col = GLOW(0.2 + q * 0.8)
        a = np.clip(q * 1.5, 0, 1) * 200
        img[k * FS:(k + 1) * FS, :, :3] = col
        img[k * FS:(k + 1) * FS, :, 3] = np.where(q <= 0, 0, a)
    return img


def main():
    os.makedirs(OUT + "/models/item", exist_ok=True); os.makedirs(OUT + "/textures/item", exist_ok=True)
    B = build()
    sheet = Sheet(256, 4, pad=0)                      # 4 texels per model pixel
    sheet.paint(B)
    sheet.save(OUT + "/textures/item/shadow_blade_model.png")
    # aura planes: one in the blade's plane, one across it
    xw, ay0, ay1 = 5.5, 0.5, 31.0
    A = [Box((8 - xw, ay0, 8), (8 + xw, ay1, 8), None, rot=R, faces=("north", "south"), name="aura", tex="aura"),
         Box((8, ay0, 8 - xw * 0.55), (8, ay1, 8 + xw * 0.55), None, rot=R, faces=("east", "west"), name="aura", tex="aura")]
    for a in A:
        for f in a.faces:
            a.uv[f] = [0, 0, 16, 16] if f in ("north", "east") else [16, 0, 0, 16]
    fr = aura_frames(xw, ay0, ay1)
    Image.fromarray(np.clip(fr, 0, 255).astype(np.uint8), "RGBA").save(OUT + "/textures/item/shadow_blade_aura.png")
    json.dump({"animation": {"frametime": 2, "interpolate": False}}, open(OUT + "/textures/item/shadow_blade_aura.png.mcmeta", "w"))
    model = {
        "credit": "Mansoority - shadow-gear/sword.py",
        "render_type": "minecraft:translucent",
        "texture_size": [256, 256],
        "textures": {"0": "theshadow:item/shadow_blade_model", "aura": "theshadow:item/shadow_blade_aura", "particle": "theshadow:item/shadow_blade_model"},
        "elements": [element(b, {"0": "0"}) for b in B] + [dict(element(a, {"aura": "aura"}), shade=False) for a in A],
        "gui_light": "front",
        "display": {
            "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
            "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
            "gui": {"rotation": [0, 0, 0], "translation": [-0.9, -0.9, 0], "scale": [0.66, 0.66, 0.66]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.42, 0.42, 0.42]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0.9, -0.9, 0], "scale": [0.7, 0.7, 0.7]},
            "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        },
    }
    json.dump(model, open(OUT + "/models/item/shadow_blade.json", "w"), indent=1)
    print("blade:", len(model["elements"]), "elements")


if __name__ == "__main__":
    main()
