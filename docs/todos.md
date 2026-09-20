# Agent TODO

The queue the orchestrating session works from: things found mid-chunk that do not belong in the
chunk that found them.

Not a changelog. **Delete entries as they land** rather than ticking them off, so this stays a
queue. Human-only items go in `OWNER-TODO.md`.

Audited 2026-09-20 against the working tree, entry by entry. Two landed and were deleted, eleven
were rewritten because a path, a constant or a premise had moved under them, and five shapes found
that day were added to the Watch list. A stale queue is worse than no queue: an agent reading it
redoes finished work.

---

## Now

### The overlay options are 14dp apart, which caps their finger target at 39.5dp

Left behind by the `GameQuietButton` fix, and it is a design question rather than a bug. The
assistive-tech target is a full 48×48 now, because the framework inflates semantics bounds once a
click action exists and resolves overlaps by distance. The **pointer** target cannot get there:
option rows sit 14dp apart, two overlapping pointer targets are settled by draw order rather than by
which label is nearer, so growth is capped at half the gap and the rows land at 39.5dp tall.

Closing the last 8.5dp means more room between the option rows on the paused overlay, which moves
copy and moves goldens. Worth doing if the rows are being touched anyway; not worth a change on its
own.

### Two iOS *test* binaries do not link, and nobody had noticed

Found on 2026-09-16, the first time `./gradlew iosSimulatorArm64Test` compiled repo-wide. Both are
test-executable-only. `:apps:compose:linkDebugFrameworkIosSimulatorArm64`, the real app framework,
links fine, so iOS dev builds are unaffected.

- **`:libraries:networking:impl:linkDebugTestIosSimulatorArm64`** — `Undefined symbols:
  __swift_FORCE_LOAD_$_swiftCompatibility56`, `…Concurrency`, from Wiretap's Swift cinterop
  (`WiretapShakeDetector.swift.o`). The libraries are present under
  `XcodeDefault.xctoolchain/usr/lib/swift/iphonesimulator/`; Kotlin/Native just does not put that
  directory on the test executable's link path. `-Pdrop2048.wiretap.ios=false` links clean, which
  pins it exactly. The fix means teaching `build-logic` Xcode's internal library layout, and the
  Wiretap variant selection already carries a warning about a `-P` flag on one of two Gradle
  invocations silently re-linking the inspector into release. Read that before touching it.
- **`:apps:compose:linkDebugTestIosSimulatorArm64`** — `ld: framework 'Sentry' not found`,
  independent of the above. Xcode supplies the Sentry Cocoa framework when it embeds the app
  framework; it is not on the Kotlin test executable's search path. What it blocks is currently
  nothing: `:apps:compose`'s whole Native test payload is the template placeholder asserting
  `1 + 2 == 3`.

Excluding those two, Native is **866 tests, 0 skipped, 0 failures across 35 modules**, including
`:libraries:networking:impl`'s 14 that had never run. Until they are fixed, the honest Native command
is `./gradlew iosSimulatorArm64Test -Pdrop2048.wiretap.ios=false -x :apps:compose:iosSimulatorArm64Test`.

### Achievements has a baseline profile hole, and it is not one screen

`BenchmarkJourney` leaves it out and D27 took away the stand-in: the Daily Challenge used to cover
the same shape from the same menu, one tap away, and it is gone. What is left is the one screen a
player can only reach through Settings, in the fourth of eight sections, so reaching it means
scrolling a list.

The R8 half is closed. `MinifiedReleaseSurfacesTest.theMinifiedAppDrawsTheAchievementsGrid` covers
the grid on the minified release variant, and `SurfaceJourney`'s KDoc names the three reasons the
naive swipe fails: a row under the translucent `TopBar` is still `hasObject`, a swipe down the middle
of the column grabs a text field, and `waitForIdle` returns while the list is still flinging. Those
verbs are deliberately kept out of `BenchmarkJourney`, because a verb added there changes what gets
AOT-compiled.

So this is a judgement and not a chore: either accept that the grid is not on the cold-start path and
say so in `BenchmarkJourney`'s KDoc, or lift `aboveReach` / `scrollContent` / `awaitStill` into the
shared journey and pay the profile cost.

