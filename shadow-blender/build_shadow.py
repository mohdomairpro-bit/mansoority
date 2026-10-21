"""
The Shadow - procedural creature (run with: blender -b -P build_shadow.py -- <outdir> [what])
what: preview | jumpscare | export | all
Design rules (horror concept-art principles): forward-leaning top-heavy silhouette, limbs too long with an
extra joint, knees bending the wrong way, an almost-human face whose mouth splits the cheeks open,
human teeth, asymmetric bone growths and a broken one, sunken sockets with pinpoint glints.
"""
import bpy, bmesh, math, sys, os, random, struct
from mathutils import Vector, Matrix, noise
from mathutils.bvhtree import BVHTree

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = argv[0] if argv else "/tmp/shadow_out"
WHAT = argv[1] if len(argv) > 1 else "all"
os.makedirs(OUT, exist_ok=True)
random.seed(7)

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene

def link(obj):
    scene.collection.objects.link(obj)
    return obj

def apply_mods(obj):
    dg = bpy.context.evaluated_depsgraph_get()
    ev = obj.evaluated_get(dg)
    me = bpy.data.meshes.new_from_object(ev)
    obj.modifiers.clear()
    old = obj.data
    obj.data = me
    bpy.data.meshes.remove(old)
    return obj

# ------------------------------------------------------------------ skeleton (meters, z up, creature faces -Y)
J = {}
def j(name, x, y, z, r): J[name] = (Vector((x, y, z)), r)
j("pelvis", 0, 0.04, 1.25, 0.11)
j("spine1", 0, 0.06, 1.45, 0.075)          # wasp-thin waist
j("spine2", 0, 0.00, 1.72, 0.15)
j("spine3", 0, -0.10, 1.98, 0.19)          # hunched upper back between towering shoulders
j("neck0", 0, -0.24, 2.00, 0.07)
j("neck1", 0, -0.38, 1.94, 0.055)
j("head0", 0, -0.46, 1.84, 0.05)           # head hangs low, in front, between the shoulders
for s, sx in (("L", -1), ("R", 1)):
    # stilt arms: up to elbows that tower over the head, then all the way down to the ground
    j("shoulder" + s, 0.25 * sx, -0.08, 2.04, 0.095)
    j("elbow" + s, 0.40 * sx, -0.02, 2.62, 0.05)
    j("joint2" + s, 0.56 * sx, -0.34, 1.60, 0.038)   # the extra joint
    j("wrist" + s, 0.66 * sx, -0.62, 0.36, 0.03)
    j("hand" + s, 0.67 * sx, -0.68, 0.15, 0.045)
    j("hip" + s, 0.13 * sx, 0.05, 1.18, 0.08)
    j("knee" + s, 0.22 * sx, -0.20, 0.72, 0.062)      # big knobbly knees
    j("ankle" + s, 0.26 * sx, 0.06, 0.17, 0.032)
    j("toe" + s, 0.27 * sx, -0.10, 0.05, 0.024)

BONES = []   # (part, a, b)
def bone(part, a, b): BONES.append((part, a, b))
bone("hips", "pelvis", "spine1")
bone("chest", "spine1", "spine2"); bone("chest", "spine2", "spine3")
bone("neck", "spine3", "neck0"); bone("neck", "neck0", "neck1"); bone("neck", "neck1", "head0")
for s in "LR":
    bone("chest", "spine3", "shoulder" + s)
    bone("arm" + s, "shoulder" + s, "elbow" + s)
    bone("fore" + s, "elbow" + s, "joint2" + s)
    bone("hand" + s, "joint2" + s, "wrist" + s)
    bone("hand" + s, "wrist" + s, "hand" + s)
    bone("hips", "pelvis", "hip" + s)
    bone("leg" + s, "hip" + s, "knee" + s)
    bone("shin" + s, "knee" + s, "ankle" + s)
    bone("shin" + s, "ankle" + s, "toe" + s)

PARTS = {  # name: (parent, pivot joint)
    "hips": (None, "pelvis"), "chest": ("hips", "spine1"), "neck": ("chest", "spine3"),
    "head": ("neck", "head0"), "jaw": ("head", None), "jawR": ("head", None),
}
for s in "LR":
    PARTS["arm" + s] = ("chest", "shoulder" + s); PARTS["fore" + s] = ("arm" + s, "elbow" + s)
    PARTS["hand" + s] = ("fore" + s, "joint2" + s)
    PARTS["leg" + s] = ("hips", "hip" + s); PARTS["shin" + s] = ("leg" + s, "knee" + s)

# ------------------------------------------------------------------ body via skin modifier
def skin_object(name, verts, edges, radii, root=0, subdiv=2):
    me = bpy.data.meshes.new(name)
    me.from_pydata([tuple(v) for v in verts], edges, [])
    ob = link(bpy.data.objects.new(name, me))
    sk = ob.modifiers.new("skin", 'SKIN')
    sk.use_smooth_shade = True
    for i, r in enumerate(radii):
        sv = me.skin_vertices[0].data[i]
        sv.radius = (r, r * 0.92)
    me.skin_vertices[0].data[root].use_root = True
    sd = ob.modifiers.new("sub", 'SUBSURF'); sd.levels = subdiv; sd.render_levels = subdiv
    return ob

names = list(J.keys())
idx = {n: i for i, n in enumerate(names)}
verts = [J[n][0] for n in names]; radii = [J[n][1] for n in names]
edges = [(idx[a], idx[b]) for _, a, b in BONES]
# long fingers: 4 per hand, 3 segments each, curling forward/down, black nails at the tips
FINGER_TIPS = []
for s, sx in (("L", -1), ("R", 1)):
    h = J["hand" + s][0]
    for k in range(4):
        spread = (k - 1.5) * 0.045
        prev = idx["hand" + s]
        p = h.copy()
        for seg in range(3):
            p = p + Vector((spread * sx * 0.6 + sx * 0.005 * k, -0.07 - seg * 0.02, -0.10 + seg * 0.035))
            verts.append(p.copy()); radii.append(0.016 - seg * 0.003)
            edges.append((prev, len(verts) - 1)); prev = len(verts) - 1
            BONES.append(("hand" + s, None, None))
        FINGER_TIPS.append((p.copy(), (p - verts[-2]).normalized()))
for s, sx in (("L", -1), ("R", 1)):
    an = J["ankle" + s][0]
    for k, ang in enumerate((-0.55, 0.0, 0.55, 3.14)):
        prev = idx["ankle" + s]; p = an.copy()
        for seg in range(3):
            L_ = 0.075 if ang != 3.14 else 0.045
            p = p + Vector((math.sin(ang) * L_ * sx * (1 if ang != 3.14 else 0), -math.cos(ang) * L_, -0.045 if seg == 0 else -0.012))
            p.z = max(p.z, 0.012)
            verts.append(p.copy()); radii.append(0.013 - seg * 0.003)
            edges.append((prev, len(verts) - 1)); prev = len(verts) - 1
            BONES.append(("shin" + s, None, None))
        FINGER_TIPS.append((p.copy(), (p - verts[-2]).normalized()))
body = skin_object("body", verts, edges, radii, root=idx["pelvis"], subdiv=3)
apply_mods(body)

# ------------------------------------------------------------------ starved anatomy carved into the skin
def g(q, c, r):
    return math.exp(-((q - c).length / r) ** 2)
def seg_t(p, a, b):
    ab = b - a; t = max(0.0, min(1.0, (p - a).dot(ab) / ab.length_squared))
    return t, (a + ab * t - p).length
