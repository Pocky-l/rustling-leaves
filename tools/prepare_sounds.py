"""Builds the leaf sounds of Rustling Leaves from CC0 field recordings on Freesound.

Usage: python tools/prepare_sounds.py   (from the mod folder; needs numpy, scipy, soundfile)

The recordings (all Creative Commons 0, see SOURCES) are downloaded into tools/.cache (not committed). From each one the
clearest single events are cut: a foot swishing through dry leaves, a step into wet leaves, a body landing in a pile,
a rake stroke. Every cut starts a little before the event, fades out naturally and is normalized to a loudness that
says how loud it is meant to be next to the others; the game only scales them by distance and the rustle volume.
The files are mono: the game places them where the leaves are.
"""
import os
import re
import urllib.request

import numpy as np
import soundfile as sf
from scipy import signal

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, ".cache")
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "rustling_leaves", "sounds")
RATE = 44100

# Freesound id -> (author, title). All CC0. Listed in the README credits.
SOURCES = {
    # Dry leaves under feet.
    352963: ("Shain_T", "RustlingLeaves02"),
    218305: ("SpliceSound", "01-20 Footsteps, sneakers on dry leaves"),
    420390: ("Magdaadga", "walking the leaves"),
    179339: ("lolamadeus", "Crunchy Footsteps On Leaves"),
    # A body landing in leaves.
    853591: ("Wigglesworth", "A body falling to the ground, with leaf crush"),
    # Wet leaves.
    503262: ("nickmaysoundmusic", "Walking forest wet leaves foley trousers rustle"),
    455063: ("audioninja001", "footsteps-wet-leaves"),
    94342: ("Ryding", "Fotsteg i pa vata lov"),
    # Raking.
    448966: ("Garuda1982", "raking leaves field recording"),
    593201: ("Stefan21100190", "raking leaves"),
    419617: ("14GPanskaMacakova_Anna", "7. Rake"),
}


def fetch(url, binary=False):
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    data = urllib.request.urlopen(request, timeout=120).read()
    return data if binary else data.decode("utf-8", "replace")


def source(sound_id):
    """The recording as a mono signal at RATE."""
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, f"{sound_id}.ogg")
    if not os.path.exists(path):
        page = fetch(f"https://freesound.org/s/{sound_id}/")
        if "publicdomain/zero" not in page:
            raise RuntimeError(f"Freesound {sound_id} is not CC0")
        preview = re.search(r"https://cdn.freesound.org/previews/\d+/\d+_\d+-hq\.mp3", page).group(0)
        with open(path, "wb") as f:
            f.write(fetch(preview[:-4] + ".ogg", True))
    x, rate = sf.read(path, always_2d=True)
    x = x.mean(axis=1)
    if rate != RATE:
        x = signal.resample_poly(x, RATE, rate)
    return x


def filt(x, kind, cut, order=2):
    sos = signal.butter(order, cut, btype=kind, fs=RATE, output="sos")
    return signal.sosfiltfilt(sos, x, axis=0)


def normalize(x, rms_db):
    """To a mean power of rms_db over the loud part (the first 60% of the cut), peaks kept under -1 dBFS."""
    body = x[:max(1, int(len(x) * 0.6))]
    x = x * (10 ** (rms_db / 20) / (np.sqrt(np.mean(body ** 2)) + 1e-12))
    peak = np.max(np.abs(x))
    return x * (0.89 / peak) if peak > 0.89 else x


def write(name, data):
    path = os.path.join(OUT, name + ".ogg")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = data.astype(np.float32)
    # libsndfile crashes on large single Vorbis writes; feed it in blocks.
    with sf.SoundFile(path, "w", RATE, 1, format="OGG", subtype="VORBIS") as out:
        for start in range(0, len(data), 8192):
            out.write(data[start:start + 8192])


def events(x, spacing, smooth=12.0):
    """Peaks of the loudness envelope at least {spacing} seconds apart, with their prominence (loudest first)."""
    envelope = filt(np.abs(x), "low", smooth)
    peaks, props = signal.find_peaks(envelope, distance=int(spacing * RATE), prominence=envelope.max() * 0.08)
    order = np.argsort(props["prominences"])[::-1]
    return peaks[order], props["prominences"][order], envelope


