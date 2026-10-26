"""builds the shrine (lair.nbt) out of pale bone. run from shadow-mod/: python3 tools/make_lair.py"""
import math, random, nbt
random.seed(13)
SX, SY, SZ = 21, 13, 21; C = 10
blocks = {}
def put(x, y, z, name, props=None, tag=None):
    if 0 <= x < SX and 0 <= y < SY and 0 <= z < SZ: blocks[(x, y, z)] = (name, props or {}, tag)
T = 'theshadow:'
# clear the space (air in a dome) and lay the floor
for x in range(SX):
    for z in range(SZ):
        d = math.hypot(x - C, z - C)
        if d > 10.4: continue
        put(x, 0, z, T + ('pale_bone_tiles' if d < 4.5 else random.choice(['pale_bone_bricks'] * 4 + ['cracked_pale_bone_bricks', 'minecraft:bone_block' if d > 8 else 'pale_bone_bricks'])),
            {'axis': 'y'} if False else None)
        for y in range(1, SY): put(x, y, z, 'minecraft:air')
        if d > 9.2 and random.random() < 0.35: put(x, 0, z, 'minecraft:coarse_dirt')      # ragged edge into the ground
        elif 2.5 < d < 9 and random.random() < 0.12: put(x, 1, z, T + 'web_floor', {'temp': 'false'})
# fix bone_block axis
for k, v in list(blocks.items()):
    if v[0] == 'theshadow:minecraft:bone_block': blocks[k] = ('minecraft:bone_block', {'axis': 'y'}, None)
# ring of 8 bone pillars, some snapped off
for i in range(8):
    a = i * math.pi / 4 + math.pi / 8
    px, pz = round(C + math.cos(a) * 8), round(C + math.sin(a) * 8)
    h = [9, 6, 8, 4, 9, 7, 5, 8][i]
    for y in range(1, h + 1):
        put(px, y, pz, T + 'pale_bone', {'axis': 'y'})
    if h >= 8:
        put(px, h + 1, pz, 'minecraft:skeleton_skull', {'rotation': str((i * 2 + 12) % 16)})
        put(px, h, pz, T + 'chiseled_pale_bone')
    else:
        put(px, h, pz, T + 'cracked_pale_bone_bricks')
        bx, bz = px + random.choice([-1, 1]), pz                   # the broken-off top lies beside it
        put(bx, 1, bz, T + 'pale_bone', {'axis': 'x'})
    # a rib curving from each tall pillar toward the centre
    if h >= 8:
        for s in range(1, 4):
            rx, rz = round(px + (C - px) * s / 7), round(pz + (C - pz) * s / 7)
            put(rx, h - s + 1, rz, T + 'pale_bone_brick_wall', {'up': 'true', 'north': 'none', 'south': 'none', 'east': 'none', 'west': 'none', 'waterlogged': 'false'})
        put(round(px + (C - px) * 3 / 7), h - 3, round(pz + (C - pz) * 3 / 7), 'minecraft:chain', {'axis': 'y', 'waterlogged': 'false'})
        put(round(px + (C - px) * 3 / 7), h - 4, round(pz + (C - pz) * 3 / 7), 'minecraft:soul_lantern', {'hanging': 'true', 'waterlogged': 'false'})
