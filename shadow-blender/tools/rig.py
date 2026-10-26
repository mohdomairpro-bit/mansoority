"""
Mansoority's skeleton without Blender: forward kinematics, two-bone IK and quaternion helpers.
Same maths as the mod (ShadowSkeleton.java), so what the clips look like here is what they look like in game.
Model space = Minecraft axes, metres: y up, the creature faces +z, its left side is -x.
Bone local transform: T(offset) * T(pivot) * R * T(-pivot); world = parent world * local.
"""
import math
import numpy as np

# ---------------------------------------------------------------- quaternions (w, x, y, z)
def qmul(a, b):
    w1, x1, y1, z1 = a; w2, x2, y2, z2 = b
    return np.array([w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2, w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
                     w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2, w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2])

def qaxis(axis, ang):
    axis = np.asarray(axis, float); n = np.linalg.norm(axis)
    if n < 1e-12 or abs(ang) < 1e-12: return np.array([1.0, 0, 0, 0])
    axis = axis / n; s = math.sin(ang / 2)
    return np.array([math.cos(ang / 2), axis[0] * s, axis[1] * s, axis[2] * s])

def qeuler(x=0.0, y=0.0, z=0.0):
    """the mod's convention: rotationZYX(z, y, x) = Rz * Ry * Rx"""
    return qmul(qmul(qaxis((0, 0, 1), z), qaxis((0, 1, 0), y)), qaxis((1, 0, 0), x))

def qmat(q):
    w, x, y, z = q / np.linalg.norm(q)
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])

def mat_q(m):
    t = np.trace(m)
    if t > 0:
        s = math.sqrt(t + 1.0) * 2
        q = [0.25 * s, (m[2, 1] - m[1, 2]) / s, (m[0, 2] - m[2, 0]) / s, (m[1, 0] - m[0, 1]) / s]
    elif m[0, 0] > m[1, 1] and m[0, 0] > m[2, 2]:
        s = math.sqrt(1.0 + m[0, 0] - m[1, 1] - m[2, 2]) * 2
        q = [(m[2, 1] - m[1, 2]) / s, 0.25 * s, (m[0, 1] + m[1, 0]) / s, (m[0, 2] + m[2, 0]) / s]
    elif m[1, 1] > m[2, 2]:
        s = math.sqrt(1.0 + m[1, 1] - m[0, 0] - m[2, 2]) * 2
        q = [(m[0, 2] - m[2, 0]) / s, (m[0, 1] + m[1, 0]) / s, 0.25 * s, (m[1, 2] + m[2, 1]) / s]
    else:
        s = math.sqrt(1.0 + m[2, 2] - m[0, 0] - m[1, 1]) * 2
        q = [(m[1, 0] - m[0, 1]) / s, (m[0, 2] + m[2, 0]) / s, (m[1, 2] + m[2, 1]) / s, 0.25 * s]
    q = np.array(q); return q / np.linalg.norm(q)

def qbetween(a, b):
    a = a / np.linalg.norm(a); b = b / np.linalg.norm(b)
    c = np.cross(a, b); d = float(np.dot(a, b))
    if d < -0.999999:
        ax = np.cross(a, (1, 0, 0))
        if np.linalg.norm(ax) < 1e-6: ax = np.cross(a, (0, 1, 0))
        return qaxis(ax, math.pi)
    q = np.array([1 + d, c[0], c[1], c[2]]); return q / np.linalg.norm(q)

def slerp(a, b, t):
    d = float(np.dot(a, b))
    if d < 0: b = -b; d = -d
    if d > 0.9995: q = a + (b - a) * t; return q / np.linalg.norm(q)
    th = math.acos(d); s = math.sin(th)
    return (math.sin((1 - t) * th) * a + math.sin(t * th) * b) / s

