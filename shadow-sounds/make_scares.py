"""
the scare sounds, third pass: louder and nastier, nothing sourced from vanilla.

  stinger_*  - the hit when it shows itself: a slam, screeching dissonant strings, a shrieking scream on top
  whisper_*  - many voices whispering right at your ear (speech turned to breath: its formants, no voice)
  glitch_*   - replaces the old static: a wet, stuttering, reversed shriek that cuts out dead
  shriek     - a synthesised human-like scream (formants, jitter, a growl an octave under), used in the jumpscare

and it re-masters the creature sounds already in the pack (scream/breath/far/screech) much hotter: compressed,
saturated and limited, so they hit at nearly full scale instead of peaking at half.

usage: python3 make_scares.py <assets/theshadow/sounds dir>      (run after make_sounds.py and make_jumpscare.py)
"""
import os, sys, subprocess, tempfile
import numpy as np
from scipy import signal
sys.argv = sys.argv[:2] if len(sys.argv) > 1 else ["x", "out"]
import make_sounds as M
from make_sounds import SR, filt, env_exp, reverb, drive, mix, delay, sine_sweep, fade, trim, rubber, resample, SCREAMS, load, speak

OUT = sys.argv[1]
rng = np.random.default_rng(1313)


def hot(x, heat=4.0, ceiling=0.985):
    """as loud as a file can be: compress hard toward a high level, saturate, then a hard ceiling"""
    x = np.asarray(x, np.float64)
    x = x / (np.max(np.abs(x)) + 1e-9)
    a = np.exp(-1.0 / (0.02 * SR))
    env = np.sqrt(signal.lfilter([1 - a], [1, -a], x ** 2)) + 1e-5          # 20 ms rms follower
    g = np.clip((0.35 / env) ** 0.6, 0.4, 6.0)
    g = signal.lfilter([1 - a], [1, -a], g)                                   # smooth the gain: no pumping clicks
    x = x * g
    x = x / (np.max(np.abs(x)) + 1e-9)
    x = np.tanh(x * heat) / np.tanh(heat)
    return x / (np.max(np.abs(x)) + 1e-9) * ceiling


def save(x, name, q=6):
    x = np.clip(np.asarray(x, np.float64), -1, 1)
    d = os.path.join(OUT, os.path.dirname(name)); os.makedirs(d, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".f32") as t:
        t.write(x.astype(np.float32).tobytes()); t.flush()
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", t.name,
                        "-c:a", "libvorbis", "-q:a", str(q), os.path.join(OUT, name + ".ogg")], check=True)
    r = 20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-9)
    print("   %-28s %.2fs  rms %.1f dB" % (name, len(x) / SR, r))


def saw(f):
    ph = np.cumsum(f) / SR
    return 2 * (ph % 1.0) - 1


def resonators(src, formants, q=9.0):
    out = np.zeros_like(src)
    for f, g in formants:
        b, a = signal.iirpeak(f, q, fs=SR)
        out += g * signal.lfilter(b, a, src)
    return out


def shriek(dur=2.6, f0=780, seed=0, growl=0.7):
    """a scream from nothing: a buzzing source at a strained pitch, wobbling and cracking, through 'AAAH' formants;
    a second voice cracks up into a squeal, a third growls an octave down with a rattling subharmonic"""
    r = np.random.default_rng(seed)
    n = int(dur * SR); t = np.arange(n) / SR
    jit = signal.lfilter([0.02], [1, -0.98], r.standard_normal(n)) * 9          # slow random pitch wander
    vib = 0.03 * np.sin(2 * np.pi * (6.5 + r.random()) * t)
    rise = 1 + 0.35 * (1 - np.exp(-t / 0.08)) - 0.12 * np.clip((t - dur * 0.7) / (dur * 0.3), 0, 1)
    crack = 1 + 0.5 * (np.sin(2 * np.pi * 0.8 * t + seed) > 0.85)                 # the voice breaking upward
    out = np.zeros(n)
    for k, (mul, amp) in enumerate(((1.0, 1.0), (1.012, 0.7), (crack * 1.5, 0.45))):
        f = f0 * rise * mul * (1 + vib + jit * 0.01) * (1 + 0.004 * k)
        src = saw(f) + 0.1 * r.standard_normal(n)
        out += amp * resonators(src, [(1000, 1.0), (1450, 0.8), (2750, 0.9), (3600, 0.6), (4800, 0.3)])
    # the growl under it
    fl = f0 * 0.5 * rise * (1 + vib * 0.5)
    sub = (1 + 0.9 * np.sign(np.sin(np.pi * np.cumsum(fl) / SR)))                # period-doubling: vocal fry
    gr = resonators(saw(fl) * sub + 0.08 * r.standard_normal(n), [(500, 1.0), (900, 0.8), (2400, 0.5)], 6.0)
    out = out / (np.max(np.abs(out)) + 1e-9) + growl * gr / (np.max(np.abs(gr)) + 1e-9)
    env = np.minimum(1, t / 0.012) * np.where(t > dur - 0.35, (dur - t) / 0.35, 1) * (0.85 + 0.15 * np.sin(2 * np.pi * 11 * t))
    return out * env


def creature_layer(i, semis, dur):
    a = trim(SCREAMS[i % len(SCREAMS)])
    a = rubber(a, semis, tempo=max(0.3, len(a) / SR / dur))
    return M.pad(a, int(dur * SR))[:int(dur * SR)]


