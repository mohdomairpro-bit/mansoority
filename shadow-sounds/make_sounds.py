"""
Mansoority's second sound pass, generated here (no downloads): the heavy body (steps, slams), the boss voice and
its lines, the stomach, the bite, and three music loops - one per phase of the fight.
Sources: the CC0 creature recordings already processed by process.sh (scream_*, breath_*), flite speech, and synthesis.
usage: python3 make_sounds.py <assets/theshadow/sounds dir>
"""
import os, subprocess, sys, tempfile
import numpy as np
from scipy import signal

SR = 44100
OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
SRC = os.path.join(OUT, "creature")
rng = np.random.default_rng(7)


# ---------------------------------------------------------------- io + basic dsp
def load(path):
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).astype(np.float64)

def save(x, name, q=5):
    x = np.asarray(x, np.float64)
    peak = np.max(np.abs(x)) + 1e-9
    x = x / peak * 0.89
    d = os.path.join(OUT, os.path.dirname(name)); os.makedirs(d, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".f32") as t:
        t.write(x.astype(np.float32).tobytes()); t.flush()
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", t.name,
                        "-c:a", "libvorbis", "-q:a", str(q), os.path.join(OUT, name + ".ogg")], check=True)
    print("  ", name, "%.2fs" % (len(x) / SR))

def speak(text, voice="rms"):
    with tempfile.NamedTemporaryFile(suffix=".wav") as t:
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "flite=text='%s':voice=%s" % (text.replace("'", ""), voice),
                        "-ar", str(SR), "-ac", "1", t.name], check=True)
        return load(t.name)

def rubber(x, semis, tempo=1.0):
    """pitch shift without changing length (and optional tempo change)"""
    with tempfile.NamedTemporaryFile(suffix=".f32") as a, tempfile.NamedTemporaryFile(suffix=".f32") as b:
        a.write(x.astype(np.float32).tobytes()); a.flush()
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", a.name,
                        "-af", "rubberband=pitch=%f:tempo=%f:formant=preserved" % (2 ** (semis / 12), tempo), "-f", "f32le", b.name], check=True)
        return np.frombuffer(open(b.name, "rb").read(), np.float32).astype(np.float64)

def resample(x, ratio):
    """slower and lower (ratio < 1), like a tape slowing down"""
    n = int(len(x) / ratio)
    return np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x)

def filt(x, kind, f, order=4):
    sos = signal.butter(order, f, btype=kind, fs=SR, output="sos")
    return signal.sosfilt(sos, x)

def env_exp(n, tau):
    return np.exp(-np.arange(n) / (tau * SR))

def ir(seconds, damp=3.0, bright=6000):
    n = int(seconds * SR)
    e = rng.standard_normal(n) * np.exp(-np.arange(n) / (seconds / damp * SR))
    e = filt(e, "low", bright, 2)
    e[0] += 1.0
    return e / np.sqrt(np.sum(e ** 2))

def reverb(x, seconds=2.0, wet=0.35, bright=6000):
    w = signal.fftconvolve(x, ir(seconds, bright=bright))[: len(x) + int(seconds * SR)]
    dry = np.concatenate([x, np.zeros(len(w) - len(x))])
    return dry * (1 - wet) + w * wet * 3.0

def drive(x, k):
    return np.tanh(x / (np.max(np.abs(x)) + 1e-9) * k)

def pad(x, n):
    return np.concatenate([x, np.zeros(max(0, n - len(x)))])

def mix(*parts):
    """sum, with every part's own end faded so nothing stops with a click"""
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        p = np.asarray(p, np.float64).copy(); k = min(len(p), int(0.03 * SR))
        if k > 1: p[-k:] *= np.linspace(1, 0, k)
        out[:len(p)] += p
    return out

def delay(x, sec):
    return np.concatenate([np.zeros(int(sec * SR)), x])

def sine_sweep(f0, f1, dur, curve=3.0):
    t = np.arange(int(dur * SR)) / SR
    f = f1 + (f0 - f1) * np.exp(-t * curve / dur * 3)
    return np.sin(2 * np.pi * np.cumsum(f) / SR)

def fade(x, a=0.005, b=0.05):
    x = x.copy(); na, nb = int(a * SR), int(b * SR)
    if na: x[:na] *= np.linspace(0, 1, na)
    if nb: x[-nb:] *= np.linspace(1, 0, nb)
    return x

def trim(x, thr=0.01):
    idx = np.where(np.abs(x) > thr * np.max(np.abs(x)))[0]
    return x[idx[0]: idx[-1] + 1] if len(idx) else x


