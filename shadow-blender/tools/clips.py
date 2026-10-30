"""
Mansoority's animation clips, authored as code: the body is posed directly and the four limbs are placed with
two-bone IK, so feet and hands stay planted on the ground instead of sliding or sinking.
writes shadow.anim (SHA2) for the mod and anim.json for the viewer page.
usage: python3 clips.py <mesh.SHD2 or SHD3> <out dir>
"""
import sys, os, json, math, struct
import numpy as np
from rig import ORDER, PIVOT, J, LIMBS, qeuler, qmul, fk, two_bone, end_pos, set_pivots, xf, reach_assist

PI = math.pi
SIDE = {"L": -1.0, "R": 1.0}
REST_FOOT = {"legL": J["toeL"].copy(), "legR": J["toeR"].copy(), "armL": J["handL"].copy(), "armR": J["handR"].copy()}
GROUND = {k: float(v[1]) for k, v in REST_FOOT.items()}          # height of each contact point when standing flat


def ease(t): t = min(1.0, max(0.0, t)); return t * t * (3 - 2 * t)
def ease_out(t): t = min(1.0, max(0.0, t)); return 1 - (1 - t) ** 3
def ease_in(t): t = min(1.0, max(0.0, t)); return t ** 3
def lerp(a, b, t): return a + (b - a) * t
def wob(t, *f):
    """organic drift: a few incommensurate sines (period in frames, amplitude) pairs"""
    return sum(a * math.sin(2 * PI * t / p + i * 1.7) for i, (p, a) in enumerate(f))


class P:
    """one frame of pose being built: euler rotations + offsets per bone, then IK targets for the limbs"""
    def __init__(self):
        self.r = {b: np.zeros(3) for b in ORDER}
        self.t = {b: np.zeros(3) for b in ORDER}
        self.feet = {k: v.copy() for k, v in REST_FOOT.items()}
        self.pole = {}
        self.free = set()           # limbs posed by hand (FK) instead of planted
        self.contact = {k: True for k in REST_FOOT}
        self.tongue(0.0)

    def tongue(self, out, pitch=0.0, wave=0.0, phase=0.0, droop=0.0, side=0.0):
        """out 0 = folded away down its throat, 1 = all the way out. the segments telescope into each other.
        pitch tips the whole tongue (- up), wave sends a ripple down it, droop lets the tip hang."""
        if "tongue0" not in ORDER: return self
        d = PIVOT["tongue1"] - PIVOT["tongue0"]; seg = float(np.linalg.norm(d)); d = d / seg
        r = 1.0 - out
        self.t["tongue0"] = -d * 0.075 * r
        for i in range(1, 6):
            self.t["tongue%d" % i] = -d * seg * 0.94 * r
        self.r["tongue0"] = np.array([pitch, side * 0.4, 0.0])
        for i in range(1, 6):
            k = i / 5
            self.r["tongue%d" % i] = np.array([wave * math.sin(phase - i * 0.75) + droop * k * 0.35, side * 0.15 * math.sin(phase * 0.5 - i), 0.0])
        return self

    def rot(self, bone, x=0, y=0, z=0): self.r[bone] += (x, y, z); return self
    def off(self, bone, x=0, y=0, z=0): self.t[bone] += (x, y, z); return self

    def solve(self):
        pose = {b: (qeuler(*self.r[b]), self.t[b].copy()) for b in ORDER}
        for limb in ("legL", "legR", "armL", "armR"):
            if limb in self.free: continue
            s = SIDE[limb[-1]]
            if limb.startswith("leg"):
                pole = self.pole.get(limb, np.array([0.35 * s, 0.75, 1.0]))     # knees forward and a bit out
            else:
                pole = self.pole.get(limb, np.array([1.6 * s, 1.4, 0.9]))       # the extra joint juts out to the side
            reach_assist(pose, limb, self.feet[limb])
            W = fk(pose)
            up = LIMBS[limb][0]
            base = xf(W[up], PIVOT[up])
            two_bone(pose, limb, self.feet[limb], base + pole)
            # IK replaced the rotation: add back any twist/curl authored on the lower bone tip (hands/feet)
        return pose


