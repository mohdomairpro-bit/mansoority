"""Read/write the mod's mesh formats without Blender.
SHD2: rigid parts (each triangle belongs to one part).
SHD3: one skinned vertex buffer (2 bones per vertex) + index lists, parts = bones.
All positions are model metres in Minecraft axes (y up, the creature faces +z)."""
import struct
import numpy as np


def read_shd2(path):
    d = open(path, "rb").read()
    assert d[:4] == b"SHD2", "not an SHD2 mesh"
    o = 4
    n = struct.unpack_from("<i", d, o)[0]; o += 4
    parts = []
    for _ in range(n):
        l = struct.unpack_from("<h", d, o)[0]; o += 2
        name = d[o:o + l].decode(); o += l
        par, px, py, pz, t = struct.unpack_from("<ifffi", d, o); o += 20
        rec = np.dtype([("p", "<f4", 3), ("n", "i1", 3), ("uv", "<f4", 2), ("g", "u1")])
        arr = np.frombuffer(d, dtype=rec, count=t * 3, offset=o); o += t * 3 * rec.itemsize
        parts.append(dict(name=name, parent=par, pivot=np.array([px, py, pz], np.float32),
                          pos=arr["p"].astype(np.float32).copy(), nrm=arr["n"].astype(np.float32) / 127.0,
                          uv=arr["uv"].astype(np.float32).copy(), glow=arr["g"][::3].astype(bool).copy()))
    return parts


def write_shd3(path, bones, pos, nrm, uv, b0, b1, w0, solid, glow):
    """bones: list of (name, parent index, pivot xyz). per vertex arrays. solid/glow: int triangle index arrays (N,3)"""
    with open(path, "wb") as f:
        f.write(b"SHD3")
        f.write(struct.pack("<i", len(bones)))
        for name, parent, piv in bones:
            nb = name.encode(); f.write(struct.pack("<h", len(nb))); f.write(nb)
            f.write(struct.pack("<i3f", parent, *[float(x) for x in piv]))
        nv = len(pos)
        f.write(struct.pack("<i", nv))
        rec = np.zeros(nv, dtype=np.dtype([("p", "<f4", 3), ("n", "i1", 3), ("uv", "<f4", 2), ("b0", "u1"), ("b1", "u1"), ("w", "u1")]))
        rec["p"] = pos
        rec["n"] = np.clip(np.round(nrm * 127), -127, 127).astype(np.int8)
        rec["uv"] = uv
        rec["b0"] = b0; rec["b1"] = b1
        rec["w"] = np.clip(np.round(w0 * 255), 0, 255).astype(np.uint8)
        f.write(rec.tobytes())
        for tris in (solid, glow):
            f.write(struct.pack("<i", len(tris)))
            f.write(np.asarray(tris, dtype="<i4").tobytes())


def read_shd3(path):
    d = open(path, "rb").read()
    assert d[:4] == b"SHD3"
    o = 4
    n = struct.unpack_from("<i", d, o)[0]; o += 4
    bones = []
    for _ in range(n):
        l = struct.unpack_from("<h", d, o)[0]; o += 2
        name = d[o:o + l].decode(); o += l
        par, px, py, pz = struct.unpack_from("<i3f", d, o); o += 16
        bones.append((name, par, (px, py, pz)))
    nv = struct.unpack_from("<i", d, o)[0]; o += 4
    dt = np.dtype([("p", "<f4", 3), ("n", "i1", 3), ("uv", "<f4", 2), ("b0", "u1"), ("b1", "u1"), ("w", "u1")])
    rec = np.frombuffer(d, dtype=dt, count=nv, offset=o); o += nv * dt.itemsize
    out = []
    for _ in range(2):
        t = struct.unpack_from("<i", d, o)[0]; o += 4
        out.append(np.frombuffer(d, dtype="<i4", count=t * 3, offset=o).reshape(-1, 3)); o += t * 12
    return bones, rec, out[0], out[1]
