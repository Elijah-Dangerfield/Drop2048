#!/usr/bin/env python3
"""Synthesise the placeholder sound bank described by `Sound` in Cue.kt.

These are stand-ins, not produced audio. Every sound here is a handful of sine
partials, a filtered noise burst or a pitch sweep, written with the standard
library alone so the bank can be regenerated on any machine with python3 and a
Vorbis encoder.

    ./scripts/generate_placeholder_audio.py

Output is mono 44.1 kHz Ogg Vorbis, written to both places the engines look:

    libraries/ui/src/androidMain/assets/audio/
    apps/ios/iosApp/audio/

The file stems come from `Sound.key`, read out of Cue.kt at run time, so a new
entry in the enum fails this script loudly instead of shipping a silent cue.
"""

import math
import os
import random
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import wave

SAMPLE_RATE = 44100
FADE_IN = 0.002
FADE_OUT = 0.012

# Vorbis does not always hand back exactly the samples it was given: decoders
# trim to the last granule position and can land a frame or two short. On a
# sound that is still ringing at its final sample that shows up as a click, so
# every file ends on a run of digital silence that a trim can eat instead.
TAIL_SILENCE = 0.010

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CUE_KT = os.path.join(
    REPO,
    "libraries/ui/src/commonMain/kotlin/com/dangerfield/drop2048/libraries/ui/system/Cue.kt",
)
DESTINATIONS = (
    os.path.join(REPO, "libraries/ui/src/androidMain/assets/audio"),
    os.path.join(REPO, "apps/ios/iosApp/audio"),
)

# Ogg Vorbis quality. 4 keeps a 200ms blip near 3 KB, and SoundPool decodes the
# whole bank into memory, so size is not free.
VORBIS_QUALITY = "4"


# --- primitives -------------------------------------------------------------


def frames(duration):
    return int(round(duration * SAMPLE_RATE))


def buffer(duration):
    return [0.0] * frames(duration)


def mix(target, source, at=0.0, gain=1.0):
    """Add `source` into `target` starting at `at` seconds, clipped to length."""
    start = frames(at)
    for i, value in enumerate(source):
        index = start + i
        if index >= len(target):
            break
        target[index] += value * gain


def decay(tau, attack=0.003):
    """Exponential decay behind a short linear attack, as a function of seconds."""

    def envelope(t):
        rise = min(1.0, t / attack) if attack > 0 else 1.0
        return rise * math.exp(-t / tau)

    return envelope


def swell(attack, tau, peak=1.0):
    """Slower in, long out. For the two danger tones, which are held rather than struck."""

    def envelope(t):
        return peak * min(1.0, t / attack) * math.exp(-t / tau)

    return envelope


def partials(freq, duration, stack, tau, attack=0.003):
    """A stack of (ratio, amplitude, tau_scale) sines sharing one attack."""
    out = buffer(duration)
    for n in range(len(out)):
        t = n / SAMPLE_RATE
        rise = min(1.0, t / attack) if attack > 0 else 1.0
        total = 0.0
        for ratio, amplitude, tau_scale in stack:
            total += amplitude * math.exp(-t / (tau * tau_scale)) * math.sin(
                2 * math.pi * freq * ratio * t
            )
        out[n] = rise * total
    return out


# Marimba-ish: a strong fundamental with a fourth and a tenth over it. The
# ratios are whole numbers on purpose. The cascade resamples this up to 2.0x,
# and a partial that is a whole-number multiple of the fundamental stays one at
# any playback rate, so the tone reads as the same note an octave up rather than
# as a chirp.
BELL_STACK = (
    (1.0, 1.00, 1.00),
    (2.0, 0.16, 0.55),
    (4.0, 0.22, 0.40),
    (10.0, 0.05, 0.12),
)


def bell(freq, duration, tau, amplitude=1.0, attack=0.003):
    return [s * amplitude for s in partials(freq, duration, BELL_STACK, tau, attack)]