SCREAMS = [load(os.path.join(SRC, "scream_%d.ogg" % i)) for i in range(10)]
BREATHS = [load(os.path.join(SRC, "breath_%d.ogg" % i)) for i in range(10)]


# ---------------------------------------------------------------- the body: steps, slams, cracking bone
def step(i):
    r = np.random.default_rng(100 + i)
    thump = sine_sweep(95 + r.uniform(-10, 10), 34, 0.45, 4) * env_exp(int(0.45 * SR), 0.11)
    body = filt(r.standard_normal(int(0.3 * SR)), "low", 380) * env_exp(int(0.3 * SR), 0.05) * 0.8
    grit = filt(r.standard_normal(int(0.12 * SR)), "band", [700, 3500]) * env_exp(int(0.12 * SR), 0.02) * 0.25
    x = mix(thump * 1.2, body, delay(grit, 0.004))
    return fade(reverb(drive(x, 1.6), 0.9, 0.18, 2500), 0.001, 0.2)

def slam(i):
    r = np.random.default_rng(200 + i)
    n = int(2.6 * SR)
    boom = sine_sweep(70, 24, 2.2, 2) * env_exp(int(2.2 * SR), 0.6)
    rumble = filt(r.standard_normal(n), "low", 160) * env_exp(n, 0.7) * 1.4
    crack = filt(r.standard_normal(int(0.03 * SR)), "high", 900) * env_exp(int(0.03 * SR), 0.006) * 2.5
    debris = np.zeros(n)
    for _ in range(70):                                   # stones and grit raining back down
        at = int(r.uniform(0.15, 2.2) * SR); ln = int(r.uniform(0.004, 0.02) * SR)
        g = filt(r.standard_normal(ln), "band", [r.uniform(600, 1500), r.uniform(2500, 6000)]) * env_exp(ln, 0.004)
        debris[at:at + ln] += g * r.uniform(0.05, 0.35) * np.exp(-at / SR / 1.1)
    x = mix(boom * 1.6, rumble, crack, debris)
    x = drive(x, 2.2) * np.exp(-np.arange(len(x)) / SR / 0.9)
    return fade(reverb(x, 3.0, 0.35, 3000), 0.001, 0.4)

def crack(i):
    """bones snapping: a fast run of sharp clicks"""
    r = np.random.default_rng(300 + i)
    n = int(0.7 * SR); x = np.zeros(n)
    t = 0.0
    for k in range(r.integers(5, 10)):
        t += r.uniform(0.012, 0.07)
        at = int(t * SR); ln = int(r.uniform(0.003, 0.012) * SR)
        if at + ln >= n: break
        c = filt(r.standard_normal(ln), "band", [r.uniform(900, 2000), r.uniform(4000, 9000)]) * env_exp(ln, 0.003)
        x[at:at + ln] += c * r.uniform(0.4, 1.0)
    low = filt(r.standard_normal(n), "low", 300) * env_exp(n, 0.06) * 0.6
    return fade(reverb(mix(x * 2, low), 0.6, 0.15), 0.001, 0.1)


# ---------------------------------------------------------------- the voice: roars and the screech of the charge
def roar(i):
    s = trim(SCREAMS[i % 10])
    low = resample(s, 0.66)                                  # down a fifth and slower
    lower = resample(s, 0.45) * 0.55
    env = np.abs(signal.hilbert(filt(low, "low", 400)))
    env = filt(env, "low", 12, 2); env /= env.max() + 1e-9
    sub = sine_sweep(85, 38, len(low) / SR, 1.5)[: len(low)] * env * 0.7
    x = mix(low, lower, sub)
    x = drive(x, 2.6)
    return fade(reverb(x, 2.8, 0.32, 4500), 0.01, 0.6)

def screech(i):
    """the charge / the rush: a rising shriek with a chattering edge"""
    s = trim(SCREAMS[(i * 3 + 1) % 10])
    hi = resample(s, 1.2)
    lay = [delay(resample(s, 1.2 * d), k * 0.011) * 0.6 for k, d in enumerate((0.985, 1.015, 0.97))]
    x = mix(hi, *lay, resample(s, 0.8) * 0.5)
    t = np.arange(len(x)) / SR
    x = x * (0.75 + 0.25 * np.sin(2 * np.pi * 19 * t))        # chatter
    x = filt(x, "band", [500, 7000])
    return fade(reverb(drive(x, 2.0), 1.6, 0.25), 0.005, 0.3)


