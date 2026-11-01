"""the shared materials: obsidian-violet blade steel, pale bone, black plate, leather, the violet glow"""
import numpy as np
from boxes import Ramp, fbm, vnoise, border, top_edge, bottom_edge

BLADE = Ramp("#07050c", "#110b1c", "#1b1230", "#281b45", "#3a2862", "#553f8c", "#8270bf", "#bdb0ea", "#f1ecff")
BONE = Ramp("#231d19", "#3f3630", "#635649", "#8d7f6c", "#b6a991", "#d6cbb5", "#ece5d6", "#fbf8f1")
PLATE = Ramp("#050508", "#0c0b12", "#15131e", "#1f1c2b", "#2c2840", "#3f3958", "#5b5378", "#857ca3")
LEATHER = Ramp("#0f0a08", "#1c130e", "#2b1d15", "#3c291e", "#523829", "#6b4a36")
GLOW = Ramp("#24044a", "#3f0a85", "#6418c8", "#8a3cf5", "#ad72ff", "#cfa8ff", "#ecdcff", "#ffffff")


def rgba(ramp, t, a=255.0):
    c = ramp(t)
    return np.concatenate([c, np.full(c.shape[:-1] + (1,), a)], -1)


def bone(seed=1, scale=1.0, dark_tip=None, base=0.66, seams="all"):
    """pale bone: clean, softly mottled, a few hairline cracks, darker in the seams (outer ring), lit along the top"""
    def sh(f, P, U, V, w, h):
        n = fbm(P, 3.5 * scale, 2, seed)
        t = base + (n - 0.5) * 0.16
        cr = np.abs(fbm(P, 2.2 * scale, 2, seed + 7) - 0.5) < 0.012          # rare hairline cracks
        t = np.where(cr, t - 0.2, t)
        if dark_tip is not None:
            t = t - np.clip((P[..., dark_tip[0]] - dark_tip[1]) * dark_tip[2], 0, 0.4)
        if f == "up": t = t + 0.08
        if f == "down": t = t - 0.12
        if seams == "all":
            t = np.where(top_edge(V, h) & (f not in ("up", "down")), t + 0.12, t)
            t = np.where(border(U, V, w, h) & ~top_edge(V, h), t - 0.14, t)
        elif seams == "sides":                                              # runs on into its neighbours above and below
            xi = np.floor(U * w)
            if f in ("north", "south"): t = np.where((xi < 1) | (xi >= w - 1), t - 0.14, t)
        return rgba(BONE, t)
    return sh


def plate(seed=2, base=0.38, rivets=False, trim=None):
    """black-violet plate: faint brushed streaks, a bevel (light top-left, dark bottom-right), optional rivets"""
    def sh(f, P, U, V, w, h):
        n = fbm(P * [1, 0.4, 1], 2.0, 2, seed)
        t = base + (n - 0.5) * 0.18 + 0.06 * np.sin(P[..., 1] * 4.0 + n * 3)
        xi = np.floor(U * w); yi = np.floor(V * h)
        t = np.where((xi < 1) | (yi < 1), t + 0.22, t)                       # bevel highlight
        t = np.where((xi >= w - 1) | (yi >= h - 1), t - 0.2, t)
        if rivets and w >= 6 and h >= 6:
            for cx, cy in ((2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3)):
                m = (np.abs(xi - cx) < 1) & (np.abs(yi - cy) < 1)
                t = np.where(m, 0.85, t)
        return rgba(PLATE, t)
    return sh


def leather(seed=4):
    def sh(f, P, U, V, w, h):
        ang = np.arctan2(P[..., 2] - 8, P[..., 0] - 8)
        s = P[..., 1] * 1.3 + ang / np.pi
        fr = s - np.floor(s)
        t = 0.45 + 0.35 * np.sin(fr * np.pi) - np.where(fr < 0.16, 0.35, 0) + (fbm(P, 1.0, 2, seed) - 0.5) * 0.15
        return rgba(LEATHER, t)
    return sh


def glow(base=0.75, core_axis=None, center=8.0, width=1.0, flicker=0.15, seed=5):
    """the violet light: brightest down its middle (core_axis: which axis runs across it)"""
    def sh(f, P, U, V, w, h):
        t = np.full(U.shape, base)
        if core_axis is not None:
            d = np.abs(P[..., core_axis] - center) / width
            t = base + 0.3 * (1 - np.clip(d, 0, 1)) - 0.25 * np.clip(d - 0.5, 0, 1)
        t = t + (vnoise(P, 0.9, seed) - 0.5) * flicker
        return rgba(GLOW, t)
    return sh


def solid(ramp, t0):
    def sh(f, P, U, V, w, h): return rgba(ramp, np.full(U.shape, t0))
    return sh