def sculpt_body(ob):
    me = ob.data
    S1, S2, S3 = J["spine1"][0], J["spine2"][0], J["spine3"][0]
    knobs = [(J[n][0], J[n][1]) for n in J if n[:-1] in ("elbow", "knee", "joint2", "wrist", "ankle", "shoulder")]
    limbs = [(J[a][0], J[b][0], J[a][1]) for _, a, b in BONES if a and b and a[:-1] in ("shoulder", "elbow", "joint2", "hip", "knee")]
    for v in me.vertices:
        p = v.co; n = v.normal.normalized(); d = 0.0
        t, dist = seg_t(p, S2 + Vector((0, 0, -0.14)), S3)
        on_torso = abs(p.x) < 0.21 and dist < 0.34 and p.z < S3.z + 0.02
        front = max(0.0, -n.y + 0.3); back = max(0.0, n.y - 0.15)
        if on_torso:
            if 0.0 < t < 0.95:                                                    # ribcage: hard bars, deep gaps
                ph = math.sin(t * math.pi * 8.0 - abs(p.x) * 7.0)
                fade = min(1.0, t / 0.12) * (1 - t * 0.35)
                d += (0.034 * max(0.0, ph) ** 0.5 - 0.022 * max(0.0, -ph) ** 0.7) * min(1.0, front * 1.6 + abs(n.x) * 0.8) * fade
                d += 0.016 * math.exp(-(p.x / 0.018) ** 2) * front * fade                  # sternum ridge
            if back > 0:                                                           # spine bones punching out of the back
                tt, dd = seg_t(p, S1 + Vector((0, 0, -0.1)), S3 + Vector((0, 0, 0.05)))
                d += 0.045 * max(0.0, math.sin(tt * math.pi * 13.0)) ** 3 * back * math.exp(-(p.x / 0.03) ** 2)
            for sx in (-1, 1):
                q = p - Vector((0.13 * sx, 0.08, 1.92))
                d += 0.05 * math.exp(-(q.x / 0.07) ** 2 - (q.z / 0.09) ** 2 - (q.y / 0.08) ** 2) * back      # shoulder blades
        # starved belly: one smooth deep hollow under the ribs (no step)
        q = p - Vector((0, -0.04, 1.58))
        d -= 0.055 * math.exp(-(q.x / 0.11) ** 2 - (q.z / 0.13) ** 2) * min(1.0, front * 1.5)
        for sx in (-1, 1):
            t2, d2 = seg_t(p, Vector((0.03 * sx, -0.26, 2.07)), Vector((0.27 * sx, -0.14, 2.05)))
            d += 0.022 * math.exp(-(d2 / 0.02) ** 2)                                 # collarbones
            d += 0.034 * g(p, Vector((0.11 * sx, -0.05, 1.33)), 0.04)                 # hip bones
        for a_, b_, r_ in limbs:                                                     # wasted limbs between swollen joints
            tl, dl = seg_t(p, a_, b_)
            if dl < r_ * 2.2 and 0.0 < tl < 1.0:
                d -= r_ * 0.28 * math.sin(tl * math.pi) ** 1.5
        for k, r_ in knobs:
            d += max(0.022, r_ * 0.45) * g(p, k, 0.05)
        d += 0.004 * noise.noise(p * 40) + 0.008 * noise.noise(Vector((p.x * 9, p.y * 9, p.z * 2.5)))   # stringy tendons
        v.co = p + n * d
sculpt_body(body)

claws = []
for tip, dirn in FINGER_TIPS:
    bpy.ops.mesh.primitive_cone_add(vertices=8, radius1=0.011, radius2=0.0008, depth=0.075, location=(0, 0, 0))
    c_ = bpy.context.active_object; c_.name = "claw"
    rot = Vector((0, 0, 1)).rotation_difference((dirn + Vector((0, 0, -0.6))).normalized()).to_matrix()
    for v in c_.data.vertices:
        v.co = rot @ (v.co + Vector((0, 0, 0.032))) + tip
    for p_ in c_.data.polygons: p_.use_smooth = True
    claws.append(c_)

# ribs pushing through the skin + spine ridge
extra = []
def ribs():
    objs = []
    for i in range(6):
        t = i / 5
        c = J["spine2"][0].lerp(J["spine3"][0], 0.15 + t * 0.7) + Vector((0, 0, -0.06))
        w = 0.135 + 0.035 * math.sin(t * math.pi)
        for side in (-1, 1):
            vs = []; rs = []
            for a in range(6):
                ang = math.radians(side * (14 + a * 15))
                vs.append(c + Vector((math.sin(ang) * w, -math.cos(ang) * w * 0.9, -0.03 * abs(math.sin(ang)))))
                rs.append(0.011 - a * 0.0008)
            objs.append(skin_object("rib%d%d" % (i, side), vs, [(k, k + 1) for k in range(5)], rs, 0, 1))
    for i in range(9):
        c = J["spine1"][0].lerp(J["spine3"][0], i / 8) + Vector((0, 0.14 + 0.05 * math.sin(i / 8 * math.pi), 0))
        bpy.ops.mesh.primitive_uv_sphere_add(segments=10, ring_count=6, radius=0.021, location=c)
        r = bpy.context.active_object; r.name = "ridge%d" % i
        objs.append(r)
    return objs
# (ribs and spine are carved into the body mesh now)

# ------------------------------------------------------------------ head: gaunt skull under stretched skin, no eyes, no nose.
# the whole lower face is two halves that split down the middle and swing open sideways
HEAD_C = J["head0"][0] + Vector((0, -0.10, 0.06))
SOCKETS = [(Vector((-0.038, -0.088, 0.030)), 0.030), (Vector((0.040, -0.086, 0.026)), 0.033)]   # skin grown over them
MOUTH_Z = -0.030
def g(q, c, r):
    return math.exp(-((q - c).length / r) ** 2)
def make_head():
    bpy.ops.mesh.primitive_uv_sphere_add(segments=96, ring_count=64, radius=1.0, location=(0, 0, 0))
    hd = bpy.context.active_object; hd.name = "skull"
    for v in hd.data.vertices:
        p = v.co.copy()
        z = p.z * 0.16
        w = 0.082 if z > 0 else 0.082 * (1 - 0.40 * (-z / 0.16) ** 1.4)     # narrowing to a long chin
        if z < -0.08: z = -0.08 + (z + 0.08) * 1.7                           # stretched mandibles
        d = 0.108 if z > 0 else 0.108 * (1 - 0.20 * (-z / 0.16))
        q = Vector((p.x * w, p.y * d, z))
        if z > 0.05: q.x *= 1 + 0.10 * (z - 0.05) / 0.11                   # wide cranium
        q += Vector((0, -0.014, 0)) * g(q, Vector((0, -0.10, 0.058)), 0.045) * (1 if q.y < 0 else 0)   # brow ridge
        for c, r in SOCKETS:
            q += Vector((0, 0.040, 0)) * g(q, c, r)                           # deep, dark sockets
        for sx in (-1, 1):
            q += Vector((0.010 * sx, -0.012, 0)) * g(q, Vector((0.060 * sx, -0.070, -0.008)), 0.025)  # cheekbones
            q += Vector((-0.022 * sx, 0.014, 0)) * g(q, Vector((0.052 * sx, -0.055, -0.055)), 0.035)  # sucked-in cheeks
            q += Vector((-0.012 * sx, 0, 0)) * g(q, Vector((0.085 * sx, -0.030, 0.040)), 0.03)        # hollow temples
        q += Vector((0, 0.022, 0)) * g(q, Vector((0, -0.105, -0.010)), 0.02)                          # flat where a nose should be
        q += q.normalized() * (0.0015 * noise.noise(q * 60) + 0.003 * max(0, noise.noise(q * 9 + Vector((3, 1, 2)))))
        v.co = q
    for f in hd.data.polygons: f.use_smooth = True
    return hd
