"""blockstates/models/loot/recipes/lang for stage 3. run from shadow-mod/"""
import json, os
A = 'src/main/resources/assets/theshadow/'; D = 'src/main/resources/data/theshadow/'; M = 'theshadow:'
def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f: json.dump(obj, f, indent=1)

cube = lambda n, t=None: {"parent": "minecraft:block/cube_all", "textures": {"all": M + "block/" + (t or n)}}
# simple cubes
for n in ["pale_bone_bricks", "cracked_pale_bone_bricks", "chiseled_pale_bone", "pale_bone_tiles"]:
    w(A + f"blockstates/{n}.json", {"variants": {"": {"model": M + "block/" + n}}})
    w(A + f"models/block/{n}.json", cube(n))
# pillar
w(A + "blockstates/pale_bone.json", {"variants": {
    "axis=y": {"model": M + "block/pale_bone"}, "axis=z": {"model": M + "block/pale_bone_horizontal", "x": 90},
    "axis=x": {"model": M + "block/pale_bone_horizontal", "x": 90, "y": 90}}})
w(A + "models/block/pale_bone.json", {"parent": "minecraft:block/cube_column", "textures": {"end": M + "block/pale_bone_top", "side": M + "block/pale_bone_side"}})
w(A + "models/block/pale_bone_horizontal.json", {"parent": "minecraft:block/cube_column_horizontal", "textures": {"end": M + "block/pale_bone_top", "side": M + "block/pale_bone_side"}})
# stairs / slab / wall from the bricks
T = M + "block/pale_bone_bricks"; B = "pale_bone_brick"
for suf, par in [("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")]:
    w(A + f"models/block/{B}_stairs{suf}.json", {"parent": f"minecraft:block/{par}", "textures": {"bottom": T, "top": T, "side": T}})
v = {}
for facing, y in [("east", 0), ("south", 90), ("west", 180), ("north", 270)]:
    for half in ["bottom", "top"]:
        for shape in ["straight", "inner_left", "inner_right", "outer_left", "outer_right"]:
            mdl = M + f"block/{B}_stairs" + ("_inner" if "inner" in shape else "_outer" if "outer" in shape else "")
            yy = y
            if shape in ("inner_left", "outer_left"): yy = (y + 270) % 360
            if half == "top" and shape in ("inner_left", "outer_left"): yy = y
            if half == "top" and shape in ("inner_right", "outer_right"): yy = (y + 90) % 360
            e = {"model": mdl}
            if half == "top": e["x"] = 180
            if yy: e["y"] = yy
            if half == "top" or yy: e["uvlock"] = True
            v[f"facing={facing},half={half},shape={shape}"] = e
w(A + f"blockstates/{B}_stairs.json", {"variants": v})
w(A + f"models/block/{B}_slab.json", {"parent": "minecraft:block/slab", "textures": {"bottom": T, "top": T, "side": T}})
w(A + f"models/block/{B}_slab_top.json", {"parent": "minecraft:block/slab_top", "textures": {"bottom": T, "top": T, "side": T}})
w(A + f"blockstates/{B}_slab.json", {"variants": {"type=bottom": {"model": M + f"block/{B}_slab"}, "type=top": {"model": M + f"block/{B}_slab_top"},
                                                 "type=double": {"model": M + "block/pale_bone_bricks"}}})
for part in ["post", "side", "side_tall"]:
    w(A + f"models/block/{B}_wall_{part}.json", {"parent": f"minecraft:block/template_wall_{part}", "textures": {"wall": T}})
w(A + f"models/block/{B}_wall_inventory.json", {"parent": "minecraft:block/wall_inventory", "textures": {"wall": T}})
mp = [{"when": {"up": "true"}, "apply": {"model": M + f"block/{B}_wall_post"}}]
for d, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]:
    for h, m in [("low", "side"), ("tall", "side_tall")]:
        a = {"model": M + f"block/{B}_wall_{m}", "uvlock": True}
        if y: a["y"] = y
        mp.append({"when": {d: h}, "apply": a})
w(A + f"blockstates/{B}_wall.json", {"multipart": mp})
# web floor
w(A + "blockstates/web_floor.json", {"variants": {"temp=false": {"model": M + "block/web_floor"}, "temp=true": {"model": M + "block/web_floor"}}})
w(A + "models/block/web_floor.json", {"parent": "minecraft:block/carpet", "render_type": "minecraft:cutout", "textures": {"wool": M + "block/web_floor", "particle": M + "block/web_floor"}})

