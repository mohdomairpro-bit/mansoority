"""
SHD2 (rigid parts) -> SHD3 (skinned): every vertex gets up to two bones with smooth weights near the joints,
so elbows, knees, the waist and the neck bend like skin instead of separate pieces sliding through each other.
usage: python3 skin.py in.mesh out.mesh
"""
import sys
import numpy as np
from meshio import read_shd2, write_shd3

# the skeleton (build_shadow.py, Blender metres, z up, faces -y) -> converted to MC axes below
J = {"pelvis": (0, 0.04, 1.25), "spine1": (0, 0.06, 1.45), "spine2": (0, 0.00, 1.72), "spine3": (0, -0.10, 1.98),
     "neck0": (0, -0.24, 2.00), "neck1": (0, -0.38, 1.94), "head0": (0, -0.46, 1.84)}
for s, sx in (("L", -1), ("R", 1)):
    J["shoulder" + s] = (0.25 * sx, -0.08, 2.04); J["elbow" + s] = (0.40 * sx, -0.02, 2.62)
    J["joint2" + s] = (0.56 * sx, -0.34, 1.60); J["wrist" + s] = (0.66 * sx, -0.62, 0.36); J["hand" + s] = (0.67 * sx, -0.68, 0.15)
    J["hip" + s] = (0.13 * sx, 0.05, 1.18); J["knee" + s] = (0.22 * sx, -0.20, 0.72)
    J["ankle" + s] = (0.26 * sx, 0.06, 0.17); J["toe" + s] = (0.27 * sx, -0.10, 0.05)
J = {k: np.array([v[0], v[2], -v[1]], np.float32) for k, v in J.items()}

SEGS = {"hips": [("pelvis", "spine1"), ("pelvis", "hipL"), ("pelvis", "hipR")],
        "chest": [("spine1", "spine2"), ("spine2", "spine3"), ("spine3", "shoulderL"), ("spine3", "shoulderR")],
        "neck": [("spine3", "neck0"), ("neck0", "neck1"), ("neck1", "head0")]}
for s in "LR":
    SEGS["arm" + s] = [("shoulder" + s, "elbow" + s)]
    SEGS["fore" + s] = [("elbow" + s, "joint2" + s)]
    SEGS["hand" + s] = [("joint2" + s, "wrist" + s), ("wrist" + s, "hand" + s)]
    SEGS["leg" + s] = [("hip" + s, "knee" + s)]
    SEGS["shin" + s] = [("knee" + s, "ankle" + s), ("ankle" + s, "toe" + s)]
RIGID = {"head", "jaw", "jawR", "eyeL", "eyeR"}          # the skull, the two halves of the face and the eyes stay hard
BELLY_Y = (1.46, 1.71)                                   # (MC y) the stretch of stomach that splits open


def seg_dist(p, a, b):
    ab = b - a
    t = np.clip(((p - a) @ ab) / max(ab @ ab, 1e-9), 0, 1)
    return np.linalg.norm(a + t[:, None] * ab - p, axis=1)


def part_dist(p, part):
    return np.min(np.stack([seg_dist(p, J[a], J[b]) for a, b in SEGS[part]]), axis=0)


