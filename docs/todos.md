# Agent TODO

The queue the orchestrating session works from: things found mid-chunk that do not belong in the
chunk that found them.

Not a changelog. **Delete entries as they land** rather than ticking them off, so this stays a
queue. Human-only items go in `OWNER-TODO.md`.

Audited 2026-09-11 — thirteen entries had landed chunks ago and were removed. A stale queue is
worse than no queue: an agent reading it redoes finished work.

---

## Now

### A haptic can be cancelled by the next one a millisecond later

Observed in `dumpsys`: a `Move` and the `Merge` behind it landed 1ms apart and the first came back
`cancelled_superseded`. The `Cues` throttle covers Move→Move and **not** Move→Merge. Needs a
priority or a queue, not a longer gate.

### Extend the R8 smoke test past one journey

C13a proved R8 needs **zero keep rules** for the tutorial, a cascade, three routes and a Room
screen. Untouched and all carrying `@Serializable` models: ads, billing, paywall, sharing,
leaderboards, achievements, launch gates, remote config.

Also unexplained: 15 `R8: An error occurred when parsing kotlin metadata` warnings, consistent with
R8 being older than this Kotlin version. Nobody has checked which classes.

### Restore Achievements to the benchmark journey

It has no baseline profile coverage and no R8 coverage. Same journey, one more screen.

### Widen `AnimatedStateReadInComposition` to raw `Animatable.value` reads

The rule only catches `by animateFloatAsState`. A raw `Animatable.value` read in composition is the
same bug with the same 60fps cost and is not covered. The board is the most animation-dense screen
in the app and nothing enforces it there.

### A second metric for permanent obstructions

D8 deferred it. Stones arrive at level 12 and clocked runs reach 19-22, so board congestion is real
and `clutter` deliberately cannot see it. A second count, not a change to `clutter`.

### Delete the iOS camera bridge

`NativeViewFactory`'s six camera members, `CameraGuidanceState`, and the camera half of
`IOSNativeViewFactory.swift` (544 lines). C13a removed the Kotlin side and the permission; this is
dead code in the iOS binary. The Apple Sign In button factory is dead the same way. Needs someone
who can build the Xcode target.

### `FuseReach` could come up ~10%

The bomb fuse is legible at real cell size and is the smallest of the three special marks. One
constant in `BlockFace.kt`.

### Decide whether a step-1 merge should pop the score louder

With C3a's per-step pacing (L58) the chain steps read well, and the single merge is now the one beat
that looks plain by comparison.

## Soon

### The saved-run blob carries a full `EngineConfig` copy, ~3KB

Correct for determinism (D5) — a seed only replays if the numbers it was played under travel with
it. But now that C7 made the config remote, the saved run should probably carry a config **version**
rather than a copy.

### Stop the convention plugin injecting coroutines into every KMP module

`configureKotlinMultiplatform` puts `kotlinx-coroutines-core` in every KMP module's `commonMain`, so
"zero dependencies" is true of `:libraries:cascade`'s build file and **not** of its compile
classpath. There is already a `path == ":libraries:core"` special case to mirror.

### Give blocks a stable identity across a transcript

The transcript gives from/to cells, which is enough to animate. A stable id makes "this specific
tile travelled" trivial rather than inferred. **Cheap now, invasive later.**

### `dragAcrossCells` and the steer gesture have no test

Neither gesture is unit-testable; `BoardGeometry` underneath them is, thoroughly. Wants a Compose UI
test.

### Measure the two things no policy has ever done

**Hold** does not exist any more (D11), so that one is closed. Still open: how often SPEC 5.3's
board-aware cap actually clamps a draw. It is almost certainly inert once a 1024 is on the board,
making it an early-game valve only — worth knowing before anyone reasons about `spawn.cap.divisor`
as a live knob.

### Re-run `tools/balance` with measured constants

Once a week of telemetry exists, replace the modelled 250ms `decisionMillis` and 120ms `tapMillis`
with real ones and update SPEC 4.4's numbers with error bars. This is the whole point of C8's
instrument (L41).

### `:tools:balance:test` is not in the standard gate