def sweep(f_start, f_end, duration, envelope, harmonic=0.0):
    """Exponential glide, phase-accumulated so the pitch stays continuous."""
    out = buffer(duration)
    phase = 0.0
    for n in range(len(out)):
        t = n / SAMPLE_RATE
        freq = f_start * (f_end / f_start) ** (t / duration)
        phase += 2 * math.pi * freq / SAMPLE_RATE
        out[n] = envelope(t) * (math.sin(phase) + harmonic * math.sin(2 * phase))
    return out


def noise(duration, seed):
    rng = random.Random(seed)
    return [rng.uniform(-1.0, 1.0) for _ in range(frames(duration))]


def low_pass(samples, cutoff):
    a = 1.0 - math.exp(-2 * math.pi * cutoff / SAMPLE_RATE)
    out = []
    y = 0.0
    for x in samples:
        y += a * (x - y)
        out.append(y)
    return out


def high_pass(samples, cutoff):
    return [x - y for x, y in zip(samples, low_pass(samples, cutoff))]


def shaped(samples, envelope):
    return [s * envelope(n / SAMPLE_RATE) for n, s in enumerate(samples)]


def normalise(samples, peak):
    loudest = max(abs(s) for s in samples) or 1.0
    return [s * peak / loudest for s in samples]