# ---------------------------------------------------------------- shared stances
def spider(p, k, wide=1.0):
    """the low spider form: body drops and tips forward, limbs splay out with the joints high, face forward"""
    p.off("hips", 0, -0.66 * k, -0.10 * k)
    p.rot("hips", 0.80 * k).rot("chest", 0.30 * k).rot("neck", -0.85 * k).rot("head", -0.55 * k)
    p.rot("armL", 0.25 * k, 0, 0.30 * k).rot("armR", 0.25 * k, 0, -0.30 * k)     # elbows swing up and out like spider legs
    for limb, s in (("legL", -1), ("legR", 1)):
        p.feet[limb] += np.array([0.48 * s * wide, 0, -0.22]) * k
        p.pole[limb] = np.array([lerp(0.35, 1.6, k) * s, lerp(0.75, 1.6, k), lerp(1.0, 0.1, k)])
    for limb, s in (("armL", -1), ("armR", 1)):
        p.feet[limb] += np.array([0.40 * s * wide, 0, 0.30]) * k
        p.pole[limb] = np.array([lerp(1.6, 0.9, k) * s, lerp(1.4, 2.8, k), lerp(0.9, 0.6, k)])     # the extra joint arches up high
    return p


def breathe(p, f, amt=1.0, period=72):
    b = math.sin(2 * PI * f / period)
    p.rot("chest", -0.035 * b * amt).off("hips", 0, 0.006 * b * amt, 0)
    p.rot("neck", 0.02 * b * amt).rot("head", 0.015 * b * amt)
    p.rot("armL", 0, 0, -0.012 * b * amt).rot("armR", 0, 0, 0.012 * b * amt)
    return b


def gait(p, f, cycle, stride, lift, duty, phases, speed_z=True, height=None):
    """quadruped stepping: each limb plants (slides back with the body's travel) then swings forward in an arc.
    returns which limbs touch the ground this frame."""
    for limb, ph in phases.items():
        u = ((f / cycle) + ph) % 1.0
        if u < duty:                                   # stance: planted, the body moves over it
            a = u / duty
            dz = lerp(stride / 2, -stride / 2, a)
            dy = 0.0
            p.contact[limb] = True
        else:                                          # swing: lift, reach forward, put it down
            a = (u - duty) / (1 - duty)
            dz = lerp(-stride / 2, stride / 2, ease(a))
            dy = lift * math.sin(PI * a) ** 0.8
            p.contact[limb] = False
        p.feet[limb] = p.feet[limb] + np.array([0, dy, dz])
    return p


CLIPS = {}
def clip(name, frames, loop=True, speed=0.0):
    def deco(fn): CLIPS[name] = (frames, loop, speed, fn); return fn
    return deco


# ---------------------------------------------------------------- idle: towering, never still
@clip("idle", 160)
def idle(f):
    p = P()
    breathe(p, f, 1.0, 80)
    sh = math.sin(2 * PI * f / 160)                                       # slow weight shift side to side
    p.off("hips", 0.035 * sh, -0.01 * abs(sh), 0).rot("hips", 0, 0, -0.04 * sh).rot("chest", 0, 0, 0.03 * sh)
    p.rot("head", wob(f, (160, 0.05), (80, 0.03)), wob(f, (160, 0.18), (53.3, 0.05)), 0.10 + wob(f, (80, 0.12), (32, 0.03)))
    p.rot("neck", wob(f, (160, 0.04)), 0, 0)
    # one hand at a time curls its fingers (lift the hand a touch, then set it back)
    c = max(0.0, math.sin(2 * PI * f / 160 + 0.6)) ** 3
    p.feet["armR"] = p.feet["armR"] + np.array([0, 0.05 * c, -0.02 * c]); p.contact["armR"] = c < 0.2
    c2 = max(0.0, math.sin(2 * PI * f / 160 + 0.6 + PI)) ** 3
    p.feet["armL"] = p.feet["armL"] + np.array([0, 0.05 * c2, -0.02 * c2]); p.contact["armL"] = c2 < 0.2
    return p


# ---------------------------------------------------------------- peek: lurking low behind cover, head tipped over
@clip("peek", 120)
def peek(f):
    p = spider(P(), 0.72)
    breathe(p, f, 1.4, 60)
    sw = math.sin(2 * PI * f / 120)
    p.off("hips", 0.05 * sw, 0, 0).rot("chest", 0, 0, -0.06 * sw)
    p.rot("head", 0.05 * math.sin(2 * PI * f / 60), 0.1 * sw, 0.55 + 0.08 * math.sin(2 * PI * f / 40))
    return p