# the altar: stepped pale bone, chest on top, candles
for x in range(C - 2, C + 3):
    for z in range(C - 2, C + 3):
        edge = max(abs(x - C), abs(z - C))
        if edge == 2:
            f = 'north' if z == C - 2 else 'south' if z == C + 2 else 'west' if x == C - 2 else 'east'
            if abs(x - C) == 2 and abs(z - C) == 2: put(x, 1, z, T + 'pale_bone_brick_slab', {'type': 'bottom', 'waterlogged': 'false'})
            else: put(x, 1, z, T + 'pale_bone_brick_stairs', {'facing': {'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west'}[f], 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'})
        else:
            put(x, 1, z, T + 'pale_bone_bricks')
            put(x, 2, z, T + ('chiseled_pale_bone' if edge == 0 else 'pale_bone_brick_slab'), None if edge == 0 else {'type': 'bottom', 'waterlogged': 'false'})
put(C, 3, C, 'minecraft:chest', {'facing': 'south', 'type': 'single', 'waterlogged': 'false'}, {'LootTable': (nbt.STR, 'theshadow:chests/lair'), 'id': (nbt.STR, 'minecraft:chest')})
for dx, dz, n in [(-1, -1, 3), (1, -1, 1), (-1, 1, 2), (1, 1, 4)]:
    put(C + dx, 3, C + dz, 'minecraft:black_candle', {'candles': str(n), 'lit': 'true', 'waterlogged': 'false'})
# the gate behind it: a pale arch with its face carved in the keystone
gz = C - 6
for x in range(C - 3, C + 4):
    for y in range(1, 8):
        side = abs(x - C) == 3; top = y >= 6
        if side or top:
            if top and abs(x - C) <= 2 and y == 6: put(x, y, gz, T + 'pale_bone_bricks')
            elif side: put(x, y, gz, T + ('pale_bone' if y < 6 else 'pale_bone_bricks'), {'axis': 'y'} if y < 6 else None)
            else: put(x, y, gz, T + 'pale_bone_bricks')
put(C, 7, gz, T + 'chiseled_pale_bone')
put(C, 8, gz, 'minecraft:skeleton_skull', {'rotation': '0'})
for x in range(C - 2, C + 3): put(x, 5, gz, 'minecraft:cobweb')
# the spawner his spiders crawl out of, under a web sheet in front of the altar
put(C, 0, C + 5, 'minecraft:spawner', None, {'id': (nbt.STR, 'minecraft:mob_spawner'),
    'SpawnData': (nbt.COMP, {'entity': (nbt.COMP, {'id': (nbt.STR, 'theshadow:pale_biter')})}),
    'SpawnCount': (nbt.SHORT, 2), 'MaxNearbyEntities': (nbt.SHORT, 4), 'RequiredPlayerRange': (nbt.SHORT, 14),
    'MinSpawnDelay': (nbt.SHORT, 300), 'MaxSpawnDelay': (nbt.SHORT, 700)})
put(C, 1, C + 5, T + 'web_floor', {'temp': 'false'})
# cobwebs in the corners of the ring, sculk creeping out from under the altar
for _ in range(14):
    a = random.random() * math.tau; d = random.uniform(6, 9)
    x, z = round(C + math.cos(a) * d), round(C + math.sin(a) * d)
    if blocks.get((x, 1, z), ('minecraft:air',))[0] == 'minecraft:air': put(x, random.choice([1, 2, 3]), z, 'minecraft:cobweb')
for _ in range(10):
    a = random.random() * math.tau; d = random.uniform(2.5, 5)
    put(round(C + math.cos(a) * d), 0, round(C + math.sin(a) * d), 'minecraft:sculk')

# write
pal, idx, out = [], {}, []
for (x, y, z), (n, p, tag) in sorted(blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
    key = (n, tuple(sorted(p.items())))
    if key not in idx:
        idx[key] = len(pal)
        e = {'Name': (nbt.STR, n)}
        if p: e['Properties'] = (nbt.COMP, {k: (nbt.STR, v) for k, v in p.items()})
        pal.append(e)
    b = {'pos': (nbt.LIST, (nbt.INT, [x, y, z])), 'state': (nbt.INT, idx[key])}
    if tag: b['nbt'] = (nbt.COMP, tag)
    out.append(b)
root = {'DataVersion': (nbt.INT, 3465), 'size': (nbt.LIST, (nbt.INT, [SX, SY, SZ])),
        'palette': (nbt.LIST, (nbt.COMP, pal)), 'blocks': (nbt.LIST, (nbt.COMP, out)), 'entities': (nbt.LIST, (nbt.COMP, []))}
nbt.save('src/main/resources/data/theshadow/structures/lair.nbt', root)
print(len(out), 'blocks,', len(pal), 'states')
