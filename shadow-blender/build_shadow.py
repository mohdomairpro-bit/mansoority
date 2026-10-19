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
j("pelvis", 0, 0.02, 1.30, 0.12)
j("spine1", 0, 0.05, 1.50, 0.085)          # wasp-thin waist
j("spine2", 0, 0.00, 1.78, 0.17)
j("spine3", 0, -0.14, 2.02, 0.20)          # big hunched upper back (top-heavy)
j("neck0", 0, -0.30, 2.12, 0.075)
j("neck1", 0, -0.46, 2.12, 0.06)
j("head0", 0, -0.56, 2.08, 0.055)
for s, sx in (("L", -1), ("R", 1)):
    j("shoulder" + s, 0.30 * sx, -0.10, 2.02, 0.10)
    j("elbow" + s, 0.46 * sx, -0.05, 1.50, 0.05)
    j("joint2" + s, 0.50 * sx, -0.22, 1.02, 0.042)   # the extra joint
    j("wrist" + s, 0.48 * sx, -0.38, 0.55, 0.034)
    j("hand" + s, 0.48 * sx, -0.46, 0.40, 0.05)
    j("hip" + s, 0.12 * sx, 0.04, 1.22, 0.085)
    j("knee" + s, 0.15 * sx, 0.24, 0.72, 0.055)       # bends backwards
    j("ankle" + s, 0.15 * sx, -0.02, 0.18, 0.036)
    j("toe" + s, 0.15 * sx, -0.24, 0.03, 0.03)

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
    "head": ("neck", "head0"), "jaw": ("head", None),
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
        FINGER_TIPS.append(p.copy())
body = skin_object("body", verts, edges, radii, root=idx["pelvis"], subdiv=2)
apply_mods(body)

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
for o in ribs():
    apply_mods(o); extra.append(o)

# ------------------------------------------------------------------ head: almost human, stretched, mouth split ear to ear
HEAD_C = J["head0"][0] + Vector((0, -0.10, 0.06))
SOCKETS = [(Vector((-0.040, -0.088, 0.028)), 0.028), (Vector((0.042, -0.086, 0.022)), 0.032)]   # uneven
def g(q, c, r):
    return math.exp(-((q - c).length / r) ** 2)
def make_head():
    bpy.ops.mesh.primitive_uv_sphere_add(segments=96, ring_count=64, radius=1.0, location=(0, 0, 0))
    hd = bpy.context.active_object; hd.name = "skull"
    for v in hd.data.vertices:
        p = v.co.copy()
        z = p.z * 0.17
        w = 0.083 if z > 0 else 0.083 * (1 - 0.45 * (-z / 0.17) ** 1.3)     # long, narrowing to a pointed chin
        if z < -0.09: z = -0.09 + (z + 0.09) * 1.6                           # stretched, sagging chin
        d = 0.115 if z > 0 else 0.115 * (1 - 0.25 * (-z / 0.17))
        q = Vector((p.x * w, p.y * d, z))
        if z > 0.06: q.x *= 1 + 0.12 * (z - 0.06) / 0.11                   # wide cranium
        q += Vector((0, -0.016, 0)) * g(q, Vector((0, -0.10, 0.058)), 0.045) * (1 if q.y < 0 else 0)   # brow ridge
        for c, r in SOCKETS:
            q += Vector((0, 0.05, 0)) * g(q, c, r)                            # deep sockets
        for sx in (-1, 1):
            q += Vector((0.010 * sx, -0.010, 0)) * g(q, Vector((0.060 * sx, -0.070, -0.010)), 0.025)   # cheekbones
            q += Vector((-0.020 * sx, 0.012, 0)) * g(q, Vector((0.055 * sx, -0.055, -0.060)), 0.035)   # sucked-in cheeks
            q += Vector((-0.012 * sx, 0, 0)) * g(q, Vector((0.085 * sx, -0.030, 0.040)), 0.03)         # hollow temples
        q += Vector((0, 0.03, 0)) * g(q, Vector((0, -0.105, -0.020)), 0.016)                           # no nose
        q += q.normalized() * (0.002 * noise.noise(q * 60) + 0.004 * max(0, noise.noise(q * 9 + Vector((3, 1, 2)))))
        v.co = q
    for f in hd.data.polygons: f.use_smooth = True
    return hd