# ---------------------------------------------------------------- walk: long, heavy stilt strides (lateral sequence)
WALK_CYCLE, WALK_STRIDE = 48, 0.95
@clip("walk", WALK_CYCLE, speed=WALK_STRIDE / WALK_CYCLE)
def walk(f):
    p = P()
    gait(p, f, WALK_CYCLE, WALK_STRIDE, 0.13, 0.76, {"legL": 0.0, "armL": 0.25, "legR": 0.5, "armR": 0.75})
    ph = 2 * PI * f / WALK_CYCLE
    p.off("hips", 0.05 * math.sin(ph), -0.035 * abs(math.cos(ph)), 0)               # weight rolls over each planted foot
    p.rot("hips", 0.03, 0.06 * math.sin(ph), -0.06 * math.sin(ph))
    p.rot("chest", 0.08, -0.08 * math.sin(ph), 0.05 * math.sin(ph))
    p.rot("armL", -0.10 * math.sin(ph + 0.5 * PI), 0, 0).rot("armR", 0.10 * math.sin(ph + 0.5 * PI), 0, 0)   # shoulders roll
    p.rot("neck", 0.05, 0.05 * math.sin(ph), 0).rot("head", -0.03 * math.cos(2 * ph), -0.04 * math.sin(ph), 0.12 * math.sin(ph * 0.5))
    breathe(p, f, 0.5, WALK_CYCLE)
    return p


# ---------------------------------------------------------------- crawl (stare B): low, head upside down, coming at you
CRAWL_CYCLE, CRAWL_STRIDE = 64, 0.55
@clip("crawl", CRAWL_CYCLE, speed=CRAWL_STRIDE / CRAWL_CYCLE)
def crawl(f):
    p = spider(P(), 0.9)
    gait(p, f, CRAWL_CYCLE, CRAWL_STRIDE, 0.16, 0.78, {"legL": 0.0, "armL": 0.25, "legR": 0.5, "armR": 0.75})
    ph = 2 * PI * f / CRAWL_CYCLE
    p.off("hips", 0.06 * math.sin(ph), 0.015 * math.sin(2 * ph), 0).rot("hips", 0, 0.1 * math.sin(ph), 0)
    p.rot("head", 0.25, 0, 2.95 + 0.08 * math.sin(2 * ph))
    return p


# ---------------------------------------------------------------- stare A: still, leans in, arms lift off and open, head rolls over
@clip("stareA", 80, loop=False)
def stareA(f):
    p = P()
    lean = ease(f / 24); op = ease((f - 14) / 50); roll = ease((f - 30) / 50)
    p.rot("chest", 0.17 * lean).rot("neck", 0.16 * lean).rot("head", 0, 0, 0.9 * roll)
    p.off("hips", 0, -0.04 * lean, 0.03 * lean)
    for limb, s in (("armL", -1), ("armR", 1)):                    # hands come off the ground and rise toward you, fingers open
        p.feet[limb] = p.feet[limb] + np.array([0.45 * s * op, 1.15 * op, 0.55 * op])
        p.pole[limb] = np.array([2.0 * s, 1.0 + op, 0.4])
        p.contact[limb] = op < 0.05
    p.rot("armL", 0, 0, 0.25 * op).rot("armR", 0, 0, -0.25 * op)
    tg = ease((f - 48) / 30)
    if tg > 0: p.tongue(0.4 * tg, pitch=0.25, wave=0.12 * tg, phase=f * 0.25, droop=0.9)
    if f > 60:                                                      # the head starts to tremble
        p.rot("head", 0.03 * math.sin(f * 2.1), 0.03 * math.sin(f * 1.7), 0.04 * math.sin(f * 2.9))
    return p


# ---------------------------------------------------------------- neck (stare D): the neck stretches out at you
@clip("neck", 80, loop=False)
def neck(f):
    p = P()
    a = ease_out((f - 6) / 60)
    p.rot("chest", 0.1 * a).rot("neck", 0.5 * a).rot("head", -0.45 * a, 0, 0.4 * a)
    p.off("neck", 0, 0.05 * a, 0.18 * a).off("head", 0, 0.03 * a, 0.10 * a)
    p.rot("armL", 0.05 * a, 0, 0.2 * a).rot("armR", 0.05 * a, 0, -0.2 * a)
    if f < 8: p.rot("neck", -0.12 * math.sin(f / 8 * PI))           # pulls back first (anticipation)
    return p