# ---------------------------------------------------------------- the stomach
def bubbles(dur, rate, fmin, fmax, seed):
    r = np.random.default_rng(seed)
    n = int(dur * SR); x = np.zeros(n)
    for _ in range(int(dur * rate)):
        at = int(r.uniform(0, dur - 0.1) * SR); f0 = r.uniform(fmin, fmax); tau = r.uniform(0.01, 0.05)
        ln = int(tau * 5 * SR); t = np.arange(ln) / SR
        b = np.sin(2 * np.pi * f0 * (t + 0.5 * t * t / tau * 0.15 * 3)) * np.exp(-t / tau)
        x[at:at + ln] += b[: n - at] * r.uniform(0.2, 1.0)
    return x

def gurgle(i):
    x = bubbles(2.6, 55, 120, 520, 400 + i)
    growl = resample(trim(BREATHS[(i + 2) % 10]), 0.6)[: len(x)] * 0.6
    churn = filt(np.random.default_rng(410 + i).standard_normal(len(x)), "band", [90, 400]) * 0.35
    t = np.arange(len(x)) / SR
    churn *= 0.5 + 0.5 * np.sin(2 * np.pi * 2.3 * t) ** 2
    return fade(reverb(mix(x, growl, churn), 1.2, 0.25, 3000), 0.05, 0.4)

def spray():
    n = int(2.2 * SR); r = np.random.default_rng(500)
    a = np.minimum(1, np.arange(n) / (0.04 * SR)) * np.exp(-np.maximum(0, np.arange(n) / SR - 1.2) / 0.35)
    hiss = filt(r.standard_normal(n), "high", 1400) * a * 0.7
    wet = bubbles(2.2, 160, 300, 1400, 501) * 0.6
    splat = filt(r.standard_normal(n), "band", [150, 900]) * a * 0.6
    retch = resample(trim(SCREAMS[6]), 0.55)[: n] * 0.5
    return fade(reverb(drive(mix(hiss, wet, splat, retch), 1.5), 1.4, 0.25), 0.002, 0.3)

def crunch(i):
    """the bite: teeth through bone, then wet chewing"""
    r = np.random.default_rng(600 + i)
    c = crack(i + 7)
    sq = filt(r.standard_normal(int(0.9 * SR)), "low", 700) * 0.5
    t = np.arange(len(sq)) / SR
    sq *= np.abs(np.sin(2 * np.pi * 3.2 * t)) ** 3 * np.exp(-t / 0.6)
    chomp = sine_sweep(160, 60, 0.25, 4) * env_exp(int(0.25 * SR), 0.05)
    return fade(mix(chomp, c * 1.3, delay(sq, 0.08), delay(bubbles(0.9, 60, 200, 700, 610 + i) * 0.4, 0.1)), 0.001, 0.2)


# ---------------------------------------------------------------- the arena
def spikes():
    r = np.random.default_rng(700)
    n = int(1.4 * SR); x = np.zeros(n)
    for k in range(9):                                   # a line of bone bursting up, getting closer
        at = int((0.05 + k * 0.11) * SR)
        b = crack(20 + k)[: int(0.3 * SR)] * (0.6 + k * 0.05)
        x[at:at + len(b)] += b[: n - at]
        e = filt(r.standard_normal(int(0.2 * SR)), "low", 250) * env_exp(int(0.2 * SR), 0.05)
        x[at:at + len(e)] += e[: n - at] * 0.7
    return fade(reverb(x, 1.2, 0.2), 0.001, 0.2)

def flood():
    """the darkness pouring in: a rising roar of air and a sub-bass swell"""
    n = int(3.5 * SR); r = np.random.default_rng(800); t = np.arange(n) / SR
    noise = r.standard_normal(n)
    sos = [signal.butter(2, f, btype="low", fs=SR, output="sos") for f in (200, 600, 1800, 4000)]
    swept = sum(signal.sosfilt(s, noise) * w for s, w in zip(sos, (1.0, 0.6, 0.35, 0.15)))
    swell = np.minimum(1, t / 2.2) ** 2 * np.exp(-np.maximum(0, t - 2.6) / 0.4)
    sub = np.sin(2 * np.pi * (28 + 6 * t) * t) * swell
    whisper = rubber(speak("you can not see me", "slt"), -3)[: n] * 0.25
    return fade(reverb(mix(swept * swell, sub * 0.9, delay(whisper, 1.0)), 2.5, 0.4, 2500), 0.2, 0.6)