def main(src, dst, power=4.0):
    parts = read_shd2(src)
    names = [p["name"] for p in parts]
    parent = {p["name"]: (names[p["parent"]] if p["parent"] >= 0 else None) for p in parts}
    children = {n: [c for c in names if parent[c] == n] for n in names}
    # 1) one shared vertex buffer: identical corners (same spot, normal, uv) become one vertex, also across parts
    allp, alln, alluv, home, glow_tri = [], [], [], [], []
    for i, p in enumerate(parts):
        allp.append(p["pos"]); alln.append(p["nrm"]); alluv.append(p["uv"]); home.append(np.full(len(p["pos"]), i))
        glow_tri.append(p["glow"])
    P = np.concatenate(allp); N = np.concatenate(alln); UV = np.concatenate(alluv); H = np.concatenate(home)
    G = np.concatenate(glow_tri)
    # the stomach is split down the middle: corners on the seam must stay on their own side
    cen = np.repeat(P.reshape(-1, 3, 3).mean(axis=1), 3, axis=0)
    seam = (np.abs(P[:, 0]) < 0.003) & (P[:, 1] > BELLY_Y[0]) & (P[:, 1] < BELLY_Y[1]) & (P[:, 2] > 0)
    side = np.where(seam, np.sign(cen[:, 0]), 0)
    key = np.concatenate([np.round(P * 2000), np.round(N * 20), np.round(UV * 8192), side[:, None]], axis=1).astype(np.int64)
    uniq, first, inv = np.unique(key, axis=0, return_index=True, return_inverse=True)
    inv = inv.reshape(-1)
    vp, vn, vuv, vh, vside = P[first], N[first], UV[first], H[first], side[first]
    print("corners", len(P), "-> vertices", len(vp))
    # 2) weights: own bone + its parent/children, inverse-distance^power, best two
    nv = len(vp)
    b0 = vh.copy(); b1 = vh.copy(); w0 = np.ones(nv, np.float32)
    dist = {n: (part_dist(vp, n) if n in SEGS else None) for n in names}
    for hi, hn in enumerate(names):
        sel = np.where(vh == hi)[0]
        if len(sel) == 0 or hn in RIGID or hn not in SEGS: continue
        cands = [hn] + ([parent[hn]] if parent[hn] and parent[hn] in SEGS else []) + [c for c in children[hn] if c in SEGS]
        if hn == "neck": cands.append("head")
        D = []
        for c in cands:
            if c == "head":      # the head bone: distance to the top of the neck
                D.append(np.linalg.norm(vp[sel] - J["head0"], axis=1) + 0.03)
            else:
                D.append(dist[c][sel])
        D = np.maximum(np.stack(D, axis=1), 1e-4)
        W = (1.0 / D) ** power
        order = np.argsort(-W, axis=1)
        top, sec = order[:, 0], order[:, 1] if W.shape[1] > 1 else order[:, 0]
        wt = W[np.arange(len(sel)), top]; ws = W[np.arange(len(sel)), sec] if W.shape[1] > 1 else np.zeros(len(sel))
        ci = np.array([names.index(c) for c in cands])
        b0[sel] = ci[top]; b1[sel] = ci[sec]
        w0[sel] = wt / (wt + ws)
    # 2b) the tongue: one bone per segment along its length, blended at each joint
    if "tongue0" in names:
        T = [np.asarray(parts[names.index("tongue%d" % i)]["pivot"]) for i in range(6)]
        seg = np.linalg.norm(T[1] - T[0]); axis = (T[5] - T[0]) / np.linalg.norm(T[5] - T[0])
        sel = np.where(vh == names.index("tongue0"))[0]
        s_ = ((vp[sel] - T[0]) @ axis) / seg - 0.5
        i0 = np.clip(np.floor(s_), 0, 5).astype(int); fr = np.clip(s_ - np.floor(s_), 0, 1)
        i1 = np.clip(i0 + 1, 0, 5)
        fr = np.where(s_ < 0, 0.0, np.where(s_ >= 5, 0.0, fr))
        ti = np.array([names.index("tongue%d" % i) for i in range(6)])
        b0[sel] = ti[i0]; b1[sel] = ti[i1]; w0[sel] = 1 - fr * fr * (3 - 2 * fr)
    # 2c) the stomach: skin over the belly follows two "door" bones, fading out toward the edges (it stretches, then tears at the seam)
    if "bellyL" in names:
        sm = lambda a, b, x: np.clip((x - a) / (b - a), 0, 1) ** 2 * (3 - 2 * np.clip((x - a) / (b - a), 0, 1))
        mid = (BELLY_Y[0] + BELLY_Y[1]) / 2; half = (BELLY_Y[1] - BELLY_Y[0]) / 2
        body = np.isin(vh, [names.index(n) for n in ("hips", "chest") if n in names])
        wy = 1 - sm(half * 0.55, half * 1.05, np.abs(vp[:, 1] - mid))
        wx = 1 - sm(0.07, 0.13, np.abs(vp[:, 0]))
        wf = sm(0.1, 0.45, vn[:, 2]) * (vp[:, 2] > -0.02)
        W = wy * wx * wf * body
        sel = np.where(W > 0.01)[0]
        sidev = np.where(vside[sel] != 0, vside[sel], np.sign(vp[sel, 0]))
        bb = np.where(sidev < 0, names.index("bellyL"), names.index("bellyR"))
        keep = np.where(w0[sel] >= 0.5, b0[sel], b1[sel])
        b1[sel] = keep; b0[sel] = bb; w0[sel] = W[sel]
        print("belly vertices:", len(sel))
    # 3) triangles
    tri = inv.reshape(-1, 3)
    solid, glow = tri[~G], tri[G]
    bones = [(p["name"], p["parent"], p["pivot"]) for p in parts]
    write_shd3(dst, bones, vp, vn, vuv, b0, b1, w0, solid, glow)
    blended = np.sum(w0 < 0.98)
    print("bones", len(bones), "tris", len(solid), "+", len(glow), "glow;", blended, "vertices blend two bones")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
