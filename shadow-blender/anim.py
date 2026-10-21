"""
Keyframed animation clips for Mansoority, authored on a part rig in Blender (bezier F-curves),
previewed as rendered frames, and sampled into shadow.anim for the mod.

Poses are written in the mod's convention (Minecraft axes, radians): x = pitch (+ bends the part
forward), y = yaw, z = roll; t = translation in model metres.  Converted to Blender here.
Executed from build_shadow.py (shares its globals: bpy, Vector, PARTS, J, JAW_PIVOTS, OUT ...).
"""
import math as _m
from mathutils import Quaternion as _Q, Euler as _E, Matrix as _M

ORDER = ["hips", "chest", "neck", "head", "jaw", "jawR", "armL", "foreL", "handL",
         "armR", "foreR", "handR", "legL", "shinL", "legR", "shinR"]


def _mc_quat(x, y, z):
    """mod applies Quaternionf.rotationZYX(z, y, x) in MC space -> same rotation as a Blender quaternion"""
    qx = _Q((1, 0, 0), x); qy = _Q((0, 1, 0), y); qz = _Q((0, 0, 1), z)
    q = qz @ qy @ qx                       # MC-space quaternion
    return _Q((q.w, q.x, -q.z, q.y))       # MC (x,y,z) = Blender (x, z, -y)


def _bl_quat_to_mc(qb):
    return (qb.x, qb.z, -qb.y, qb.w)       # inverse mapping, as (x, y, z, w)


def spider(k, P=None):
    """the low spider crouch (k 0..1)"""
    P = P or {}
    def add(part, x=0, y=0, z=0):
        a = P.get(part, (0, 0, 0)); P[part] = (a[0] + x, a[1] + y, a[2] + z)
    add("hips", 0.85 * k); add("chest", 0.45 * k); add("neck", -1.05 * k); add("head", -0.85 * k)
    add("legL", -0.85 * k, 0, -0.95 * k); add("legR", -0.85 * k, 0, 0.95 * k)
    add("shinL", 0, 0, 0.9 * k); add("shinR", 0, 0, -0.9 * k)
    add("armL", 0.35 * k, 0, -0.75 * k); add("armR", 0.35 * k, 0, 0.75 * k)
    add("foreL", 0, 0, 0.5 * k); add("foreR", 0, 0, -0.5 * k)
    return P


def merge(*ds):
    out = {}
    for d in ds:
        for k, v in d.items():
            a = out.get(k, (0, 0, 0)); out[k] = (a[0] + v[0], a[1] + v[1], a[2] + v[2])
    return out


def gait(phase, amp, P=None, arm_amp=None):
    """diagonal pairs: legL with armR, legR with armL"""
    P = dict(P or {})
    s = _m.sin(phase); c = _m.cos(phase)
    aa = amp if arm_amp is None else arm_amp
    P = merge(P, {"legL": (s * amp, 0, 0), "armR": (s * aa, 0, 0), "legR": (-s * amp, 0, 0), "armL": (-s * aa, 0, 0),
                  "shinL": (max(0, c) * amp * 1.1, 0, 0), "shinR": (max(0, -c) * amp * 1.1, 0, 0)})
    return P