skull = make_head()
skull_bvh = BVHTree.FromObject(skull, bpy.context.evaluated_depsgraph_get())
def front_y(x, z):
    h = skull_bvh.ray_cast(Vector((x, -0.5, z)), Vector((0, 1, 0)), 1.0)
    return h[0].y if h[0] is not None else None

bm = bmesh.new(); bm.from_mesh(skull.data)
bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:], plane_co=(0, 0, MOUTH_Z), plane_no=(0, 0, 1))
bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:], plane_co=(0, 0, 0), plane_no=(1, 0, 0))
def take(faces, name):
    nb = bmesh.new(); vmap = {}
    for f in faces:
        nv = []
        for v in f.verts:
            if v not in vmap: vmap[v] = nb.verts.new(v.co)
            nv.append(vmap[v])
        try: nb.faces.new(nv)
        except ValueError: pass
    me = bpy.data.meshes.new(name); nb.to_mesh(me); nb.free()
    return link(bpy.data.objects.new(name, me))
pet = [f for f in bm.faces if f.calc_center_median().z < MOUTH_Z and f.calc_center_median().y < 0.025]
fl = [f for f in pet if f.calc_center_median().x < 0]; fr = [f for f in pet if f.calc_center_median().x >= 0]
jaw = take(fl, "jaw"); jawR = take(fr, "jawR")
bmesh.ops.delete(bm, geom=pet, context='FACES')
bm.to_mesh(skull.data); bm.free()
for ob in (skull, jaw, jawR):
    sol = ob.modifiers.new("solid", 'SOLIDIFY'); sol.thickness = 0.018 if ob is not skull else 0.006
    apply_mods(ob)
    for p in ob.data.polygons: p.use_smooth = True
PETALS = (jaw, jawR)
# wet red gullet behind the halves, black nothing beyond it
bpy.ops.mesh.primitive_uv_sphere_add(segments=32, ring_count=16, radius=1, location=(0, -0.010, -0.095))
throat = bpy.context.active_object; throat.name = "throat"; throat.scale = (0.050, 0.042, 0.085)
apply_mods(throat)
bpy.ops.mesh.primitive_uv_sphere_add(segments=32, ring_count=16, radius=1, location=(0, 0.0, 0.03))
void = bpy.context.active_object; void.name = "void"; void.scale = (0.072, 0.082, 0.10)
apply_mods(void)

def tooth(loc, h, w, d):
    """needle tooth from loc pointing along d"""
    bpy.ops.mesh.primitive_cone_add(vertices=5, radius1=w, radius2=w * random.uniform(0.08, 0.35), depth=h, location=(0, 0, 0))
    t = bpy.context.active_object
    d = (Vector(d).normalized() + Vector((random.uniform(-0.25, 0.25), random.uniform(-0.25, 0.25), random.uniform(-0.25, 0.25)))).normalized()
    rot = Vector((0, 0, 1)).rotation_difference(d).to_matrix()
    for v in t.data.vertices:
        v.co.y *= 0.8
        v.co = rot @ (v.co + Vector((0, 0, h / 2))) + Vector(loc)
    for p in t.data.polygons: p.use_smooth = True
    return t
upper, lower, lowerR = [], [], []
random.seed(5)
# interlocking needles along the vertical split, in two staggered rows
z = MOUTH_Z - 0.006
while z > -0.235:
    yf = front_y(0.0, z)
    if yf is None: break
    for row, (dy, sc) in enumerate(((0.009, 1.0), (0.022, 0.75))):
        for side, lst in ((-1, lower), (1, lowerR)):
            zz = z + (0.0045 if side > 0 else 0) + row * 0.003
            h = random.uniform(0.014, 0.026) * sc
            lst.append(tooth((side * 0.004, yf + dy, zz), h, random.uniform(0.0025, 0.0038) * sc, (-side, -0.15, random.uniform(-0.3, 0.1))))
    z -= 0.0095
# along the top edge: upper fangs hang down, petal teeth point up
for i in range(22):
    x = -0.066 + i * (0.132 / 21)
    yf = front_y(x, MOUTH_Z + 0.004)
    if yf is None: continue
    h = random.uniform(0.012, 0.024) * (1.25 if abs(x) < 0.02 else 1)
    upper.append(tooth((x, yf + 0.008, MOUTH_Z + 0.002), h, random.uniform(0.003, 0.0045), (0, -0.1, -1)))
    yb = front_y(x, MOUTH_Z - 0.006)
    if yb is not None and abs(x) > 0.006:
        (lower if x < 0 else lowerR).append(tooth((x, yb + 0.009, MOUTH_Z - 0.004), random.uniform(0.008, 0.016), 0.0028, (0, -0.1, 1)))
for sx in (-1, 1):
    for k_ in range(3, 22, 2):
        t_ = k_ / 23
        gx = sx * (0.012 + 0.07 * t_); gz = MOUTH_Z + 0.002 + 0.05 * t_ ** 2.2
        yf = front_y(gx, gz)
        if yf is None: continue
        upper.append(tooth((gx, yf + 0.005, gz + 0.0025), 0.006, 0.0022, (0, -0.2, -1)))
        upper.append(tooth((gx, yf + 0.005, gz - 0.0025), 0.005, 0.002, (0, -0.2, 1)))
# rings of teeth down the gullet
for ring in range(4):
    zc = -0.055 - ring * 0.035; rr = 0.036 - ring * 0.004
    for k in range(13):
        a = k / 13 * 2 * math.pi + ring
        pos = Vector((math.cos(a) * rr, -0.010 + math.sin(a) * rr * 0.8, zc))
        upper.append(tooth(pos, random.uniform(0.008, 0.014), 0.0025, Vector((0, -0.010, zc)) - pos))
drips = []
for i in range(22):
    if i < 12:
        x = random.choice((-1, 1)) * random.uniform(0.003, 0.008); zt = random.uniform(-0.20, MOUTH_Z - 0.01)
    else:
        x = random.uniform(-0.06, 0.06); zt = MOUTH_Z - 0.002
    yf = front_y(x, zt)
    if yf is None: continue
    L = random.uniform(0.008, 0.035)
    bpy.ops.mesh.primitive_cone_add(vertices=8, radius1=0.0011, radius2=0.0024, depth=L, location=(x, yf + 0.002, zt - L / 2))
    d0 = bpy.context.active_object; d0.name = "drip"
    bpy.ops.mesh.primitive_uv_sphere_add(segments=8, ring_count=6, radius=0.0028, location=(x, yf + 0.002, zt - L))
    d1 = bpy.context.active_object; d1.name = "drip"
    for o_ in (d0, d1):
        for p_ in o_.data.polygons: p_.use_smooth = True
    drips += [d0, d1]