# ---------------------------------------------------------------- the lines: a voice coming through a broken connection
def encrypted(text, seed, deep=-6):
    r = np.random.default_rng(seed)
    v = trim(speak(text, "rms"))
    v = resample(v, 0.86)                                 # dragged out
    a = rubber(v, deep)
    b = delay(rubber(v, deep - 7), 0.025) * 0.55
    c = filt(rubber(v, deep + 5), "high", 1500) * 0.18     # a thin second voice under it
    x = mix(a, b, c)
    t = np.arange(len(x)) / SR
    x = x * (0.55 + 0.45 * np.sin(2 * np.pi * 37 * t))     # ring-modulated
    crushed = np.round(x / (np.max(np.abs(x)) + 1e-9) * 12) / 12
    hold = np.repeat(crushed[::4], 4)[: len(x)]            # sample-and-hold: digital grit
    x = x * 0.55 + hold * 0.45 * np.max(np.abs(x))
    out, i, step_ = [], 0, int(0.045 * SR)                 # the signal stutters and drops out
    while i < len(x):
        chunk = x[i:i + step_]
        roll = r.random()
        if roll < 0.08: out += [chunk, chunk, chunk * 0.7]
        elif roll < 0.12: out.append(chunk[::-1])
        elif roll < 0.15: out.append(chunk * 0.05)
        else: out.append(chunk)
        i += step_
    x = np.concatenate(out)
    noise = filt(r.standard_normal(len(x)), "band", [2000, 8000]) * 0.04
    x = mix(x, noise)
    pre = reverb(x[::-1], 1.8, 0.9)[::-1][: int(0.5 * SR)] * 0.5   # it breathes in backwards before it speaks
    return fade(reverb(drive(mix(pre, delay(x, 0.5)), 1.8), 2.2, 0.3), 0.05, 0.5)


LINES = {"destroy": "I'll destroy you", "mine": "this world is mine", "kneel": "kneel", "again": "again", "taste": "I can taste you",
         "nowhere": "there is nowhere to go", "slow": "too slow", "stay": "stay with me", "yours": "your world is mine now",
         "last": "one more"}


# ---------------------------------------------------------------- music: three loops, one per phase
def bar_len(bpm): return 60 / bpm * 4

def kick(f=55, dur=0.5, click=0.6, seed=0):
    r = np.random.default_rng(seed)
    k = sine_sweep(f * 3.2, f, dur, 6) * env_exp(int(dur * SR), dur / 3.5)
    c = filt(r.standard_normal(int(0.01 * SR)), "high", 2000) * click
    return drive(mix(k, c), 1.4)

def taiko(f=90, dur=0.7, seed=0):
    r = np.random.default_rng(seed)
    body = sine_sweep(f * 1.6, f, dur, 5) * env_exp(int(dur * SR), dur / 4)
    skin = filt(r.standard_normal(int(dur * SR)), "band", [80, 900]) * env_exp(int(dur * SR), 0.05) * 0.5
    return drive(mix(body, skin), 1.3)

def metal(f=220, dur=2.5, seed=0):
    """an inharmonic clang: a struck iron bar"""
    t = np.arange(int(dur * SR)) / SR
    parts = [(1, 1), (2.76, 0.6), (5.4, 0.4), (8.93, 0.25), (13.3, 0.15)]
    x = sum(a * np.sin(2 * np.pi * f * m * t) * np.exp(-t * (1.5 + m * 0.6)) for m, a in parts)
    return x

def hat(seed):
    r = np.random.default_rng(seed); n = int(0.06 * SR)
    return filt(r.standard_normal(n), "high", 7000) * env_exp(n, 0.012)

def saw_pad(freqs, dur, cutoff, detune=0.004):
    t = np.arange(int(dur * SR)) / SR
    x = np.zeros(len(t))
    for f in freqs:
        for d in (-detune, 0, detune):
            ph = (f * (1 + d) * t) % 1.0
            x += 2 * ph - 1
    x = filt(x, "low", cutoff, 2)
    a = np.minimum(1, t / (dur * 0.35)) * np.minimum(1, (dur - t) / (dur * 0.3))
    return x * a / (len(freqs) * 3)

def pluck(f, dur, seed):
    """karplus-strong string, low and dark"""
    r = np.random.default_rng(seed)
    n = int(dur * SR); p = max(2, int(SR / f))
    buf = r.uniform(-1, 1, p); out = np.zeros(n)
    for i in range(n):
        out[i] = buf[i % p]
        buf[i % p] = 0.497 * (buf[i % p] + buf[(i + 1) % p])
    return filt(out, "low", 1800, 2)

NOTE = lambda m: 440.0 * 2 ** ((m - 69) / 12)