# ---------------------------------------------------------------- fold: standing -> spider, in jerks, bones cracking
FOLD_STEPS = (10, 22, 32, 42, 50)
@clip("fold", 64, loop=False)
def fold(f):
    k = 0.0
    for i, at in enumerate(FOLD_STEPS):              # each step: a sharp snap (4 frames), then a hold with a tremor
        k += ease_out((f - at) / 4.0) / len(FOLD_STEPS)
    k = ease(min(1.0, k * 1.0))
    p = spider(P(), k)
    snap = sum(math.exp(-((f - at) / 2.0) ** 2) for at in FOLD_STEPS)
    tw = (1 if int(f / 10) % 2 else -1)
    p.rot("head", 0.15 * snap, 0.2 * snap * tw, 0.6 * (1 - k) * tw + 0.35 * snap * tw)
    p.rot("chest", -0.08 * snap, 0, 0.05 * snap * tw)
    p.rot("hips", 0.02 * math.sin(f * 1.9) * (1 - k * 0.5), 0, 0)
    return p


# ---------------------------------------------------------------- scuttle: low and fast, diagonal pairs, twitchy head
SCUT_CYCLE, SCUT_STRIDE = 16, 0.62
@clip("scuttle", SCUT_CYCLE, speed=SCUT_STRIDE / SCUT_CYCLE)
def scuttle(f):
    p = spider(P(), 1.0, 1.05)
    gait(p, f, SCUT_CYCLE, SCUT_STRIDE, 0.18, 0.5, {"legL": 0.0, "armR": 0.0, "legR": 0.5, "armL": 0.5})
    ph = 2 * PI * f / SCUT_CYCLE
    p.off("hips", 0.035 * math.sin(ph), 0.03 * abs(math.sin(ph)), 0).rot("hips", 0.04 * math.sin(2 * ph), 0.12 * math.sin(ph), 0.05 * math.sin(ph))
    p.rot("chest", 0, -0.1 * math.sin(ph), 0)
    p.rot("head", 0.06 * math.sin(2 * ph + 1), 0.08 * math.sin(ph * 3 + 2), 0.12 * math.sin(ph + 1))
    p.tongue(0.55, pitch=0.35, wave=0.3, phase=ph * 2, droop=0.8, side=math.sin(ph))
    return p


# ---------------------------------------------------------------- grab: one long arm shoots out at you
@clip("grab", 24, loop=False)
def grab(f):
    p = P()
    wind = ease(f / 7) * (1 - ease((f - 7) / 3))              # coils back and down first
    reach = ease_out((f - 8) / 5)
    over = math.exp(-((f - 13) / 2.2) ** 2)                    # overshoots past you, then snaps back
    close = ease((f - 14) / 6)                                 # fingers closing round you
    p.rot("chest", -0.12 * wind + 0.2 * reach + 0.08 * over, 0.18 * wind - 0.15 * reach, -0.06 * wind)
    p.rot("neck", -0.1 * wind + 0.18 * reach).rot("head", 0.1 * wind - 0.15 * reach, 0, 0.2 * over)
    p.off("hips", 0, -0.08 * wind, -0.05 * wind + 0.1 * reach)
    p.free.add("armR"); p.contact["armR"] = False
    p.rot("armR", 0.6 * reach + 0.15 * over - 0.3 * wind, 0, -0.1 * wind)
    p.rot("foreR", -1.3 * reach - 0.4 * wind - 0.2 * over).rot("handR", -0.4 * reach + 1.2 * close)
    p.feet["armL"] = p.feet["armL"] + np.array([0.08, 0, 0.12 * reach])     # the other hand braces
    return p


# ---------------------------------------------------------------- lick: you, up at its face; it leans in again and again
@clip("lick", 40)
def lick(f):
    p = P()
    ph = 2 * PI * f / 40
    p.free.add("armR"); p.contact["armR"] = False
    pull = 0.5 + 0.5 * math.sin(ph - 0.8)                    # it pulls you in to its face and pushes you back out
    p.rot("armR", 0.5 + 0.12 * pull, 0.08 * math.sin(ph * 2), 0.05).rot("foreR", -1.95 + 0.25 * pull).rot("handR", 0.9 + 0.15 * math.sin(ph * 3))
    lean = 0.5 + 0.5 * math.sin(ph)
    heave = math.sin(ph * 2)                                  # the chest heaves as it breathes on you
    p.rot("chest", 0.08 * lean - 0.05 * heave, 0.08 * math.sin(ph), 0.04 * math.sin(ph + 1))
    p.rot("neck", 0.32 + 0.14 * lean, 0.1 * math.sin(ph + 0.4), 0)
    p.rot("head", 0.28 + 0.15 * lean + 0.05 * math.sin(f * 1.3), 0.15 * math.sin(ph * 2 + 1), 0.3 * math.sin(ph + 0.6) + 0.06 * math.sin(f * 2.1))
    p.off("neck", 0, 0, 0.05 * lean).off("hips", 0.03 * math.sin(ph), -0.02 * abs(heave), 0)
    p.feet["armL"] = p.feet["armL"] + np.array([0, 0.06 * max(0, math.sin(ph * 2)), 0])   # the free hand twitches
    out = ease_out(f / 9) * (1 - ease((f - 31) / 9))
    stroke = 2 * PI * (f - 6) / 13                                   # two long strokes upward per cycle
    p.tongue(out, pitch=-0.55 * out - 0.25 * math.sin(stroke) * out, wave=0.42 * out, phase=stroke, droop=0.2 * (1 - out), side=0.4 * math.sin(ph))
    return p