def scream_full(seed, dur=2.8):
    """the jumpscare scream: synthetic shriek + real creature screams pitched up, dry, distorted"""
    s = shriek(dur, 760 + seed * 60, seed)
    c1 = creature_layer(3 + seed, 4, dur); c2 = creature_layer(7 + seed, -5, dur)
    x = mix(s * 1.0, c1 * 0.8 / (np.max(np.abs(c1)) + 1e-9), c2 * 0.6 / (np.max(np.abs(c2)) + 1e-9))
    x = filt(x, "high", 140)
    x = x + 0.6 * filt(x, "band", [2000, 4500])                                 # presence: the part that hurts
    x = drive(x, 2.2)                                                            # torn, but still a voice
    return hot(reverb(x, 0.6, 0.12)[:len(x) + int(0.25 * SR)], 3.0)


def stinger(i):
    dur = 2.4; n = int(dur * SR); t = np.arange(n) / SR
    # the slam
    slam = sine_sweep(110, 28, 1.2, 5.0); slam = M.pad(slam * env_exp(len(slam), 0.35), n)
    crack = filt(rng.standard_normal(n), "high", 1800) * env_exp(n, 0.03)
    # screeching dissonant strings: a cluster stabbing, sliding up
    base = [1318, 1397, 1480, 1568, 1661, 2093, 2217][i % 3:] + [987, 1046]
    slide = 2 ** (np.clip(t - 0.15, 0, None) * (0.4 + 0.1 * i) / 12 * 3)
    st = sum(saw(f * slide * (1 + 0.01 * np.sin(2 * np.pi * (5.5 + k * 0.4) * t + k))) for k, f in enumerate(base)) / len(base)
    st = filt(st * (0.6 + 0.4 * np.sin(2 * np.pi * 14 * t)), "band", [900, 9000]) * np.exp(-t / 1.4)
    # a brass blat underneath, distorted
    br = sum(saw(f * np.ones(n)) for f in (55, 58.3, 82.4)) / 3
    br = drive(filt(br, "low", 900) * np.exp(-t / 0.9), 3.0)
    # and a shriek on top
    sh = M.pad(shriek(1.6, 900 + i * 70, 50 + i, 0.4), n)
    x = mix(slam * 1.3, crack * 0.9, st * 0.9, br * 0.6, sh * 0.85)
    x = fade(x, 0.0005, 0.3)
    return hot(reverb(x, 1.1, 0.18)[:n], 3.0)


def whisperize(x):
    """keep the shape of the words, throw away the voice: random phase in every STFT frame -> breath"""
    f, tt, Z = signal.stft(x, SR, nperseg=1024, noverlap=768)
    ph = np.exp(2j * np.pi * rng.random(Z.shape))
    _, y = signal.istft(np.abs(Z) * ph, SR, nperseg=1024, noverlap=768)
    return y


WORDS = ["behind you", "turn around", "i can see you", "dont look", "its right here", "you cant hide"]


def whisper(i):
    v = speak(WORDS[i], "slt" if i % 2 else "rms")
    v = resample(trim(v), 0.9)
    layers = []
    for k, (semis, dl, g) in enumerate(((0, 0.0, 1.0), (-3, 0.05, 0.7), (4, 0.11, 0.55), (-7, 0.17, 0.45))):
        w = whisperize(rubber(v, semis)) if semis else whisperize(v)
        layers.append(delay(w * g, dl))
    x = mix(*layers)
    x = filt(x, "band", [350, 9000])
    x = x + 0.5 * filt(x, "band", [2500, 6000])                                  # sibilance, right in your ear
    x = reverb(x, 0.5, 0.15)
    return hot(fade(x, 0.02, 0.2), 2.2, 0.9)


def glitch(i):
    """a reversed, stuttering shriek that swells and then cuts dead"""
    s = shriek(1.4, 650 + i * 90, 80 + i, 0.8)
    s = s[::-1] * np.linspace(0.2, 1, len(s)) ** 2
    out = []
    pos = 0; L = len(s)
    while pos < L:
        seg = int(rng.uniform(0.03, 0.09) * SR)
        chunk = s[pos:pos + seg]
        rep = 1 if rng.random() < 0.6 else rng.integers(2, 4)
        for _ in range(rep): out.append(chunk)                                   # stutter
        pos += seg
    x = np.concatenate(out)
    x = drive(x, 2.0)
    x[-int(0.004 * SR):] *= np.linspace(1, 0, int(0.004 * SR))
    return hot(x, 2.8)


def remaster(path, heat, extra_gain_db=0.0):
    x = load(path)
    if len(x) == 0: return
    x = x * 10 ** (extra_gain_db / 20)
    y = hot(x, heat)
    if path.endswith(".ogg"):
        name = os.path.relpath(path, OUT)[:-4]
        save(y, name)


if __name__ == "__main__":
    print("scares")
    for i in range(6): save(stinger(i), "scare/stinger_%d" % i)
    for i in range(6): save(whisper(i), "scare/whisper_%d" % i)
    for i in range(4): save(glitch(i), "scare/glitch_%d" % i)
    for i in range(3): save(scream_full(i), "jumpscare/js_scream_%d" % i)
    print("remaster")
    for k in range(10):
        remaster(os.path.join(OUT, "creature/scream_%d.ogg" % k), 4.0)
        remaster(os.path.join(OUT, "creature/breath_%d.ogg" % k), 3.0)
        remaster(os.path.join(OUT, "creature/far_%d.ogg" % k), 2.5)
    for k in range(3): remaster(os.path.join(OUT, "creature/screech_%d.ogg" % k), 3.5)
    for k in range(2): remaster(os.path.join(OUT, "jumpscare/js_hit_%d.ogg" % k), 3.0)
    for k in range(2): remaster(os.path.join(OUT, "jumpscare/js_strings_%d.ogg" % k), 3.5)