### Widen `AnimatedStateReadInComposition` to raw `Animatable.value` reads

The rule only catches `by animateFloatAsState`. A raw `Animatable.value` read in composition is the
same bug with the same 60fps cost and is not covered. The board is the most animation-dense screen
in the app and nothing enforces it there.

### A second metric for permanent obstructions

D8 deferred it and D26 re-confirmed the premise rather than removing it. Stones still arrive at level
12, measured and deliberately left there, and `special.stone.firstLevel` is a live remote key priced
at roughly half the 2048 rate per two levels, so the one dial that moves board congestion can be
turned from a console with nothing measuring what it does.

**The numbers this entry used to quote were measured at `blocksPerLevel` 20 and should not be
reused.** D26 took it to 15, so level 12 now arrives at 180 drops rather than 240, against a median
greedy run of 317 rather than 355. Congestion starts earlier in a shorter run, and by how much is
exactly what nothing counts. SPEC 4.4's warning applies to whatever replaces them: a cheaper level is
not a lower difficulty, so read run length and tier ceiling, never the level number.

A second count, not a change to `clutter`. D8's binding constraint still holds: the harness and SPEC
17's live telemetry must compute it from the same function in `:libraries:cascade`.

### Delete the iOS camera bridge

`NativeViewFactory`'s six camera members, `CameraGuidanceState`, and the camera half of
`IOSNativeViewFactory.swift` (544 lines). C13a removed the Kotlin side and the permission; this is
dead code in the iOS binary. The Apple Sign In button factory is dead the same way. Needs someone
who can build the Xcode target.

### `FuseReach` could come up ~10%

The bomb fuse is legible at real cell size and is the smallest of the three special marks. One
constant: `FuseReach = 0.34f` at the bottom of `:libraries:ui`'s `Tile.kt`. This entry said
`BlockFace.kt` until the audit of 2026-09-20; that file was renamed during the game screen rebuild.

### Decide whether a step-1 merge should pop the score louder

With C3a's per-step pacing (L58) the chain steps read well, and the single merge is now the one beat
that looks plain by comparison.

### The level-up callout overwrites the chain callout it lands on

`ResolutionStep.LevelUp` is appended last in `Cascade.kt` and gets `ScoreOnlyMillis` (60) in
`Playback.kt`. A later callout replaces the one before it by design (`Playback.callout()`'s KDoc
makes that the precedence rule). So on any drop that both chains and levels up, `CHAIN xN` is on
screen for 60ms and is then replaced by a routine `LEVEL n`.

That is the exact failure C3a raised `CascadeStepMillis` from 195 to 300 to fix, undone from the
other end. `Motion.CascadeStepMillis`'s own KDoc argues at length that the number the player is
being congratulated on has to stay readable.

Two shapes, and they are not equivalent: give `LevelUp` a real hold so both are read in order, or
take the level announcement out of the toast channel entirely and put it on the level meter, which
is the surface that already means "level". The second is better and is the same work as the entry
below it.

### Level up is not distinguishable from anything else on screen

`LEVEL n` is the same font, size, position and animation as `CHAIN x3` and `WILD!`. It is a state
change wearing a reward's clothes, and it is the only signal the player gets that the game just got
harder.

It is also the *wrong* signal for most players. C1e pinned
`theDropControlChangesTheWallClockAndNothingElse` in `:tools:balance`'s `HarnessTest`: the drop
control moves the wall clock by a factor of four and moves no outcome column at all, and C1c measured
three opening curves with every outcome column identical to the digit. D26 measured the curve a third
time and changed nothing. **The difficulty is the rising spawn floor, not the speed**, so a player who
ends the drop early never experiences the speed change at all. Under `ControlScheme.Drag`, now the
default, that control is the downward flick, and the rewritten tutorial teaches it on drop one. What
the player experiences is a 32 landing on a board of 4s, with nothing anywhere saying that was the
game and not bad luck.

The level meter is the surface with room to say it, and today it is a bare `fraction`.
`game-danger.png` is the model for what a legible state change looks like in this app.

Note `game_start_body`, "Blocks fall on their own…", has the same problem from the other side: it is
true of a player who never ends a drop early and of nobody else.

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

### `dragAcrossCells` has no test *and* no call site