# ================================================================ the boss: Mansoority in the End
def predator(p, k=1.0):
    """the fighting stance: half-crouched, head low and forward, limbs wide and ready"""
    spider(p, 0.38 * k, 1.1)
    p.rot("neck", 0.2 * k).rot("head", 0.15 * k)
    return p

def belly(p, amt):
    """the stomach splits and the two halves swing out like doors"""
    if "bellyL" in ORDER:
        p.rot("bellyL", 0, -1.25 * amt, 0).rot("bellyR", 0, 1.25 * amt, 0)
    return p

@clip("boss_idle", 120)
def boss_idle(f):
    p = predator(P())
    breathe(p, f, 2.2, 40)                                          # heavy, fast breathing
    sw = math.sin(2 * PI * f / 120)
    p.off("hips", 0.06 * sw, 0, 0).rot("chest", 0, 0.06 * sw, -0.05 * sw)
    p.rot("head", wob(f, (60, 0.06)), wob(f, (120, 0.25), (40, 0.05)), 0.15 * math.sin(2 * PI * f / 60))
    p.tongue(0.0)
    return p

BW_CYCLE, BW_STRIDE = 56, 1.15
@clip("boss_walk", BW_CYCLE, speed=BW_STRIDE / BW_CYCLE)
def boss_walk(f):
    p = predator(P())
    gait(p, f, BW_CYCLE, BW_STRIDE, 0.28, 0.7, {"legL": 0.0, "armL": 0.25, "legR": 0.5, "armR": 0.75})
    ph = 2 * PI * f / BW_CYCLE
    p.off("hips", 0.07 * math.sin(ph), -0.05 * abs(math.cos(ph)), 0).rot("hips", 0, 0.08 * math.sin(ph), -0.07 * math.sin(ph))
    p.rot("chest", 0, -0.1 * math.sin(ph), 0.06 * math.sin(ph))
    p.rot("head", 0, -0.06 * math.sin(ph), 0.08 * math.sin(ph * 0.5))
    return p

@clip("roar", 70, loop=False)
def roar(f):
    """rears up, arms off the ground and thrown wide, everything shaking"""
    p = predator(P(), 1 - ease((f - 4) / 14) * 0.8)
    up = ease_out((f - 6) / 14) * (1 - ease((f - 58) / 12))
    p.rot("chest", -0.45 * up).rot("neck", -0.35 * up).rot("head", -0.45 * up).off("hips", 0, 0.12 * up, -0.05 * up)
    for limb, s in (("armL", -1), ("armR", 1)):
        p.feet[limb] = p.feet[limb] + np.array([0.55 * s * up, 1.7 * up, -0.05 * up])
        p.pole[limb] = np.array([1.6 * s, 2.2, -0.3])
        p.contact[limb] = up < 0.05
    if 14 < f < 58:
        sh = (f - 14) * 2.7
        p.rot("head", 0.06 * math.sin(sh), 0.05 * math.sin(sh * 1.3), 0.08 * math.sin(sh * 0.9))
        p.rot("chest", 0.02 * math.sin(sh * 1.7))
    belly(p, 0.15 * up)
    return p

@clip("scream", 60, loop=False)
def scream(f):
    """low and forward: the neck stretches at you while it screams"""
    p = predator(P(), 1.0 + 0.25 * ease(f / 10))
    a = ease_out((f - 4) / 10) * (1 - ease((f - 50) / 10))
    p.rot("neck", 0.45 * a).rot("head", -0.5 * a).off("neck", 0, 0.03 * a, 0.16 * a).off("head", 0, 0, 0.08 * a)
    if 10 < f < 50: p.rot("head", 0.05 * math.sin(f * 3.1), 0.04 * math.sin(f * 2.3), 0.07 * math.sin(f * 2.7))
    p.tongue(0.35 * a, pitch=0.5, wave=0.25, phase=f * 0.6, droop=1.0)
    return p