# ---------------------------------------------------------------- the clips: (length, loop, keys)
# keys: list of (frame, rotations{part:(x,y,z)}, translations{part:(x,y,z)})
def clips():
    C = {}
    # idle: never still - breathing, weight shifting, head drifting like it's listening, one hand curling at a time
    C["idle"] = (80, True, [
        (0,  {"hips": (0, 0, 0.03), "chest": (0.0, 0, 0), "head": (0, 0.10, 0.06), "handL": (0.05, 0, 0), "handR": (0.30, 0, 0), "legL": (0, 0, -0.03), "legR": (0, 0, -0.03)}, {"hips": (0, 0.0, 0)}),
        (20, {"hips": (0, 0, 0.0), "chest": (0.05, 0, 0), "head": (0.05, -0.05, 0.22), "handL": (0.35, 0, 0), "handR": (0.10, 0, 0)}, {"hips": (0, -0.012, 0)}),
        (40, {"hips": (0, 0, -0.03), "chest": (0.0, 0, 0), "head": (0, -0.12, -0.08), "handL": (0.10, 0, 0), "handR": (0.05, 0, 0), "legL": (0, 0, 0.03), "legR": (0, 0, 0.03)}, {"hips": (0, 0.0, 0)}),
        (60, {"hips": (0, 0, 0.0), "chest": (0.05, 0, 0), "head": (-0.04, 0.02, 0.30), "handL": (0.02, 0, 0), "handR": (0.40, 0, 0)}, {"hips": (0, -0.012, 0)}),
        (80, {"hips": (0, 0, 0.03), "chest": (0.0, 0, 0), "head": (0, 0.10, 0.06), "handL": (0.05, 0, 0), "handR": (0.30, 0, 0), "legL": (0, 0, -0.03), "legR": (0, 0, -0.03)}, {"hips": (0, 0.0, 0)}),
    ])
    # peek: lurking low, swaying, head tipped over sideways watching you
    base = spider(0.62)
    C["peek"] = (60, True, [
        (0,  merge(base, {"head": (0, 0.05, 0.45), "chest": (0, 0, 0.04)}), {"hips": (0, -0.34, 0)}),
        (30, merge(base, {"head": (0.05, -0.08, 0.60), "chest": (0, 0, -0.04), "handL": (0.3, 0, 0)}), {"hips": (0, -0.36, 0)}),
        (60, merge(base, {"head": (0, 0.05, 0.45), "chest": (0, 0, 0.04)}), {"hips": (0, -0.34, 0)}),
    ])
    # stare A: goes still, leans in, arms creep open, head slowly rolls over
    C["stareA"] = (60, False, [
        (0,  {}, {}),
        (14, {"chest": (0.15, 0, 0), "neck": (0.15, 0, 0)}, {}),
        (34, {"chest": (0.15, 0, 0), "neck": (0.15, 0, 0), "head": (0, 0, 0.35), "armL": (-0.55, 0, -0.22), "armR": (-0.55, 0, 0.22),
              "foreL": (-0.25, 0, 0), "foreR": (-0.25, 0, 0), "handL": (-0.35, 0, 0), "handR": (-0.35, 0, 0)}, {}),
        (60, {"chest": (0.18, 0, 0), "neck": (0.18, 0, 0), "head": (0, 0, 0.85), "armL": (-1.1, 0, -0.45), "armR": (-1.1, 0, 0.45),
              "foreL": (-0.5, 0, 0), "foreR": (-0.5, 0, 0), "handL": (-0.7, 0, 0), "handR": (-0.7, 0, 0)}, {}),
    ])
    # stare B: head turns all the way upside down and it crawls toward you, slowly
    keys = []
    for f in range(0, 41, 5):
        ph = f / 40 * 2 * _m.pi
        P = gait(ph, 0.22, spider(0.85))
        P = merge(P, {"head": (0.2, 0, 2.9 + 0.08 * _m.sin(ph * 2))})
        keys.append((f, P, {"hips": (0, -0.47 + 0.02 * abs(_m.sin(ph)), 0)}))
    C["crawl"] = (40, True, keys)
    # walk (and the mirror stare: it copies your steps): long stilt strides, arms planted like front legs
    keys = []
    for f in range(0, 41, 5):
        ph = f / 40 * 2 * _m.pi
        P = gait(ph, 0.28, {"chest": (0.06, 0, 0), "head": (0, 0, 0.12 * _m.sin(ph))}, arm_amp=0.22)
        keys.append((f, P, {"hips": (0, -0.03 * abs(_m.cos(ph)), 0)}))
    C["walk"] = (40, True, keys)
    # stare D: the neck stretches out toward you while the body stays where it is
    C["neck"] = (60, False, [
        (0,  {}, {}),
        (10, {"neck": (-0.1, 0, 0)}, {"neck": (0, 0.02, 0.05)}),
        (60, {"neck": (-0.35, 0, 0), "head": (0.25, 0, 0.4), "armL": (-0.2, 0, -0.3), "armR": (-0.2, 0, 0.3)}, {"neck": (0, 0.18, 0.75), "head": (0, 0.05, 0.25)}),
    ])
    # fold: snaps down into the spider crouch in jerks (each step: a snap, then a hold)
    keys = [(0, {}, {})]
    prevP, prevT = {}, {}
    for i, f in enumerate((7, 15, 23, 31, 39)):
        k = (i + 1) / 5
        P = merge(spider(k), {"head": (0, 0, 0.6 * (1 - k) * (1 if i % 2 else -1))})
        T = {"hips": (0, -0.55 * k, 0)}
        keys.append((f - 1, prevP, prevT))     # hold...
        keys.append((f, P, T))                 # ...snap
        prevP, prevT = P, T
    keys.append((45, spider(1.0), {"hips": (0, -0.55, 0)}))
    C["fold"] = (45, False, keys)
    # scuttle: fast and twitchy on all fours
    keys = []
    for f in range(0, 13, 3):
        ph = f / 12 * 2 * _m.pi
        P = gait(ph, 0.45, spider(1.0))
        P = merge(P, {"hips": (0, 0, 0.05 * _m.sin(ph * 2))})
        keys.append((f, P, {"hips": (0, -0.55 + 0.03 * abs(_m.sin(ph)), 0)}))
    C["scuttle"] = (12, True, keys)
    # grab: one long arm shoots out
    C["grab"] = (24, False, [
        (0, {}, {}),
        (6, {"armR": (-0.5, 0, 0.35), "chest": (0.05, 0, 0)}, {}),
        (14, {"armR": (-1.45, 0, 0.25), "foreR": (-0.45, 0, 0), "handR": (-0.3, 0, 0), "chest": (0.12, 0, 0)}, {}),
        (24, {"armR": (-1.35, 0, 0.2), "foreR": (-0.35, 0, 0), "handR": (0.9, 0, 0), "chest": (0.1, 0, 0)}, {}),
    ])
    # lick: holding you up at its face, head bobbing in to you
    hold = {"armR": (-2.25, 0, 0.2), "foreR": (-0.35, 0, 0), "handR": (0.9, 0, 0)}
    C["lick"] = (30, True, [
        (0,  merge(hold, {"neck": (0.35, 0, 0), "head": (0.30, 0, -0.2)}), {}),
        (10, merge(hold, {"neck": (0.45, 0, 0), "head": (0.45, 0, 0.1)}), {"neck": (0, 0, 0.05)}),
        (20, merge(hold, {"neck": (0.35, 0, 0), "head": (0.30, 0, 0.25)}), {}),
        (30, merge(hold, {"neck": (0.35, 0, 0), "head": (0.30, 0, -0.2)}), {}),
    ])
    return C