skull = make_head()
CUTTERS = []
bpy.ops.mesh.primitive_uv_sphere_add(segments=48, ring_count=24, radius=1, location=(0, -0.09, -0.068))
cutter = bpy.context.active_object; CUTTERS.append(cutter)
cutter.scale = (0.105, 0.075, 0.016)                                        # splits the cheeks open
b = skull.modifiers.new("mouth", 'BOOLEAN'); b.object = cutter; b.operation = 'DIFFERENCE'
for sx in (-1, 1):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=24, ring_count=12, radius=1, location=(0.072 * sx, -0.075, -0.058), rotation=(0, math.radians(-30 * sx), 0))
    cc = bpy.context.active_object; CUTTERS.append(cc); cc.scale = (0.032, 0.05, 0.007)
    bc = skull.modifiers.new("corner", 'BOOLEAN'); bc.object = cc; bc.operation = 'DIFFERENCE'
for c, r in SOCKETS:
    bpy.ops.mesh.primitive_uv_sphere_add(segments=24, ring_count=16, radius=r * 0.62, location=c + Vector((0, 0.004, 0)))
    sc = bpy.context.active_object; CUTTERS.append(sc)
    bb = skull.modifiers.new("socket", 'BOOLEAN'); bb.object = sc; bb.operation = 'DIFFERENCE'
bpy.ops.mesh.primitive_cone_add(vertices=3, radius1=0.013, radius2=0.002, depth=0.06, location=(0, -0.095, -0.018), rotation=(math.radians(-90), 0, math.radians(180)))
nc = bpy.context.active_object; CUTTERS.append(nc); nc.scale = (1.0, 1.0, 1.0)
bn = skull.modifiers.new("nose", 'BOOLEAN'); bn.object = nc; bn.operation = 'DIFFERENCE'
apply_mods(skull)
for o in CUTTERS: bpy.data.objects.remove(o)

JAW_PIVOT = Vector((0, 0.01, -0.06))
bm = bmesh.new(); bm.from_mesh(skull.data)
bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:], plane_co=(0, 0, -0.068), plane_no=(0, 0, 1))
jaw_faces = [f for f in bm.faces if f.calc_center_median().z < -0.068]
jaw_bm = bmesh.new(); vmap = {}
for f in jaw_faces:
    nv = []
    for v in f.verts:
        if v not in vmap: vmap[v] = jaw_bm.verts.new(v.co)
        nv.append(vmap[v])
    try: jaw_bm.faces.new(nv)
    except ValueError: pass
bmesh.ops.delete(bm, geom=jaw_faces, context='FACES')
bm.to_mesh(skull.data); bm.free()
jme = bpy.data.meshes.new("jaw"); jaw_bm.to_mesh(jme); jaw_bm.free()
jaw = link(bpy.data.objects.new("jaw", jme))
for ob in (skull, jaw):
    sol = ob.modifiers.new("solid", 'SOLIDIFY'); sol.thickness = 0.005
    apply_mods(ob)
    for p in ob.data.polygons: p.use_smooth = True
bpy.ops.mesh.primitive_uv_sphere_add(segments=32, ring_count=16, radius=1, location=(0, 0.005, -0.074))
throat = bpy.context.active_object; throat.name = "throat"; throat.scale = (0.05, 0.04, 0.02)
apply_mods(throat)
bpy.ops.mesh.primitive_uv_sphere_add(segments=32, ring_count=16, radius=1, location=(0, 0.0, 0.01))
void = bpy.context.active_object; void.name = "void"; void.scale = (0.075, 0.085, 0.13)   # black inside the skull
apply_mods(void)

def tooth(loc, h, w, up):
    bpy.ops.mesh.primitive_cone_add(vertices=7, radius1=w, radius2=w * random.uniform(0.15, 0.5), depth=h, location=(0, 0, 0))
    t = bpy.context.active_object
    tilt = Matrix.Rotation(random.uniform(-0.35, 0.35), 3, 'X') @ Matrix.Rotation(random.uniform(-0.3, 0.3), 3, 'Y')
    for v in t.data.vertices:
        v.co.y *= 0.75
        if not up: v.co.z = -v.co.z
        v.co = tilt @ v.co + Vector(loc)
    for p in t.data.polygons: p.use_smooth = True
    return t
