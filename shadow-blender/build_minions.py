"""
Builds every smaller model in the mod as rigid SHD2 meshes (each triangle belongs to one part):
  pale_biter / pale_spitter (8 legs, 4 tentacles, 4 eyes), hollow_husk, scavenger,
  shadow_blade, pale_ward (shield), and the Shade armour pieces.
Colour comes from one shared palette texture (minion_palette.png): u picks the material, v is how lit that spot is
(cavities and undersides darker, a little noise). No baking, so it builds in seconds.

  blender -b -P build_minions.py -- <out dir>
"""
import bpy, bmesh, math, os, random, struct, sys
from mathutils import Vector, Matrix, noise

OUT = sys.argv[sys.argv.index("--") + 1] if "--" in sys.argv else "/tmp/minions"
os.makedirs(OUT, exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
random.seed(11)

# materials (columns of the palette)
BONE, JOINT, CHITIN, PURPLE, FLESH, EYE, TOOTH, HUSK, BILE, METAL, EDGE, VIOLET, VOID, ASH, BLOOD, SILK = range(16)
GLOW_SHADE = {EYE: 0.28, BILE: 0.55, VIOLET: 0.6, EDGE: 0.62}      # how bright each glowing material sits on its strip


def link(o):
    scene.collection.objects.link(o)
    return o


def apply_mods(o):
    dg = bpy.context.evaluated_depsgraph_get()
    me = bpy.data.meshes.new_from_object(o.evaluated_get(dg))
    o.modifiers.clear(); old = o.data; o.data = me; bpy.data.meshes.remove(old)
    return o


def tube(pts, radii, sub=1, flat=1.0):
    """a smooth limb through the points (skin modifier), radius per point"""
    me = bpy.data.meshes.new("t")
    me.from_pydata([tuple(p) for p in pts], [(i, i + 1) for i in range(len(pts) - 1)], [])
    o = link(bpy.data.objects.new("t", me))
    sk = o.modifiers.new("s", 'SKIN'); sk.use_smooth_shade = True
    for i, r in enumerate(radii): me.skin_vertices[0].data[i].radius = (r, r * flat)
    me.skin_vertices[0].data[0].use_root = True
    if sub: s = o.modifiers.new("d", 'SUBSURF'); s.levels = sub
    apply_mods(o)
    for p in o.data.polygons: p.use_smooth = True
    return o


def blob(c, scale, seg=20, rings=12, bumpy=0.0, freq=6.0):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=seg, ring_count=rings, radius=1, location=c)
    o = bpy.context.active_object; o.scale = scale; apply_mods(o)
    if bumpy:
        for v in o.data.vertices: v.co += v.normal * bumpy * noise.noise(v.co * freq)
    for p in o.data.polygons: p.use_smooth = True
    return o


def cone(base, tip, r, seg=8, r2=0.0):
    d = tip - base
    bpy.ops.mesh.primitive_cone_add(vertices=seg, radius1=r, radius2=r2, depth=d.length, location=base + d * 0.5)
    o = bpy.context.active_object
    o.rotation_mode = 'QUATERNION'; o.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(d.normalized())
    apply_mods(o)
    return o


def hook(base, direction, length, r, bend=0.35, segs=5, side=Vector((1, 0, 0))):
    """a curved claw/spike: a tapering tube bending toward 'side'"""
    pts, rad = [], []
    d = direction.normalized()
    for i in range(segs + 1):
        t = i / segs
        p = base + d * (length * t) + side.normalized() * (bend * length * t * t)
        pts.append(p); rad.append(max(0.002, r * (1 - t) ** 0.8))
    return tube(pts, rad, sub=1)


def box(lo, hi):
    c = (lo + hi) * 0.5
    bpy.ops.mesh.primitive_cube_add(size=1, location=c)
    o = bpy.context.active_object; o.scale = hi - lo; apply_mods(o)
    return o


# ------------------------------------------------------------------ export
def mc(v): return Vector((v.x, v.z, -v.y))                   # creature space: blender -> minecraft (y up, faces +z)
def part_space(v): return Vector((-v.x, -v.z, v.y))          # armour: humanoid model-part space (y down, faces -z)


def shade_values(objs):
    """per vertex: lit 0..1 from local convexity, a little noise, and how far down it is"""
    out = {}
    for o in objs:
        me = o.data
        n = len(me.vertices)
        nb = [[] for _ in range(n)]
        for e in me.edges:
            a, b = e.vertices; nb[a].append(b); nb[b].append(a)
        vals = []
        for v in me.vertices:
            if nb[v.index]:
                avg = sum((me.vertices[j].co for j in nb[v.index]), Vector()) / len(nb[v.index])
                conc = (avg - v.co).dot(v.normal)            # >0: sits in a dent
                el = sum((me.vertices[j].co - v.co).length for j in nb[v.index]) / len(nb[v.index]) + 1e-6
                c = max(-1.0, min(1.0, conc / el * 2.0))
            else: c = 0.0
            nz = noise.noise(v.co * 23.0) * 0.5 + noise.noise(v.co * 71.0) * 0.25
            blot = max(0.0, noise.noise(v.co * 7.0 + Vector((3, 1, 2))))
            up = v.normal.z
            vals.append(max(0.04, min(0.97, 0.52 - c * 0.7 + nz * 0.2 + up * 0.18 - blot * 0.25)))
        out[o.name] = vals
    return out