hair = []
random.seed(11)
for k in range(150):
    th = random.uniform(-2.9, 2.9); ph = random.uniform(0.15, 1.55)
    root = Vector((math.sin(th) * math.sin(ph) * 0.084, math.cos(th) * math.sin(ph) * 0.11, math.cos(ph) * 0.16))
    if root.y < -0.03 and abs(root.x) < 0.07: continue     # the blank face stays bare
    L = random.uniform(0.18, 0.5)
    pts = [root]
    p_ = root.copy(); d_ = (root * Vector((1.0, 1.0, 0.0))).normalized() * 0.012
    for sgm in range(9):
        d_ = (d_ * 0.6 + Vector((0, 0, -1.0)) * 0.03 + root.normalized() * Vector((1, 1, 0)) * (0.006 if sgm < 2 else 0.0)
              + Vector((random.uniform(-1, 1), random.uniform(-1, 1), 0)) * 0.003).normalized() * (L / 9)
        p_ = p_ + d_; pts.append(p_.copy())
    cd = bpy.data.curves.new("hair", 'CURVE'); cd.dimensions = '3D'; cd.bevel_depth = random.uniform(0.0025, 0.0045); cd.bevel_resolution = 0
    sp = cd.splines.new('POLY'); sp.points.add(len(pts) - 1)
    for i_, q_ in enumerate(pts): sp.points[i_].co = (q_.x, q_.y, q_.z, 1); sp.points[i_].radius = 1.0 - i_ / (len(pts) + 1)
    ho = link(bpy.data.objects.new("hair", cd))
    me_ = bpy.data.meshes.new_from_object(ho.evaluated_get(bpy.context.evaluated_depsgraph_get()))
    mo = link(bpy.data.objects.new("hairm", me_)); bpy.data.objects.remove(ho)
    for p2 in mo.data.polygons: p2.use_smooth = True
    hair.append(mo)
eyes, glints = [], []
for c, r in SOCKETS:                                   # tiny pale points of light far back in the sockets
    yb = front_y(c.x, c.z)
    bpy.ops.mesh.primitive_uv_sphere_add(segments=10, ring_count=6, radius=0.0027, location=(c.x, (yb if yb is not None else c.y) + 0.0035, c.z - 0.003))
    glints.append(bpy.context.active_object)
# branching spines sweeping up and back off the skull
tendrils = []
def tendril(root, d, L, r0, depth=0):
    pts = [root]; p_ = root.copy(); d_ = d.normalized()
    n = 8
    for i in range(n):
        d_ = (d_ + Vector((0, 0.10, -0.05)) * (i / n) + Vector((random.uniform(-1, 1), random.uniform(-1, 1), random.uniform(-1, 1))) * 0.12).normalized()
        p_ = p_ + d_ * (L / n); pts.append(p_.copy())
    cd = bpy.data.curves.new("tendril", 'CURVE'); cd.dimensions = '3D'; cd.bevel_depth = r0; cd.bevel_resolution = 1
    sp = cd.splines.new('POLY'); sp.points.add(len(pts) - 1)
    for i_, q_ in enumerate(pts): sp.points[i_].co = (q_.x, q_.y, q_.z, 1); sp.points[i_].radius = max(0.08, 1.0 - i_ / len(pts))
    ho = link(bpy.data.objects.new("tendril", cd))
    me_ = bpy.data.meshes.new_from_object(ho.evaluated_get(bpy.context.evaluated_depsgraph_get()))
    mo = link(bpy.data.objects.new("tendrilm", me_)); bpy.data.objects.remove(ho)
    for p2 in mo.data.polygons: p2.use_smooth = True
    tendrils.append(mo)
    if depth < 1:
        for bi in (3, 5):
            dd = (pts[bi + 1] - pts[bi]).normalized() + Vector((random.uniform(-0.8, 0.8), random.uniform(-0.3, 0.3), random.uniform(0.2, 0.7)))
            tendril(pts[bi], dd, L * random.uniform(0.35, 0.55), r0 * 0.55, depth + 1)