upper, lower = [], []
NT = 26
for i in range(NT):
    a = math.radians(-80 + i * (160 / (NT - 1)))
    rx, ry = 0.072, 0.058
    x = math.sin(a) * rx; y = -0.04 - math.cos(a) * ry
    h = random.uniform(0.010, 0.020) * (1.3 if abs(i - (NT - 1) / 2) < 3 else 1)
    upper.append(tooth((x, y, -0.056 - h / 2), h, random.uniform(0.0035, 0.0055), False))
    if i % 7 != 3:
        h2 = random.uniform(0.009, 0.017)
        lower.append(tooth((x * 0.96, y * 0.97, -0.080 + h2 / 2), h2, random.uniform(0.003, 0.005), True))
    if i % 2 == 0 and abs(i - (NT - 1) / 2) < 9:      # nested inner rows
        upper.append(tooth((x * 0.8, y * 0.8 + 0.008, -0.058 - h * 0.35), h * 0.7, 0.003, False))
        lower.append(tooth((x * 0.8, y * 0.8 + 0.008, -0.078 + h * 0.3), h * 0.6, 0.0028, True))

for i in range(14):
    a = math.radians(-70 + i * (140 / 13))
    x = math.sin(a) * 0.046; y = -0.012 - math.cos(a) * 0.036
    upper.append(tooth((x, y, -0.060), random.uniform(0.008, 0.013), 0.0026, False))
    lower.append(tooth((x, y, -0.084), random.uniform(0.007, 0.011), 0.0024, True))
drips = []
for i in range(16):
    a = math.radians(random.uniform(-70, 70))
    x = math.sin(a) * 0.07; y = -0.042 - math.cos(a) * 0.056
    L = random.uniform(0.008, 0.03)
    bpy.ops.mesh.primitive_cone_add(vertices=8, radius1=0.0011, radius2=0.0024, depth=L, location=(x, y, -0.07 - L / 2))
    d0 = bpy.context.active_object; d0.name = "drip"
    bpy.ops.mesh.primitive_uv_sphere_add(segments=8, ring_count=6, radius=0.0028, location=(x, y, -0.07 - L))
    d1 = bpy.context.active_object; d1.name = "drip"
    for o_ in (d0, d1):
        for p_ in o_.data.polygons: p_.use_smooth = True
    drips += [d0, d1]

def growth(base, dirs, radii):
    vs = [base]; rs = [radii[0]]; es = []
    p = base.copy()
    for k, d in enumerate(dirs):
        p = p + d; vs.append(p.copy()); rs.append(radii[min(k + 1, len(radii) - 1)]); es.append((k, k + 1))
    return skin_object("growth", vs, es, rs, 0, 2)
gL = growth(Vector((-0.06, -0.02, 0.10)), [Vector((-0.05, 0.05, 0.06)), Vector((-0.07, 0.10, 0.05)), Vector((-0.05, 0.12, 0.09)), Vector((-0.01, 0.10, 0.12)), Vector((0.02, 0.06, 0.10))],
            [0.016, 0.013, 0.010, 0.008, 0.005, 0.002])
gL2 = growth(Vector((-0.18, 0.17, 0.21)), [Vector((-0.08, 0.02, 0.07)), Vector((-0.05, -0.02, 0.06))], [0.007, 0.005, 0.002])
gL3 = growth(Vector((-0.11, 0.07, 0.16)), [Vector((-0.07, -0.03, 0.05)), Vector((-0.03, -0.04, 0.03))], [0.008, 0.005, 0.002])
gR = growth(Vector((0.06, -0.02, 0.10)), [Vector((0.06, 0.06, 0.05)), Vector((0.05, 0.07, 0.02))], [0.017, 0.015, 0.014])
gR2 = growth(Vector((0.04, 0.03, 0.14)), [Vector((0.02, 0.05, 0.06)), Vector((0.0, 0.05, 0.04))], [0.008, 0.006, 0.002])
for g_ in (gL, gL2, gL3, gR, gR2): apply_mods(g_)
hair = []
random.seed(11)
for k in range(110):
    th = random.uniform(-2.6, 2.6); ph = random.uniform(0.15, 1.35)
    root = Vector((math.sin(th) * math.sin(ph) * 0.084, math.cos(th) * math.sin(ph) * 0.11, math.cos(ph) * 0.16))
    if root.y < -0.05 and abs(root.x) < 0.055 and root.z < 0.10: continue     # keep the face uncovered... mostly
    L = random.uniform(0.25, 0.6)
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
eyes = []
EYE_R = [0.0105, 0.0118]                          # uneven
for (c, r), er in zip(SOCKETS, EYE_R):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=32, ring_count=24, radius=er, location=c + Vector((0, 0.006, 0)))
    e_ = bpy.context.active_object; e_.name = "eye"
    for p_ in e_.data.polygons: p_.use_smooth = True
    eyes.append(e_)