# item models
blocks = ["pale_bone", "pale_bone_bricks", "cracked_pale_bone_bricks", "chiseled_pale_bone", "pale_bone_tiles", f"{B}_stairs", f"{B}_slab"]
for n in blocks: w(A + f"models/item/{n}.json", {"parent": M + "block/" + n})
w(A + f"models/item/{B}_wall.json", {"parent": M + f"block/{B}_wall_inventory"})
w(A + "models/item/web_floor.json", {"parent": "minecraft:item/generated", "textures": {"layer0": M + "block/web_floor"}})
for n in ["pale_silk", "shade_essence", "shade_helmet", "shade_chestplate", "shade_leggings", "shade_boots"]:
    w(A + f"models/item/{n}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": M + "item/" + n}})
w(A + "models/item/pale_ward.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": M + "item/pale_ward"}})
for n in ["pale_biter_spawn_egg", "pale_spitter_spawn_egg"]: w(A + f"models/item/{n}.json", {"parent": "minecraft:item/template_spawn_egg"})

# block loot
def self_drop(n):
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": M + n}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]}
for n in blocks[:5] + [f"{B}_stairs", f"{B}_wall"]: w(D + f"loot_tables/blocks/{n}.json", self_drop(n))
w(D + f"loot_tables/blocks/{B}_slab.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": M + f"{B}_slab",
    "functions": [{"function": "minecraft:set_count", "count": 2, "conditions": [{"condition": "minecraft:block_state_property", "block": M + f"{B}_slab", "properties": {"type": "double"}}]},
                  {"function": "minecraft:explosion_decay"}]}]}]})
w(D + "loot_tables/blocks/web_floor.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": M + "web_floor"}],
    "conditions": [{"condition": "minecraft:match_tool", "predicate": {"items": ["minecraft:shears"]}}, {"condition": "minecraft:block_state_property", "block": M + "web_floor", "properties": {"temp": "false"}}]}]})

# entity loot
def mob(items):
    pools = []
    for name, lo, hi, chance in items:
        e = {"type": "minecraft:item", "name": name, "functions": [{"function": "minecraft:set_count", "count": {"min": lo, "max": hi}},
                                                                   {"function": "minecraft:looting_enchant", "count": {"min": 0, "max": 1}}]}
        p = {"rolls": 1, "entries": [e]}
        if chance < 1: p["conditions"] = [{"condition": "minecraft:killed_by_player"}, {"condition": "minecraft:random_chance_with_looting", "chance": chance, "looting_multiplier": 0.1}]
        pools.append(p)
    return {"type": "minecraft:entity", "pools": pools}
w(D + "loot_tables/entities/pale_biter.json", mob([(M + "pale_silk", 0, 2, 1), ("minecraft:string", 0, 2, 1), ("minecraft:bone", 0, 1, 1)]))
w(D + "loot_tables/entities/pale_spitter.json", mob([(M + "pale_silk", 1, 2, 1), ("minecraft:spider_eye", 0, 1, 1)]))
w(D + "loot_tables/entities/shade.json", mob([(M + "shade_essence", 1, 2, 1)]))

# recipes
R = D + "recipes/"
def shaped(n, pat, key, out, cnt=1): w(R + n + ".json", {"type": "minecraft:crafting_shaped", "pattern": pat, "key": {k: {"item": v} for k, v in key.items()}, "result": {"item": out, "count": cnt}})
def cut(n, src, out, cnt=1): w(R + n + ".json", {"type": "minecraft:stonecutting", "ingredient": {"item": src}, "result": M + out, "count": cnt})
def smelt(n, src, out): w(R + n + ".json", {"type": "minecraft:smelting", "ingredient": {"item": src}, "result": M + out, "experience": 0.1, "cookingtime": 200})
smelt("pale_bone", "minecraft:bone_block", "pale_bone")             # bone, bleached in the fire
shaped("pale_bone_bricks", ["##", "##"], {"#": M + "pale_bone"}, M + "pale_bone_bricks", 4)
shaped("pale_bone_tiles", ["##", "##"], {"#": M + "pale_bone_bricks"}, M + "pale_bone_tiles", 4)
smelt("cracked_pale_bone_bricks", M + "pale_bone_bricks", "cracked_pale_bone_bricks")
shaped("chiseled_pale_bone", ["#", "#"], {"#": M + f"{B}_slab"}, M + "chiseled_pale_bone")
shaped(f"{B}_stairs", ["#  ", "## ", "###"], {"#": M + "pale_bone_bricks"}, M + f"{B}_stairs", 4)
shaped(f"{B}_slab", ["###"], {"#": M + "pale_bone_bricks"}, M + f"{B}_slab", 6)
shaped(f"{B}_wall", ["###", "###"], {"#": M + "pale_bone_bricks"}, M + f"{B}_wall", 6)
for src in ["pale_bone", "pale_bone_bricks"]:
    cut(f"{B}_stairs_from_{src}_cutting", M + src, f"{B}_stairs"); cut(f"{B}_slab_from_{src}_cutting", M + src, f"{B}_slab", 2)
    cut(f"{B}_wall_from_{src}_cutting", M + src, f"{B}_wall"); cut(f"chiseled_pale_bone_from_{src}_cutting", M + src, "chiseled_pale_bone")
    cut(f"pale_bone_tiles_from_{src}_cutting", M + src, "pale_bone_tiles")