def cut(x, peak, before, length, fade_in=0.006, tail=0.35):
    """The event at {peak}: from {before} seconds ahead, {length} seconds long, faded in quickly and out softly."""
    start = max(0, peak - int(before * RATE))
    part = x[start:start + int(length * RATE)].copy()
    if len(part) < int(length * RATE * 0.8):
        return None
    ramp = np.ones(len(part))
    n_in = int(fade_in * RATE)
    n_out = int(len(part) * tail)
    ramp[:n_in] = np.linspace(0, 1, n_in)
    # Equal-power fade out, so the rustle dies away instead of being chopped.
    ramp[-n_out:] = np.cos(np.linspace(0, np.pi / 2, n_out))
    return part * ramp


def clean_cut(part, envelope_floor_db=-26.0):
    """Whether the cut is one event: it ends much quieter than its loudest moment (no second step running into it)."""
    hop = int(0.02 * RATE)
    frames = len(part) // hop
    db = np.array([10 * np.log10(np.mean(part[i * hop:(i + 1) * hop] ** 2) + 1e-12) for i in range(frames)])
    return db[-3:-1].max() - db.max() < envelope_floor_db * 0.5


def noisy(part, max_crest_db):
    """Whether the loud part of the cut is a rustle (a burst of noise) rather than a click or a twig snapping."""
    hop = int(0.3 * RATE)
    start = max(0, int(np.argmax(np.abs(part))) - hop // 4)
    body = part[start:start + hop]
    peak = 20 * np.log10(np.max(np.abs(body)) + 1e-12)
    rms = 10 * np.log10(np.mean(body ** 2) + 1e-12)
    return peak - rms <= max_crest_db


def one_shots(sound_ids, name, count, before, length, rms_db, spacing, highpass=120.0, lowpass=None, pick=None,
              strict=True, max_crest_db=19.5):
    """The {count} clearest events of the recordings as {name}1..{name}{count}."""
    found = []
    for sound_id in sound_ids:
        x = filt(source(sound_id), "high", highpass)
        if lowpass:
            x = filt(x, "low", lowpass, order=4)
        peaks, prominences, envelope = events(x, spacing)
        for peak, prominence in zip(peaks, prominences):
            part = cut(x, peak, before, length)
            if part is None or (strict and not clean_cut(part)) or not noisy(part, max_crest_db):
                continue
            # Dead air (gaps cut out of the take) inside the cut.
            if np.min(filt(np.abs(part), "low", 30.0)[: int(len(part) * 0.7)]) < 1e-5:
                continue
            found.append((prominence / envelope.max(), sound_id, part))
    if pick:
        found = pick(found)
    found.sort(key=lambda f: -f[0])
    # Spread the picks over the recordings, so the variants do not all sound like one take.
    chosen = []
    by_source = {}
    for f in found:
        by_source.setdefault(f[1], []).append(f)
    while len(chosen) < count and any(by_source.values()):
        for sound_id in list(by_source):
            if by_source[sound_id] and len(chosen) < count:
                chosen.append(by_source[sound_id].pop(0))
    if len(chosen) < count:
        raise RuntimeError(f"only {len(chosen)} clean {name} events")
    for k, (_, sound_id, part) in enumerate(chosen):
        write(f"{name}{k + 1}", normalize(part, rms_db))
        print(f"{name}{k + 1} <- {sound_id}")


def clean(folder):
    path = os.path.join(OUT, folder)
    if os.path.isdir(path):
        for old in os.listdir(path):
            os.remove(os.path.join(path, old))


def main():
    clean("leaves")
    # A foot through dry leaves: short, crisp, the airy top softened a little.
    one_shots([352963, 218305, 420390, 179339], "leaves/rustle", 8, before=0.05, length=0.5, rms_db=-29, spacing=0.35,
              lowpass=11000.0)
    # Into wet leaves: duller and softer.
    one_shots([503262, 455063, 94342], "leaves/wet", 5, before=0.04, length=0.45, rms_db=-30, spacing=0.3,
              lowpass=7000.0)
    # Landing in a pile: a body dropping into leaves, and the heaviest crunches of the walks, longer.
    one_shots([853591, 420390, 218305], "leaves/plunge", 3, before=0.06, length=0.95, rms_db=-24, spacing=0.8,
              lowpass=12000.0, strict=False, max_crest_db=21.0)
    # A rake stroke dragging leaves over the ground.
    one_shots([448966, 593201, 419617], "leaves/rake", 5, before=0.08, length=0.8, rms_db=-27, spacing=0.6,
              strict=False)


if __name__ == "__main__":
    main()