glints = []
for c, r in SOCKETS:
    bpy.ops.mesh.primitive_uv_sphere_add(segments=12, ring_count=8, radius=0.0019, location=c + Vector((0.0025, -0.0035, 0.002)))
    glints.append(bpy.context.active_object)

for g_ in (gL, gL2, gL3, gR, gR2): bpy.data.objects.remove(g_)
HEAD_PARTS = {"head": [skull, void] + hair + eyes + upper + glints + drips, "jaw": [jaw] + lower}
# place head on the neck
head_xf = Matrix.Translation(HEAD_C) @ Matrix.Rotation(math.radians(-6), 4, 'X')
head_inv = head_xf.inverted()
for objs in HEAD_PARTS.values():
    for o in objs: o.matrix_world = head_xf @ o.matrix_world
throat.matrix_world = head_xf @ throat.matrix_world
JAW_PIVOT_W = head_xf @ JAW_PIVOT
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
    if kind == "glint":  return Vector((1.0, 0.95, 0.85))
    return Vector((0.5, 0.5, 0.5))

KIND = {}
for o in [body] + extra: KIND[o.name] = "skin"
KIND[skull.name] = "face"; KIND[jaw.name] = "face"; KIND[throat.name] = "throat"; KIND[void.name] = "void"
for o in hair: KIND[o.name] = "hair"
for o in drips: KIND[o.name] = "saliva"
for o in upper + lower: KIND[o.name] = "tooth"
for o in glints: KIND[o.name] = "glint"
for o in eyes: KIND[o.name] = "eye"

ALL = [body] + extra + [skull, void, jaw, throat] + hair + eyes + upper + lower + glints + drips
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
        occ = 1.0 if kind in ("glint",) else (0.25 + 0.75 * ao(p, n))
        if kind == "face":
            lp = head_inv @ p
            for sc_, sr_ in SOCKETS:
                c = c * (1 - 0.95 * g(lp, sc_ + Vector((0, 0.02, 0)), sr_ * 1.1))      # pitch-black sockets
            c = c * (1 - 0.6 * g(lp, Vector((0, -0.09, -0.068)), 0.05))               # filth around the mouth
            c = c * (1 - 0.7 * g(lp, Vector((0, -0.105, -0.02)), 0.02))               # nose cavity
            c = c * (0.75 + 0.25 * max(0, min(1, (lp.z + 0.17) / 0.3)))              # darker toward the chin
            # blood running from the eyes and the corners of the mouth
            for bx, bz, wd in ((-0.040, 0.010, 0.0045), (-0.033, 0.012, 0.003), (0.044, 0.004, 0.005), (0.050, 0.006, 0.0028),
                               (-0.085, -0.072, 0.006), (0.088, -0.074, 0.007), (-0.02, -0.09, 0.004), (0.015, -0.095, 0.005)):
                if lp.z < bz and lp.y < 0:
                    run = bz - lp.z
                    wob = bx + 0.004 * math.sin(lp.z * 90 + bx * 50)
                    wdt = wd * (1 + run * 6)
                    k = max(0, 1 - abs(lp.x - wob) / wdt) * max(0, 1 - run / 0.16)
                    if k > 0: c = c.lerp(Vector((0.16, 0.005, 0.008)), min(1, k * 1.6))
            # red gums along the split mouth
            gum = max(0, 1 - abs(lp.z + 0.066) / 0.012) * (1 if lp.y < -0.02 else 0)
            c = c.lerp(Vector((0.30, 0.02, 0.03)), gum * 0.85)
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
            pale, rot = (0.20, 0.165, 0.15, 1), (0.045, 0.03, 0.032, 1)
        c = mix(pale, rot, ramp(big.outputs["Fac"], 0.38, 0.66))                         # big rotting blotches
        c = mix(c, (0.0, 0.0, 0.0, 1), ramp(mid.outputs["Fac"], 0.5, 0.8), 'MIX')        # dark pores / grime
        c = mix(c, (0.09, 0.01, 0.02, 1), ramp(vor.outputs["Distance"], 0.03, 0.0))      # dark red veins
        c = mix(c, ramp(wave.outputs["Fac"], 0.0, 1.0), 0.35, 'MULTIPLY')                # stretched wrinkles
        if name == "skin":                                                               # old blood / dirt pooling low
            dirt = node("ShaderNodeTexNoise", Scale=11.0, Detail=5.0); L(co, dirt.inputs["Vector"])
            c = mix(c, (0.13, 0.02, 0.015, 1), ramp(dirt.outputs["Fac"], 0.62, 0.72))
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
    bump = nt.nodes.new("ShaderNodeBump"); bump.inputs["Strength"].default_value = 0.07
    nt.links.new(tex.outputs["Fac"], bump.inputs["Height"]); nt.links.new(bump.outputs["Normal"], bsdf.inputs["Normal"])
    return mt
