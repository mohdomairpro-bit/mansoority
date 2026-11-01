"""
the small creatures rebuilt as cube models with painted pixel textures (like the gear): the pale spiders (biter and
spitter), the hollow husk and the scavenger. same parts, pivots and hierarchy as before, so their animation code
drives them unchanged - but a few hundred triangles each instead of thousands, and crisp instead of blobby.

writes SHD2 meshes (RigidMesh) + one texture per creature:
  models/<name>.mesh, textures/entity/<name>_cubes.png
space: blocks, y up, facing +z.
usage: python3 mobs.py <assets/theshadow dir>
"""
import math, os, struct, sys
import numpy as np
from PIL import Image
from boxes import Ramp, fbm, vnoise
from materials import BONE, PLATE, GLOW, LEATHER, rgba

OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
DEN = 40                                        # texels per block
SKIN = Ramp("#1e1b18", "#34302b", "#4d4842", "#686259", "#857e73", "#a39b8e", "#bfb7a8")
CHITIN = Ramp("#050407", "#0d0a12", "#17121f", "#231b2e", "#342843", "#4a3a5e")
FLESH = Ramp("#1a0406", "#33070b", "#4f0d12", "#6e1a1d", "#8f2f2c")
RED = Ramp("#4a0000", "#8a0000", "#d10a0a", "#ff3a2a", "#ff9a80", "#ffe0d6")
BILE = Ramp("#2a3300", "#4f6000", "#7d9400", "#b2cc12", "#dcf060", "#f6ffc0")
VOID = Ramp("#000000", "#050307", "#0b0710")


def nrm(v): v = np.asarray(v, float); return v / (np.linalg.norm(v) + 1e-12)


# --------------------------------------------------------------------------------------------- materials
def mat(ramp, base=0.55, noise=0.18, scale=0.12, cracks=0.0, seams=True, seed=0, tip=None):
    """a pixel material: mottled ramp colour, optional hairline cracks, dark seams round each face, a lit top edge.
    tip=(axis_vector, start, rate): darkens toward the end of a limb"""
    def sh(face, P, U, V, w, h):
        n = fbm(P / scale, 1.0, 3, seed)
        t = base + (n - 0.5) * noise * 2
        if cracks:
            t = np.where(np.abs(fbm(P / (scale * 0.7), 1.0, 2, seed + 9) - 0.5) < cracks, t - 0.25, t)
        if tip is not None:
            ax, st, rate = tip
            d = P @ np.asarray(ax, float) - st
            t = t - np.clip(d * rate, 0, 0.5)
        if face == "up": t = t + 0.07
        if face == "down": t = t - 0.12
        if seams:
            xi = np.floor(U * w); yi = np.floor(V * h)
            edge = (xi < 1) | (yi < 1) | (xi >= w - 1) | (yi >= h - 1)
            t = np.where(edge, t - 0.13, t)
        return rgba(ramp, t)
    return sh


def glowmat(ramp, base=0.7):
    def sh(face, P, U, V, w, h):
        d = np.maximum(np.abs(U - 0.5), np.abs(V - 0.5)) * 2
        return rgba(ramp, base + 0.3 * (1 - d) - 0.2 * d)
    return sh


# --------------------------------------------------------------------------------------------- geometry
class Box:
    """a box: centre, three axes (orthonormal), half sizes; shader; glow flag"""
    def __init__(self, part, c, axes, half, shader, glow=False, decal=None):
        self.part = part; self.c = np.asarray(c, float); self.ax = [nrm(a) for a in axes]
        self.h = np.asarray(half, float); self.sh = shader; self.glow = glow; self.decal = decal


def box(part, c, size, shader, glow=False, decal=None):
    return Box(part, c, [(1, 0, 0), (0, 1, 0), (0, 0, 1)], np.asarray(size, float) / 2, shader, glow, decal)


def beam(part, a, b, w, h, shader, up=(0, 1, 0), glow=False, over=0.0):
    """a box running from a to b (w across, h 'up'), overlapping its ends by 'over' so joints never gap"""
    a = np.asarray(a, float); b = np.asarray(b, float)
    d = nrm(b - a)
    upv = np.asarray(up, float)
    if abs(np.dot(d, nrm(upv))) > 0.95: upv = np.array([0, 0, 1.0]) if abs(d[2]) < 0.9 else np.array([1.0, 0, 0])
    side = nrm(np.cross(d, upv)); u2 = nrm(np.cross(side, d))
    L = np.linalg.norm(b - a) + over * 2
    return Box(part, (a + b) / 2, [side, u2, d], [w / 2, h / 2, L / 2], shader, glow)