def write_rigid(name, items, parts, convert=mc, budget=3000):
    """items: (obj, part, material, glow). parts: ordered {name: (parent or None, pivot blender Vector)}.
    everything is thinned to about 'budget' triangles in total (big smooth surfaces lose the most)"""
    objs = [i[0] for i in items]
    total = sum(len(o.data.polygons) * 2 for o in objs)
    ratio = min(1.0, budget / max(1, total))
    if ratio < 1.0:
        for o in objs:
            if len(o.data.polygons) < 40: continue
            dm = o.modifiers.new("dec", 'DECIMATE'); dm.ratio = max(ratio, 0.08); dm.use_collapse_triangulate = True
            apply_mods(o)
    sh = shade_values(objs)
    tris = {k: [] for k in parts}
    for o, part, mat, glow in items:
        me = o.data; me.calc_loop_triangles()
        m = o.matrix_world; nm = m.to_3x3().inverted().transposed()
        mirror = convert is part_space                 # that change of axes is a reflection: keep the faces outward
        for lt in me.loop_triangles:
            for vi in (reversed(lt.vertices) if mirror else lt.vertices):
                v = me.vertices[vi]
                p = convert(m @ v.co); n = convert((nm @ v.normal).normalized())
                s = sh[o.name][vi] if not glow else GLOW_SHADE.get(mat, 0.7) + 0.1 * sh[o.name][vi]
                u = (mat * 16 + 8) / 256.0
                vv = (1.0 - s) * 62.0 / 64.0 + 1.0 / 64.0
                tris[part].append((p, n, (u, vv), 1 if glow else 0))
    order = list(parts.keys())
    path = os.path.join(OUT, name + ".mesh")
    total = 0
    with open(path, "wb") as f:
        f.write(b"SHD2"); f.write(struct.pack("<i", len(order)))
        for k in order:
            par, piv = parts[k]
            nb_ = k.encode(); f.write(struct.pack("<h", len(nb_))); f.write(nb_)
            f.write(struct.pack("<i", order.index(par) if par else -1))
            f.write(struct.pack("<3f", *convert(piv)))
            f.write(struct.pack("<i", len(tris[k]) // 3)); total += len(tris[k]) // 3
            for (p, n, c, e) in tris[k]:
                f.write(struct.pack("<3f", *p))
                f.write(struct.pack("<3b", *[max(-127, min(127, int(round(x * 127)))) for x in n]))
                f.write(struct.pack("<2f", *c))
                f.write(struct.pack("<B", e))
    print("%-14s %5d tris, %d parts" % (name, total, len(order)), flush=True)
    for o in objs: bpy.data.objects.remove(o, do_unlink=True)


# ================================================================== the pale spiders
def spider(name, spitter):
    items, parts = [], {}
    S = 0.9 if spitter else 1.0
    body_c = Vector((0, -0.02, 0.52)) * S
    parts["body"] = (None, body_c)
    # cephalothorax: a pale bony shell, ridged, with spines down the back
    ceph = blob(body_c, Vector((0.25, 0.30, 0.17)) * S, 28, 16, 0.012, 9)
    for v in ceph.data.vertices:
        q = v.co - body_c
        v.co += v.normal * 0.010 * max(0.0, math.sin(q.y / S * 38)) * (1 if q.z > 0 else 0)     # plated ridges
    items.append((ceph, "body", BONE, False))
    for i in range(5):
        y = body_c.y + (-0.16 + i * 0.08) * S
        items.append((cone(Vector((0, y, body_c.z + 0.15 * S * math.cos((i - 2) * 0.35))), Vector((0, y + 0.05, body_c.z + (0.27 - abs(i - 2) * 0.03) * S)), 0.022 * S), "body", JOINT, False))
    # head: low and forward, four eyes (two big, two small) glowing red
    head_c = body_c + Vector((0, -0.31, -0.02)) * S
    parts["head"] = ("body", body_c + Vector((0, -0.22, 0)) * S)
    head = blob(head_c, Vector((0.17, 0.15, 0.12)) * S, 24, 14, 0.008, 12)
    items.append((head, "head", BONE, False))
    for ex, ez, r in ((-0.065, 0.045, 0.034), (0.065, 0.045, 0.034), (-0.11, 0.0, 0.020), (0.11, 0.0, 0.020)):
        e = blob(head_c + Vector((ex, -0.12, ez)) * S, Vector((r, r * 0.8, r)) * S, 12, 8)
        items.append((e, "head", EYE, True))
        rim = blob(head_c + Vector((ex, -0.105, ez)) * S, Vector((r * 1.35, r * 0.7, r * 1.35)) * S, 12, 8)
        items.append((rim, "head", VOID, False))
    # fangs: hooked, black-tipped, they open sideways
    for side, nm in ((-1, "fangL"), (1, "fangR")):
        root = head_c + Vector((0.05 * side, -0.12, -0.06)) * S
        parts[nm] = ("head", root)
        f1 = hook(root, Vector((0.15 * side, -0.35, -1)), 0.19 * S, 0.028 * S, 0.5, 5, Vector((-side, -0.6, 0)))
        items.append((f1, nm, TOOTH, False))
        items.append((cone(root + Vector((0.03 * side, -0.07, -0.18)) * S, root + Vector((0.01 * side, -0.11, -0.25)) * S, 0.011 * S), nm, CHITIN, False))
    # tentacles: four, from under the mouth, pale with purple ends (3 segments each)
    for k in range(4):
        sx = (-1.5 + k) * 0.045
        root = head_c + Vector((sx, -0.06, -0.10)) * S
        prev = "head"
        p = root.copy()
        d = Vector((sx * 2.5, -0.55, -0.85)).normalized()
        curl = Vector((-sx * 3, -0.4, 0.55)).normalized()
        for sgi in range(3):
            nm = "tent%d_%d" % (k, sgi)
            parts[nm] = (prev, p.copy())
            L = (0.17 - sgi * 0.025) * S
            pts, rad = [p.copy()], [(0.028 - sgi * 0.008) * S]
            dd = d.copy()
            for u in range(1, 5):
                dd = (dd + curl * 0.12).normalized()
                pts.append(pts[-1] + dd * (L / 4)); rad.append(max(0.004, (0.028 - sgi * 0.008 - u * 0.002) * S))
            t = tube(pts, rad, 1)
            for v in t.data.vertices: v.co += v.normal * 0.003 * S * max(0.0, math.sin((v.co - p).length / S * 160))   # suckers / rings
            items.append((t, nm, HUSK if sgi < 2 else PURPLE, False))
            q = pts[-1]
            if sgi == 2:
                items.append((blob(q, Vector((0.014, 0.014, 0.014)) * S, 8, 6), nm, VIOLET, True))
            prev = nm; p = q; d = dd
    # abdomen: big, segmented, bony plates; spitters are swollen with venom sacs
    ab_c = body_c + Vector((0, 0.42, 0.06)) * S
    parts["abdomen"] = ("body", body_c + Vector((0, 0.24, 0.02)) * S)
    ab_s = Vector((0.30, 0.40, 0.27)) * S * (1.18 if spitter else 1.0)
    ab = blob(ab_c, ab_s, 32, 18, 0.01, 7)
    for v in ab.data.vertices:
        q = v.co - ab_c
        v.co += v.normal * 0.018 * S * max(0.0, math.sin(q.y / S * 30)) ** 2                    # segment bands
    items.append((ab, "abdomen", BONE, False))
    items.append((blob(ab_c + Vector((0, ab_s.y * 0.92, -0.02)), Vector((0.06, 0.05, 0.06)) * S, 12, 8), "abdomen", VOID, False))   # spinneret
    # a black mark on its back like a screaming face
    for (mx, my, sz) in ((-0.09, -0.12, 0.05), (0.09, -0.12, 0.05), (0.0, 0.08, 0.075)):
        p0 = ab_c + Vector((mx * S, my * S, 0))
        top = ab_c.z + ab_s.z * math.sqrt(max(0.0, 1 - (mx * S / ab_s.x) ** 2 - (my * S / ab_s.y) ** 2)) - 0.012 * S
        items.append((blob(Vector((p0.x, p0.y, top)), Vector((sz, sz * (1.5 if my > 0 else 1.0), 0.02)) * S, 12, 8), "abdomen", VOID, False))
    for i in range(6):                          # a row of bone spikes along its back
        t = -0.6 + i * 0.25
        base = ab_c + Vector((0, ab_s.y * t, ab_s.z * math.sqrt(max(0.0, 1 - t * t)) * 0.92))
        items.append((cone(base, base + Vector((0, 0.04, 0.10 - abs(t) * 0.04)) * S, 0.022 * S), "abdomen", JOINT, False))
    if spitter:
        for (ax, ay, az, r) in ((-0.17, 0.0, 0.12, 0.09), (0.17, 0.05, 0.10, 0.085), (0.0, 0.18, 0.2, 0.1), (-0.12, 0.25, 0.0, 0.07), (0.14, -0.15, 0.05, 0.07)):
            items.append((blob(ab_c + Vector((ax, ay, az)) * S, Vector((r, r, r * 0.85)) * S, 16, 10, 0.006, 20), "abdomen", BILE, True))
    # eight legs, three segments each: knees high, claws on the ground
    for side in (-1, 1):
        for k in range(4):
            a = (1.5 - k) * 0.42
            out = Vector((side * math.cos(a), -math.sin(a) * 1.0, 0))
            root = body_c + Vector((side * 0.19, -0.13 + k * 0.09, -0.04)) * S
            knee1 = root + (out * 0.36 + Vector((0, 0, 0.36))) * S
            knee2 = knee1 + (out * 0.38 + Vector((0, 0, -0.38))) * S
            tip = Vector((knee2.x + out.x * 0.16 * S, knee2.y + out.y * 0.16 * S, 0.0))
            segs = ((root, knee1, 0.050, 0.040), (knee1, knee2, 0.040, 0.028), (knee2, tip, 0.028, 0.004))
            prev = "body"
            for si, (a0, a1, r0, r1) in enumerate(segs):
                nm = "leg%d%s_%d" % (k, "L" if side < 0 else "R", si)
                parts[nm] = (prev, a0.copy())
                t = tube([a0, a0.lerp(a1, 0.5), a1], [r0 * S, (r0 + r1) / 2 * S, r1 * S], 1)
                items.append((t, nm, BONE if si < 2 else CHITIN, False))
                if si < 2:
                    items.append((blob(a1, Vector((r1, r1, r1)) * S * 1.35, 12, 8), nm, JOINT, False))     # knuckle
                    for h in range(3):                                                                # bristling black spines
                        q = a0.lerp(a1, 0.25 + h * 0.25)
                        sp_dir = (Vector((0, 0, 1)) + out * 0.4 + Vector((random.uniform(-0.3, 0.3), random.uniform(-0.3, 0.3), 0))).normalized()
                        items.append((cone(q, q + sp_dir * (0.05 + 0.02 * random.random()) * S, 0.009 * S), nm, CHITIN, False))
                    items.append((cone(a1, a1 + (out * 0.05 + Vector((0, 0, 0.07))) * S, r1 * 0.6 * S), nm, JOINT, False))   # spur
                prev = nm
    write_rigid(name, items, parts, budget=4200 if spitter else 3800)


# ================================================================== the hollow husk: a shed skin of him that crawls
def husk():
    items, parts = [], {}
    pelvis = Vector((0, 0.42, 0.22)); waist = Vector((0, 0.15, 0.42)); chest = Vector((0, -0.12, 0.58)); neck = Vector((0, -0.30, 0.66))
    parts["pelvis"] = (None, pelvis)
    parts["chest"] = ("pelvis", waist)
    parts["head"] = ("chest", neck)
    ch = tube([waist, waist.lerp(chest, 0.5), chest, neck], [0.10, 0.17, 0.19, 0.075], 2)
    for v in ch.data.vertices:
        q = v.co
        along = (q.y - neck.y) / (waist.y - neck.y)
        if q.z < chest.z + 0.05 and abs(q.x) > 0.05:
            v.co += v.normal * 0.016 * max(0.0, math.sin(along * 26)) ** 2                    # ribs under the skin
        if q.z > chest.z - 0.02 and abs(q.x) < 0.05 and v.normal.z > 0.3:
            v.co -= Vector((0, 0, 0.07)) * (1 - abs(q.x) / 0.05)                               # split open down the spine
        v.co += v.normal * 0.007 * noise.noise(q * 16)
    items.append((ch, "chest", HUSK, False))
    pv = tube([pelvis + Vector((0, 0.12, -0.03)), pelvis, waist], [0.12, 0.14, 0.10], 2)
    for v in pv.data.vertices: v.co += v.normal * 0.007 * noise.noise(v.co * 16)
    items.append((pv, "pelvis", HUSK, False))
    split = tube([waist + Vector((0, 0, 0.06)), chest + Vector((0, 0, 0.11)), neck + Vector((0, 0.02, 0.06))], [0.03, 0.045, 0.03], 1)
    items.append((split, "chest", VOID, False))
    for i in range(6):
        p = waist.lerp(chest, i / 5) + Vector((random.uniform(-0.03, 0.03), 0, 0.13))
        items.append((blob(p, Vector((0.016, 0.016, 0.03)), 8, 6), "chest", BILE, True))
    # head: long, eyeless, jaw hanging open far too wide
    hc = neck + Vector((0, -0.14, -0.02))
    sk = blob(hc, Vector((0.085, 0.12, 0.10)), 24, 14, 0.008, 14)
    for v in sk.data.vertices:
        q = v.co - hc
        if q.y < -0.04: v.co.z -= 0.03 * (-q.y / 0.13)                  # chin drawn down
    items.append((sk, "head", HUSK, False))
    for sx in (-1, 1):
        items.append((blob(hc + Vector((0.042 * sx, -0.105, 0.03)), Vector((0.03, 0.02, 0.026)), 12, 8), "head", VOID, False))
        items.append((blob(hc + Vector((0.042 * sx, -0.112, 0.03)), Vector((0.006, 0.004, 0.006)), 6, 4), "head", EYE, True))
    jaw_p = hc + Vector((0, -0.03, -0.06))
    parts["jaw"] = ("head", jaw_p)
    jw = blob(jaw_p + Vector((0, -0.08, -0.04)), Vector((0.07, 0.09, 0.03)), 16, 10)
    items.append((jw, "jaw", HUSK, False))
    items.append((blob(jaw_p + Vector((0, -0.07, -0.02)), Vector((0.058, 0.075, 0.02)), 12, 8), "jaw", FLESH, False))
    for i in range(7):
        a = (i - 3) * 0.32
        base = jaw_p + Vector((math.sin(a) * 0.055, -0.08 - math.cos(a) * 0.055, -0.01))
        items.append((cone(base, base + Vector((0, 0, 0.035)), 0.008), "jaw", TOOTH, False))
        base2 = hc + Vector((math.sin(a) * 0.06, -0.1 - math.cos(a) * 0.05, -0.05))
        items.append((cone(base2, base2 + Vector((0, 0, -0.03)), 0.007), "head", TOOTH, False))
    # arms: too long, propping it up; hands with long fingers clawing the ground
    for side, s in ((-1, "L"), (1, "R")):
        sh = chest + Vector((0.17 * side, 0.0, 0.0)); el = Vector((0.40 * side, -0.32, 0.36)); wr = Vector((0.32 * side, -0.70, 0.06))
        parts["arm" + s] = ("chest", sh); parts["fore" + s] = ("arm" + s, el)
        items.append((tube([sh, sh.lerp(el, 0.5), el], [0.055, 0.04, 0.042], 1), "arm" + s, HUSK, False))
        items.append((blob(el, Vector((0.05, 0.05, 0.05)), 10, 8), "arm" + s, JOINT, False))
        items.append((tube([el, el.lerp(wr, 0.5), wr], [0.04, 0.03, 0.028], 1), "fore" + s, HUSK, False))
        for f in range(4):
            a = (f - 1.5) * 0.35
            d = Vector((math.sin(a) * side * 0.6, -math.cos(a), -0.4)).normalized()
            items.append((hook(wr, d, 0.13, 0.012, 0.25, 4, Vector((0, 0, -1))), "fore" + s, HUSK, False))
            items.append((cone(wr + d * 0.12, wr + d * 0.16 + Vector((0, 0, -0.03)), 0.006), "fore" + s, CHITIN, False))
    # legs: dead weight, dragged behind
    for side, s in ((-1, "L"), (1, "R")):
        hp = pelvis + Vector((0.09 * side, 0.03, -0.02)); kn = Vector((0.17 * side, 0.85, 0.07)); ft = Vector((0.13 * side, 1.25, 0.05))
        parts["thigh" + s] = ("pelvis", hp); parts["shin" + s] = ("thigh" + s, kn)
        items.append((tube([hp, hp.lerp(kn, 0.5), kn], [0.07, 0.055, 0.045], 1), "thigh" + s, HUSK, False))
        items.append((tube([kn, kn.lerp(ft, 0.5), ft, ft + Vector((0, 0.1, -0.02))], [0.045, 0.035, 0.03, 0.02], 1), "shin" + s, HUSK, False))
    write_rigid("hollow_husk", items, parts, budget=3200)


# ================================================================== the scavenger: carries his skull home
def scavenger():
    items, parts = [], {}
    bc = Vector((0, 0.05, 1.05))
    parts["body"] = (None, bc)
    items.append((blob(bc, Vector((0.16, 0.30, 0.14)), 24, 14, 0.01, 10), "body", ASH, False))
    for i in range(6):                               # a cradle of bony fingers on its back: the skull rides in it
        a = i / 6 * math.tau
        base = bc + Vector((math.cos(a) * 0.09, math.sin(a) * 0.14, 0.1))
        items.append((hook(base, Vector((math.cos(a) * 0.4, math.sin(a) * 0.4, 1)), 0.22, 0.016, 0.3, 4, Vector((-math.cos(a), -math.sin(a), 0))), "body", BONE, False))
    neck_p = bc + Vector((0, -0.26, 0.02))
    parts["neck"] = ("body", neck_p)
    hd = neck_p + Vector((0, -0.42, 0.22))
    items.append((tube([neck_p, neck_p + Vector((0, -0.2, 0.18)), hd], [0.05, 0.035, 0.03], 1), "neck", ASH, False))
    parts["head"] = ("neck", hd)
    h = blob(hd + Vector((0, -0.09, 0)), Vector((0.06, 0.15, 0.05)), 16, 10)
    items.append((h, "head", BONE, False))
    for sx in (-1, 1):
        items.append((blob(hd + Vector((0.04 * sx, -0.1, 0.025)), Vector((0.012, 0.03, 0.006)), 8, 6), "head", VIOLET, True))
    for i in range(8):
        y = hd.y - 0.04 - i * 0.025
        items.append((cone(Vector((0.03, y, hd.z - 0.03)), Vector((0.02, y, hd.z - 0.07)), 0.006), "head", TOOTH, False))
        items.append((cone(Vector((-0.03, y, hd.z - 0.03)), Vector((-0.02, y, hd.z - 0.07)), 0.006), "head", TOOTH, False))
    for side in (-1, 1):
        for k in range(3):
            a = (1 - k) * 0.6
            out = Vector((side * math.cos(a), -math.sin(a), 0))
            root = bc + Vector((side * 0.1, -0.1 + k * 0.12, -0.05))
            kn = root + out * 0.55 + Vector((0, 0, 0.55))
            tip = Vector((root.x + out.x * 1.05, root.y + out.y * 1.05, 0))
            nm1, nm2 = "leg%d%s_0" % (k, "L" if side < 0 else "R"), "leg%d%s_1" % (k, "L" if side < 0 else "R")
            parts[nm1] = ("body", root); parts[nm2] = (nm1, kn)
            items.append((tube([root, root.lerp(kn, 0.5), kn], [0.03, 0.022, 0.02], 1), nm1, ASH, False))
            items.append((blob(kn, Vector((0.03, 0.03, 0.03)), 8, 6), nm1, JOINT, False))
            items.append((tube([kn, kn.lerp(tip, 0.5), tip], [0.02, 0.014, 0.004], 1), nm2, CHITIN, False))
    write_rigid("scavenger", items, parts, budget=3200)


# ================================================================== the Shadow Blade (item space: blade along +z, edge along x)
def blade():
    items, parts = [], {"blade": (None, Vector((0, 0, 0)))}
    # grip: wrapped bone with knuckles, a skull pommel
    items.append((tube([Vector((0, 0, -0.30)), Vector((0, 0, -0.1)), Vector((0, 0, 0.05))], [0.035, 0.032, 0.036], 1), "blade", CHITIN, False))
    for i in range(6):
        items.append((blob(Vector((0, 0, -0.27 + i * 0.055)), Vector((0.04, 0.04, 0.012)), 12, 6), "blade", JOINT, False))
    sk = blob(Vector((0, 0, -0.38)), Vector((0.06, 0.065, 0.075)), 16, 10, 0.004, 20)
    items.append((sk, "blade", BONE, False))
    for sx in (-1, 1): items.append((blob(Vector((0.024 * sx, -0.05, -0.37)), Vector((0.016, 0.012, 0.016)), 8, 6), "blade", VIOLET, True))
    # guard: a jaw of bone, teeth pointing up the blade
    g = tube([Vector((-0.24, 0, 0.12)), Vector((-0.12, 0, 0.07)), Vector((0, 0, 0.06)), Vector((0.12, 0, 0.07)), Vector((0.24, 0, 0.12))], [0.02, 0.04, 0.05, 0.04, 0.02], 1, 0.7)
    items.append((g, "blade", BONE, False))
    for sx in (-1, 1):
        items.append((hook(Vector((0.22 * sx, 0, 0.11)), Vector((0.5 * sx, 0, 1)), 0.18, 0.02, 0.4, 4, Vector((-sx, 0, 0))), "blade", BONE, False))
    for i in range(7):
        x = -0.15 + i * 0.05
        items.append((cone(Vector((x, 0, 0.09)), Vector((x * 0.8, 0, 0.16)), 0.012), "blade", TOOTH, False))
    # the blade: long, black, jagged, curving a little; a glowing violet edge and a groove
    L, n = 1.55, 40
    bm = bmesh.new()
    rows = []
    for i in range(n + 1):
        t = i / n
        z = 0.12 + t * L
        w = 0.13 * (1 - t ** 1.8) + 0.008
        serr = 0.045 * max(0.0, math.sin(t * 30)) ** 4 * (1 - t * 0.6) if t > 0.08 else 0.0
        curve = 0.08 * t * t
        th = 0.03 * (1 - t) + 0.004
        rows.append([bm.verts.new((curve - w - serr, 0, z)), bm.verts.new((curve, -th, z)), bm.verts.new((curve + w * 0.8, 0, z)), bm.verts.new((curve, th, z))])
    tip = bm.verts.new((0.08 + 0.01, 0, 0.12 + L + 0.12))
    for i in range(n):
        for k in range(4):
            a, b = rows[i][k], rows[i][(k + 1) % 4]; c, d = rows[i + 1][(k + 1) % 4], rows[i + 1][k]
            bm.faces.new((a, b, c, d))
    for k in range(4): bm.faces.new((rows[n][k], rows[n][(k + 1) % 4], tip))
    me = bpy.data.meshes.new("bl"); bm.to_mesh(me); bm.free()
    bo = link(bpy.data.objects.new("bl", me))
    sd = bo.modifiers.new("d", 'SUBSURF'); sd.levels = 1; apply_mods(bo)
    items.append((bo, "blade", METAL, False))
    # glowing edge strip + runes down the groove
    ep, er = [], []
    for i in range(0, n + 1, 2):
        t = i / n
        z = 0.12 + t * L
        w = 0.13 * (1 - t ** 1.8) + 0.008
        serr = 0.045 * max(0.0, math.sin(t * 30)) ** 4 * (1 - t * 0.6) if t > 0.08 else 0.0
        ep.append(Vector((0.08 * t * t - w - serr - 0.002, 0, z))); er.append(0.006)
    items.append((tube(ep, er, 1), "blade", EDGE, True))
    for i in range(9):
        z = 0.3 + i * 0.13
        c = 0.08 * ((z - 0.12) / L) ** 2
        for sy in (-1, 1):
            items.append((blob(Vector((c - 0.01, sy * 0.012, z)), Vector((0.01, 0.003, 0.035)), 8, 6), "blade", VIOLET, True))
    write_rigid("shadow_blade", items, parts, budget=2600)


# ================================================================== the Pale Ward (shield, item space: front faces -y, up +z)
def shield():
    items, parts = [], {"shield": (None, Vector((0, 0, 0)))}
    bm = bmesh.new()
    def outline(t):                              # kite shield silhouette, t in 0..1 around
        a = t * math.tau
        x = math.sin(a) * 0.36
        z = math.cos(a) * 0.5 + 0.08
        if z < 0.08: x *= (1 - (0.08 - z) / 0.55) ** 0.9
        z = z if z > 0.08 else 0.08 + (z - 0.08) * 1.35
        return x, z
    N = 48
    ring_f, ring_b = [], []
    for i in range(N):
        x, z = outline(i / N)
        ring_f.append(bm.verts.new((x, -0.03 - 0.05 * (1 - (x / 0.36) ** 2), z)))
        ring_b.append(bm.verts.new((x, 0.02, z)))
    cf = bm.verts.new((0, -0.12, 0.1)); cb = bm.verts.new((0, 0.02, 0.1))
    for i in range(N):
        j = (i + 1) % N
        bm.faces.new((ring_f[i], ring_f[j], cf)); bm.faces.new((ring_b[j], ring_b[i], cb))
        bm.faces.new((ring_f[j], ring_f[i], ring_b[i], ring_b[j]))
    me = bpy.data.meshes.new("sh"); bm.to_mesh(me); bm.free()
    so = link(bpy.data.objects.new("sh", me))
    sd = so.modifiers.new("d", 'SUBSURF'); sd.levels = 2; apply_mods(so)
    for v in so.data.vertices:
        v.co.y -= 0.012 * max(0.0, noise.noise(v.co * 9)) if v.co.y < 0 else 0
    items.append((so, "shield", CHITIN, False))
    # bone rim with spikes, ribs across the face, purple veins, a skull boss in the middle
    rp, rr = [], []
    for i in range(N + 1):
        x, z = outline((i % N) / N)
        rp.append(Vector((x * 1.02, -0.03, z * 1.0 + (0.0))))
        rr.append(0.028)
    items.append((tube(rp, rr, 1, 0.8), "shield", BONE, False))
    for i in range(0, N, 6):
        x, z = outline(i / N)
        out = Vector((x, 0, z - 0.1)).normalized()
        base = Vector((x, -0.03, z))
        items.append((cone(base, base + out * 0.12 + Vector((0, -0.02, 0)), 0.022), "shield", BONE, False))
    for k in range(4):
        z = 0.42 - k * 0.15
        w = 0.30 * (1 - abs(k - 0.8) * 0.18)
        items.append((tube([Vector((-w, -0.09, z)), Vector((0, -0.13, z - 0.03)), Vector((w, -0.09, z))], [0.016, 0.022, 0.016], 1, 0.7), "shield", BONE, False))
    for k in range(5):
        a = -0.9 + k * 0.45
        items.append((tube([Vector((0, -0.12, 0.12)), Vector((math.sin(a) * 0.14, -0.105, 0.12 + math.cos(a) * 0.14)), Vector((math.sin(a) * 0.27, -0.08, 0.1 + math.cos(a) * 0.3))], [0.007, 0.006, 0.003], 1), "shield", VIOLET, True))
    sk = blob(Vector((0, -0.15, 0.12)), Vector((0.11, 0.07, 0.12)), 20, 12, 0.006, 18)
    items.append((sk, "shield", BONE, False))
    for sx in (-1, 1):
        items.append((blob(Vector((0.04 * sx, -0.205, 0.145)), Vector((0.026, 0.012, 0.022)), 10, 6), "shield", VOID, False))
        items.append((blob(Vector((0.04 * sx, -0.212, 0.145)), Vector((0.011, 0.006, 0.01)), 8, 6), "shield", VIOLET, True))
    for i in range(6):
        x = -0.05 + i * 0.02
        items.append((cone(Vector((x, -0.2, 0.06)), Vector((x, -0.21, 0.025)), 0.007), "shield", TOOTH, False))
    items.append((tube([Vector((0, 0.03, -0.08)), Vector((0, 0.09, 0.1)), Vector((0, 0.03, 0.28))], [0.02, 0.022, 0.02], 1), "shield", CHITIN, False))
    write_rigid("pale_ward", items, parts, budget=2800)


# ================================================================== Shade armour (authored around the humanoid model parts; 1 px = 1/16)
PX = 1 / 16.0


def armour():
    # helmet: black hood, a bone skull-mask, horns, a crest, violet slits for eyes
    items, parts = [], {"head": (None, Vector())}
    hood = box(Vector((-4.6, -4.6, -0.3)) * PX, Vector((4.6, 4.6, 8.7)) * PX)
    bpy.ops.object.select_all(action='DESELECT')
    hm = hood.modifiers.new("b", 'BEVEL'); hm.width = 1.2 * PX; hm.segments = 3; apply_mods(hood)
    items.append((hood, "head", CHITIN, False))
    mask = blob(Vector((0, -4.6, 4.2)) * PX, Vector((4.3, 1.4, 4.6)) * PX, 24, 14, 0.15 * PX, 3)
    items.append((mask, "head", BONE, False))
    for sx in (-1, 1):
        items.append((blob(Vector((1.8 * sx, -5.85, 4.9)) * PX, Vector((1.3, 0.5, 0.9)) * PX, 12, 8), "head", VOID, False))
        items.append((blob(Vector((1.8 * sx, -6.05, 4.9)) * PX, Vector((0.9, 0.25, 0.32)) * PX, 10, 6), "head", VIOLET, True))
        horn = hook(Vector((3.9 * sx, -1.0, 7.0)) * PX, Vector((0.9 * sx, 0.8, 0.9)), 9 * PX, 1.1 * PX, 0.55, 6, Vector((0, 1, -0.6)))
        items.append((horn, "head", BONE, False))
        items.append((cone(Vector((3.9 * sx, -1.0, 7.0)) * PX + Vector((0.9 * sx, 0.8, 0.9)).normalized() * 8 * PX + Vector((0, 1, -0.6)).normalized() * 3 * PX,
                           Vector((3.9 * sx, -1.0, 7.0)) * PX + Vector((0.9 * sx, 1.4, 0.5)).normalized() * 11 * PX + Vector((0, 1, -0.6)).normalized() * 4 * PX, 0.5 * PX), "head", CHITIN, False))
    for i in range(7):
        x = (-3 + i) * 0.9
        items.append((cone(Vector((x, -5.4, 1.2)) * PX, Vector((x * 0.95, -5.5, 0.1)) * PX, 0.35 * PX), "head", TOOTH, False))
    for i in range(5):
        y = (-2.5 + i * 1.6)
        items.append((cone(Vector((0, y, 8.6)) * PX, Vector((0, y + 0.8, 10.6 - abs(i - 2) * 0.5)) * PX, 0.55 * PX), "head", BONE, False))
    write_rigid("shade_helmet", items, parts, part_space, budget=1600)

    # chestplate: black cuirass, a bone ribcage over it with a violet core, spine spikes on the back; pauldrons on the arms
    items, parts = [], {"body": (None, Vector()), "right_arm": (None, Vector()), "left_arm": (None, Vector())}
    cu = box(Vector((-4.6, -2.7, -12.4)) * PX, Vector((4.6, 2.7, 0.3)) * PX)
    bm_ = cu.modifiers.new("b", 'BEVEL'); bm_.width = 1.0 * PX; bm_.segments = 3; apply_mods(cu)
    items.append((cu, "body", CHITIN, False))
    items.append((tube([Vector((0, -2.9, -1.0)) * PX, Vector((0, -3.3, -4.5)) * PX, Vector((0, -3.0, -7.5)) * PX], [0.7 * PX, 0.8 * PX, 0.6 * PX], 1), "body", BONE, False))
    for k in range(5):
        z = -1.8 - k * 1.45
        for sx in (-1, 1):
            pts = [Vector((0.6 * sx, -3.3, z)), Vector((2.6 * sx, -3.35, z - 0.3)), Vector((4.4 * sx, -2.2, z - 0.7)), Vector((4.85 * sx, 0.2, z - 0.9))]
            items.append((tube([p * PX for p in pts], [0.45 * PX, 0.5 * PX, 0.42 * PX, 0.3 * PX], 1), "body", BONE, False))
    items.append((blob(Vector((0, -3.0, -5.0)) * PX, Vector((1.0, 0.4, 1.4)) * PX, 10, 8), "body", VIOLET, True))
    for i in range(6):
        z = -1.2 - i * 1.9
        items.append((cone(Vector((0, 2.6, z)) * PX, Vector((0, 5.0 - i * 0.2, z + 1.2)) * PX, 0.7 * PX), "body", BONE, False))
    for nm, sx in (("right_arm", 1), ("left_arm", -1)):
        pa = blob(Vector((1.0 * sx, 0, 0.4)) * PX, Vector((3.4, 3.2, 2.6)) * PX, 18, 10, 0.12 * PX, 3)
        items.append((pa, nm, BONE, False))
        for k in range(3):
            base = Vector(((1.8 + k * 0.4) * sx, (-1.2 + k * 1.2), 2.4)) * PX
            items.append((cone(base, base + Vector((1.5 * sx, 0, 3.6 - k * 0.6)) * PX, 0.65 * PX), nm, BONE, False))
        sl = box(Vector((-2.4 + sx, -2.4, -6.5)) * PX, Vector((2.4 + sx, 2.4, -1.0)) * PX)
        bm2 = sl.modifiers.new("b", 'BEVEL'); bm2.width = 0.7 * PX; bm2.segments = 2; apply_mods(sl)
        sl.location.x += 0; items.append((sl, nm, CHITIN, False))
        items.append((tube([Vector((2.5 * sx + sx, -1.6, -4.0)) * PX, Vector((2.7 * sx + sx, 0, -4.4)) * PX, Vector((2.5 * sx + sx, 1.6, -4.0)) * PX], [0.35 * PX] * 3, 1), nm, VIOLET, True))
    write_rigid("shade_chestplate", items, parts, part_space, budget=2200)

    # leggings: a bone-plated belt with hanging tassets, black greaves with bone knee caps
    items, parts = {}, {}
    items, parts = [], {"body": (None, Vector()), "right_leg": (None, Vector()), "left_leg": (None, Vector())}
    belt = box(Vector((-4.5, -2.6, -12.4)) * PX, Vector((4.5, 2.6, -9.6)) * PX)
    bm3 = belt.modifiers.new("b", 'BEVEL'); bm3.width = 0.6 * PX; bm3.segments = 2; apply_mods(belt)
    items.append((belt, "body", CHITIN, False))
    for k in range(4):
        x = -3.3 + k * 2.2
        items.append((box(Vector((x - 0.9, -2.95, -14.5)) * PX, Vector((x + 0.9, -2.6, -10.2)) * PX), "body", BONE, False))
    items.append((blob(Vector((0, -2.8, -11.0)) * PX, Vector((0.7, 0.3, 0.7)) * PX, 8, 6), "body", VIOLET, True))
    for nm in ("right_leg", "left_leg"):
        gr = box(Vector((-2.45, -2.45, -7.4)) * PX, Vector((2.45, 2.45, 0.1)) * PX)
        bm4 = gr.modifiers.new("b", 'BEVEL'); bm4.width = 0.6 * PX; bm4.segments = 2; apply_mods(gr)
        items.append((gr, nm, CHITIN, False))
        kc = blob(Vector((0, -2.6, -5.6)) * PX, Vector((1.7, 0.8, 1.6)) * PX, 12, 8)
        items.append((kc, nm, BONE, False))
        items.append((cone(Vector((0, -3.2, -5.6)) * PX, Vector((0, -5.2, -4.8)) * PX, 0.6 * PX), nm, BONE, False))
        for k in range(3):
            items.append((tube([Vector((-2.5, -1.5 + k * 1.5, -2 - k * 1.3)) * PX, Vector((2.5, -1.5 + k * 1.5, -2.6 - k * 1.3)) * PX], [0.25 * PX, 0.25 * PX], 0), nm, BONE, False))
    write_rigid("shade_leggings", items, parts, part_space, budget=1200)

    # boots: black, bone toe-claws, a spur at the heel, violet seam
    items, parts = [], {"right_leg": (None, Vector()), "left_leg": (None, Vector())}
    for nm in ("right_leg", "left_leg"):
        bt = box(Vector((-2.55, -2.6, -12.4)) * PX, Vector((2.55, 2.55, -8.0)) * PX)
        bm5 = bt.modifiers.new("b", 'BEVEL'); bm5.width = 0.6 * PX; bm5.segments = 2; apply_mods(bt)
        items.append((bt, nm, CHITIN, False))
        for k in range(3):
            x = (-1.3 + k * 1.3)
            items.append((hook(Vector((x, -2.5, -11.6)) * PX, Vector((x * 0.2, -1, -0.25)), 2.6 * PX, 0.55 * PX, 0.35, 4, Vector((0, 0, -1))), nm, BONE, False))
        items.append((cone(Vector((0, 2.5, -11.0)) * PX, Vector((0, 4.6, -11.8)) * PX, 0.55 * PX), nm, BONE, False))
        items.append((tube([Vector((-2.6, -2.62, -8.6)) * PX, Vector((2.6, -2.62, -8.6)) * PX], [0.3 * PX] * 2, 0), nm, VIOLET, True))
    write_rigid("shade_boots", items, parts, part_space, budget=1200)


spider("pale_biter", False)
spider("pale_spitter", True)
husk()
scavenger()
blade()
shield()
armour()
print("done", flush=True)