# ---------------------------------------------------------------- the skeleton
ORDER = ["hips", "chest", "neck", "head", "jaw", "jawR", "armL", "foreL", "handL",
         "armR", "foreR", "handR", "legL", "shinL", "legR", "shinR",
         "eyeL", "eyeR", "tongue0", "tongue1", "tongue2", "tongue3", "tongue4", "tongue5", "bellyL", "bellyR"]
PARENT = {"hips": None, "chest": "hips", "neck": "chest", "head": "neck", "jaw": "head", "jawR": "head"}
PARENT.update({"eyeL": "head", "eyeR": "head", "bellyL": "chest", "bellyR": "chest", "tongue0": "head"})
for _i in range(1, 6): PARENT["tongue%d" % _i] = "tongue%d" % (_i - 1)
for s in "LR":
    PARENT.update({"arm" + s: "chest", "fore" + s: "arm" + s, "hand" + s: "fore" + s, "leg" + s: "hips", "shin" + s: "leg" + s})

_JB = {"pelvis": (0, 0.04, 1.25), "spine1": (0, 0.06, 1.45), "spine2": (0, 0.00, 1.72), "spine3": (0, -0.10, 1.98),
       "neck0": (0, -0.24, 2.00), "neck1": (0, -0.38, 1.94), "head0": (0, -0.46, 1.84)}
for _s, _sx in (("L", -1), ("R", 1)):
    _JB.update({"shoulder" + _s: (0.25 * _sx, -0.08, 2.04), "elbow" + _s: (0.40 * _sx, -0.02, 2.62),
                "joint2" + _s: (0.56 * _sx, -0.34, 1.60), "wrist" + _s: (0.66 * _sx, -0.62, 0.36), "hand" + _s: (0.67 * _sx, -0.68, 0.15),
                "hip" + _s: (0.13 * _sx, 0.05, 1.18), "knee" + _s: (0.22 * _sx, -0.20, 0.72),
                "ankle" + _s: (0.26 * _sx, 0.06, 0.17), "toe" + _s: (0.27 * _sx, -0.10, 0.05)})
J = {k: np.array([v[0], v[2], -v[1]], float) for k, v in _JB.items()}

PIVOT = {"hips": J["pelvis"], "chest": J["spine1"], "neck": J["spine3"], "head": J["head0"], "jaw": J["head0"], "jawR": J["head0"]}
for s in "LR":
    PIVOT.update({"arm" + s: J["shoulder" + s], "fore" + s: J["elbow" + s], "hand" + s: J["joint2" + s],
                  "leg" + s: J["hip" + s], "shin" + s: J["knee" + s]})


def set_pivots(piv):
    """take the exact pivots from the exported mesh (jaw, eye, tongue and stomach pivots only exist there)"""
    for k, v in piv.items(): PIVOT[k] = np.asarray(v, float)
    global ORDER
    ORDER[:] = [b for b in ORDER if b in PIVOT]          # an older mesh without the new bones still works


# the four limbs as two-bone chains: (upper bone, lower bone, end point at rest, side)
LIMBS = {"legL": ("legL", "shinL", J["toeL"]), "legR": ("legR", "shinR", J["toeR"]),     # plant the toes, not the ankle
         "armL": ("foreL", "handL", J["handL"]), "armR": ("foreR", "handR", J["handR"])}


def local(q, t, piv):
    M = np.eye(4); R = qmat(q)
    M[:3, :3] = R; M[:3, 3] = np.asarray(t) + piv - R @ piv
    return M


def fk(pose):
    """pose: {bone: (quat, offset)} -> {bone: 4x4 world matrix}"""
    W = {}
    for b in ORDER:
        q, t = pose.get(b, (np.array([1.0, 0, 0, 0]), np.zeros(3)))
        L = local(q, t, PIVOT[b])
        W[b] = L if PARENT[b] is None else W[PARENT[b]] @ L
    return W


def xf(M, p): return (M @ np.append(p, 1.0))[:3]