@clip("slam", 80, loop=False)
def slam(f):
    """crouch, leap, crash down on both hands, recover. the server moves it through the air between 22 and 44"""
    crouch = ease((f - 0) / 18) * (1 - ease((f - 18) / 4))
    air = ease((f - 22) / 6) * (1 - ease((f - 40) / 4))
    hit = math.exp(-((f - 46) / 3.5) ** 2)
    rec = ease((f - 52) / 24)
    p = predator(P(), 1.0 + 0.6 * crouch + 0.7 * hit - 0.4 * air)
    p.rot("chest", -0.25 * air + 0.35 * hit).rot("head", -0.3 * air + 0.25 * hit)
    for limb, s in (("armL", -1), ("armR", 1)):                       # arms swing up in the air, then down
        p.feet[limb] = p.feet[limb] + np.array([0.3 * s * air, 1.5 * air, -0.35 * air - 0.25 * crouch])
        p.pole[limb] = np.array([1.8 * s, 2.0, 0.2])
        p.contact[limb] = air < 0.1
    for limb in ("legL", "legR"):
        p.feet[limb] = p.feet[limb] + np.array([0, 0.35 * air, 0.15 * air])
        p.contact[limb] = air < 0.1
    p.off("hips", 0, -0.12 * hit * (1 - rec), 0)
    return p

@clip("bite", 60, loop=False)
def bite(f):
    """draws its head back, then the neck snaps out at you"""
    p = predator(P())
    back = ease((f - 0) / 14) * (1 - ease((f - 14) / 3))
    out = ease_out((f - 15) / 5) * (1 - ease((f - 40) / 18))
    p.rot("chest", -0.15 * back + 0.25 * out).rot("neck", -0.35 * back + 0.55 * out).rot("head", 0.3 * back - 0.45 * out)
    p.off("neck", 0, 0.02 * out, -0.08 * back + 0.22 * out).off("head", 0, 0, 0.12 * out)
    p.off("hips", 0, 0, -0.06 * back + 0.12 * out)
    return p

@clip("chew", 24)
def chew(f):
    """you're in its mouth: the head thrashes side to side"""
    p = predator(P())
    ph = 2 * PI * f / 24
    p.rot("neck", 0.35, 0.25 * math.sin(ph), 0).rot("head", -0.2 + 0.12 * math.sin(2 * ph), 0.35 * math.sin(ph + 0.5), 0.3 * math.sin(ph))
    p.off("neck", 0, 0, 0.12)
    return p

@clip("spray", 90, loop=False)
def spray(f):
    """stands tall, arms thrown back, the stomach opens and empties itself at you"""
    up = ease((f - 2) / 14) * (1 - ease((f - 78) / 12))
    p = predator(P(), 1 - 0.7 * up)
    opn = ease_out((f - 12) / 10) * (1 - ease((f - 74) / 10))
    heave = math.sin(f * 0.9) * opn if 22 < f < 72 else 0
    p.rot("chest", -0.3 * up + 0.06 * heave).rot("neck", 0.2 * up).rot("head", 0.25 * up).off("hips", 0, 0.1 * up, 0)
    for limb, s in (("armL", -1), ("armR", 1)):
        p.feet[limb] = p.feet[limb] + np.array([0.7 * s * up, 1.3 * up, -0.55 * up])
        p.pole[limb] = np.array([1.6 * s, 2.0, -0.6])
        p.contact[limb] = up < 0.05
    belly(p, opn * (1 + 0.08 * heave))
    return p

@clip("spikes", 60, loop=False)
def spikes(f):
    """rears, then drives both hands into the ground"""
    up = ease_out((f - 2) / 14) * (1 - ease((f - 18) / 4))
    down = ease((f - 20) / 4) * (1 - ease((f - 46) / 14))
    p = predator(P(), 1 + 0.5 * down - 0.5 * up)
    p.rot("chest", -0.3 * up + 0.4 * down).rot("head", -0.2 * up + 0.3 * down)
    for limb, s in (("armL", -1), ("armR", 1)):
        p.feet[limb] = p.feet[limb] + np.array([0.1 * s * up, 1.4 * up - 0.25 * down, 0.2 * up - 0.3 * down])
        p.contact[limb] = up < 0.05 and down < 0.05          # buried in the ground: not "standing" on it
    return p