def build_rig():
    """an empty per part at its pivot, parented like the mod's part tree"""
    rig = {}
    for name in ORDER:
        piv = JAW_PIVOTS[name] if name in JAW_PIVOTS else J[PARTS[name][1]][0]
        e = link(bpy.data.objects.new("rig_" + name, None))
        e.location = piv
        e.rotation_mode = 'QUATERNION'
        rig[name] = e
    bpy.context.view_layer.update()
    for name in ORDER:
        par = PARTS[name][0]
        if par:
            rig[name].parent = rig[par]
            rig[name].matrix_parent_inverse = rig[par].matrix_world.inverted()
    bpy.context.view_layer.update()
    return rig


def key_clip(rig, clip):
    length, loop, keys = clip
    rest = {n: rig[n].location.copy() for n in ORDER}
    for n in ORDER:
        rig[n].animation_data_clear()
    for (f, R, T) in keys:
        for n in ORDER:
            x, y, z = R.get(n, (0, 0, 0))
            rig[n].rotation_quaternion = _mc_quat(x, y, z)
            tx, ty, tz = T.get(n, (0, 0, 0))
            rig[n].location = rest[n] + Vector((tx, -tz, ty))          # MC -> Blender
            rig[n].keyframe_insert("rotation_quaternion", frame=f + 1)
            rig[n].keyframe_insert("location", frame=f + 1)
    for n in ORDER:
        ad = rig[n].animation_data
        if ad and ad.action:
            for fc in ad.action.fcurves:
                for kp in fc.keyframe_points:
                    kp.interpolation = 'BEZIER'; kp.handle_left_type = kp.handle_right_type = 'AUTO_CLAMPED'
    return rest


