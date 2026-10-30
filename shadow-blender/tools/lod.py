"""Blender: make the three detail levels from the full SHD2 export.
big surfaces (skull, limbs) get a quadric decimate; small loose pieces (hair, spines, teeth, claws)
stay whole so the silhouette survives at a distance. glow triangles are never touched.
blender -b -P lod.py -- in.mesh out_prefix
"""
import sys, struct, bpy, bmesh, numpy as np
sys.path.insert(0, __file__.rsplit('/', 1)[0])
import meshio

src, prefix = sys.argv[sys.argv.index('--') + 1:][:2]
parts = meshio.read_shd2(src)

# per level: ratio for big islands, keep fraction for small islands
LEVELS = {
    'lod0': dict(big=0.40, head=0.52, small=1.0),
    'lod1': dict(big=0.20, head=0.26, small=1.0),
    'lod2': dict(big=0.085, head=0.10, small=0.5),
}
SMALL = 300

def islands(pos):
    p = np.round(pos / 1e-5).astype(np.int64)
    keys, inv = np.unique(p, axis=0, return_inverse=True)
    inv = inv.reshape(-1); tri = inv.reshape(-1, 3)
    par = np.arange(len(keys))
    def f(a):
        while par[a] != a:
            par[a] = par[par[a]]; a = par[a]
        return a
    for a, b, c in tri:
        ra, rb, rc = f(a), f(b), f(c)
        if rb != ra: par[rb] = ra
        rc = f(c)
        if rc != ra: par[rc] = ra
    return np.array([f(t[0]) for t in tri])

def decimate(pos, uv, ratio):
    """triangle soup -> welded mesh -> collapse decimate -> triangle soup (pos, nrm, uv)"""
    p = np.round(pos / 1e-6).astype(np.int64)
    keys, first, inv = np.unique(p, axis=0, return_index=True, return_inverse=True)
    inv = inv.reshape(-1)
    verts = pos[first]
    faces = inv.reshape(-1, 3)
    me = bpy.data.meshes.new('d'); me.from_pydata(verts.tolist(), [], faces.tolist()); me.update()
    ul = me.uv_layers.new(name='uv')
    ul.data.foreach_set('uv', uv.reshape(-1).astype(np.float32))
    ob = bpy.data.objects.new('d', me); bpy.context.scene.collection.objects.link(ob)
    bpy.context.view_layer.objects.active = ob
    md = ob.modifiers.new('dec', 'DECIMATE'); md.ratio = ratio; md.use_collapse_triangulate = True
    md.use_symmetry = False
    dg = bpy.context.evaluated_depsgraph_get()
    ev = ob.evaluated_get(dg); m2 = ev.to_mesh()
    m2.calc_loop_triangles()
    nt = len(m2.loop_triangles)
    vco = np.zeros(len(m2.vertices) * 3, np.float32); m2.vertices.foreach_get('co', vco); vco = vco.reshape(-1, 3)
    lt = np.zeros(nt * 3, np.int32); m2.loop_triangles.foreach_get('loops', lt)
    lv = np.zeros(len(m2.loops), np.int32); m2.loops.foreach_get('vertex_index', lv)
    luv = np.zeros(len(m2.loops) * 2, np.float32); m2.uv_layers[0].data.foreach_get('uv', luv); luv = luv.reshape(-1, 2)
    # smooth normals per vertex (area weighted)
    tv = lv[lt].reshape(-1, 3)
    a, b, c = vco[tv[:, 0]], vco[tv[:, 1]], vco[tv[:, 2]]
    fn = np.cross(b - a, c - a)
    vn = np.zeros_like(vco)
    for k in range(3): np.add.at(vn, tv[:, k], fn)
    vn /= np.maximum(np.linalg.norm(vn, axis=1, keepdims=True), 1e-9)
    out_p = vco[tv].reshape(-1, 3); out_n = vn[tv].reshape(-1, 3); out_uv = luv[lt].reshape(-1, 2)
    ev.to_mesh_clear(); bpy.data.objects.remove(ob); bpy.data.meshes.remove(me)
    return out_p, out_n, out_uv

def write_shd2(path, parts_out):
    with open(path, 'wb') as f:
        f.write(b'SHD2'); f.write(struct.pack('<i', len(parts_out)))
        rec = np.dtype([("p", "<f4", 3), ("n", "i1", 3), ("uv", "<f4", 2), ("g", "u1")])
        for pt in parts_out:
            nb = pt['name'].encode(); f.write(struct.pack('<h', len(nb))); f.write(nb)
            t = len(pt['pos']) // 3
            f.write(struct.pack('<ifffi', pt['parent'], *[float(x) for x in pt['pivot']], t))
            r = np.zeros(t * 3, rec)
            r['p'] = pt['pos']; r['n'] = np.clip(np.round(pt['nrm'] * 127), -127, 127).astype(np.int8); r['uv'] = pt['uv']
            r['g'] = np.repeat(pt['glow'].astype(np.uint8), 3)
            f.write(r.tobytes())

isl = {pt['name']: (islands(pt['pos']) if len(pt['pos']) else None) for pt in parts}
for lvl, cfg in LEVELS.items():
    out = []; total = 0
    for pt in parts:
        o = dict(name=pt['name'], parent=pt['parent'], pivot=pt['pivot'])
        if len(pt['pos']) == 0:
            o.update(pos=pt['pos'], nrm=pt['nrm'], uv=pt['uv'], glow=pt['glow']); out.append(o); continue
        roots = isl[pt['name']]
        nt = len(roots)
        P = pt['pos'].reshape(-1, 3, 3); N = pt['nrm'].reshape(-1, 3, 3); U = pt['uv'].reshape(-1, 3, 2); G = pt['glow']
        u, cnt = np.unique(roots, return_counts=True)
        size = dict(zip(u, cnt))
        big = np.array([size[r] >= SMALL for r in roots]) & ~G
        keep_small = np.zeros(nt, bool)
        smalls = [r for r in u if size[r] < SMALL]
        for i, r in enumerate(smalls):                       # thin out the small pieces evenly (every other hair)
            if cfg['small'] >= 1 or (i % round(1 / cfg['small'])) == 0: keep_small |= (roots == r)
        keep_small &= ~big
        ratio = cfg['head'] if pt['name'] in ('head', 'jaw', 'jawR') else cfg['big']
        if pt['name'].startswith('tongue') or pt['name'].startswith('eye'): ratio = max(ratio, 0.5 if lvl != 'lod2' else 0.25)
        ps, ns, us, gs = [], [], [], []
        if big.any():
            dp, dn, du = decimate(P[big].reshape(-1, 3), U[big].reshape(-1, 2), ratio)
            ps.append(dp); ns.append(dn); us.append(du); gs.append(np.zeros(len(dp) // 3, bool))
        rest = keep_small | (G & True)
        if rest.any():
            ps.append(P[rest].reshape(-1, 3)); ns.append(N[rest].reshape(-1, 3)); us.append(U[rest].reshape(-1, 2)); gs.append(G[rest])
        o.update(pos=np.concatenate(ps).astype(np.float32), nrm=np.concatenate(ns).astype(np.float32),
                 uv=np.concatenate(us).astype(np.float32), glow=np.concatenate(gs))
        total += len(o['pos']) // 3
        out.append(o)
    write_shd2(f'{prefix}_{lvl}.mesh', out)
    print(lvl, 'tris', total, flush=True)