random.seed(21)
for k in range(11):
    sx = -1 if k % 2 else 1; f = (k // 2) / 5
    root = Vector((sx * (0.02 + 0.05 * f), -0.06 + 0.07 * f, 0.10 + 0.04 * (1 - f)))
    d = Vector((sx * (0.35 + 0.6 * f), 0.55 + 0.3 * f, 0.75 - 0.3 * f))
    tendril(root, d, random.uniform(0.22, 0.42) * (1.2 - 0.4 * f), random.uniform(0.0055, 0.0085))

HEAD_PARTS = {"head": [skull, void] + hair + upper + drips + tendrils + glints, "jaw": [jaw] + lower, "jawR": [jawR] + lowerR}
# place head on the neck
head_xf = Matrix.Translation(HEAD_C) @ Matrix.Rotation(math.radians(-6), 4, 'X')
head_inv = head_xf.inverted()
for objs in HEAD_PARTS.values():
    for o in objs: o.matrix_world = head_xf @ o.matrix_world
throat.matrix_world = head_xf @ throat.matrix_world
JAW_PIVOTS = {"jaw": head_xf @ Vector((-0.070, 0.005, -0.09)), "jawR": head_xf @ Vector((0.070, 0.005, -0.09))}
JAW_PIVOT_W = JAW_PIVOTS["jaw"]
HEAD_PARTS["head"].append(throat)

# ------------------------------------------------------------------ colour (per-vertex) + ambient occlusion
def mat_color(kind, co):
    n = noise.noise(co * 18) * 0.5 + noise.noise(co * 70) * 0.25
    if kind == "skin":   return Vector((0.012, 0.010, 0.011)) * (1 + 0.6 * n)
    if kind == "void":   return Vector((0.0, 0.0, 0.0))
    if kind == "hair":   return Vector((0.012, 0.010, 0.010)) * (1 + 0.4 * n)
    if kind == "saliva": return Vector((0.62, 0.60, 0.52))
    if kind == "face":
        blot = 0.7 + 0.6 * (noise.noise(co * 9 + Vector((5, 2, 1))) * 0.5 + 0.5)
        vein = 1 - abs(noise.noise(co * 32))
        c = Vector((0.17, 0.175, 0.15)) * blot * (1 + 0.2 * n)
        if vein > 0.94: c = c * 0.45 + Vector((0.05, 0.0, 0.02))
        return c
    if kind == "bone":   return Vector((0.30, 0.27, 0.22)) * (1 + 0.4 * n)
    if kind == "tooth":  return Vector((0.55, 0.48, 0.32)) * (0.6 + 0.5 * (noise.noise(co * 300) * 0.5 + 0.5))
    if kind == "throat": return Vector((0.035, 0.002, 0.004))
    if kind == "glint":  return Vector((1.0, 0.88, 0.55))
    if kind == "claw":   return Vector((0.05, 0.04, 0.035))
    return Vector((0.5, 0.5, 0.5))

KIND = {}
for o in [body] + extra: KIND[o.name] = "skin"
KIND[skull.name] = "face"; KIND[jaw.name] = "face"; KIND[jawR.name] = "face"; KIND[throat.name] = "throat"; KIND[void.name] = "void"
for o in hair: KIND[o.name] = "hair"
for o in drips: KIND[o.name] = "saliva"
for o in upper + lower + lowerR: KIND[o.name] = "tooth"
for o in glints: KIND[o.name] = "glint"
for o in claws: KIND[o.name] = "claw"
for o in tendrils: KIND[o.name] = "bone"
for o in eyes: KIND[o.name] = "eye"

ALL = [body] + extra + [skull, void, jaw, jawR, throat] + hair + eyes + upper + lower + lowerR + glints + drips + claws + tendrils
# join a temporary copy of everything for AO ray casts
def world_tris(objs):
    vs, fs = [], []
    for o in objs:
        m = o.matrix_world
        base = len(vs)
        vs += [m @ v.co for v in o.data.vertices]
        fs += [[base + i for i in p.vertices] for p in o.data.polygons]
    return vs, fs
wv, wf = world_tris(ALL)
bvh = BVHTree.FromPolygons(wv, wf)
DIRS = []
for i in range(24):
    z = 1 - 2 * (i + 0.5) / 24; r = math.sqrt(1 - z * z); phi = i * 2.399963
    DIRS.append(Vector((r * math.cos(phi), r * math.sin(phi), z)))
def ao(p, n):
    hit = 0; tot = 0
    for d in DIRS:
        if d.dot(n) <= 0.05: continue
        tot += 1
        h = bvh.ray_cast(p + n * 0.004, d, 0.25)
        if h[0] is not None: hit += 1
    return 1 - hit / max(tot, 1)
for o in ALL:
    me = o.data
    attr = me.color_attributes.new("Col", 'FLOAT_COLOR', 'POINT')
    m = o.matrix_world; nm = m.to_3x3().inverted().transposed()
    kind = KIND[o.name]
    centre = sum((v.co for v in me.vertices), Vector()) / max(1, len(me.vertices))
    for i, v in enumerate(me.vertices):
        p = m @ v.co; n = (nm @ v.normal).normalized()
        c = mat_color(kind, p)
        if kind == "eye":
            d = (v.co - centre).normalized()
            fwd = (head_xf.to_3x3() @ Vector((0, -1, 0))).normalized()
            ang = math.acos(max(-1, min(1, d.dot(fwd))))
            c = Vector((0.004, 0.003, 0.003))                                         # all-black eyes
        occ = 1.0 if kind in ("glint", "skin", "claw", "hair") else (0.25 + 0.75 * ao(p, n))   # skin gets AO from the bake
        if kind == "face":
            lp = head_inv @ p
            ln = (head_inv.to_3x3() @ n).normalized()
            for sc_, sr_ in SOCKETS:
                c = c * (1 - 0.92 * g(lp, sc_ + Vector((0, 0.02, 0)), sr_ * 1.05))     # pitch-dark sockets
            c = c * (1 - 0.35 * g(lp, Vector((0, -0.105, -0.01)), 0.022))              # flat dip, no nose
            for sc_, sr_ in SOCKETS:                                                   # raw red rims, tear-tracks of old blood
                rim = g(lp, sc_, sr_ * 1.5) - g(lp, sc_, sr_ * 0.9)
                c = c.lerp(Vector((0.20, 0.02, 0.03)), max(0.0, min(1.0, rim * 1.6)))
                if lp.z < sc_.z and abs(lp.x - sc_.x - 0.004 * math.sin(lp.z * 80)) < 0.0035 * (1 + (sc_.z - lp.z) * 8) and sc_.z - lp.z < 0.07 and lp.y < 0:
                    c = c.lerp(Vector((0.14, 0.004, 0.008)), 0.85 * (1 - (sc_.z - lp.z) / 0.07))
            # closed mouth: a lipless grin carved far too wide, the corners hooking up toward the sockets
            for sx in (-1, 1):
                for k_ in range(24):
                    t_ = k_ / 23
                    gx = sx * (0.012 + 0.07 * t_); gz = MOUTH_Z + 0.002 + 0.05 * t_ ** 2.2
                    dd = math.hypot(lp.x - gx, lp.z - gz)
                    if dd < 0.0045 and lp.y < -0.01:
                        c = c.lerp(Vector((0.03, 0.0, 0.004)), (1 - dd / 0.0045) * 0.95)
            c = c * (0.75 + 0.25 * max(0, min(1, (lp.z + 0.2) / 0.3)))
            front = 1 if lp.y < -0.02 else 0
            seam = max(max(0, 1 - abs(lp.x) / 0.006) * (1 if lp.z < MOUTH_Z else 0), max(0, 1 - abs(lp.z - MOUTH_Z) / 0.005)) * front
            c = c.lerp(Vector((0.12, 0.01, 0.015)), seam * 0.9)                       # the split, crusted red
            for bx in (-0.012, 0.009, -0.035, 0.042, 0.022):                          # old blood leaking from the split
                if lp.z < MOUTH_Z and lp.y < 0:
                    run = MOUTH_Z - lp.z
                    wob = bx + 0.003 * math.sin(lp.z * 90 + bx * 50)
                    k = max(0, 1 - abs(lp.x - wob) / (0.003 * (1 + run * 6))) * max(0, 1 - run / 0.14)
                    if k > 0: c = c.lerp(Vector((0.16, 0.005, 0.008)), min(1, k * 1.4))
            if o in PETALS and ln.dot(Vector((0, -0.01, -0.095)) - lp) > 0:          # inside of the halves: raw wet flesh
                c = Vector((0.22, 0.015, 0.025)) * (0.7 + 0.6 * (noise.noise(lp * 120) * 0.5 + 0.5))
        attr.data[i].color = (c.x * occ, c.y * occ, c.z * occ, 1.0)

# ------------------------------------------------------------------ materials for Blender renders
def make_mat(name, rough, spec, emit=0.0, sss=0.0):
    mt = bpy.data.materials.new(name); mt.use_nodes = True
    nt = mt.node_tree; bsdf = nt.nodes["Principled BSDF"]
    a = nt.nodes.new("ShaderNodeAttribute"); a.attribute_name = "Col"; a.attribute_type = 'GEOMETRY'
    base_out = a.outputs["Color"]
    if name in ("skin", "face", "bone"):
        L = nt.links.new
        def node(t, **kw):
            n = nt.nodes.new(t)
            for k, v in kw.items(): n.inputs[k].default_value = v
            return n
        def mix(a, b, fac, blend='MIX'):
            m = nt.nodes.new("ShaderNodeMix"); m.data_type = 'RGBA'; m.blend_type = blend
            if isinstance(fac, float): m.inputs["Factor"].default_value = fac
            else: L(fac, m.inputs["Factor"])
            for idx, v in ((6, a), (7, b)):
                if isinstance(v, tuple): m.inputs[idx].default_value = v
                else: L(v, m.inputs[idx])
            return m.outputs[2]
        def ramp(src, lo, hi):
            r = node("ShaderNodeMapRange", **{"From Min": lo, "From Max": hi}); L(src, r.inputs["Value"]); r.clamp = True
            return r.outputs["Result"]
        co = nt.nodes.new("ShaderNodeTexCoord").outputs["Object"]
        big = node("ShaderNodeTexNoise", Scale=4.0, Detail=6.0, Roughness=0.6); L(co, big.inputs["Vector"])
        mid = node("ShaderNodeTexNoise", Scale=22.0, Detail=8.0, Roughness=0.7); L(co, mid.inputs["Vector"])
        vor = nt.nodes.new("ShaderNodeTexVoronoi"); vor.feature = 'DISTANCE_TO_EDGE'; vor.inputs["Scale"].default_value = 9.0 if name == "skin" else 16.0
        warp = node("ShaderNodeTexNoise", Scale=6.0, Detail=3.0)
        vadd = nt.nodes.new("ShaderNodeVectorMath"); vadd.operation = 'ADD'; L(co, vadd.inputs[0]); L(warp.outputs["Color"], vadd.inputs[1])
        L(vadd.outputs[0], vor.inputs["Vector"])
        wave = nt.nodes.new("ShaderNodeTexWave"); wave.bands_direction = 'Z'
        wave.inputs["Scale"].default_value = 26.0; wave.inputs["Distortion"].default_value = 9.0; wave.inputs["Detail"].default_value = 4.0
        L(co, wave.inputs["Vector"])
        ao = nt.nodes.new("ShaderNodeAmbientOcclusion"); ao.samples = 8; ao.inputs["Distance"].default_value = 0.08
        if name == "face":
            pale, rot = a.outputs["Color"], (0.10, 0.075, 0.07, 1)
        elif name == "bone":
            pale, rot = (0.36, 0.33, 0.27, 1), (0.12, 0.10, 0.07, 1)
        else:
            pale, rot = (0.78, 0.76, 0.70, 1), (0.42, 0.39, 0.35, 1)   # corpse-white, grey rot
        c = mix(pale, rot, ramp(big.outputs["Fac"], 0.38, 0.66))                         # big rotting blotches
        c = mix(c, (0.18, 0.16, 0.15, 1), ramp(mid.outputs["Fac"], 0.55, 0.85), 'MIX')   # grime
        c = mix(c, (0.30, 0.16, 0.22, 1), ramp(vor.outputs["Distance"], 0.025, 0.0))    # purple-grey veins under white skin
        c = mix(c, ramp(wave.outputs["Fac"], 0.0, 1.0), 0.35, 'MULTIPLY')                # stretched wrinkles
        if name == "skin":                                                               # old blood / dirt pooling low
            dirt = node("ShaderNodeTexNoise", Scale=11.0, Detail=5.0); L(co, dirt.inputs["Vector"])
            c = mix(c, (0.35, 0.05, 0.04, 1), ramp(dirt.outputs["Fac"], 0.64, 0.72))
        c = mix(c, ramp(ao.outputs["AO"], 0.0, 1.0), 0.8, 'MULTIPLY')                   # baked cavity shading
        base_out = c
    nt.links.new(base_out, bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Specular IOR Level"].default_value = spec
    if sss:
        bsdf.inputs["Subsurface Weight"].default_value = sss
        bsdf.inputs["Subsurface Radius"].default_value = (0.02, 0.006, 0.004)
    if emit:
        nt.links.new(a.outputs["Color"], bsdf.inputs["Emission Color"])
        bsdf.inputs["Emission Strength"].default_value = emit
    tex = nt.nodes.new("ShaderNodeTexNoise"); tex.inputs["Scale"].default_value = 90
    tex.inputs["Detail"].default_value = 6
    bump = nt.nodes.new("ShaderNodeBump"); bump.inputs["Strength"].default_value = 0.22 if name in ("skin", "face") else 0.07
    nt.links.new(tex.outputs["Fac"], bump.inputs["Height"]); nt.links.new(bump.outputs["Normal"], bsdf.inputs["Normal"])
    return mt
MAT = {"skin": make_mat("skin", 0.45, 0.45), "face": make_mat("face", 0.6, 0.3, sss=0.05), "hair": make_mat("hair", 0.5, 0.4), "saliva": make_mat("saliva", 0.02, 1.0), "void": make_mat("void", 1.0, 0.0),
       "bone": make_mat("bone", 0.7, 0.3), "tooth": make_mat("tooth", 0.25, 0.7),
       "throat": make_mat("throat", 0.15, 0.9), "eye": make_mat("eye", 0.08, 0.9), "glint": make_mat("glint", 0.1, 0.5, emit=6), "claw": make_mat("claw", 0.3, 0.6)}
for o in ALL:
    o.data.materials.clear(); o.data.materials.append(MAT[KIND[o.name]])

# parenting for animation in renders
def empty(name, loc, parent=None):
    e = link(bpy.data.objects.new(name, None)); e.location = loc
    bpy.context.view_layer.update()
    if parent:
        e.parent = parent; e.matrix_parent_inverse = parent.matrix_world.inverted()
        e.location = loc
    bpy.context.view_layer.update()
    return e
head_rig = empty("head_rig", J["head0"][0])
jaw_rig = empty("jaw_rig", JAW_PIVOTS["jaw"], head_rig)
jawR_rig = empty("jawR_rig", JAW_PIVOTS["jawR"], head_rig)
for rig, objs in ((head_rig, HEAD_PARTS["head"]), (jaw_rig, HEAD_PARTS["jaw"]), (jawR_rig, HEAD_PARTS["jawR"])):
    for o in objs:
        mw = o.matrix_world.copy()
        o.parent = rig
        o.matrix_parent_inverse = rig.matrix_world.inverted()
        o.matrix_world = mw
bpy.context.view_layer.update()

print("[SHADOW] tris:", sum(len(o.data.polygons) for o in ALL))

# ------------------------------------------------------------------ export for the mod
def bake_atlas(objs, size=2048):
    img = bpy.data.images.new("atlas", size, size, alpha=False)
    for o in objs:
        for mt in o.data.materials:
            nt = mt.node_tree
            if "BakeTarget" not in nt.nodes:
                n = nt.nodes.new("ShaderNodeTexImage"); n.name = "BakeTarget"; n.image = img
            nt.nodes.active = nt.nodes["BakeTarget"]
    bpy.ops.object.select_all(action='DESELECT')
    for o in objs: o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.smart_project(angle_limit=math.radians(60), island_margin=0.002)
    bpy.ops.object.mode_set(mode='OBJECT')
    for o in objs:                                   # keep the top strip free for the swatches
        for d in o.data.uv_layers.active.data: d.uv = d.uv * 0.985
    scene.render.engine = 'CYCLES'; scene.cycles.device = 'CPU'; scene.cycles.samples = 16
    scene.render.bake.margin = 4
    w = bpy.data.worlds.new("bakeworld"); w.use_nodes = True
    w.node_tree.nodes["Background"].inputs["Color"].default_value = (1, 1, 1, 1)
    w.node_tree.nodes["Background"].inputs["Strength"].default_value = 1.15
    scene.world = w
    scene.cycles.samples = 40
    # albedo x soft sky light: the bump detail and the deep cavities end up painted into the texture
    bpy.ops.object.bake(type='DIFFUSE', pass_filter={'COLOR', 'DIRECT', 'INDIRECT'}, use_clear=True)
    import numpy as np
    px = np.empty(size * size * 4, dtype=np.float32); img.pixels.foreach_get(px); px = px.reshape(size, size, 4)
    for i, (k, col) in enumerate(SOLID.items()):          # swatches live in the top-left corner (v=0.998)
        x0 = int(i * 0.004 * size); px[size - 12:size, x0:x0 + int(0.004 * size), :3] = col
    img.pixels.foreach_set(px.ravel())
    img.filepath_raw = os.path.join(OUT, "shadow_atlas.png"); img.file_format = 'PNG'; img.save()
    print("[SHADOW] atlas baked")

# small/flat-coloured parts don't waste atlas space: they sample a painted swatch
SOLID = {"glint": (1.0, 0.86, 0.5), "hair": (0.025, 0.02, 0.022), "void": (0, 0, 0), "throat": (0.14, 0.01, 0.01), "eye": (0.004, 0.004, 0.004), "claw": (0.06, 0.05, 0.045)}
SWATCH = {k: (0.002 + i * 0.004, 0.998) for i, k in enumerate(SOLID)}
DECIMATE = {"bone": 0.5, "skin": 0.13, "face": 0.16, "void": 0.1, "hair": 0.3, "throat": 0.25, "eye": 0.5, "tooth": 0.6, "claw": 0.6}

def export():
    # part per face: body faces go to nearest bone segment
    segs = []
    for part, a, b in BONES:
        if a is None: continue
        segs.append((part, J[a][0], J[b][0]))
    def nearest_part(p):
        best, bp = 1e9, "chest"
        for part, a, b in segs:
            ab = b - a; t = max(0, min(1, (p - a).dot(ab) / max(ab.length_squared, 1e-9)))
            d = (a + ab * t - p).length
            if d < best: best, bp = d, part
        return bp
    bake_atlas([o for o in ALL if KIND[o.name] not in SOLID], 4096)
    global TRIS, TRIS_LOD
    TRIS = build_tris(nearest_part, 1.0)
    TRIS_LOD = build_tris(nearest_part, 0.3, skip=("hair", "tooth", "saliva", "glint", "bone"))   # far away: a much lighter model
    write_mesh(os.path.join(OUT, "shadow.mesh"), TRIS)
    write_mesh(os.path.join(OUT, "shadow_lod.mesh"), TRIS_LOD)
    hc = head_xf @ Vector((0, -0.05, 0))
    print("[SHADOW] head centre (mc coords, unscaled):", mc(hc))
    print("[SHADOW] height:", max(v.z for v in wv))
    print("[SHADOW] tris per part:", {k: len(v) // 3 for k, v in TRIS.items()})
    print("[SHADOW] lod tris:", sum(len(v) // 3 for v in TRIS_LOD.values()))

ORDER = ["hips", "chest", "neck", "head", "jaw", "jawR", "armL", "foreL", "handL", "armR", "foreR", "handR", "legL", "shinL", "legR", "shinR"]
def mc(v): return (v.x, v.z, -v.y)        # blender -> minecraft (y up, faces +z)

def build_tris(nearest_part, factor, skip=()):
    tris = {k: [] for k in PARTS}
    def add_obj(o, force=None):
        kind = KIND[o.name]
        if kind in skip and not (kind == "bone" and factor >= 1): return
        src = o
        if kind in DECIMATE or factor < 1:         # lighter copy for the game, same UVs
            cp = o.copy(); cp.data = o.data.copy(); link(cp)
            dm = cp.modifiers.new("dec", 'DECIMATE'); dm.ratio = DECIMATE.get(kind, 1.0) * factor
            apply_mods(cp); src = cp
        me = src.data; me.calc_loop_triangles()
        m = src.matrix_world; nm = m.to_3x3().inverted().transposed()
        uvl = me.uv_layers.active.data if me.uv_layers.active else None
        emis = 1 if kind == "glint" else 0
        for lt in me.loop_triangles:
            ws = [m @ me.vertices[i].co for i in lt.vertices]
            part = force or nearest_part((ws[0] + ws[1] + ws[2]) / 3)
            if part not in tris: part = "chest"
            for k, vi in enumerate(lt.vertices):
                n = (nm @ me.vertices[vi].normal).normalized()
                uv = SWATCH[kind] if kind in SOLID else uvl[lt.loops[k]].uv
                tris[part].append((ws[k], n, uv, emis))
        if src is not o: bpy.data.objects.remove(src)
    for o in [body] + extra + claws: add_obj(o)
    for o in HEAD_PARTS["head"]: add_obj(o, "head")
    for o in HEAD_PARTS["jaw"]: add_obj(o, "jaw")
    for o in HEAD_PARTS["jawR"]: add_obj(o, "jawR")
    return tris

def write_mesh(path, tris):
    order = ORDER
    piv = {k: (JAW_PIVOTS[k] if k in JAW_PIVOTS else J[PARTS[k][1]][0]) for k in order}
    with open(path, "wb") as f:
        f.write(b"SHD2"); f.write(struct.pack("<i", len(order)))
        for k in order:
            nb = k.encode(); f.write(struct.pack("<h", len(nb))); f.write(nb)
            par = PARTS[k][0]; f.write(struct.pack("<i", order.index(par) if par else -1))
            f.write(struct.pack("<3f", *mc(piv[k])))
            f.write(struct.pack("<i", len(tris[k]) // 3))
            for (p, n, c, e) in tris[k]:
                f.write(struct.pack("<3f", *mc(p)))
                nn = mc(n); f.write(struct.pack("<3b", *[max(-127, min(127, int(round(x * 127)))) for x in nn]))
                f.write(struct.pack("<2f", c[0], 1.0 - c[1]))
                f.write(struct.pack("<B", e))

# ------------------------------------------------------------------ rendering
def setup_render(w, h, samples):
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'; scene.cycles.samples = samples
    scene.cycles.use_denoising = True
    scene.render.resolution_x = w; scene.render.resolution_y = h; scene.render.resolution_percentage = 100
    scene.view_settings.view_transform = 'AgX' if 'AgX' in [i.identifier for i in scene.view_settings.bl_rna.properties['view_transform'].enum_items] else 'Filmic'
    scene.view_settings.look = 'None'
    world = bpy.data.worlds.new("w"); scene.world = world; world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.004, 0.004, 0.006, 1)
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 1.0

def light(name, kind, loc, energy, color=(1, 1, 1), size=0.1, target=None):
    ld = bpy.data.lights.new(name, kind); ld.energy = energy; ld.color = color
    if kind == 'SPOT': ld.spot_size = math.radians(60); ld.spot_blend = 0.6
    if hasattr(ld, "shadow_soft_size"): ld.shadow_soft_size = size
    lo = link(bpy.data.objects.new(name, ld)); lo.location = loc
    if target is not None:
        c = lo.constraints.new('TRACK_TO'); c.target = target; c.track_axis = 'TRACK_NEGATIVE_Z'; c.up_axis = 'UP_Y'
    return lo

def camera(loc, target, lens=35):
    cd = bpy.data.cameras.new("cam"); cd.lens = lens; cd.clip_start = 0.01
    co = link(bpy.data.objects.new("cam", cd)); co.location = loc
    c = co.constraints.new('TRACK_TO'); c.target = target; c.track_axis = 'TRACK_NEGATIVE_Z'; c.up_axis = 'UP_Y'
    scene.camera = co
    return co

def preview():
    setup_render(960, 1280, 48)
    bpy.ops.mesh.primitive_plane_add(size=30, location=(0, 0, 0))
    floor = bpy.context.active_object
    fm = bpy.data.materials.new("floor"); fm.use_nodes = True
    fm.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.03, 0.03, 0.03, 1)
    floor.data.materials.append(fm)
    tgt = empty("tgt", (0, -0.1, 1.35))
    light("key", 'AREA', (-2.5, -3.0, 3.2), 220, (0.75, 0.82, 1.0), 1.5, tgt)
    light("rim", 'AREA', (2.0, 2.5, 3.0), 450, (1.0, 0.85, 0.75), 1.0, tgt)
    light("under", 'POINT', (0, -1.3, 1.2), 25, (1.0, 0.6, 0.45), 0.2)
    shots = {"front": (0, -6.0, 1.6), "three_quarter": (-3.8, -4.6, 1.8), "side": (-6.0, -0.3, 1.5)}
    for nm, loc in shots.items():
        camera(loc, tgt, 40)
        scene.render.filepath = os.path.join(OUT, "preview_%s.png" % nm)
        bpy.ops.render.render(write_still=True)
    # face close-up
    ft = empty("ft", HEAD_C + Vector((0, -0.05, -0.04)))
    camera(HEAD_C + Vector((0.12, -0.75, -0.08)), ft, 50)
    scene.render.resolution_x = 1024; scene.render.resolution_y = 1024
    scene.render.filepath = os.path.join(OUT, "preview_face.png")
    bpy.ops.render.render(write_still=True)
    jaw_rig.rotation_euler = (math.radians(8), 0, math.radians(-80)); jawR_rig.rotation_euler = (math.radians(8), 0, math.radians(80))
    bpy.context.view_layer.update()
    scene.render.filepath = os.path.join(OUT, "preview_face_open.png")
    bpy.ops.render.render(write_still=True)
    jaw_rig.rotation_euler = (0, 0, 0); jawR_rig.rotation_euler = (0, 0, 0)

def jumpscare(frames=26):
    """dolly into the face while the jaw unhinges - rendered as frames for the in-game overlay"""
    setup_render(960, 540, 40)
    for o in list(scene.objects):
        if o.type in ('LIGHT', 'CAMERA'): bpy.data.objects.remove(o)
    face = head_xf @ Vector((0, -0.085, -0.085))
    ft = empty("ft2", face)
    cam = camera(face + Vector((0, -0.75, 0.0)), ft, 30)
    sinew_mat = bpy.data.materials.new("sinew"); sinew_mat.use_nodes = True
    sb = sinew_mat.node_tree.nodes["Principled BSDF"]
    sb.inputs["Base Color"].default_value = (0.78, 0.74, 0.66, 1); sb.inputs["Roughness"].default_value = 0.02
    sb.inputs["Transmission Weight"].default_value = 0.85; sb.inputs["Specular IOR Level"].default_value = 1.0
    sb.inputs["IOR"].default_value = 1.33
    random.seed(77)
    STR = [(random.uniform(-0.20, MOUTH_Z - 0.01), random.uniform(0.004, 0.02), random.uniform(0.0006, 0.0016),
            random.uniform(-0.01, 0.01), random.random()) for _ in range(13)]
    drops = []              # falling drool: [obj, velocity]
    spit = []               # spray flying at the camera
    strands = []
    REST = {r.name: r.matrix_world.copy() for r in (jaw_rig, jawR_rig)}
    def on(rig, p_local):
        return rig.matrix_world @ REST[rig.name].inverted() @ (head_xf @ p_local)
    def build_strands(open_amt):
        for o_ in strands: bpy.data.objects.remove(o_)
        strands.clear()
        if open_amt < 0.05: return
        for (zz, dy, thick, wob, sagr) in STR:       # the same strands every frame, stretching as it opens
            yy = (front_y(0.0, zz) or -0.08) + dy
            a_ = on(jaw_rig, Vector((-0.004, yy, zz))); b_ = on(jawR_rig, Vector((0.004, yy, zz)))
            if (a_ - b_).length > 0.32 + sagr * 0.12: continue          # stretched too far: snapped
            cd = bpy.data.curves.new("strand", 'CURVE'); cd.dimensions = '3D'
            cd.bevel_depth = thick * (1.0 - 0.5 * open_amt); cd.bevel_resolution = 2
            sp = cd.splines.new('BEZIER'); sp.bezier_points.add(1)
            for i_, pt in enumerate((a_, b_)):
                bp = sp.bezier_points[i_]; bp.co = pt
                bp.handle_left_type = bp.handle_right_type = 'AUTO'
            mid_sag = Vector((0, wob, -0.03 - 0.09 * open_amt * sagr))
            # a fat bead of drool hanging at the lowest point
            bpy.ops.mesh.primitive_uv_sphere_add(segments=12, ring_count=8, radius=thick * 2.6, location=a_.lerp(b_, 0.5) + mid_sag * 0.75)
            bead = bpy.context.active_object; bead.scale.z = 1.6; bead.data.materials.append(sinew_mat)
            for pp in bead.data.polygons: pp.use_smooth = True
            strands.append(bead)
            sp.bezier_points[0].handle_right_type = 'FREE'; sp.bezier_points[0].handle_right = a_.lerp(b_, 0.33) + mid_sag
            sp.bezier_points[1].handle_left_type = 'FREE'; sp.bezier_points[1].handle_left = a_.lerp(b_, 0.66) + mid_sag
            ob = link(bpy.data.objects.new("strand", cd)); ob.data.materials.append(sinew_mat)
            strands.append(ob)
    under = light("flash", 'SPOT', face + Vector((0.04, -0.35, -0.30)), 22, (1.0, 0.9, 0.78), 0.03, ft)
    rim = light("rimj", 'AREA', face + Vector((0.35, 0.35, 0.25)), 12, (0.55, 0.65, 1.0), 0.3, ft)
    for f in range(frames):
        t = f / (frames - 1)
        push = 1 - (1 - min(1, t / 0.4)) ** 3
        dist = 0.85 - 0.45 * push
        shake = Vector(((random.random() - 0.5) * 0.012, 0, (random.random() - 0.5) * 0.012)) * (1 if t > 0.3 else 0.2)
        cam.location = face + Vector((0, -dist, 0.01)) + shake
        jaw_open = 1 - (1 - min(1, max(0, (t - 0.05) / 0.5))) ** 2
        ang = math.radians(4 + 84 * jaw_open)
        jaw_rig.rotation_euler = (math.radians(10 * jaw_open), 0, -ang); jawR_rig.rotation_euler = (math.radians(10 * jaw_open), 0, ang)
        head_rig.rotation_euler = (0, math.sin(f * 2.3) * (0.05 if t > 0.35 else 0), math.sin(f * 1.7) * (0.06 if t > 0.35 else 0))
        under.data.energy = 22 * (0.3 if (f % 6 == 4) else 1.0)
        bpy.context.view_layer.update()
        build_strands(jaw_open)
        # drool dripping off the teeth and falling
        if f % 2 == 0 and jaw_open > 0.15:
            zz = MOUTH_Z - 0.002; xx = random.uniform(-0.05, 0.05); yy = (front_y(xx, zz) or -0.08) + 0.006
            bpy.ops.mesh.primitive_uv_sphere_add(segments=10, ring_count=6, radius=random.uniform(0.0025, 0.004), location=head_xf @ Vector((xx, yy, zz)))
            d_ = bpy.context.active_object; d_.data.materials.append(sinew_mat); d_.scale.z = 1.8
            drops.append([d_, 0.0])
        for d_ in drops:
            d_[1] += 0.004; d_[0].location.z -= d_[1]; d_[0].scale.z = 1.8 + d_[1] * 120
        # a spray of spit at the screen as it lunges
        if 9 <= f <= 13:
            for _ in range(3):
                o_ = face + Vector((random.uniform(-0.03, 0.03), -0.02, random.uniform(-0.03, 0.02)))
                bpy.ops.mesh.primitive_uv_sphere_add(segments=10, ring_count=6, radius=random.uniform(0.0006, 0.0016), location=o_)
                s_ = bpy.context.active_object; s_.data.materials.append(sinew_mat)
                v_ = (cam.location - o_).normalized() * random.uniform(0.015, 0.035) + Vector((random.uniform(-0.02, 0.02), 0, random.uniform(-0.015, 0.02)))
                spit.append([s_, v_])
        for s_ in spit:
            s_[0].location += s_[1]; s_[1].z -= 0.003; s_[0].scale = (1, 1, 1.0 + s_[1].length * 25)
        bpy.context.view_layer.update()
        scene.render.filepath = os.path.join(OUT, "js_%02d.png" % f)
        bpy.ops.render.render(write_still=True)
    jaw_rig.rotation_euler = (0, 0, 0); jawR_rig.rotation_euler = (0, 0, 0); head_rig.rotation_euler = (0, 0, 0)

def animations():
    exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "anim.py")).read(), globals())
    setup_render(240, 320, 6)
    scene.cycles.use_denoising = False
    tgt = empty("atgt", (0, -0.15, 1.2))
    camera((-3.6, -4.8, 1.9), tgt, 40)
    if "key" not in bpy.data.objects:
        light("akey", 'AREA', (-2.5, -3.0, 3.2), 260, (0.85, 0.88, 1.0), 1.5, tgt)
        light("arim", 'AREA', (2.0, 2.5, 3.0), 450, (1.0, 0.85, 0.75), 1.0, tgt)
    bake_animations(TRIS_LOD)

if WHAT in ("export", "all", "anim"): export()
if WHAT in ("preview", "all"): preview()
if WHAT in ("anim", "all"): animations()
if WHAT in ("jumpscare", "all"): jumpscare()
print("[SHADOW] done")
