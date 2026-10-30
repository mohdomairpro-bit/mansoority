"""how low does each clip put the body? (lowest skinned vertex per frame; the floor is y=0). no procedural layers."""
import sys, json, numpy as np
sys.path.insert(0, __file__.rsplit('/', 1)[0])
import meshio
mesh, animj = sys.argv[1], sys.argv[2]
bones, rec, solid, glow = meshio.read_shd3(mesh)
A = json.load(open(animj))
order = A['bones']
idx = {b[0]: i for i, b in enumerate(bones)}
P = rec['p'].astype(np.float64); B0 = rec['b0']; B1 = rec['b1']; W0 = rec['w'] / 255.0

def qm(q):
    x, y, z, w = q
    return np.array([[1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w)], [2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w)], [2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y)]])

def frame_min(row):
    W = [None] * len(bones)
    for i, (name, par, piv) in enumerate(bones):
        piv = np.array(piv)
        if name in order:
            v = row[order.index(name)]; q = v[:4]; t = np.array(v[4:7])
        else: q = (0, 0, 0, 1); t = np.zeros(3)
        L = np.eye(4); L[:3, :3] = qm(q); L[:3, 3] = t + piv - qm(q) @ piv
        W[i] = L if par < 0 else W[par] @ L
    M = np.stack(W)
    pa = np.einsum('nij,nj->ni', M[B0][:, :3, :3], P) + M[B0][:, :3, 3]
    pb = np.einsum('nij,nj->ni', M[B1][:, :3, :3], P) + M[B1][:, :3, 3]
    out = pa * W0[:, None] + pb * (1 - W0[:, None])
    return out[:, 1].min(), out

if __name__ == '__main__':
    for name, c in A['clips'].items():
        fr = c['frames']; step = max(1, len(fr) // 12)
        mins = [frame_min(fr[i])[0] for i in range(0, len(fr), step)]
        print('%-12s frames %4d  lowest %+.3f  highest-low %+.3f' % (name, len(fr), min(mins), max(mins)))