MAT = {"skin": make_mat("skin", 0.45, 0.45), "face": make_mat("face", 0.6, 0.3, sss=0.05), "hair": make_mat("hair", 0.5, 0.4), "saliva": make_mat("saliva", 0.02, 1.0), "void": make_mat("void", 1.0, 0.0),
       "bone": make_mat("bone", 0.7, 0.3), "tooth": make_mat("tooth", 0.25, 0.7),
       "throat": make_mat("throat", 0.15, 0.9), "eye": make_mat("eye", 0.08, 0.9), "glint": make_mat("glint", 0.1, 0.5, emit=12)}
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
jaw_rig = empty("jaw_rig", JAW_PIVOT_W, head_rig)
for rig, objs in ((head_rig, HEAD_PARTS["head"]), (jaw_rig, HEAD_PARTS["jaw"])):
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
    bpy.ops.object.bake(type='DIFFUSE', pass_filter={'COLOR'}, use_clear=True)
    import numpy as np
    px = np.empty(size * size * 4, dtype=np.float32); img.pixels.foreach_get(px); px = px.reshape(size, size, 4)
    for i, (k, col) in enumerate(SOLID.items()):          # swatches live in the top-left corner (v=0.998)
        x0 = int(i * 0.004 * size); px[size - 12:size, x0:x0 + int(0.004 * size), :3] = col
    img.pixels.foreach_set(px.ravel())
    img.filepath_raw = os.path.join(OUT, "shadow_atlas.png"); img.file_format = 'PNG'; img.save()
    print("[SHADOW] atlas baked")