The steer half is closed. `BoardFlickTest` in `:features:game:impl` drives the real board through
`performTouchInput` and covers a straight flick, a slide-then-flick with no lift, and a slow drag
that must not drop, the three cases the flick-origin fix turned on.

What is left is stranger. `Modifier.dragAcrossCells` and `BoardGeometry`, in `:libraries:ui`'s
`components/board/BoardDrag.kt`, have **no production call site at all**: `GameBoard` rolls its own
`pointerInput` for `onSteerTo` / `onFlickDown` and never touches either. `BoardGeometryTest` covers
`BoardGeometry` thoroughly, which is the Watch list's "an API with no production call site looks like
coverage" sitting in the repo rather than in a learning. Decide whether `dragAcrossCells` is the
gesture the board should have been built on or dead weight, and delete one of the two.

### Measure the two things no policy has ever done

**Hold** does not exist any more (D11), so that one is closed. Still open: how often SPEC 5.3's
board-aware cap actually clamps a draw. It is almost certainly inert once a 1024 is on the board,
making it an early-game valve only — worth knowing before anyone reasons about `spawn.cap.divisor`
as a live knob.

### Re-run `tools/balance` with measured constants

Once a week of telemetry exists, replace the modelled 250ms `decisionMillis` and 120ms `tapMillis`
with real ones and update SPEC 4.4's numbers with error bars. This is the whole point of C8's
instrument (L41).

### `:tools:balance:test` never runs in CI

It is a plain JVM module, so its guard tests hang off `test`, and CI runs `testDebugUnitTest`,
`:apps:server:test` and `:apps:integration:testDebugUnitTest` and never `check`. `HarnessTest` and
`ClutterParityTest` have therefore only ever been enforced by somebody running them by hand, which is
precisely the failure they were written to catch: a harness that quietly stopped measuring anything
looks exactly like one that found nothing. D26 rested three difficulty rulings on this module.

### Add `--csv` to the balance harness

The text report is right for a one-off read and wrong for trends. This entry used to say it was worth
doing at the second measurement rather than the first. D26 was that measurement: three sweeps
(`blocksPerLevel`, `--curve`, `--specials-from`) were hand-transcribed into `decisions.md` tables
because there was no other way to carry them out of the harness. The next sweep should not be typed
out again.

### Sweep the last 8 `VerifyStrings` baseline entries

Down from 53 → 28 → 9 → 8. What remains: `AccessDeniedScreen`, `BlockingErrorScreen`,
`ErrorDialogContent`, `ShakeDialog`, `SplashScreen`, `NativeButton.jvm`.

### Decide whether `SessionRejectionBus` and `AccessDenied` earn their keep

Both survived C0 because they are welded to `NetworkClientImpl`'s 401 path and `App.kt`'s routing.
**Nothing can trigger either one** — the server's ban gate went with the auth stack. The server
surface is final now, so this is answerable.

### `:libraries:review` has no consumer at all

Answered rather than assumed: nothing outside `:libraries:review` and `:libraries:review:impl`
references `ReviewPromptCoordinator`, `ReviewTrigger` or `ReviewLauncher`. Two modules are built,
wired into DI, and asked for nothing.

SPEC 15 wants the rate prompt only after a run that set a personal best, which is a real trigger, and
`GameUiState.newBest` already computes exactly that. So the choice is one call site in
`GameViewModel` or two modules deleted.

### `docs/PORT-CANDIDATES.md` writeback

`AGENTS.md` says anything a brand-new app would want goes back to `../KMPTemplate`'s
`docs/PORT-CANDIDATES.md`. This project has generated a lot of it: the screenshot-harness wiring
with its verification trap, the palette property test, the transcript playback architecture, the
Room migration test, the debug session latch. Collect and write back in one pass.

### Whether the board should have a ninth row

Options 1 and 2 of this entry landed in D28. `BoardMaxWidth` went from the handoff's 370dp to 480dp,
wider than the usable width of any phone, so a phone board is bounded by the screen and by its own
height and never by a number from a stylesheet; and the board is centred in whatever height is left
rather than top-aligned over a gutter. Measured on 412×915 under `ControlScheme.Drag`: 370dp wide to
380dp, cell pitch 70.8dp to 72.8dp, bottom edge 675.5dp to 770dp.

