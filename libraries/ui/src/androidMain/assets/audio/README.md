# Placeholder sound bank

These fifteen files are **synthesised placeholders, not produced audio**. Each
one is a few sine partials, a filtered noise burst or a pitch sweep, generated
by a script so the game stops shipping silent and so the cue routing can be
heard end to end. They are meant to be replaced by real sound design. Replacing
one is a drop-in: keep the name, keep it mono Ogg Vorbis, delete the recipe from
the script if it no longer describes what is in the file.

## Regenerating

```bash
./scripts/generate_placeholder_audio.py
```

The script needs `oggenc` (`brew install vorbis-tools`) or an ffmpeg built with
libvorbis. Homebrew's current ffmpeg bottle has neither libvorbis nor a mono
capable Vorbis encoder, which is why `oggenc` is the first choice.

It writes the same files to two places, and both are in the repo:

- `libraries/ui/src/androidMain/assets/audio/` (this folder), merged into the
  APK from the library module.
- `apps/ios/iosApp/audio/`. Nothing has to be dragged into Xcode: the `iosApp`
  folder is a file system synchronized root group (`objectVersion = 77`), so
  every file under it is in the target already and `project.pbxproj` does not
  change. Xcode flattens the subfolder, so the samples land in the bundle root
  rather than in `audio/`, which is the second place `SoundBank.ios.kt` looks.
  Verified against Xcode's own build plan: the manifest has a `CpResource` task
  for all fifteen files.

The file names come from `Sound.key` in `Cue.kt`, read at generation time. A new
entry in that enum fails the script rather than quietly shipping a silent cue.

## What is here

| File | Length | What it is |
| --- | --- | --- |
| `move.ogg` | 34 ms | A 2.6 kHz tick, quiet. Fires on every column of steering. |
| `hard_drop.ogg` | 170 ms | 1500 Hz down to 170 Hz, plus a little air. |
| `lock.ogg` | 130 ms | A 190 Hz body sagging to 85 Hz under a damped click. |
| `merge.ogg` | 190 ms | C5 with harmonic partials at 2x, 4x and 10x. |
| `merge_big.ogg` | 270 ms | The same tone with C4 under it and G5 over it. |
| `burst.ogg` | 370 ms | C5 E5 G5 C6 arpeggio and a noise shimmer. |
| `bomb.ogg` | 230 ms | Low filtered noise over a body sliding 62 Hz to 44 Hz. |
| `board_cleared.ogg` | 610 ms | The arpeggio, then the whole chord held. |
| `danger_enter.ogg` | 310 ms | Two tones a few cents apart, beating, over a sub. |
| `danger_exit.ogg` | 290 ms | The same pair, with the upper one resolving to a fifth. |
| `level_up.ogg` | 270 ms | D5, A5, D6 on a hollow odd-harmonic tone. |
| `stacked_out.ogg` | 510 ms | F4, D4, Bb3, F3 falling, the last one sagging flat. |
| `ui_tap.ogg` | 55 ms | A soft 1180 Hz blip. |
| `ui_back.ogg` | 60 ms | The same blip at 760 Hz. Currently unused. |
| `spawn.ogg` | 40 ms | Almost nothing, on purpose. Currently unused. |

`spawn` and `ui_back` are declared in `Sound` but no game code fires them yet.
They are here so the bank is complete and so nothing is missing the day
something does.

## The choices that are not arbitrary

**`merge` has to survive being resampled up an octave.** `SoundBank.rate` pitches
a cascade by playback rate, up to 2.0x at twelve semitones, so the merge tone is
built from whole-number partials with no noise and a 3 ms attack. Whole-number
ratios stay whole-number at any playback rate, which is what keeps the top of a
cascade sounding like a note rather than a chirp. Measured after encoding: the
dominant partial is 523.2 Hz at 1.0x, 740.0 Hz at 1.5x and 1046.5 Hz at 2.0x,
with the partial structure unchanged.

**The set is not level matched to one peak.** `move` sits around -20 dBFS and
`board_cleared` around -1 dBFS. Flattening them would make the two sounds that
fire hundreds of times a run as loud as the one that fires almost never, which
is the fastest way to get the sound switch turned off.

**Every file fades in and out and then ends in silence.** A couple of
milliseconds in, twelve out, ten of digital silence after that. Vorbis decoders
trim to the last granule position and can come back a frame or two short, and on
a sound still ringing at its final sample that is an audible click.