@clip("stun", 48)
def stun(f):
    """dazed: slumped to one side, head hanging, jaw slack, twitching"""
    p = predator(P(), 1.3)
    ph = 2 * PI * f / 48
    p.off("hips", -0.1, -0.12, 0).rot("hips", 0, 0, 0.18).rot("chest", 0.25, 0, 0.15 + 0.03 * math.sin(ph))
    p.rot("neck", 0.5).rot("head", 0.5, 0.1 * math.sin(ph), 0.5 + 0.05 * math.sin(ph * 2))
    if f % 16 < 2: p.rot("head", 0.1, 0, -0.15)
    p.tongue(0.5, pitch=0.9, droop=1.0, wave=0.1, phase=ph)
    return p

@clip("collapse", 50, loop=False)
def collapse(f):
    """it dies: legs give, it folds over onto its side"""
    k = ease(f / 34); thud = math.exp(-((f - 34) / 3) ** 2)
    p = spider(P(), 0.4 + 0.9 * k, 1.3)
    p.off("hips", 0, -0.45 * k - 0.04 * thud, 0).rot("hips", 0.1 * k, 0, 1.15 * k).rot("chest", 0.3 * k, 0, 0.35 * k)
    p.rot("neck", 0.5 * k).rot("head", 0.4 * k, 0.3 * k, 0.9 * k)
    p.tongue(0.6 * k, pitch=1.0, droop=1.0)
    return p

@clip("dead", 40)
def dead(f):
    p = collapse(49)
    if f in (11, 12, 27): p.rot("handR", -0.3)            # a finger twitches
    return p

def blend(a, b, k):
    """a pose part-way between two poses (rotations, offsets, foot targets, poles)"""
    p = P()
    for bn in ORDER:
        p.r[bn] = a.r[bn] * (1 - k) + b.r[bn] * k
        p.t[bn] = a.t[bn] * (1 - k) + b.t[bn] * k
    for limb in p.feet:
        p.feet[limb] = a.feet[limb] * (1 - k) + b.feet[limb] * k
        pa, pb = a.pole.get(limb), b.pole.get(limb)
        if pa is not None or pb is not None:
            dflt = np.array([0.35 * SIDE[limb[-1]], 0.75, 1.0]) if limb.startswith("leg") else np.array([1.6 * SIDE[limb[-1]], 1.4, 0.9])
            p.pole[limb] = (pa if pa is not None else dflt) * (1 - k) + (pb if pb is not None else dflt) * k
    return p

@clip("rise", 70, loop=False)
def rise(f):
    """...and it gets back up, wrong, in jerks"""
    k = 0.0
    for at in (6, 18, 30, 44, 56): k += ease_out((f - at) / 4) / 5
    p = blend(collapse(49), predator(P()), k)
    p.rot("head", 0, 0, 0.5 * math.sin(f * 0.7) * (1 - k))
    return p

@clip("blade_swing", 36, loop=False)
def blade_swing(f):
    """phase 2: the blade in its teeth - the whole neck whips it across in front"""
    p = predator(P(), 1.2)
    wind = ease((f - 0) / 10) * (1 - ease((f - 12) / 3))
    sw = ease_out((f - 13) / 6) * (1 - ease((f - 24) / 12))
    p.rot("neck", 0.2, 0.7 * wind - 0.9 * sw, 0).rot("head", 0, 0.5 * wind - 0.6 * sw, -0.3 * wind + 0.4 * sw)
    p.rot("chest", 0, 0.25 * wind - 0.3 * sw, 0).off("neck", 0, 0, 0.1 * sw)
    return p

@clip("throw", 40, loop=False)
def throw(f):
    """phase 3: it pulls the blade out of its own stomach and hurls it"""
    p = predator(P())
    pull = ease((f - 2) / 12) * (1 - ease((f - 16) / 4))
    hurl = ease_out((f - 17) / 5) * (1 - ease((f - 26) / 14))
    p.free.add("armR"); p.contact["armR"] = False
    p.rot("armR", 0.6 * pull - 0.4 * hurl, 0, -0.3 * pull).rot("foreR", -1.4 * pull - 0.6 * hurl).rot("handR", 0.6 * pull + 0.9 * hurl)
    p.rot("chest", 0.25 * pull - 0.2 * hurl, -0.3 * pull + 0.4 * hurl, 0)
    belly(p, 0.5 * pull)
    return p

@clip("intro_stare", 100)
def intro_stare(f):
    """the opening: right in front of you, completely still, the head slowly tipping as it looks into your eyes"""
    p = predator(P(), 0.6)
    p.rot("neck", 0.35).off("neck", 0, 0, 0.14).rot("head", -0.25, 0, 0.35 * math.sin(2 * PI * f / 100))
    breathe(p, f, 0.6, 50)
    return p