What is left is option 3, **more rows**, and it is a balance change rather than a layout one.
`board.rows` is a remote key (SPEC 10) precisely so the 7-versus-8 question can be reopened, but SPEC
3 says the high score table is not comparable across dimensions and so rows do not move mid-version.
C1a's finding is the other half: the spawn table sets the tier ceiling and **the board geometry sets
the level**, so a ninth row wants a `tools/balance` pass before it wants a UI pass, and it lands at a
version boundary with everything else that moves `PINNED_DIGEST`.

### The stacked-out sheet under-reports the run it is summarising

It shows SCORE and BIGGEST. `run_record` already holds level reached, longest cascade, bursts,
blocks placed and duration, and the ViewModel already knows the best score it is comparing against
(it draws "new best!" from it).

The two worth adding are **level reached**, because it is the run's difficulty and it is the number
the player was watching all game, and a **distance-to-best** read for a run that did not set one.
"4,896" against a best of 130,450 is the standard near-miss beat and the data is sitting there
unused.

D28 did not do either, and it tightened the budget: the sheet now carries a continue option and a Pro
upsell card, and `StackedOutOverlay`'s own comment says it already overflows a short phone when the
card is up. Two more figures go into the existing SCORE / BIGGEST row or they do not go in at all.

### `GameControls` argues for the quiet ▼ with the number that argues against it

`GameControlRow`'s KDoc calls the drop control's recessiveness "intentional and load-bearing" and
then cites C1c: a player who reaches for it hits level 4 in 33 seconds against 289 for one who does
not. That measurement is the case for making ▼ *prominent*. "Steer first" is a defensible design
position and the muted treatment may well be right, but it is currently justified by evidence
pointing the other way, and the comment will mislead whoever reads it next.

Partly overtaken by the drag-default ruling above, which removes the button row from the default
experience entirely. It still governs `Both` and `Buttons`, and it is one colour constant either
way.

### There is no rules reference anywhere after the tutorial

SPEC 8.4 lists How to Play on the pause overlay. The overlay ships Resume, Restart, Quit, Stats and
Settings. A player who skips the tutorial from drop 3 (which SPEC 13 explicitly allows) has no way
to learn the priority order, that a Stone is permanent, or that 2048 takes the row with it.

Settings' Replay tutorial is the closest thing and it is a six-drop scripted run, not a reference.
Small: one screen, and the copy is mostly already written in SPEC 4.3 and 5.2.

### No policy in `tools/balance` models a player who plans

Since D11 removed the preview, `Lookahead-1` sees a block the player cannot and never will. SPEC 4.4
already says it must not be quoted as a prediction of play, which is the honest framing, but the
consequence is that **the declared ceiling is a policy playing a different game** and nothing measures
the ceiling of the game that shipped.

This matters more now than it did. The owner has ruled the next-block preview permanently out
(2026-09-16, "it's too easy with that"), so the no-preview game is the only game there will be. And
D26 ran the harness harder than anything before it, three sweeps, with every difficulty ruling in the
spec now resting on `Policy.Greedy` clocked, and this gap did not close. So "wait for the next
harness pass" has stopped being an answer. A policy that plans against board shape rather than
against a known next block would give the tail past 1024 an honest ceiling.

## Later

### Zen mode is nearly free

SPEC 2 cut it. It is a one-line engine change (never tick) plus a Pro gate, and Pro exists now —
which makes its value proposition much less thin.

### The powerup economy re-entry

All five powerups are designed against board state the engine already exposes, and Undo ships
internally (SPEC 5.6). Keep it additive: no engine change should make a powerup harder to add later.

### Nothing rewards two good drops in a row

The cascade multiplier resets at every lock (SPEC 7). Within a drop, chains pay enormously; across
drops, a player who sets up three merges in a row is paid exactly as if they had got lucky three
times. It is the cheapest lever left for making skill *feel* like skill, and it needs no change to
the merge rules.

