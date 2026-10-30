"""the shared colour palette for every rigid model (16 materials x 64 shades). run from shadow-mod/"""
import numpy as np
from PIL import Image
MATS = [((70, 62, 52), (232, 226, 208)),   # 0 bone
        ((34, 28, 26), (130, 118, 102)),   # 1 joint / dark bone
        ((7, 6, 9), (62, 56, 72)),         # 2 black chitin
        ((26, 8, 42), (160, 90, 220)),     # 3 purple sheen
        ((40, 4, 8), (150, 32, 40)),       # 4 flesh / gums
        ((170, 16, 16), (255, 236, 226)),  # 5 eye glow (red-white)
        ((118, 102, 78), (245, 238, 214)), # 6 tooth
        ((44, 44, 40), (168, 164, 148)),   # 7 husk skin
        ((50, 70, 16), (196, 228, 96)),    # 8 bile
        ((10, 8, 14), (78, 68, 96)),       # 9 blade metal
        ((120, 40, 210), (240, 210, 255)), # 10 glowing edge
        ((80, 18, 150), (226, 176, 255)),  # 11 violet glow
        ((2, 1, 3), (16, 11, 18)),         # 12 void
        ((48, 46, 48), (156, 152, 156)),   # 13 ash
        ((28, 2, 4), (112, 10, 14)),       # 14 blood
        ((168, 166, 158), (252, 252, 246))]# 15 silk
img = np.zeros((64, 256, 3))
rng = np.random.default_rng(3)
for m, (dk, lt) in enumerate(MATS):
    for y in range(64):
        s = 1 - y / 63.0                       # top row = fully lit
        c = np.array(dk) + (np.array(lt) - np.array(dk)) * (s ** 1.15)
        img[y, m * 16:(m + 1) * 16] = c + rng.normal(0, 3.0, (16, 3))
Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save('src/main/resources/assets/theshadow/textures/entity/minion_palette.png')
print('palette ok')