cut("pale_bone_bricks_from_pale_bone_cutting", M + "pale_bone", "pale_bone_bricks")
shaped("web_floor", ["SS"], {"S": M + "pale_silk"}, M + "web_floor", 3)
shaped("pale_ward", ["SBS", "SHS", "SBS"], {"S": M + "pale_silk", "B": M + "pale_bone", "H": "minecraft:shield"}, M + "pale_ward")
for piece, base in [("helmet", "diamond_helmet"), ("chestplate", "diamond_chestplate"), ("leggings", "diamond_leggings"), ("boots", "diamond_boots")]:
    n = {"helmet": 5, "chestplate": 8, "leggings": 7, "boots": 4}[piece]
    w(R + f"shade_{piece}.json", {"type": "minecraft:crafting_shapeless",
        "ingredients": [{"item": "minecraft:" + base}] + [{"item": M + "shade_essence"}] * n, "result": {"item": M + f"shade_{piece}"}})

# block tags: pickaxe, walls
w("src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json", {"replace": False, "values": [M + n for n in blocks + [f"{B}_wall"]]})
w("src/main/resources/data/minecraft/tags/blocks/walls.json", {"replace": False, "values": [M + f"{B}_wall"]})
w("src/main/resources/data/minecraft/tags/items/walls.json", {"replace": False, "values": [M + f"{B}_wall"]})
w("src/main/resources/data/minecraft/tags/blocks/stairs.json", {"replace": False, "values": [M + f"{B}_stairs"]})
w("src/main/resources/data/minecraft/tags/blocks/slabs.json", {"replace": False, "values": [M + f"{B}_slab"]})

# lang
p = A + "lang/en_us.json"; L = json.load(open(p))
L.update({
    "entity.theshadow.pale_biter": "Pale Biter", "entity.theshadow.pale_spitter": "Pale Spitter", "entity.theshadow.web_shot": "Web",
    "item.theshadow.pale_silk": "Pale Silk", "item.theshadow.shade_essence": "Shade Essence",
    "item.theshadow.pale_ward": "Pale Ward", "item.theshadow.pale_ward.desc": "Hold it in either hand: nothing shot at you lands.",
    "item.theshadow.shade_helmet": "Shade Helmet", "item.theshadow.shade_chestplate": "Shade Chestplate",
    "item.theshadow.shade_leggings": "Shade Leggings", "item.theshadow.shade_boots": "Shade Boots",
    "item.theshadow.shade_armor.desc": "Woven from what's left of its shades.",
    "item.theshadow.shade_armor.set": "Each piece: 6% less from its creatures. Full set: darkness can't touch you.",
    "item.theshadow.pale_biter_spawn_egg": "Pale Biter Spawn Egg", "item.theshadow.pale_spitter_spawn_egg": "Pale Spitter Spawn Egg",
    "block.theshadow.pale_bone": "Pale Bone", "block.theshadow.pale_bone_bricks": "Pale Bone Bricks",
    "block.theshadow.cracked_pale_bone_bricks": "Cracked Pale Bone Bricks", "block.theshadow.chiseled_pale_bone": "Chiseled Pale Bone",
    "block.theshadow.pale_bone_tiles": "Pale Bone Tiles", "block.theshadow.pale_bone_brick_stairs": "Pale Bone Brick Stairs",
    "block.theshadow.pale_bone_brick_slab": "Pale Bone Brick Slab", "block.theshadow.pale_bone_brick_wall": "Pale Bone Brick Wall",
    "block.theshadow.web_floor": "Web Floor"})
json.dump(L, open(p, 'w'), indent=1, ensure_ascii=False)
print("data done")
