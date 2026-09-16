# Agent TODO

The queue the orchestrating session works from: things found mid-chunk that do not belong in the
chunk that found them.

Not a changelog. **Delete entries as they land** rather than ticking them off, so this stays a
queue. Human-only items go in `OWNER-TODO.md`.

Audited 2026-09-11 — thirteen entries had landed chunks ago and were removed. A stale queue is
worse than no queue: an agent reading it redoes finished work.

---

## Now

### Point the legal config keys at the real Pages URLs

`legal.privacyUrl` and `legal.termsUrl` default to `https://drop2048.app/terms` and `/privacy`,
which have never existed. The pages are now live and serving:

- `https://elijah-dangerfield.github.io/Drop2048/privacy.html`
- `https://elijah-dangerfield.github.io/Drop2048/terms.html`

Both returned HTTP 200. Change the compiled defaults in `:libraries:gameconfig`. Both are
remote-overridable, so a custom domain later is a config push rather than a release.

Note C13a rewrote `privacy.html` to describe the app that actually exists (AdMob, Grafana, the
install id), so what is being served is accurate — it was just unreachable.


### C16 · The owner-directive channel, the debug FAB, and a QA menu

Owner request, 2026-09-11: port Sodogku's system wholesale. Four pieces, and the
point of the whole thing is that the round trip from "this bothers me while playing" to "it is on
the list" is one swipe.

**1. `FeedbackKind` as a Sentry tag.** `Sodogku/libraries/sodogku/src/.../FeedbackKind.kt`. Three
kinds — `feedback`, `bug_report`, `owner_directive` — riding as the `feedback_kind` **tag**, not a
message prefix, because a carrier event's message is also its issue title and titles get grouped,
AI-resummarised and edited. A tag is indexed and queryable.

**2. The carrier + twin in Sentry.** `Sodogku/libraries/sodogku/impl/.../AppTelemetry.kt` around
lines 200-265. A `captureMessage` carrier holds the tags, breadcrumbs and attachments
(`feedback.txt`, `session-log.txt`, `screenshot-N.jpg`); Sentry's own user-feedback record holds the
words. **Each report is fingerprinted to its own issue** (`["feedback", uuid]`), set on a *local*
scope so nothing leaks onto later events, at `INFO` level so it does not sort with crashes.

The message is deliberately duplicated onto the carrier as an extra **and** an attachment: the
legacy feedback API's comments render wherever the org's settings decide, and a real report arrived
with the log and screenshot visible and the typed text nowhere.

**3. The draggable FAB.** `Sodogku/apps/compose/src/.../devfeedback/` — `DevFeedbackFab` (48dp,
position stored as *fractions* so it survives rotation and a different device, clamped so it can
never be parked off-screen), `DevFeedbackHost` (records the content into a `GraphicsLayer` so a
screenshot needs no platform API or permission, **tester builds only** — a player's build gets one
bare `Box`), `DevFeedbackPanel`, `DevFeedbackViewModel`, `DevFeedbackFabCache`.

Read `DevFeedbackFab`'s KDoc before touching the gesture: `clickable` and `detectDragGestures`
coexist on purpose, and getting the order wrong means a dragged button also files a directive.

**4. The QA menu.** `Sodogku/apps/compose/src/.../qa/QaToolsScreen.kt` + `QaToolsRoute`. Its
feedback switch is the reason it exists, and it is deliberately **the one screen reachable without
the button it switches off**. Note its split: the FAB toggle shows on any tester build, everything
destructive is `isDebug` only, because a TestFlight tester needs to hide the button but must not be
handed irreversible tools.

**Also port `.claude/skills/feedback-triage/`** and its `docs/feedback-log.md` ledger. The ledger is
what makes triage idempotent — the TODO queue is not a record of what was seen, because items are
deleted when they ship, so without it every run after a fix re-files the same report.

**Adapt, do not copy blind.** Drop 2048 already has: a debug menu behind seven taps with a
`debugMenuUnlocked` flag (C12), `ShakeDialog`, and a feedback path whose session-log attachment C13a
made **opt-in** with breadcrumbs cleared, because every report was leaking scores. An owner
directive from the owner's own build wants the log unconditionally — that is a different kind, not a
reason to undo C13a's fix. `BuildInfo.isTesterBuild` needs checking; Sodogku's is
`isDebug || isTestFlight`.


### The Settings row on the Home and pause overlays swallows taps

Found repeatedly by C15 while navigating (~25 minutes lost). The overlay's own click — "tap to
resume" — wins over the `Settings` text row. Reproduces on a stock debug build and predates the ads
work.

### `:libraries:ads:fake` is linked into iOS release binaries

Kotlin/Native has no build-type source sets, so the house ad network ships in every iOS binary and
is held out only by a runtime `Platform.isDebugBinary` check (L78, layer 3). If iOS ever gains a real
ad SDK, this wants a Gradle-level exclusion or an `expect`/`actual` no-op so iOS gets layer 1 too.

### `GameQuietButton` has no button semantics and no minimum touch target

A raw `pointerInput` with no `Role.Button` and no 48dp floor. Inherited from `GameScreen`'s private
`OverlayOption` and **now used on the paywall's Restore**, so it has spread. C11's accessibility
pass did not cover it because it did not exist yet.

### No harness exercises a `bottomSheet<>` destination

The paywall is the app's first one. Its conversion is verified on Android by hand only, and
Material draws the sheet in its own window so the goldens pin the content and not the scrim or the
drag handle. iOS has never rendered it at all.

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