def two_bone(pose, limb, target, pole):
    """bend the chain so its end lands on 'target' (model space) with the middle joint pushed toward 'pole'.
    writes new local rotations for both bones into pose (keeps whatever offset they had)."""
    up, lo, end = LIMBS[limb]
    W = fk(pose)
    Mp = W[PARENT[up]]
    inv = np.linalg.inv(Mp)
    h = PIVOT[up]; k0 = PIVOT[lo]; e0 = end
    T = xf(inv, target); P = xf(inv, pole) - h            # into the parent's rest space
    l1 = np.linalg.norm(k0 - h); l2 = np.linalg.norm(e0 - k0)
    dv = T - h; d = np.linalg.norm(dv)
    d = min(max(d, abs(l1 - l2) + 1e-4), l1 + l2 - 1e-4)
    u = dv / max(np.linalg.norm(dv), 1e-9)
    a = (l1 * l1 - l2 * l2 + d * d) / (2 * d)
    hgt = math.sqrt(max(l1 * l1 - a * a, 0.0))
    pp = P - u * np.dot(P, u)
    if np.linalg.norm(pp) < 1e-6: pp = np.cross(u, (1, 0, 0))
    pp /= np.linalg.norm(pp)
    K = h + u * a + pp * hgt
    Te = h + u * d
    # upper bone: rest frame (bone dir, bend normal) -> wanted frame
    x0 = (k0 - h) / l1; n0 = np.cross(k0 - h, e0 - k0)
    if np.linalg.norm(n0) < 1e-8: n0 = np.cross(x0, (0, 0, 1))
    n0 /= np.linalg.norm(n0)
    x1 = (K - h) / l1; n1 = np.cross(K - h, Te - K); n1 /= max(np.linalg.norm(n1), 1e-9)
    B0 = np.stack([x0, n0, np.cross(x0, n0)], axis=1); B1 = np.stack([x1, n1, np.cross(x1, n1)], axis=1)
    RA = B1 @ B0.T
    qa = mat_q(RA)
    # lower bone: in the upper bone's rest frame, swing the rest direction onto the wanted one
    want = RA.T @ (Te - K)
    qb = qbetween(e0 - k0, want)
    pose[up] = (qa, pose.get(up, (None, np.zeros(3)))[1])
    pose[lo] = (qb, pose.get(lo, (None, np.zeros(3)))[1])
    return K


def reach_assist(pose, limb, target, slack=0.95):
    """the stilt arms are almost straight: if the hand can't get there, swing the whole arm from the shoulder
    (just enough) so the elbow leans toward the target, then let the IK finish."""
    if not limb.startswith("arm"): return
    s = limb[-1]
    arm, up, lo, end = "arm" + s, LIMBS[limb][0], LIMBS[limb][1], LIMBS[limb][2]
    reach = (np.linalg.norm(PIVOT[lo] - PIVOT[up]) + np.linalg.norm(end - PIVOT[lo])) * slack
    W = fk(pose)
    S = xf(W[arm], PIVOT[arm]); E = xf(W[up], PIVOT[up])
    if np.linalg.norm(target - E) <= reach: return
    inv3 = np.linalg.inv(W[PARENT[arm]][:3, :3])
    a = inv3 @ (E - S); b = inv3 @ (target - S)            # in the shoulder's parent space
    full = qbetween(a, b)
    lo_t, hi_t = 0.0, 1.0
    q0, t0 = pose[arm]
    for _ in range(14):                                     # smallest swing that brings the target into reach
        mid = (lo_t + hi_t) / 2
        pose[arm] = (qmul(slerp(np.array([1.0, 0, 0, 0]), full, mid), q0), t0)
        W = fk(pose); E = xf(W[up], PIVOT[up])
        if np.linalg.norm(target - E) <= reach: hi_t = mid
        else: lo_t = mid
    pose[arm] = (qmul(slerp(np.array([1.0, 0, 0, 0]), full, hi_t), q0), t0)


def end_pos(W, limb):
    up, lo, end = LIMBS[limb]
    return xf(W[lo], end)