def faces(bx):
    """six faces: (name, corner, u vector, v vector) - u right, v down as seen from outside"""
    X, Y, Z = [a * s for a, s in zip(bx.ax, bx.h)]
    c = bx.c
    out = []
    for n, vec, uu, vv in (((0, 0, 1), Z, X, -Y), ((0, 0, -1), -Z, -X, -Y), ((1, 0, 0), X, -Z, -Y), ((-1, 0, 0), -X, Z, -Y),
                           ((0, 1, 0), Y, X, Z), ((0, -1, 0), -Y, X, -Z)):
        corner = c + vec - uu - vv
        out.append((vec / (np.linalg.norm(vec) + 1e-12), corner, 2 * uu, 2 * vv))
    return out


class Pack:
    def __init__(self, size):
        self.size = size; self.img = np.zeros((size, size, 4)); self.x = self.y = self.row = 0
    def alloc(self, w, h):
        if self.x + w + 1 > self.size: self.x = 0; self.y += self.row + 1; self.row = 0
        if self.y + h + 1 > self.size: raise RuntimeError("texture full")
        at = (self.x, self.y); self.x += w + 1; self.row = max(self.row, h); return at


def build(name, boxes, parts, size=256):
    """parts: ordered list of (name, parent or None, pivot). boxes: Box list"""
    jobs = []
    for b in boxes:
        for n, corner, uu, vv in faces(b):
            lu, lv = np.linalg.norm(uu), np.linalg.norm(vv)
            w = max(1, int(round(lu * DEN))); h = max(1, int(round(lv * DEN)))
            jobs.append((h, w, b, n, corner, uu, vv))
    jobs.sort(key=lambda j: (-j[0], -j[1]))
    pk = Pack(size)
    tris = {p[0]: [] for p in parts}
    for h, w, b, n, corner, uu, vv in jobs:
        x, y = pk.alloc(w, h)
        U, V = np.meshgrid((np.arange(w) + 0.5) / w, (np.arange(h) + 0.5) / h)
        P = corner[None, None] + uu[None, None] * U[..., None] + vv[None, None] * V[..., None]
        fname = "up" if n[1] > 0.7 else "down" if n[1] < -0.7 else "side"
        col = b.sh(fname, P, U, V, w, h)
        if b.decal: col = b.decal(fname, n, P, U, V, w, h, col)
        pad = np.pad(col, ((1, 1), (1, 1), (0, 0)), mode="edge")       # bleed: no seams at a distance
        y0, x0 = max(0, y - 1), max(0, x - 1)
        blk = pad[y0 - (y - 1):, x0 - (x - 1):]
        hh = min(blk.shape[0], size - y0); ww = min(blk.shape[1], size - x0)
        region = pk.img[y0:y0 + hh, x0:x0 + ww]
        empty = region[..., 3] == 0
        region[empty] = blk[:hh, :ww][empty]
        pk.img[y:y + h, x:x + w] = col
        # inset the uvs half a texel so filtering never reads the neighbour
        u0, v0 = (x + 0.02) / size, (y + 0.02) / size; u1, v1 = (x + w - 0.02) / size, (y + h - 0.02) / size
        C = [corner, corner + uu, corner + uu + vv, corner + vv]
        UV = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
        for i in (0, 1, 2, 0, 2, 3):
            tris[b.part].append((C[i], n, UV[i], 1 if b.glow else 0))
    os.makedirs(os.path.join(OUT, "models"), exist_ok=True); os.makedirs(os.path.join(OUT, "textures/entity"), exist_ok=True)
    Image.fromarray(np.clip(pk.img, 0, 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, "textures/entity/%s_cubes.png" % name))
    order = [p[0] for p in parts]
    with open(os.path.join(OUT, "models/%s.mesh" % name), "wb") as f:
        f.write(b"SHD2"); f.write(struct.pack("<i", len(parts)))
        total = 0
        for pn, par, piv in parts:
            nb = pn.encode(); f.write(struct.pack("<h", len(nb))); f.write(nb)
            f.write(struct.pack("<i", order.index(par) if par else -1)); f.write(struct.pack("<3f", *[float(v) for v in piv]))
            t = tris[pn]; f.write(struct.pack("<i", len(t) // 3)); total += len(t) // 3
            for p, n, uv, g in t:
                f.write(struct.pack("<3f", *[float(v) for v in p]))
                f.write(struct.pack("<3b", *[max(-127, min(127, int(round(v * 127)))) for v in n]))
                f.write(struct.pack("<2f", *uv)); f.write(struct.pack("<B", g))
    print("%-14s %4d tris  %2d parts  texture rows %d/%d" % (name, total, len(parts), pk.y + pk.row, size))


# --------------------------------------------------------------------------------------------- the pale spiders
def spider(name, spitter):
    S = 0.9 if spitter else 1.0
    B, P = [], []
    bone = mat(BONE, 0.62, 0.1, 0.1, 0.012, seed=1)
    bone_dark = mat(BONE, 0.42, 0.1, 0.1, seed=2)
    chit = mat(CHITIN, 0.45, 0.12, 0.08, seed=3)
    v = lambda *a: np.array(a, float) * S
    body_c = v(0, 0.52, 0.02)
    P.append(("body", None, body_c))
    B.append(box("body", body_c, v(0.46, 0.27, 0.5), bone))
    B.append(box("body", body_c + v(0, 0.14, 0), v(0.34, 0.06, 0.4), bone_dark))                  # back plate
    for i in range(5):                                                                            # spines down the back
        z = 0.16 - i * 0.08
        B.append(box("body", body_c + v(0, 0.2 + 0.02 * (2 - abs(i - 2)), z), v(0.04, 0.09 + 0.02 * (2 - abs(i - 2)), 0.04), chit))
    # head: low and forward, four red eyes
    hp = body_c + v(0, 0, 0.22)
    P.append(("head", "body", hp))
    hc = body_c + v(0, -0.02, 0.33)
    B.append(box("head", hc, v(0.32, 0.22, 0.24), bone))
    B.append(box("head", hc + v(0, -0.07, 0.11), v(0.24, 0.07, 0.05), mat(VOID, 0.5, 0.05, seams=False)))   # mouth
    for ex, ey, r in ((-0.065, 0.045, 0.06), (0.065, 0.045, 0.06), (-0.115, 0.0, 0.035), (0.115, 0.0, 0.035)):
        B.append(box("head", hc + v(ex, ey, 0.115), v(r * 1.5, r * 1.3, 0.03), mat(VOID, 0.3, 0.05, seams=False)))
        B.append(box("head", hc + v(ex, ey, 0.125), v(r, r * 0.85, 0.03), glowmat(RED, 0.62), glow=True))
    for side in (-1, 1):                                                                          # a heavy brow, slanting down to the middle: angry
        B.append(beam("head", hc + v(0.15 * side, 0.1, 0.115), hc + v(0.015 * side, 0.065, 0.13), 0.05 * S, 0.04 * S, bone_dark, over=0.01))
    for side, nm in ((-1, "fangL"), (1, "fangR")):                                                 # hooked fangs, black tips
        root = hc + v(0.05 * side, -0.07, 0.11)
        P.append((nm, "head", root))
        k1 = root + v(0.04 * side, -0.12, 0.07); k2 = k1 + v(-0.03 * side, -0.07, -0.02)
        B.append(beam(nm, root, k1, 0.05 * S, 0.05 * S, mat(BONE, 0.7, 0.08, seed=5), over=0.01))
        B.append(beam(nm, k1, k2, 0.035 * S, 0.035 * S, chit, over=0.01))
    # tentacles: four, from under the mouth, pale fading to purple, glowing tips
    for k in range(4):
        sx = (-1.5 + k) * 0.05
        p = hc + v(sx, -0.09, 0.06)
        d = nrm((sx * 2.0, -0.95, 0.35))
        curl = nrm((-sx * 3, 0.25, 0.6))
        prev = "head"
        for sgi in range(3):
            nm = "tent%d_%d" % (k, sgi)
            P.append((nm, prev, p.copy()))
            L = (0.17 - sgi * 0.025) * S
            d = nrm(d + curl * 0.22)
            q = p + d * L
            w = (0.05 - sgi * 0.012) * S
            shd = mat(SKIN, 0.62 - sgi * 0.12, 0.1, 0.06, seed=10 + sgi) if sgi < 2 else mat(Ramp("#1d0730", "#3a0f5c", "#5e1f8f", "#8a3cc7"), 0.55, 0.1, 0.06)
            B.append(beam(nm, p, q, w, w, shd, over=0.01))
            if sgi == 2: B.append(box(nm, q, v(0.035, 0.035, 0.035), glowmat(GLOW, 0.75), glow=True))
            prev = nm; p = q
    # abdomen: big and banded, spikes along the top, a black screaming face on its back
    ap = body_c + v(0, 0.02, -0.24)
    P.append(("abdomen", "body", ap))
    sw = 1.18 if spitter else 1.0
    ac = body_c + v(0, 0.06, -0.6)
    asz = v(0.56, 0.46, 0.72) * sw

    def face_mark(fname, n, Pt, U, V, w, h, col):
        if fname != "up": return col
        x = (Pt[..., 0] - ac[0]) / (asz[0] / 2); z = (Pt[..., 2] - ac[2]) / (asz[2] / 2)
        m = ((x + 0.35) ** 2 / 0.05 + (z - 0.25) ** 2 / 0.04 < 1) | ((x - 0.35) ** 2 / 0.05 + (z - 0.25) ** 2 / 0.04 < 1) | (x ** 2 / 0.07 + (z + 0.25) ** 2 / 0.12 < 1)
        col = col.copy(); col[m] = rgba(VOID, np.full(m.sum(), 0.4)); return col
    B.append(box("abdomen", ac, asz, mat(BONE, 0.58, 0.12, 0.12, 0.01, seed=6), decal=face_mark))
    for zz in (-0.36, -0.56, -0.76):                                                              # dark bands
        B.append(box("abdomen", body_c + v(0, 0.06, zz), v(0.58, 0.48, 0.05) * np.array([sw, sw, 1]), bone_dark))
    B.append(box("abdomen", ac + v(0, -0.04, -0.37 * sw), v(0.14, 0.12, 0.05), mat(VOID, 0.4, 0.05, seams=False)))  # spinneret
    for i in range(5):
        z = -0.32 - i * 0.14
        B.append(box("abdomen", body_c + v(0, 0.06 + asz[1] / S / 2 + 0.04, z), v(0.05, 0.1 - abs(i - 2) * 0.015, 0.05), chit))
    if spitter:
        for (ax, ay, az, r) in ((-0.2, 0.0, 0.0, 0.16), (0.2, 0.04, 0.05, 0.15), (0.0, 0.22, -0.12, 0.17), (-0.13, 0.2, 0.2, 0.11), (0.15, -0.05, -0.25, 0.12)):
            B.append(box("abdomen", ac + v(ax, ay, az), v(r, r, r), glowmat(BILE, 0.62), glow=True))
    # eight legs, three segments: knees high, claws on the ground
    for side in (-1, 1):
        for k in range(4):
            a = (1.5 - k) * 0.42
            out = np.array([side * math.cos(a), 0, math.sin(a)])
            root = body_c + v(side * 0.19, -0.04, 0.13 - k * 0.09)
            kn1 = root + (out * 0.36 + np.array([0, 0.36, 0])) * S
            kn2 = kn1 + (out * 0.38 + np.array([0, -0.38, 0])) * S
            tip = np.array([kn2[0] + out[0] * 0.16 * S, 0.0, kn2[2] + out[2] * 0.16 * S])
            names = ["leg%d%s_%d" % (k, "L" if side < 0 else "R", s) for s in range(3)]
            P.append((names[0], "body", root)); P.append((names[1], names[0], kn1)); P.append((names[2], names[1], kn2))
            B.append(beam(names[0], root, kn1, 0.075 * S, 0.075 * S, bone, over=0.02))
            B.append(box(names[0], kn1, v(0.09, 0.09, 0.09), bone_dark))                            # knuckle
            B.append(beam(names[1], kn1, kn2, 0.06 * S, 0.06 * S, mat(BONE, 0.6, 0.1, 0.1, seed=7), over=0.02))
            B.append(box(names[1], kn2, v(0.07, 0.07, 0.07), bone_dark))
            B.append(beam(names[2], kn2, tip, 0.045 * S, 0.045 * S, chit, over=0.01))
            mid = (root + kn1) / 2                                                                    # a spine on the upper leg
            B.append(beam(names[0], mid, mid + v(0, 0.08, 0) + out * 0.02, 0.025 * S, 0.025 * S, chit))
    build(name, B, P)


# --------------------------------------------------------------------------------------------- the hollow husk
def husk():
    B, P = [], []
    m = lambda x, y, z: np.array([x, z, -y], float)               # its old Blender coordinates -> ours
    skin = mat(SKIN, 0.55, 0.14, 0.1, 0.012, seed=21)
    skin2 = mat(SKIN, 0.45, 0.14, 0.1, 0.012, seed=22)
    void = mat(VOID, 0.4, 0.05, seams=False)
    pelvis = m(0, 0.42, 0.22); waist = m(0, 0.15, 0.42); chest = m(0, -0.12, 0.58); neck = m(0, -0.30, 0.66)
    P += [("pelvis", None, pelvis), ("chest", "pelvis", waist), ("head", "chest", neck)]
    B.append(beam("pelvis", m(0, 0.56, 0.19), waist, 0.25, 0.2, skin2, over=0.03))
    B.append(beam("chest", waist, chest, 0.3, 0.26, skin, over=0.03))
    B.append(beam("chest", chest, neck, 0.26, 0.2, skin, over=0.03))
    up = np.array([0, 1.0, 0])
    for i in range(5):                                                              # ribs pressing through the skin
        q = waist + (chest - waist) * (0.15 + i * 0.18)
        B.append(box("chest", q + up * 0.01, (0.33, 0.24, 0.035), mat(BONE, 0.55, 0.08, seed=30 + i)))
    B.append(beam("chest", waist + up * 0.12, neck + up * 0.08, 0.06, 0.04, void))                     # split open down the spine
    for i in range(5):
        q = waist + (neck - waist) * (0.1 + i * 0.18) + up * 0.135
        B.append(box("chest", q, (0.035, 0.03, 0.05), glowmat(BILE, 0.6), glow=True))
    hc = neck + m(0, -0.14, -0.02)
    B.append(box("head", hc, (0.17, 0.19, 0.24), skin))
    for sx in (-1, 1):
        B.append(box("head", hc + m(0.042 * sx, -0.115, 0.03), (0.06, 0.05, 0.02), void))
        B.append(box("head", hc + m(0.042 * sx, -0.125, 0.03), (0.016, 0.016, 0.01), glowmat(RED, 0.5), glow=True))
    for i in range(6):                                                              # upper teeth
        x = -0.05 + i * 0.02
        B.append(box("head", hc + m(x, -0.11, -0.08), (0.012, 0.035, 0.012), mat(BONE, 0.75, 0.05, seams=False)))
    jp = hc + m(0, -0.03, -0.06)
    P.append(("jaw", "head", jp))
    B.append(box("jaw", jp + m(0, -0.08, -0.03), (0.14, 0.05, 0.17), skin2))
    B.append(box("jaw", jp + m(0, -0.08, -0.005), (0.11, 0.012, 0.14), mat(FLESH, 0.5, 0.1, seams=False)))
    for i in range(6):
        x = -0.05 + i * 0.02
        B.append(box("jaw", jp + m(x, -0.15, 0.01), (0.012, 0.03, 0.012), mat(BONE, 0.75, 0.05, seams=False)))
    for side, s in ((-1, "L"), (1, "R")):
        sh = chest + m(0.17 * side, 0.0, 0.0); el = m(0.40 * side, -0.32, 0.36); wr = m(0.32 * side, -0.70, 0.06)
        P += [("arm" + s, "chest", sh), ("fore" + s, "arm" + s, el)]
        B.append(beam("arm" + s, sh, el, 0.1, 0.1, skin, over=0.03))
        B.append(box("arm" + s, el, (0.11, 0.11, 0.11), skin2))
        B.append(beam("fore" + s, el, wr, 0.08, 0.08, skin, over=0.02))
        for f in range(4):
            a = (f - 1.5) * 0.35
            d = nrm(m(math.sin(a) * side * 0.6, -math.cos(a), -0.4))
            k1 = wr + d * 0.1; k2 = k1 + nrm(d + np.array([0, -0.9, 0])) * 0.07
            B.append(beam("fore" + s, wr, k1, 0.03, 0.03, skin2, over=0.01))
            B.append(beam("fore" + s, k1, k2, 0.022, 0.022, mat(CHITIN, 0.4, 0.1), over=0.008))
    for side, s in ((-1, "L"), (1, "R")):
        hp = pelvis + m(0.09 * side, 0.03, -0.02); kn = m(0.17 * side, 0.85, 0.07); ft = m(0.13 * side, 1.25, 0.05)
        P += [("thigh" + s, "pelvis", hp), ("shin" + s, "thigh" + s, kn)]
        B.append(beam("thigh" + s, hp, kn, 0.13, 0.12, skin2, over=0.03))
        B.append(beam("shin" + s, kn, ft, 0.09, 0.08, skin2, over=0.03))
        B.append(beam("shin" + s, ft, ft + m(0, 0.12, -0.02), 0.1, 0.05, skin, over=0.01))
    build("hollow_husk", B, P)


# --------------------------------------------------------------------------------------------- the scavenger
def scavenger():
    B, P = [], []
    m = lambda x, y, z: np.array([x, z, -y], float)
    ash = mat(Ramp("#0d0c10", "#1a1820", "#2a2733", "#3c3848", "#55506a"), 0.5, 0.14, 0.08, 0.012, seed=40)
    bone = mat(BONE, 0.64, 0.1, 0.08, 0.012, seed=41)
    bc = m(0, 0.05, 1.05)
    P.append(("body", None, bc))
    B.append(box("body", bc, (0.3, 0.26, 0.58), ash))
    B.append(box("body", bc + np.array([0, 0.14, 0]), (0.2, 0.05, 0.44), mat(BONE, 0.5, 0.1, seed=42)))
    for i in range(6):                                                              # the cradle of bone fingers on its back
        a = i / 6 * math.tau
        base = bc + m(math.cos(a) * 0.09, math.sin(a) * 0.14, 0.1)
        o = nrm(m(math.cos(a) * 0.4, math.sin(a) * 0.4, 1))
        k1 = base + o * 0.13; k2 = k1 + nrm(o + m(-math.cos(a), -math.sin(a), 0) * 0.9) * 0.1
        B.append(beam("body", base, k1, 0.032, 0.032, bone, over=0.01))
        B.append(beam("body", k1, k2, 0.024, 0.024, bone, over=0.01))
    np_ = bc + m(0, -0.26, 0.02)
    P.append(("neck", "body", np_))
    hd = np_ + m(0, -0.42, 0.22)
    mid = np_ + m(0, -0.2, 0.18)
    B.append(beam("neck", np_, mid, 0.08, 0.08, ash, over=0.02))
    B.append(beam("neck", mid, hd, 0.06, 0.06, ash, over=0.02))
    P.append(("head", "neck", hd))
    B.append(box("head", hd + m(0, -0.09, 0), (0.12, 0.1, 0.3), bone))
    for sx in (-1, 1):
        B.append(box("head", hd + m(0.045 * sx, -0.1, 0.03), (0.02, 0.02, 0.06), glowmat(GLOW, 0.7), glow=True))
    for i in range(6):
        for sx in (-1, 1):
            B.append(box("head", hd + m(0.035 * sx, -0.05 - i * 0.03, -0.065), (0.012, 0.035, 0.012), mat(BONE, 0.78, 0.04, seams=False)))
    for side in (-1, 1):
        for k in range(3):
            a = (1 - k) * 0.6
            out = m(side * math.cos(a), -math.sin(a), 0)
            root = bc + m(side * 0.1, -0.1 + k * 0.12, -0.05)
            kn = root + out * 0.55 + np.array([0, 0.55, 0])
            tip = np.array([root[0] + out[0] * 1.05, 0.0, root[2] + out[2] * 1.05])
            n1, n2 = "leg%d%s_0" % (k, "L" if side < 0 else "R"), "leg%d%s_1" % (k, "L" if side < 0 else "R")
            P += [(n1, "body", root), (n2, n1, kn)]
            B.append(beam(n1, root, kn, 0.05, 0.05, ash, over=0.02))
            B.append(box(n1, kn, (0.065, 0.065, 0.065), bone))
            B.append(beam(n2, kn, tip, 0.035, 0.035, mat(CHITIN, 0.45, 0.1, tip=((0, -1, 0), -0.4, 2.0)), over=0.01))
    build("scavenger", B, P)


if __name__ == "__main__":
    spider("pale_biter", False)
    spider("pale_spitter", True)
    husk()
    scavenger()
