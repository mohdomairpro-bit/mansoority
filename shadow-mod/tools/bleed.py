"""baked textures: grow every UV island's colour outward into the empty space and make the whole image opaque, so
cutout rendering never punches holes at seams. python3 bleed.py in.png out.png"""
import sys, numpy as np
from PIL import Image
a = np.asarray(Image.open(sys.argv[1]).convert("RGBA")).astype(np.float32)
rgb, filled = a[..., :3].copy(), a[..., 3] > 0
for _ in range(40):
    if filled.all(): break
    acc = np.zeros_like(rgb); cnt = np.zeros(filled.shape, np.float32)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        m = np.roll(np.roll(filled, dy, 0), dx, 1)
        acc += np.roll(np.roll(rgb, dy, 0), dx, 1) * m[..., None]; cnt += m
    grow = (~filled) & (cnt > 0)
    rgb[grow] = acc[grow] / cnt[grow][:, None]
    filled = filled | grow
out = np.dstack([np.clip(rgb, 0, 255), np.full(filled.shape, 255.0)]).astype(np.uint8)
Image.fromarray(out, "RGBA").convert("RGB").save(sys.argv[2], optimize=True)
