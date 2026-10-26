"""tiny NBT reader/writer (gzip'd structure files). tags: python values wrapped as (type, value)."""
import gzip, struct
END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BARR, STR, LIST, COMP, IARR, LARR = range(13)

def _r(f, t):
    if t == BYTE: return struct.unpack('>b', f.read(1))[0]
    if t == SHORT: return struct.unpack('>h', f.read(2))[0]
    if t == INT: return struct.unpack('>i', f.read(4))[0]
    if t == LONG: return struct.unpack('>q', f.read(8))[0]
    if t == FLOAT: return struct.unpack('>f', f.read(4))[0]
    if t == DOUBLE: return struct.unpack('>d', f.read(8))[0]
    if t == BARR: n = _r(f, INT); return list(struct.unpack('>%db' % n, f.read(n)))
    if t == STR: n = struct.unpack('>H', f.read(2))[0]; return f.read(n).decode()
    if t == LIST:
        et = f.read(1)[0]; n = _r(f, INT); return (et, [_r(f, et) for _ in range(n)])
    if t == COMP:
        d = {}
        while True:
            tt = f.read(1)[0]
            if tt == END: return d
            name = _r(f, STR); d[name] = (tt, _r(f, tt))
    if t == IARR: n = _r(f, INT); return list(struct.unpack('>%di' % n, f.read(4 * n)))
    if t == LARR: n = _r(f, INT); return list(struct.unpack('>%dq' % n, f.read(8 * n)))

def _w(o, t, v):
    if t == BYTE: o += struct.pack('>b', v)
    elif t == SHORT: o += struct.pack('>h', v)
    elif t == INT: o += struct.pack('>i', v)
    elif t == LONG: o += struct.pack('>q', v)
    elif t == FLOAT: o += struct.pack('>f', v)
    elif t == DOUBLE: o += struct.pack('>d', v)
    elif t == BARR: o += struct.pack('>i', len(v)) + struct.pack('>%db' % len(v), *v)
    elif t == STR: b = v.encode(); o += struct.pack('>H', len(b)) + b
    elif t == LIST:
        et, items = v; o += bytes([et if items else END]) + struct.pack('>i', len(items))
        for it in items: _w(o, et, it)
    elif t == COMP:
        for k, (tt, vv) in v.items(): o += bytes([tt]); _w(o, STR, k); _w(o, tt, vv)
        o += bytes([END])
    elif t == IARR: o += struct.pack('>i', len(v)) + struct.pack('>%di' % len(v), *v)

def load(path):
    import io
    f = io.BytesIO(gzip.open(path).read()); t = f.read(1)[0]; _r(f, STR); return _r(f, COMP)

def save(path, root):
    o = bytearray([COMP]); _w(o, STR, ''); _w(o, COMP, root)
    with gzip.open(path, 'wb') as g: g.write(bytes(o))
