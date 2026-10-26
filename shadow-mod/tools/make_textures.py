"""paints the stage-3 textures (spiders, armour, items, pale bone blocks). run from shadow-mod/: python3 tools/make_textures.py"""
import numpy as np, os
from PIL import Image
R = 'src/main/resources/assets/theshadow/textures/'
rng = np.random.default_rng(7)

def noise(w, h, scale=1.0, octaves=3, seed=0):
    r = np.random.default_rng(seed); out = np.zeros((h, w))
    for o in range(octaves):
        s = 2 ** o
        g = r.random((h // max(1, int(4 / s / scale)) + 2, w // max(1, int(4 / s / scale)) + 2))
        im = Image.fromarray((g * 255).astype(np.uint8)).resize((w, h), Image.BILINEAR)
        out += np.asarray(im) / 255.0 / s
    return out / sum(1 / 2 ** o for o in range(octaves))

def save(img, path):
    os.makedirs(os.path.dirname(R + path), exist_ok=True); img.save(R + path)

def rgba(arr):
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA')

def boxfaces(u, v, w, h, d):
    """minecraft box unfold -> {face: (x, y, fw, fh)}"""
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
            'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}

# ------------------------------------------------------------------ pale bone palette
BONE = np.array([226, 222, 206]); BONE_D = np.array([168, 160, 140]); BONE_DD = np.array([96, 90, 78])

def bone_tex(w, h, seed, base=BONE, var=22):
    n = noise(w, h, 2.0, 3, seed)
    a = np.zeros((h, w, 4)); a[..., :3] = base[None, None] + (n[..., None] - 0.5) * var * 2; a[..., 3] = 255
    return a

# ------------------------------------------------------------------ spiders (64x32 vanilla spider layout)
def spider(spitter):
    a = np.zeros((32, 64, 4))
    def fill(u, v, w, h, d, seed, base):
        for f, (x, y, fw, fh) in boxfaces(u, v, w, h, d).items():
            t = bone_tex(fw, fh, seed + hash(f) % 97, base)
            a[y:y + fh, x:x + fw] = t
    fill(32, 4, 8, 8, 8, 1, BONE)          # head
    fill(0, 0, 6, 6, 6, 2, BONE_D)         # neck
    fill(0, 12, 10, 8, 12, 3, BONE)        # abdomen
    fill(18, 0, 16, 2, 2, 4, BONE)         # legs
    # legs: dark joint bands + darker tips
    for x in range(18, 54):
        lx = (x - 18) % 18
        if lx in (5, 6, 12): a[0:4, x, :3] *= 0.55
    a[0:4, 18:20, :3] *= 0.4
    # head front (x 40..48, y 12..20): split mouth with fangs below the eyes
    hx, hy = 40, 12
    a[hy + 5:hy + 8, hx + 1:hx + 7, :3] = [52, 8, 10]
    a[hy + 5, hx + 1:hx + 7, :3] = [24, 2, 4]
    for fx in (hx + 2, hx + 5): a[hy + 6:hy + 8, fx, :3] = [236, 232, 214]
    # cracks over the head top and abdomen
    for (x0, y0, ln, seed) in [(41, 5, 6, 1), (3, 26, 9, 2), (14, 15, 7, 3), (25, 26, 8, 4), (36, 22, 6, 5)]:
        r = np.random.default_rng(seed); x, y = x0, y0
        for _ in range(ln):
            if 0 <= y < 32 and 0 <= x < 64 and a[y, x, 3] > 0: a[y, x, :3] = BONE_DD
            x += 1; y += r.integers(-1, 2)
    if spitter:   # swollen venom sacs on the abdomen top/back, sickly yellow-green
        for (cx, cy, rr) in [(16, 17, 2.6), (21, 19, 2.0), (14, 21, 1.8), (36, 27, 2.6), (41, 25, 2.0)]:
            for y in range(32):
                for x in range(64):
                    dd = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                    if dd < rr and a[y, x, 3] > 0:
                        k = 1 - dd / rr
                        a[y, x, :3] = a[y, x, :3] * (1 - k) + np.array([178, 190, 92]) * k
                        if dd < 0.8: a[y, x, :3] = [226, 236, 150]
    return rgba(a)

save(spider(False), 'entity/pale_biter.png')
save(spider(True), 'entity/pale_spitter.png')

# ------------------------------------------------------------------ shade armour layers (64x32)
SHADE = np.array([30, 28, 36]); SHADE_HI = np.array([74, 66, 92]); SEAM = np.array([120, 60, 170])
def shade_fill(a, u, v, w, h, d, seed, seams=True):
    for f, (x, y, fw, fh) in boxfaces(u, v, w, h, d).items():
        n = noise(fw, fh, 3.0, 3, seed + hash(f) % 51)
        c = SHADE[None, None] + (n[..., None] - 0.4) * 40
        c[0, :] = SHADE_HI; c[:, 0] = SHADE_HI * 0.8                 # plate edges catch the light
        c[-1, :] = SHADE * 0.6; c[:, -1] = SHADE * 0.6
        if seams and fh > 4 and fw > 3:                               # a faint violet seam down each plate
            c[1:-1, fw // 2] = SEAM * 0.55 + c[1:-1, fw // 2] * 0.45
        a[y:y + fh, x:x + fw, :3] = c; a[y:y + fh, x:x + fw, 3] = 255

l1 = np.zeros((32, 64, 4))
shade_fill(l1, 0, 0, 8, 8, 8, 1)       # helmet
# visor slit across the helmet front
l1[8 + 3, 8 + 1:8 + 7, :3] = [14, 10, 18]; l1[8 + 3, 8 + 2, :3] = SEAM; l1[8 + 3, 8 + 5, :3] = SEAM
shade_fill(l1, 16, 16, 8, 12, 4, 2)    # chest
shade_fill(l1, 40, 16, 4, 12, 4, 3)    # arms
shade_fill(l1, 0, 16, 4, 12, 4, 4)     # boots (bottom of the leg box; vanilla only draws feet from layer 1)
save(rgba(l1), 'models/armor/shade_layer_1.png')
l2 = np.zeros((32, 64, 4))
shade_fill(l2, 16, 16, 8, 12, 4, 5)    # belt/body
shade_fill(l2, 0, 16, 4, 12, 4, 6)     # legs
save(rgba(l2), 'models/armor/shade_layer_2.png')

# ------------------------------------------------------------------ item icons (16x16), drawn from masks
def icon(mask, pal, path, shade_seed=0):
    rows = [r.ljust(16, '.') for r in mask]
    a = np.zeros((16, 16, 4))
    n = noise(16, 16, 4, 2, shade_seed)
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch in pal:
                c = np.array(pal[ch], float); a[y, x, :3] = c * (0.9 + 0.2 * n[y, x]); a[y, x, 3] = 255
    save(rgba(a), path)

D = (20, 18, 26); M = (44, 40, 56); H = (86, 78, 108); S = (150, 80, 210); O = (10, 8, 12)
icon(["................",
      "....OOOOOOOO....",
      "...OHHHHHHHHO...",
      "...OHMMMMMMMO...",
      "...OMMMMMMMMO...",
      "...OSDOMMODSO...",
      "...OMMMMMMMMO...",
      "...OMO....OMO...",
      "....O......O...."], {'O': O, 'H': H, 'M': M, 'D': D, 'S': S}, 'item/shade_helmet.png')
icon(["................",
      "..OOO......OOO..",
      ".OHHMO....OMHHO.",
      ".OMMMOOOOOOMMMO.",
      ".OMMMMMSMMMMMMO.",
      "..OOMMMSMMMMOO..",
      "....OMMSMMMO....",
      "....OMMSMMMO....",
      "....OMMSMMMO....",
      "....OMMSMMMO....",
      "....OMMMMMMO....",
      "....OOOOOOOO...."], {'O': O, 'H': H, 'M': M, 'D': D, 'S': S}, 'item/shade_chestplate.png')
icon(["................",
      "....OOOOOOOO....",
      "....OHHHHHHO....",
      "....OMMSSMMO....",
      "....OMMOOMMO....",
      "....OMSO.OSMO...",
      "....OMMO.OMMO...",
      "....OMMO.OMMO...",
      "....OMSO.OSMO...",
      "....OMMO.OMMO...",
      "....OMMO.OMMO...",
      "....OOOO.OOOO..."], {'O': O, 'H': H, 'M': M, 'D': D, 'S': S}, 'item/shade_leggings.png')
icon(["................",
      "................",
      "................",
      "................",
      "................",
      "...OOOO..OOOO...",
      "...OHMO..OMHO...",
      "...OMMO..OMMO...",
      "...OMSO..OSMO...",
      "..OMMMO..OMMMO..",
      "..OOOOO..OOOOO.."], {'O': O, 'H': H, 'M': M, 'D': D, 'S': S}, 'item/shade_boots.png')
W = (232, 228, 214); G = (180, 176, 160); K = (120, 116, 104)
icon(["................",
      "......WW........",
      ".....W..W..WW...",
      "....W....WW..W..",
      "...W..GG......W.",
      "..W..G..G.....W.",
      "..W.G....GG..W..",
      "...W.G.....GW...",
      "....W.GGG..W....",
      ".....W...WW.....",
      "......WWW.......",
      "........K.......",
      ".........K......",
      "..........K....."], {'W': W, 'G': G, 'K': K}, 'item/pale_silk.png')
icon(["................",
      "......SS........",
      ".....SHHS.......",
      "....SHMMHS......",
      "...SHMDDMHS.....",
      "...SMDOODMS.....",
      "...SMDOODMS.....",
      "...SHMDDMHS.....",
      "....SHMMHS......",
      ".....SHHS.......",
      "......SS........"], {'O': O, 'H': H, 'M': M, 'D': D, 'S': S}, 'item/shade_essence.png')
icon(["....GGGGGGGG....",
      "...GWWWWWWWWG...",
      "..GWWKWWWWKWWG..",
      "..GWKWWKKWWKWG..",
      "..GWWWKWWKWWWG..",
      "..GWWKWWWWKWWG..",
      "..GWKWWKKWWKWG..",
      "..GWWWKWWKWWWG..",
      "...GWKWWWWKWG...",
      "...GWWWKKWWWG...",
      "....GWKWWKWG....",
      ".....GWWWWG.....",
      "......GWWG......",
      ".......GG......."], {'W': W, 'G': (140, 134, 118), 'K': (190, 186, 170)}, 'item/pale_ward.png')

# ------------------------------------------------------------------ pale bone blocks (16x16)
def blk(fn, path, seed):
    a = bone_tex(16, 16, seed); fn(a); save(rgba(a), 'block/' + path)

def bricks(a, cracked=False):
    for y in range(16):
        for x in range(16):
            row = y // 4; off = 4 if row % 2 else 0
            if y % 4 == 3 or (x + off) % 8 == 7: a[y, x, :3] = BONE_D * (0.85 + 0.1 * ((x * 7 + y) % 3) / 2)
            elif y % 4 == 0: a[y, x, :3] = np.minimum(255, a[y, x, :3] * 1.05)
    if cracked:
        r = np.random.default_rng(3); x, y = 3, 0
        for _ in range(18):
            a[y % 16, x % 16, :3] = BONE_DD; x += r.integers(0, 2); y += 1
        x, y = 12, 5
        for _ in range(8): a[y % 16, x % 16, :3] = BONE_DD; x -= r.integers(0, 2); y += 1

def tiles(a):
    for y in range(16):
        for x in range(16):
            if y % 8 == 7 or x % 8 == 7: a[y, x, :3] = BONE_D * 0.9

def chiseled(a):
    a[0, :, :3] = a[15, :, :3] = a[:, 0, :3] = a[:, 15, :3] = BONE_D * 0.9
    m = ["................", "................", "................", "....DDDDDDDD....", "...D........D...",
         "...D.EE..EE.D...", "...D.EE..EE.D...", "...D........D...", "....D..DD..D....", ".....D....D.....",
         "......DTTD......", "......TDDT......", ".......DD.......", "................", "................", "................"]
    for y, r in enumerate(m):
        for x, ch in enumerate(r):
            if ch == 'D': a[y, x, :3] = BONE_D * 0.85
            if ch == 'E': a[y, x, :3] = [40, 6, 8]
            if ch == 'T': a[y, x, :3] = [240, 236, 220]

def pillar_side(a):
    for x in (0, 15): a[:, x, :3] = BONE_D
    for x in range(2, 14, 4): a[:, x, :3] = a[:, x, :3] * 0.88
    for y in (5, 6, 11): a[y, 1:15, :3] = a[y, 1:15, :3] * 0.8      # knuckled rings

def pillar_top(a):
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 6.5 < d < 7.6 or 3.2 < d < 4.0: a[y, x, :3] = BONE_D
            if d < 2.2: a[y, x, :3] = [150, 120, 110]          # marrow

blk(lambda a: bricks(a), 'pale_bone_bricks.png', 11)
blk(lambda a: bricks(a, True), 'cracked_pale_bone_bricks.png', 12)
blk(tiles, 'pale_bone_tiles.png', 13)
blk(chiseled, 'chiseled_pale_bone.png', 14)
blk(pillar_side, 'pale_bone_side.png', 15)
blk(pillar_top, 'pale_bone_top.png', 16)

# web floor: a translucent white mesh over the floor
w = np.zeros((16, 16, 4))
for y in range(16):
    for x in range(16):
        d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
        on = (x == y) or (x == 15 - y) or x == 7 or y == 8 or abs(d - 3) < 0.5 or abs(d - 6) < 0.5 or (abs(d - 8.6) < 0.5)
        if on: w[y, x] = [236, 232, 220, 210]
        elif rng.random() < 0.06: w[y, x] = [220, 216, 204, 120]
save(rgba(w), 'block/web_floor.png')
print('textures done')
