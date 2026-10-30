"""
The jumpscare, redone: no static, no radio. Four layers, all mastered hot (saturated and limited, as loud as a
file gets) and played stacked in game:
  js_riser   - a swell of air and rising tone out of dead silence that slams into...
  js_hit     - the impact: sub drop, a distorted brass 'braam', a metal clang, a body thump
  js_scream  - its scream: real creature screams, stretched, layered an octave down and up, with a vocal-fry growl
  js_strings - screeching, dissonant strings stabbing (the Psycho shower kind), bending upward
usage: python3 make_jumpscare.py <assets/theshadow/sounds dir>
"""
import sys
import numpy as np
sys.argv = sys.argv[:2] if len(sys.argv) > 1 else ["x", "out"]
import make_sounds as M
from make_sounds import SR, filt, env_exp, reverb, drive, mix, delay, sine_sweep, fade, trim, rubber, resample, SCREAMS, save

rng = np.random.default_rng(66)


def master(x, heat=2.6):
    """as loud as it can be without falling apart: normalise, saturate, normalise again"""
    x = np.asarray(x, np.float64)
    x = x / (np.max(np.abs(x)) + 1e-9)
    x = np.tanh(x * heat) / np.tanh(heat)
    return x / (np.max(np.abs(x)) + 1e-9) * 0.98


def saw(f, t):
    ph = np.cumsum(f) / SR if np.ndim(f) else f * t
    return 2 * (ph % 1.0) - 1


def riser(dur=0.75):
    n = int(dur * SR); t = np.arange(n) / SR; u = t / dur
    # filtered noise whose band climbs, a tone gliding up an octave and a half, both swelling hard to the end
    noise = rng.standard_normal(n)
    out = np.zeros(n)
    for k in range(8):                                       # stepped band so the sweep is audible
        a, b = int(k * n / 8), int((k + 1) * n / 8)
        lo = 200 * (2 ** (k * 0.55)); out[a:b] = filt(noise, "band", [lo, min(lo * 3, 16000)])[a:b]
    tone = np.sin(2 * np.pi * np.cumsum(70 * 2 ** (u * 3.2)) / SR) + 0.5 * np.sin(2 * np.pi * np.cumsum(140 * 2 ** (u * 3.4)) / SR)
    sw = (u ** 3.2)
    rev = reverb(rng.standard_normal(int(0.4 * SR)) * env_exp(int(0.4 * SR), 0.05), 1.2, 0.9)[::-1][:n]   # a reversed hit sucking in
    rev = np.concatenate([np.zeros(n - len(rev)), rev]) if len(rev) < n else rev[-n:]
    x = (out * 0.8 + tone * 0.55) * sw + rev * 0.5 * u ** 2
    x[-int(0.004 * SR):] *= np.linspace(1, 0, int(0.004 * SR))  # cut dead, the hit takes over
    return master(x, 2.0)


def hit():
    dur = 2.6; n = int(dur * SR); t = np.arange(n) / SR
    sub = sine_sweep(95, 26, dur, 4.0) * env_exp(n, 0.65) * 1.3
    thump = filt(rng.standard_normal(n), "low", 260) * env_exp(n, 0.06) * 3.0
    # the braam: detuned low saws, filter ripping open then closing, distorted
    f = [55.0, 58.27, 82.41, 110.0, 116.54]
    br = sum(saw(fr * (1 + 0.003 * np.sin(2 * np.pi * 0.7 * t + i)), t) for i, fr in enumerate(f)) / len(f)
    cut = 250 + 2600 * np.exp(-t / 0.35)
    brf = np.zeros(n); blk = 2048
    for s in range(0, n, blk):                               # time-varying lowpass, block by block
        e = min(n, s + blk); brf[s:e] = filt(br, "low", float(cut[s]), 2)[s:e]
    brf = drive(brf * env_exp(n, 1.1), 3.0)
    # metal: inharmonic partials ringing out
    clang = sum(np.sin(2 * np.pi * fr * t + i) * env_exp(n, 0.25 + 0.6 / (i + 1)) for i, fr in enumerate([187, 431, 523, 911, 1433, 2210, 3170])) / 4
    crack = filt(rng.standard_normal(n), "high", 2500) * env_exp(n, 0.02) * 1.5
    x = mix(sub, thump, brf * 0.9, clang * 0.35, crack)
    x = reverb(x, 1.8, 0.25)[:n]
    return master(fade(x, 0.0005, 0.4), 2.4)


def scream(i):
    a = trim(SCREAMS[[3, 2, 7][i % 3]]); b = trim(SCREAMS[[1, 8, 5][i % 3]])
    a = rubber(a, 0, tempo=0.5)                              # stretched to twice as long
    b = rubber(b, 0, tempo=0.55)
    body = rubber(a, -9)                                     # the chest of it: deep, monstrous
    shriek = rubber(b, +5)                                   # the top: a shriek riding over it
    slow = resample(a, 0.62)                                 # tape-slowed underneath
    n = max(len(body), len(shriek) + int(0.05 * SR), len(slow), len(a))
    t = np.arange(n) / SR
    fry = 1 + 0.75 * np.sin(2 * np.pi * (34 + 8 * np.sin(2 * np.pi * 0.9 * t)) * t)   # vocal fry: rattling, wet
    def P(x): return M.pad(x, n)
    x = P(a) * 1.0 + P(body) * fry * 0.9 + P(delay(shriek, 0.05)[:n]) * 0.7 + P(slow) * 0.6
    x = drive(filt(x, "band", [90, 9000]), 1.8)
    x = x * np.minimum(1, t / 0.008) * np.where(t > n / SR - 0.5, (n / SR - t) / 0.5, 1)
    x = reverb(x, 1.2, 0.2)[:n + int(0.3 * SR)]
    return master(x, 2.1)


def strings(i):
    dur = 2.4; n = int(dur * SR); t = np.arange(n) / SR
    base = [1480, 1567, 1661, 1760, 2093, 2217, 2349][i:] + [1396, 2489]
    bend = 2 ** ((np.clip(t - 0.6, 0, None) / 1.8) * 3 / 12)           # slides up three semitones
    x = np.zeros(n)
    for k, f0 in enumerate(base):
        vib = 1 + 0.012 * np.sin(2 * np.pi * (6.2 + k * 0.3) * t + k)
        x += saw(f0 * vib * bend, t) * (1 + 0.3 * rng.standard_normal() * 0)
    x /= len(base)
    bow = 0.65 + 0.35 * np.sin(2 * np.pi * 13 * t)                     # frantic bowing
    stabs = np.zeros(n)
    for s0 in (0.0, 0.24, 0.48, 0.72):                                 # stab, stab, stab, stab... then it holds
        k = (t >= s0); stabs += k * np.exp(-(t - s0) * 9) * (t < s0 + 0.24)
    hold = np.clip((t - 0.96) / 0.05, 0, 1) * np.exp(-np.clip(t - 1.0, 0, None) / 1.3)
    x = x * bow * (stabs + hold)
    x = filt(x, "band", [700, 9000])
    x = reverb(drive(x, 1.6), 1.4, 0.3)[:n]
    return master(fade(x, 0.002, 0.25), 2.0)


if __name__ == "__main__":
    out = sys.argv[1]
    print("jumpscare")
    save(riser(), "jumpscare/js_riser_0")
    for k in range(2): save(hit() if k == 0 else master(filt(hit(), "low", 9000)), "jumpscare/js_hit_%d" % k)
    for k in range(3): save(scream(k), "jumpscare/js_scream_%d" % k)
    for k in range(2): save(strings(k), "jumpscare/js_strings_%d" % k)
