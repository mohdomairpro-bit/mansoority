# film grain + chromatic aberration + vignette on the jumpscare frames, and a contact sheet for review
import sys, glob, os
import numpy as np
from PIL import Image
out = sys.argv[1]
frames = sorted(glob.glob(os.path.join(out, "js_*.png")))
rng = np.random.default_rng(3)
thumbs = []
for f in frames:
    im = np.asarray(Image.open(f).convert("RGB")).astype(np.float32) / 255
    h, w, _ = im.shape
    yy, xx = np.mgrid[0:h, 0:w]
    r = np.sqrt(((xx - w / 2) / (w / 2)) ** 2 + ((yy - h / 2) / (h / 2)) ** 2)
    vig = np.clip(1 - 0.55 * r ** 2.2, 0, 1)[..., None]
    ca = np.stack([np.roll(im[..., 0], 3, axis=1), im[..., 1], np.roll(im[..., 2], -3, axis=1)], -1)
    grain = rng.normal(0, 0.045, (h, w, 1))
    o = np.clip(ca * vig + grain, 0, 1)
    img = Image.fromarray((o * 255).astype(np.uint8))
    img.save(f.replace("js_", "jsfx_"))
    thumbs.append(img.resize((320, 180)))
if thumbs:
    cols = 6; rows = (len(thumbs) + cols - 1) // cols
    sheet = Image.new("RGB", (320 * cols, 180 * rows))
    for i, t in enumerate(thumbs): sheet.paste(t, ((i % cols) * 320, (i // cols) * 180))
    sheet.save(os.path.join(out, "jumpscare_strip.jpg"), quality=88)
print("post done", len(frames))