def music(phase):
    bpm = {1: 96, 2: 118, 3: 138}[phase]
    bars = {1: 16, 2: 16, 3: 16}[phase]
    beat = 60 / bpm; total = bar_len(bpm) * bars
    n = int(total * SR) + int(4 * SR)
    x = np.zeros(n)
    def put(sig, at, g=1.0):
        i = int(at * SR)
        if i >= n: return
        x[i:i + len(sig)] += sig[: n - i] * g
    # chords: D phrygian dominant, the minor second always grinding against the root
    prog = [(38, [50, 51, 57]), (34, [46, 50, 53]), (31, [43, 46, 50, 51]), (33, [45, 49, 52])]
    for b in range(bars):
        root, ch = prog[(b // 2) % 4]
        if b % 2 == 0:
            put(saw_pad([NOTE(m) for m in ch], bar_len(bpm) * 2.05, 900 + phase * 400), b * bar_len(bpm), 0.55)
            put(saw_pad([NOTE(root - 12), NOTE(root)], bar_len(bpm) * 2.05, 220), b * bar_len(bpm), 0.9)   # drone
        t0 = b * bar_len(bpm)
        # drums
        pat_k = {1: [0, 2.5], 2: [0, 1.5, 2.5, 3.25], 3: [0, 0.75, 1.5, 2, 2.5, 3, 3.5]}[phase]
        for k in pat_k: put(kick(48, 0.5, seed=b), t0 + k * beat, 1.0)
        pat_t = {1: [1, 3], 2: [1, 1.75, 3, 3.5], 3: [1, 1.5, 3, 3.25, 3.75]}[phase]
        for k in pat_t: put(taiko(85 + 10 * (k % 2), seed=int(k * 10) + b), t0 + k * beat, 0.8)
        if b % 4 == 0: put(metal(NOTE(ch[0] - 12) * 2.03, 3.0, b), t0, 0.35)
        if phase >= 2:
            for s in range(8): put(hat(b * 8 + s), t0 + s * beat / 2, 0.12 + (0.08 if s % 2 else 0))
            arp = [root, root + 1, root + 7, root + 1, root, root + 12, root + 8, root + 7]
            for s in range(16):
                put(pluck(NOTE(arp[s % 8] + 12), beat * 0.6, b * 16 + s), t0 + s * beat / 4, 0.5)
        if phase == 3:
            if b % 2 == 1:                                     # its voice, chopped into the beat
                sc = trim(SCREAMS[b % 10])
                for s in range(4):
                    piece = sc[int(s * 0.12 * SR): int((s * 0.12 + 0.18) * SR)]
                    if len(piece): put(fade(resample(piece, 0.8), 0.003, 0.02), t0 + (2 + s * 0.5) * beat, 0.45)
            for s in range(16): put(hat(1000 + b * 16 + s), t0 + s * beat / 4, 0.08 + (0.06 if s % 4 == 2 else 0))
        # tension riser into every 4th bar
        if b % 4 == 3:
            rn = int(bar_len(bpm) * SR); t = np.arange(rn) / SR
            riser = filt(np.random.default_rng(b).standard_normal(rn), "band", [300, 3000]) * (t / t[-1]) ** 2 * 0.35
            put(riser, t0)
    # wrap the tail of the last bar back onto the start, so the loop is seamless
    L = int(total * SR)
    x[:n - L] += x[L:]
    x = x[:L]
    x = drive(x, {1: 1.5, 2: 1.6, 3: 1.6}[phase]) * 0.9
    x = reverb(x, 1.6, 0.18)[:L] if phase < 3 else x
    return x


if __name__ == "__main__":
    print("body")
    for i in range(3): save(step(i), "creature/step_%d" % i)
    for i in range(2): save(slam(i), "creature/slam_%d" % i)
    for i in range(3): save(crack(i), "creature/crack_%d" % i)
    print("voice")
    for i in range(4): save(roar(i), "creature/roar_%d" % i)
    for i in range(3): save(screech(i), "creature/screech_%d" % i)
    print("stomach, bite, arena")
    for i in range(2): save(gurgle(i), "creature/gurgle_%d" % i)
    save(spray(), "creature/spray_0")
    for i in range(2): save(crunch(i), "creature/crunch_%d" % i)
    save(spikes(), "creature/spikes_0")
    save(flood(), "creature/flood_0")
    print("lines")
    for k, (name, text) in enumerate(LINES.items()): save(encrypted(text, 900 + k), "voice/%s" % name)
    print("music")
    for ph in (1, 2, 3): save(music(ph), "music/boss_%d" % ph, q=4)