def fade(samples):
    """Ramp both ends to zero, then leave silence. A step to zero is a click."""
    rise = max(1, frames(FADE_IN))
    fall = max(1, min(frames(FADE_OUT), len(samples) // 4))
    out = list(samples)
    for n in range(min(rise, len(out))):
        out[n] *= n / rise
    for n in range(min(fall, len(out))):
        out[len(out) - 1 - n] *= n / fall
    return out + [0.0] * frames(TAIL_SILENCE)


# --- the bank ---------------------------------------------------------------

C5, E5, G5, C6 = 523.25, 659.25, 783.99, 1046.50
DANGER_ROOT = 98.0  # G2


def move():
    """A tick, under 40ms. A run of these is a texture, not a rhythm."""
    return partials(
        2600.0, 0.024, ((1.0, 1.0, 1.0), (2.0, 0.2, 0.5)), tau=0.006, attack=0.001
    )


def ui_tap(freq=1180.0, duration=0.045):
    return partials(
        freq, duration, ((1.0, 1.0, 1.0), (2.0, 0.22, 0.6)), tau=0.011, attack=0.002
    )


def ui_back():
    return ui_tap(freq=760.0, duration=0.05)


def spawn():
    """Near silent by design. Cue.Spawn has no haptic either."""
    return partials(1900.0, 0.03, ((1.0, 1.0, 1.0),), tau=0.007, attack=0.002)


def lock():
    """A thud: a body that drops in pitch as it lands, under a damped click."""
    out = buffer(0.12)
    mix(out, sweep(190.0, 85.0, 0.12, decay(0.035, attack=0.002), harmonic=0.2))
    click = shaped(low_pass(noise(0.03, seed=11), cutoff=2400.0), decay(0.005, attack=0.0005))
    mix(out, click, gain=0.5)
    return out


def hard_drop():
    """The one input the player cannot take back, falling away from them."""
    out = buffer(0.16)
    mix(out, sweep(1500.0, 170.0, 0.15, decay(0.075, attack=0.004), harmonic=0.35))
    air = shaped(
        high_pass(low_pass(noise(0.15, seed=7), cutoff=3000.0), cutoff=600.0),
        decay(0.05, attack=0.005),
    )
    mix(out, air, gain=0.22)
    return out


def merge():
    """The one that has to survive playback at 2.0x. See BELL_STACK."""
    return bell(C5, 0.18, tau=0.055)


def merge_big():
    """The same tone with an octave under it and a fifth over it. Fuller, not different."""
    out = buffer(0.26)
    mix(out, bell(C5 / 2, 0.26, tau=0.095, amplitude=0.9))
    mix(out, bell(C5, 0.24, tau=0.075, amplitude=0.55), at=0.004)
    mix(out, bell(G5, 0.20, tau=0.055, amplitude=0.28), at=0.010)
    return out


def burst():
    """The row clear, rising. Around 360ms, to sit under the 390ms burst haptic."""
    out = buffer(0.36)
    for index, note in enumerate((C5, E5, G5, C6)):
        at = index * 0.068
        mix(out, bell(note, 0.36 - at, tau=0.085, amplitude=0.85), at=at)
    shimmer = shaped(high_pass(noise(0.30, seed=23), cutoff=5200.0), swell(0.08, 0.10))
    mix(out, shimmer, at=0.04, gain=0.10)
    return out


def bomb():
    """Filtered noise over a body that sags. Short, so it does not smear the next lock."""
    out = buffer(0.22)
    mix(out, sweep(62.0, 44.0, 0.22, decay(0.075, attack=0.002)))
    rumble = shaped(low_pass(noise(0.20, seed=31), cutoff=340.0), decay(0.06, attack=0.002))
    mix(out, rumble, gain=2.4)
    crack = shaped(low_pass(noise(0.04, seed=37), cutoff=1600.0), decay(0.008, attack=0.0005))
    mix(out, crack, gain=0.5)
    return out


def board_cleared():
    """The rarest event in the game, so it gets the longest and loudest sound."""
    out = buffer(0.6)
    for index, note in enumerate((C5, E5, G5, C6)):
        mix(out, bell(note, 0.30, tau=0.07, amplitude=0.7), at=index * 0.055)
    chord = ((C5 / 2, 0.55), (C5, 0.50), (E5, 0.40), (G5, 0.40), (C6, 0.35))
    for note, weight in chord:
        mix(out, bell(note, 0.36, tau=0.16, amplitude=weight, attack=0.008), at=0.22)
    shimmer = shaped(high_pass(noise(0.45, seed=41), cutoff=4800.0), swell(0.12, 0.22))
    mix(out, shimmer, at=0.05, gain=0.09)
    return out


def danger_enter():
    """Two tones a hair apart, beating against each other. The board is filling."""
    out = buffer(0.30)
    mix(out, sweep(DANGER_ROOT, DANGER_ROOT * 0.97, 0.30, swell(0.05, 0.45)))
    mix(out, sweep(DANGER_ROOT * 1.06, DANGER_ROOT * 1.03, 0.30, swell(0.05, 0.45, peak=0.8)))
    mix(out, sweep(DANGER_ROOT / 2, DANGER_ROOT / 2, 0.30, swell(0.06, 0.45, peak=0.5)))
    return out


def danger_exit():
    """The beating pair from danger_enter, resolved: the upper tone climbs to a fifth."""
    out = buffer(0.28)
    mix(out, sweep(DANGER_ROOT, DANGER_ROOT, 0.28, swell(0.02, 0.30)))
    mix(out, sweep(DANGER_ROOT * 1.06, DANGER_ROOT * 1.5, 0.28, swell(0.02, 0.30, peak=0.75)))
    mix(out, sweep(DANGER_ROOT * 2, DANGER_ROOT * 3, 0.26, swell(0.03, 0.30, peak=0.30)), at=0.02)
    return out


def level_up():
    """Three notes up on a hollow tone. Nothing else in the bank is a motif."""
    out = buffer(0.26)
    hollow = ((1.0, 1.0, 1.0), (3.0, 0.22, 0.55), (5.0, 0.09, 0.35))
    for freq, at, tau in ((587.33, 0.0, 0.055), (880.0, 0.060, 0.055), (1174.66, 0.120, 0.110)):
        mix(out, partials(freq, 0.26 - at, hollow, tau=tau, attack=0.004), at=at)
    return out


def stacked_out():
    """Four notes down, the last one sagging flat. Deflating rather than buzzing."""
    out = buffer(0.5)
    notes = (349.23, 293.66, 233.08, 174.61)
    for index, freq in enumerate(notes):
        at = index * 0.105
        last = index == len(notes) - 1
        voice = sweep(
            freq,
            freq * (0.94 if last else 0.99),
            0.5 - at,
            decay(0.15 if last else 0.09, attack=0.012),
            harmonic=0.12,
        )
        mix(out, voice, at=at, gain=1.0 - 0.1 * index)
    return low_pass(out, cutoff=3000.0)


# Peaks are deliberately uneven. `move` and `ui_tap` fire hundreds of times a
# run and `board_cleared` fires almost never, so a bank flattened to one peak
# would be exhausting at exactly the wrong moments.
BANK = {
    "spawn": (0.045, spawn),
    "move": (0.10, move),
    "hard_drop": (0.50, hard_drop),
    "lock": (0.45, lock),
    "merge": (0.55, merge),
    "merge_big": (0.62, merge_big),
    "burst": (0.80, burst),
    "bomb": (0.60, bomb),
    "board_cleared": (0.90, board_cleared),
    "danger_enter": (0.40, danger_enter),
    "danger_exit": (0.36, danger_exit),
    "level_up": (0.60, level_up),
    "stacked_out": (0.62, stacked_out),
    "ui_tap": (0.13, ui_tap),
    "ui_back": (0.12, ui_back),
}


# --- encoding ---------------------------------------------------------------


def write_wav(path, samples):
    data = bytearray()
    for sample in samples:
        clamped = max(-1.0, min(1.0, sample))
        data += struct.pack("<h", int(clamped * 32767))
    with wave.open(path, "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(SAMPLE_RATE)
        handle.writeframes(bytes(data))


def encoder():
    """oggenc if it is installed, otherwise an ffmpeg built with libvorbis.

    oggenc first rather than as a fallback: Homebrew's current ffmpeg bottle is
    built without libvorbis, and ffmpeg's own Vorbis encoder refuses mono.
    """
    oggenc = shutil.which("oggenc")
    if oggenc:
        return lambda src, dst: [oggenc, "-Q", "-q", VORBIS_QUALITY, "-o", dst, src]

    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg:
        encoders = subprocess.run(
            [ffmpeg, "-hide_banner", "-encoders"], capture_output=True, text=True, check=True
        ).stdout
        if "libvorbis" in encoders:
            return lambda src, dst: [
                ffmpeg, "-y", "-loglevel", "error", "-i", src,
                "-c:a", "libvorbis", "-q:a", VORBIS_QUALITY, "-ac", "1", dst,
            ]

    sys.exit("No Vorbis encoder found. Install one with: brew install vorbis-tools")


def keys_from_cue_kt():
    with open(CUE_KT, encoding="utf-8") as handle:
        source = handle.read()
    body = re.search(r"enum class Sound\(val key: String\) \{(.*?)\n\}", source, re.S)
    if not body:
        sys.exit(f"Could not find the Sound enum in {CUE_KT}")
    return re.findall(r'\w+\("([a-z_]+)"\)', body.group(1))


def main():
    keys = keys_from_cue_kt()
    missing = [key for key in keys if key not in BANK]
    orphaned = [key for key in BANK if key not in keys]
    if missing or orphaned:
        sys.exit(
            "The bank and Sound disagree. "
            f"No recipe for {missing}; no such Sound for {orphaned}."
        )

    command = encoder()
    for directory in DESTINATIONS:
        os.makedirs(directory, exist_ok=True)

    with tempfile.TemporaryDirectory() as scratch:
        for key in keys:
            peak, build = BANK[key]
            # Faded first, then normalised: on a 28ms tick the fade-in lands on
            # the attack, so normalising afterwards is what makes the peak the
            # peak that was asked for.
            samples = normalise(fade(build()), peak)
            loudest = max(abs(s) for s in samples)
            if loudest >= 1.0:
                sys.exit(f"{key} clips at {loudest:.3f}")

            wav = os.path.join(scratch, f"{key}.wav")
            ogg = os.path.join(scratch, f"{key}.ogg")
            write_wav(wav, samples)
            subprocess.run(command(wav, ogg), check=True, capture_output=True)
            for directory in DESTINATIONS:
                shutil.copyfile(ogg, os.path.join(directory, f"{key}.ogg"))

            size = os.path.getsize(ogg) / 1024
            duration = len(samples) / SAMPLE_RATE * 1000
            print(f"{key:<14}{duration:6.0f} ms   peak {loudest:.2f}   {size:5.1f} KB")

    print()
    for directory in DESTINATIONS:
        print(f"wrote {len(keys)} files to {os.path.relpath(directory, REPO)}")


if __name__ == "__main__":
    main()
