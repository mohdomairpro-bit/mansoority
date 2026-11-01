"""
cube models the Minecraft way (Blockbench-style elements), painted procedurally into crisp pixel art.

every box carries a shader: a function of the 3D point on its surface (in model units) and the face it's on, so the
paint runs on continuously across boxes that touch (a blade built from a stack of boxes reads as one blade). the
result is quantised to a palette ramp per material and given hard pixel edges, so it looks hand-pixelled, not
blurry.
"""
import json, math
import numpy as np
from PIL import Image

FACES = ("north", "south", "east", "west", "up", "down")


def hexc(h):
    h = h.lstrip("#"); return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], np.float64)


class Ramp:
    """a pixel-art colour ramp: 0 = darkest, 1 = brightest; values snap to the nearest step"""
    def __init__(self, *cols): self.c = np.stack([hexc(c) for c in cols])
    def __call__(self, t):
        t = np.clip(t, 0, 0.9999) * len(self.c)
        return self.c[np.floor(t).astype(int)]


class Box:
    def __init__(self, frm, to, shader, rot=None, emissive=False, faces=FACES, name="", tex="0", uvrot=None):
        self.frm = np.array(frm, float); self.to = np.array(to, float)
        self.shader = shader; self.rot = rot; self.emissive = emissive
        self.faces = faces; self.name = name; self.tex = tex
        self.uv = {}

    def face_axes(self, f):
        """for a face: (origin corner, u axis vector, v axis vector, size_u, size_v) with u to the right and v down as
        seen from outside, upright"""
        x0, y0, z0 = self.frm; x1, y1, z1 = self.to
        sx, sy, sz = self.to - self.frm
        if f == "north": return np.array([x1, y1, z0]), np.array([-1, 0, 0]), np.array([0, -1, 0]), sx, sy
        if f == "south": return np.array([x0, y1, z1]), np.array([1, 0, 0]), np.array([0, -1, 0]), sx, sy
        if f == "east":  return np.array([x1, y1, z1]), np.array([0, 0, -1]), np.array([0, -1, 0]), sz, sy
        if f == "west":  return np.array([x0, y1, z0]), np.array([0, 0, 1]), np.array([0, -1, 0]), sz, sy
        if f == "up":    return np.array([x0, y1, z0]), np.array([1, 0, 0]), np.array([0, 0, 1]), sx, sz
        if f == "down":  return np.array([x0, y0, z1]), np.array([1, 0, 0]), np.array([0, 0, -1]), sx, sz


class Sheet:
    """a texture page: faces are packed onto shelves at a fixed density (texels per model unit)"""
    def __init__(self, size, density, pad=0):
        self.size = size; self.d = density; self.pad = pad
        self.img = np.zeros((size, size, 4), np.float64)
        self.x = self.y = self.row = 0

    def alloc(self, w, h):
        if self.x + w > self.size: self.x = 0; self.y += self.row + self.pad; self.row = 0
        if self.y + h > self.size: raise RuntimeError("texture page full")
        at = (self.x, self.y); self.x += w + self.pad; self.row = max(self.row, h)
        return at

    def paint(self, boxes):
        jobs = []
        for b in boxes:
            for f in b.faces:
                o, ua, va, su, sv = b.face_axes(f)
                if su <= 1e-6 or sv <= 1e-6: continue
                w = max(1, int(round(su * self.d))); h = max(1, int(round(sv * self.d)))
                jobs.append((h, w, b, f, o, ua, va, su, sv))
        jobs.sort(key=lambda j: (-j[0], -j[1]))                     # tallest first: tight shelves
        for h, w, b, f, o, ua, va, su, sv in jobs:
            x, y = self.alloc(w, h)
            us = (np.arange(w) + 0.5) / w; vs = (np.arange(h) + 0.5) / h
            U, V = np.meshgrid(us, vs)
            P = o[None, None, :] + ua[None, None, :] * (U * su)[..., None] + va[None, None, :] * (V * sv)[..., None]
            rgba = b.shader(f, P, U, V, w, h)
            if rgba.shape[-1] == 3: rgba = np.concatenate([rgba, np.full(rgba.shape[:2] + (1,), 255.0)], -1)
            self.img[y:y + h, x:x + w] = rgba
            k = 16.0 / self.size
            b.uv[f] = [x * k, y * k, (x + w) * k, (y + h) * k]
        print("  texture used to row", self.y + self.row, "of", self.size)

    def save(self, path):
        Image.fromarray(np.clip(self.img, 0, 255).astype(np.uint8), "RGBA").save(path)


def element(b, texkey):
    e = {"from": [round(v, 4) for v in b.frm], "to": [round(v, 4) for v in b.to], "faces": {}}
    if b.name: e["name"] = b.name
    if b.rot: e["rotation"] = {"angle": b.rot[0], "axis": b.rot[1], "origin": list(b.rot[2])}
    if b.emissive: e["forge_data"] = {"block_light": 15, "sky_light": 15}
    for f in b.faces:
        if f not in b.uv: continue
        e["faces"][f] = {"uv": [round(v, 5) for v in b.uv[f]], "texture": "#" + texkey.get(b.tex, b.tex)}
        if b.emissive: e["faces"][f]["forge_data"] = {"block_light": 15, "sky_light": 15}
    return e


# --------------------------------------------------------------------------------------------- shading helpers
def vnoise(P, scale, seed=0):
    """value noise in 3D (smooth), 0..1"""
    rng = np.random.default_rng(seed)
    perm = rng.random(4096)
    Q = P / scale
    i = np.floor(Q).astype(np.int64); f = Q - i
    f = f * f * (3 - 2 * f)
    def h(dx, dy, dz):
        k = (i[..., 0] + dx) * 73856093 ^ (i[..., 1] + dy) * 19349663 ^ (i[..., 2] + dz) * 83492791
        return perm[np.abs(k) % 4096]
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[..., 0] if dx else 1 - f[..., 0]) * (f[..., 1] if dy else 1 - f[..., 1]) * (f[..., 2] if dz else 1 - f[..., 2])
                out = out + w * h(dx, dy, dz)
    return out


def fbm(P, scale, oct=3, seed=0):
    s = 0; a = 1; n = 0
    for o in range(oct):
        s = s + a * vnoise(P, scale / (2 ** o), seed + o); n += a; a *= 0.5
    return s / n


def border(U, V, w, h, px=1):
    """True on the outer ring of texels of a face"""
    xi = np.floor(U * w); yi = np.floor(V * h)
    return (xi < px) | (yi < px) | (xi >= w - px) | (yi >= h - px)


def top_edge(V, h, px=1): return np.floor(V * h) < px
def bottom_edge(V, h, px=1): return np.floor(V * h) >= h - px


FACE_LIGHT = {"up": 1.0, "north": 0.86, "south": 0.86, "east": 0.78, "west": 0.78, "down": 0.6}