# small/flat-coloured parts don't waste atlas space: they sample a painted swatch
SOLID = {"glint": (1, 1, 1), "hair": (0.025, 0.02, 0.022), "void": (0, 0, 0), "throat": (0.14, 0.01, 0.01), "eye": (0.004, 0.004, 0.004)}
SWATCH = {k: (0.002 + i * 0.004, 0.998) for i, k in enumerate(SOLID)}
DECIMATE = {"skin": 0.45, "face": 0.22, "void": 0.15, "hair": 0.5, "throat": 0.3, "eye": 0.5}

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
    tris = {k: [] for k in PARTS}
    def add_obj(o, force=None):
        kind = KIND[o.name]
        src = o
        if kind in DECIMATE:                       # lighter copy for the game, same UVs
            cp = o.copy(); cp.data = o.data.copy(); link(cp)
            dm = cp.modifiers.new("dec", 'DECIMATE'); dm.ratio = DECIMATE[kind]
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
    for o in [body] + extra: add_obj(o)
    for o in HEAD_PARTS["head"]: add_obj(o, "head")
    for o in HEAD_PARTS["jaw"]: add_obj(o, "jaw")
    order = ["hips", "chest", "neck", "head", "jaw", "armL", "foreL", "handL", "armR", "foreR", "handR", "legL", "shinL", "legR", "shinR"]
    def mc(v): return (v.x, v.z, -v.y)        # blender -> minecraft (y up, faces +z)
    piv = {k: (JAW_PIVOT_W if k == "jaw" else J[PARTS[k][1]][0]) for k in order}
    with open(os.path.join(OUT, "shadow.mesh"), "wb") as f:
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
    hc = head_xf @ Vector((0, -0.05, 0))
    print("[SHADOW] head centre (mc coords, unscaled):", mc(hc))
    print("[SHADOW] height:", max(v.z for v in wv))
    print("[SHADOW] tris per part:", {k: len(v) // 3 for k, v in tris.items()})

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

def jumpscare(frames=26):
    """dolly into the face while the jaw unhinges - rendered as frames for the in-game overlay"""
    setup_render(960, 540, 40)
    for o in list(scene.objects):
        if o.type in ('LIGHT', 'CAMERA'): bpy.data.objects.remove(o)
    face = head_xf @ Vector((0, -0.09, -0.045))
    ft = empty("ft2", face)
    cam = camera(face + Vector((0, -0.75, 0.0)), ft, 30)
    sinew_mat = bpy.data.materials.new("sinew"); sinew_mat.use_nodes = True
    sb = sinew_mat.node_tree.nodes["Principled BSDF"]
    sb.inputs["Base Color"].default_value = (0.55, 0.52, 0.45, 1); sb.inputs["Roughness"].default_value = 0.03
    sb.inputs["Transmission Weight"].default_value = 0.55; sb.inputs["Specular IOR Level"].default_value = 1.0
    strands = []
    def build_strands(open_amt):
        for o_ in strands: bpy.data.objects.remove(o_)
        strands.clear()
        if open_amt < 0.05: return
        for sx in (-1, 0, 1):
            for k in range(5 if sx else 3):
                x = sx * (0.030 + k * 0.012) if sx else random.uniform(-0.02, 0.02)
                up = head_rig.matrix_world @ head_rig.matrix_parent_inverse.inverted() @ Vector((0, 0, 0))
                a_ = head_xf @ Vector((x, -0.05 + abs(x) * 0.25, -0.060))
                lo = jaw_rig.matrix_world @ jaw_rig.matrix_parent_inverse @ Vector((0, 0, 0))
                b_local = Vector((x * 0.97, -0.05 + abs(x) * 0.25, -0.078))
                b_ = jaw.matrix_world @ (jaw.matrix_world.inverted() @ (head_xf @ b_local))
                # follow the jaw: transform the lower point by the jaw rig's current rotation
                piv = jaw_rig.matrix_world.translation
                rot = jaw_rig.matrix_world.to_3x3() @ head_rig.matrix_world.to_3x3().inverted()
                b_ = piv + rot @ ((head_xf @ b_local) - piv)
                cd = bpy.data.curves.new("strand", 'CURVE'); cd.dimensions = '3D'
                cd.bevel_depth = random.uniform(0.0004, 0.0011); cd.bevel_resolution = 2
                sp = cd.splines.new('BEZIER'); sp.bezier_points.add(1)
                for i_, pt in enumerate((a_, b_)):
                    bp = sp.bezier_points[i_]; bp.co = pt
                    bp.handle_left_type = bp.handle_right_type = 'AUTO'
                mid_sag = Vector((random.uniform(-0.003, 0.003), -0.006, -0.012 - 0.03 * open_amt * random.random()))
                sp.bezier_points[0].handle_right_type = 'FREE'; sp.bezier_points[0].handle_right = a_.lerp(b_, 0.33) + mid_sag
                sp.bezier_points[1].handle_left_type = 'FREE'; sp.bezier_points[1].handle_left = a_.lerp(b_, 0.66) + mid_sag
                ob = link(bpy.data.objects.new("strand", cd)); ob.data.materials.append(sinew_mat)
                strands.append(ob)
    under = light("flash", 'SPOT', face + Vector((0.04, -0.35, -0.30)), 22, (1.0, 0.9, 0.78), 0.03, ft)
    rim = light("rimj", 'AREA', face + Vector((0.35, 0.35, 0.25)), 12, (0.55, 0.65, 1.0), 0.3, ft)
    for f in range(frames):
        t = f / (frames - 1)
        push = 1 - (1 - min(1, t / 0.4)) ** 3
        dist = 0.75 - 0.40 * push
        shake = Vector(((random.random() - 0.5) * 0.012, 0, (random.random() - 0.5) * 0.012)) * (1 if t > 0.3 else 0.2)
        cam.location = face + Vector((0, -dist, 0.01)) + shake
        jaw_open = 1 - (1 - min(1, max(0, (t - 0.05) / 0.5))) ** 2
        jaw_rig.rotation_euler = (math.radians(8 + 92 * jaw_open), 0, 0)
        head_rig.rotation_euler = (0, math.sin(f * 2.3) * (0.05 if t > 0.35 else 0), math.sin(f * 1.7) * (0.06 if t > 0.35 else 0))
        under.data.energy = 22 * (0.3 if (f % 6 == 4) else 1.0)
        bpy.context.view_layer.update()
        build_strands(jaw_open)
        bpy.context.view_layer.update()
        scene.render.filepath = os.path.join(OUT, "js_%02d.png" % f)
        bpy.ops.render.render(write_still=True)
    jaw_rig.rotation_euler = (0, 0, 0); head_rig.rotation_euler = (0, 0, 0)

if WHAT in ("export", "all"): export()
if WHAT in ("preview", "all"): preview()
if WHAT in ("jumpscare", "all"): jumpscare()
print("[SHADOW] done")