Filed under Later because it is not free. Scoring formulas are on SPEC 10's **never remote** list and
a new payout invalidates every banked score, so it lands at a version boundary alongside anything
else that moves `PINNED_DIGEST`. **D26 was that boundary and this did not go with it**: the digest
moved a fourth time on 2026-09-20, and `decisions.md` calls it the last time the "no scores recorded
yet" argument gets written. So this is either taken before the first build ships or it costs a real
score reset. The five derived achievement score targets
(SPEC 15) move with it by construction, which is the reason they were derived.

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
  Proving the verifier works needs `--rerun` (L60). The L39 half is now handled by configuration
  rather than by luck: every Roborazzi module sets `roborazzi.test.verify = true` unless the task
  name contains `recordRoborazzi`, so do not go looking for an unset flag.
- **A golden can be green and still be the wrong scenario.** `game-stacked-out` never set
  `inDanger`, which made it the only stacked-out screen in existence that was not in danger, and it
  hid a visible defect for as long as it existed (D28). A golden asserts that a composable draws what
  it drew last time, never that the state it was handed is a state the app can reach.
- **An API with no production call site looks like coverage.** Prove the call site by deleting it
  and checking the *caller's* test goes red while the API's own test stays green (L55).

**Things that fail silently at runtime:**

- **A `published()` helper that copies everything except one field** started a real game underneath
  the game-over scrim: invisible, untouchable, live for two chunks (L32).
- **A `pointerInput` keyed on a value that changes mid-gesture** drops its release callback, so any
  hold-to-do-X leaks its "on" state (D21).
- **`Modifier.blur` clips to bounds at any radius, including zero** (L43), and it clips to the
  **rectangle** even when what it covers follows a rounded one. D28 found the second face: the
  stacked-out scrim left four red corner stubs where the board's corner arc bulges inside its own
  rectangle, because `BoardWell` draws the danger ring outside its bounds on purpose.
- **Top-level `val`s initialise in declaration order**, so a colour derived from one declared below
  it comes out transparent with no warning (L22). Same family: a `get() = false` on a sealed
  interface is a JVM default method and can half-build a companion (L42).
- **A migration fallback that destroys data** is reasonable when a server holds a copy and
  unrecoverable here (L33). **And Room prefers a migration to a drop**, so "the row survived" proves
  nothing about the narrowing (L61).
- **`UIApplication.canOpenURL` needs its scheme in `LSApplicationQueriesSchemes`**, or every outbound
  link silently opens nothing.
- **An `apply` on a call that returns `Unit` binds to the enclosing receiver.**
  `destination(Builder(...)).apply { deepLinks.forEach { deepLink(it) } }` compiles, the graph builds,
  the destination resolves, and every link lands on the *graph* instead. `bottomSheet<>` was written
  that way and silently dropped every deep link into a sheet. `NavGraph.matchDeepLink` answers `true`
  under the bug because it searches children too, so only `NavDestination.hasDeepLink` can tell.
- **A gesture threshold can be individually correct and measured from the wrong origin.** The flick's
  30dp of travel and 450ms release window were timed from the moment the finger landed rather than
  from the start of the downward stroke, so "slide it over, then send it down, without lifting", the
  gesture the tutorial asks for by name, could not be performed at all. Neither threshold changed in
  the fix.

**Things tests structurally cannot see:**

- **Every test double answers instantly**, so a gate on a slow dependency is invisible. A paywall sat
  blank for 35 seconds behind 94 passing tests (L68).
- **Infinite animations hang preview and screenshot capture** unless they return a fixed value under
  `LocalInspectionMode`.
- **A test that advances a full drop tick and then asserts on the falling block** is asserting about
  the next one (L31).
- **A `ModalBottomSheet` draws into a window of its own**, so a module's goldens capture the content
  composable and pass identically whether the route is registered with `bottomSheet<>` or `screen<>`.
  `PaywallGraphTest` asserts the destination is a `FloatingWindow` one because nothing else in the
  repo can say it.
- **A nav typeMap hole is invisible to every JVM harness.** `enter` / `exit` / `popExit` resolve
  through a reflective enum fallback on the JVM and return `UNKNOWN` on Native, so graph-build throws
  only under `iosSimulatorArm64Test`, often naming a different argument than the missing one. Proved
  by deleting `+ baseRouteTypeMap` and watching `testDebugUnitTest` stay green. AGENTS.md lists the
  crash; this is the reason no JVM test will ever find it.