It runs under `check` but not `testDebugUnitTest`, so the harness's own guard tests do not run in the
normal loop.

### Add `--csv` to the balance harness

The text report is right for a one-off read and wrong for trends. Worth doing at the second
measurement, not the first.

### Sweep the last 8 `VerifyStrings` baseline entries

Down from 53 → 28 → 9 → 8. What remains: `AccessDeniedScreen`, `BlockingErrorScreen`,
`ErrorDialogContent`, `ShakeDialog`, `SplashScreen`, `NativeButton.jvm`.

### Decide whether `SessionRejectionBus` and `AccessDenied` earn their keep

Both survived C0 because they are welded to `NetworkClientImpl`'s 401 path and `App.kt`'s routing.
**Nothing can trigger either one** — the server's ban gate went with the auth stack. The server
surface is final now, so this is answerable.

### Check `:libraries:review` is still wanted

The template ships in-app review. SPEC 15 wants the rate prompt only after a run that set a personal
best, which is a real trigger — but confirm the library is wired rather than assuming.

### `docs/PORT-CANDIDATES.md` writeback

`AGENTS.md` says anything a brand-new app would want goes back to `../KMPTemplate`'s
`docs/PORT-CANDIDATES.md`. This project has generated a lot of it: the screenshot-harness wiring
with its verification trap, the palette property test, the transcript playback architecture, the
Room migration test, the debug session latch. Collect and write back in one pass.

## Later

### Zen mode is nearly free

SPEC 2 cut it. It is a one-line engine change (never tick) plus a Pro gate, and Pro exists now —
which makes its value proposition much less thin.

### The powerup economy re-entry

All five powerups are designed against board state the engine already exposes, and Undo ships
internally (SPEC 5.6). Keep it additive: no engine change should make a powerup harder to add later.

---

## Watch list

Not tasks. Shapes that have already bitten this project, worth checking when the relevant code
lands.

**Things that look green and are not:**

- **A clean detekt run does not prove a custom rule ran.** The daemon caches detekt's worker
  classloader. Prove dispatch by making a rule report unconditionally, confirm the flood, revert.
- **A skipped test is not a passing test, and it stops being maintained.** One asserted something a
  migration had falsified the day it landed and never failed, because it never ran (L46).
- **A failed iOS link reports `BUILD SUCCEEDED`** and silently runs the previously linked framework
  (L24). **A Kotlin compiler crash** can also pass, because Gradle retries out of process (L62).
- **Goldens are not declared task inputs**, so swapping one leaves the test UP-TO-DATE and green.
  Proving the verifier works needs `--rerun` (L60). And the capture itself is a **no-op** unless
  verification is enabled (L39).
- **An API with no production call site looks like coverage.** Prove the call site by deleting it
  and checking the *caller's* test goes red while the API's own test stays green (L55).

**Things that fail silently at runtime:**

- **A `published()` helper that copies everything except one field** started a real game underneath
  the game-over scrim: invisible, untouchable, live for two chunks (L32).
- **A `pointerInput` keyed on a value that changes mid-gesture** drops its release callback, so any
  hold-to-do-X leaks its "on" state (D21).
- **`Modifier.blur` clips to bounds at any radius, including zero** (L43).
- **Top-level `val`s initialise in declaration order**, so a colour derived from one declared below
  it comes out transparent with no warning (L22). Same family: a `get() = false` on a sealed
  interface is a JVM default method and can half-build a companion (L42).
- **A migration fallback that destroys data** is reasonable when a server holds a copy and
  unrecoverable here (L33). **And Room prefers a migration to a drop**, so "the row survived" proves
  nothing about the narrowing (L61).
- **`UIApplication.canOpenURL` needs its scheme in `LSApplicationQueriesSchemes`**, or every outbound
  link silently opens nothing.

**Things tests structurally cannot see:**

- **Every test double answers instantly**, so a gate on a slow dependency is invisible. A paywall sat
  blank for 35 seconds behind 94 passing tests (L68).
- **Infinite animations hang preview and screenshot capture** unless they return a fixed value under
  `LocalInspectionMode`.
- **A test that advances a full drop tick and then asserts on the falling block** is asserting about
  the next one (L31).