# ---------------------------------------------------------------- export
def bake():
    out = {}
    for name, (frames, loop, speed, fn) in CLIPS.items():
        rows, contacts = [], []
        n = frames + 1
        for f in range(n):
            p = fn(f % frames if loop else f)
            pose = p.solve()
            row = []
            for b in ORDER:
                q, t = pose[b]
                row.append([float(q[1]), float(q[2]), float(q[3]), float(q[0]), float(t[0]), float(t[1]), float(t[2])])
            rows.append(row)
            m = 0
            for i, limb in enumerate(("legL", "legR", "armL", "armR")):
                if p.contact.get(limb) and limb not in p.free: m |= 1 << i
            contacts.append(m)
        out[name] = (loop, speed, rows, contacts)
    return out


def check(out):
    """planted feet must actually be on the ground (catches IK targets out of reach)"""
    worst = 0.0
    from rig import qeuler as _q
    for name, (loop, speed, rows, contacts) in out.items():
        for f, row in enumerate(rows):
            pose = {b: (np.array([r[3], r[0], r[1], r[2]]), np.array(r[4:7])) for b, r in zip(ORDER, row)}
            W = fk(pose)
            for i, limb in enumerate(("legL", "legR", "armL", "armR")):
                if contacts[f] >> i & 1:
                    y = end_pos(W, limb)[1] - GROUND[limb]
                    worst = max(worst, abs(y))
                    if abs(y) > 0.04: print("  !", name, f, limb, "off the ground by %.3f m" % y)
    print("worst planted-foot error %.4f m" % worst)


def lift_off_floor(out, mesh):
    """nothing but fingers, toes and the tongue may go below the floor: lying-down frames get their hips raised"""
    from meshio import read_shd3
    bones, rec, _, _ = read_shd3(mesh)
    names = [b[0] for b in bones]
    skip = {i for i, n in enumerate(names) if n.startswith(("finger", "toe", "tongue"))}
    keep = np.array([b not in skip for b in rec["b0"]])
    P = rec["p"][keep].astype(np.float64); B0 = rec["b0"][keep]
    hip = ORDER.index("hips")
    for name, (loop, speed, rows, contacts) in out.items():
        worst = 0.0
        for row in rows:
            pose = {b: (np.array([r[3], r[0], r[1], r[2]]), np.array(r[4:7])) for b, r in zip(ORDER, row)}
            W = fk(pose)
            M = np.stack([W.get(n, np.eye(4)) for n in names])
            y = (np.einsum("nj,nj->n", M[B0][:, 1, :3], P) + M[B0][:, 1, 3]).min()
            if y < -0.005:
                row[hip][5] += -y - 0.005
                worst = max(worst, -y)
        if worst > 0.01: print("  lifted %-12s up to %.3f m" % (name, worst))


def write(out, d):
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, "shadow.anim"), "wb") as fo:
        fo.write(b"SHA2"); fo.write(struct.pack("<i", len(ORDER)))
        for b in ORDER:
            nb = b.encode(); fo.write(struct.pack("<h", len(nb))); fo.write(nb)
        fo.write(struct.pack("<i", len(out)))
        for name, (loop, speed, rows, contacts) in out.items():
            nb = name.encode(); fo.write(struct.pack("<h", len(nb))); fo.write(nb)
            fo.write(struct.pack("<ibf", len(rows), 1 if loop else 0, speed))
            fo.write(bytes(contacts))
            for row in rows:
                for v in row: fo.write(struct.pack("<7f", *v))
    js = {"bones": ORDER, "clips": {k: {"loop": v[0], "speed": v[1], "frames": [[[round(x, 5) for x in b] for b in r] for r in v[2]],
                                         "contact": v[3]} for k, v in out.items()}}
    with open(os.path.join(d, "anim.json"), "w") as fo: json.dump(js, fo, separators=(",", ":"))
    print("clips:", {k: len(v[2]) - 1 for k, v in out.items()})


if __name__ == "__main__":
    mesh, d = sys.argv[1], sys.argv[2]
    from meshio import read_shd2, read_shd3
    head = open(mesh, "rb").read(4)
    if head == b"SHD2": set_pivots({p["name"]: p["pivot"] for p in read_shd2(mesh)})
    else: set_pivots({b[0]: b[2] for b in read_shd3(mesh)[0]})
    out = bake(); check(out)
    if head == b"SHD3": lift_off_floor(out, mesh)
    write(out, d)