def sample_clip(rig, rest, length):
    frames = []
    for f in range(length + 1):
        bpy.context.scene.frame_set(f + 1)
        row = []
        for n in ORDER:
            q = rig[n].rotation_quaternion.normalized()
            d = rig[n].location - rest[n]
            row.append(_bl_quat_to_mc(q) + (d.x, d.z, -d.y))
        frames.append(row)
    return frames


def bake_animations(part_tris, render_previews=True):
    """key every clip on the rig, write shadow.anim, and render small preview frames of each clip"""
    rig = build_rig()
    # preview body: one mesh per part, parented to its empty
    if render_previews:
        for n in ORDER:
            tri = part_tris.get(n, [])
            if not tri: continue
            vs = [p for (p, nn, c, e) in tri]
            fs = [(i, i + 1, i + 2) for i in range(0, len(vs) - 2, 3)]
            me = bpy.data.meshes.new("pv_" + n); me.from_pydata(vs, [], fs)
            for p in me.polygons: p.use_smooth = True
            ob = link(bpy.data.objects.new("pv_" + n, me))
            ob.data.materials.append(MAT["skin"] if n not in ("head", "jaw", "jawR") else MAT["face"])
            ob.parent = rig[n]; ob.matrix_parent_inverse = rig[n].matrix_world.inverted()
        for o in ALL: o.hide_render = True
    C = clips()
    data = {}
    for name, clip in C.items():
        rest = key_clip(rig, clip)
        data[name] = (clip[0], clip[1], sample_clip(rig, rest, clip[0]))
        if render_previews:
            os.makedirs(os.path.join(OUT, "anim"), exist_ok=True)
            for f in range(0, clip[0] + 1, 2):
                bpy.context.scene.frame_set(f + 1)
                scene.render.filepath = os.path.join(OUT, "anim", "%s_%03d.png" % (name, f))
                bpy.ops.render.render(write_still=True)
        for n in ORDER:
            rig[n].animation_data_clear(); rig[n].rotation_quaternion = (1, 0, 0, 0); rig[n].location = rest[n]
    with open(os.path.join(OUT, "shadow.anim"), "wb") as f:
        f.write(b"SHA1"); f.write(struct.pack("<i", len(ORDER)))
        for n in ORDER:
            nb = n.encode(); f.write(struct.pack("<h", len(nb))); f.write(nb)
        f.write(struct.pack("<i", len(data)))
        for name, (length, loop, frames) in data.items():
            nb = name.encode(); f.write(struct.pack("<h", len(nb))); f.write(nb)
            f.write(struct.pack("<ib", len(frames), 1 if loop else 0))
            for row in frames:
                for v in row: f.write(struct.pack("<7f", *v))
    print("[SHADOW] animations:", {k: v[0] for k, v in data.items()})
    if render_previews:                       # put the real model back for the jumpscare renders
        for o in list(bpy.data.objects):
            if o.name.startswith("pv_"): bpy.data.objects.remove(o)
        for o in ALL: o.hide_render = False
