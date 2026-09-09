# Orchestration log

The running record of what has been decided, what has been learned, and where each chunk stands.
Written by the orchestrating session, read by every subagent before it starts.

`SPEC.md` is what the game is. `BUILD-PLAN.md` is the order it gets built in. **This file is why
things are the way they are, and what bit us.** If you are a subagent, read all three.

---

## Chunk status

| Chunk | State | Notes |
|---|---|---|
| C0 · Template trim | **DONE** | `04803ff`. Identity + auth + server trim |
| C1 · `:libraries:cascade` | **DONE** | `96e6b92`. 89 engine tests, green on JVM + Native |
| C1b · Merge-position ruling | **DONE** | `3b14046`. Partner's cell (D6), one code path, digest re-pinned |
| C1a · `tools/balance` | **DONE** | `bc0a4fe`. Table measured and kept. See L18-L21 |
| C2 · Theme + design system | **DONE** | `e6e95b3` + `546eb04`. All 9 steps |
| C3 · `:features:game` | **DONE** | `f34279c`. Playable on device. 5x8 and the HUD settled (L25) |
| C1c · Balance with a clock | **DONE** | `321070a`. 500ms opening (D9). See L27-L31 |
| C4 · Persistence + stats | **IN PROGRESS** | Also deletes `AppData.bestScore` |
| C2c · Design language + screenshot harness | **IN PROGRESS** | The handoff (D10) |
| C1d · Cut hard drop and hold, add nudge | queued | Engine change, digest will move (D11) |
| C1e · Re-measure pacing without hard drop | queued | Blocked on C1d. Every number assumed it existed |
| C3b · Game screen to handoff fidelity | queued | Blocked on C4 + C2c + C1d |
| C3a · Feel | not started | |
| C4 · Persistence + stats | not started | |
| C5 · Tutorial | not started | |
| C6 · Daily Challenge | not started | |
| C7 · Remote config | not started | |
| C8 · Telemetry | not started | |
| C9 · Achievements, leaderboards, sharing | not started | |
| C10 · Ads + billing | not started | |
| C11 · Settings, legal, gates, a11y | not started | |
| C12 · Debug menu | not started | |
| C13 · Store prep | not started | |

---

## Standing rules for every subagent

These are not suggestions. A change that violates one gets reverted, not debated.

1. **Do not hand-roll what the design system already has.** `:libraries:ui` is the home for every
   visual primitive. Before writing a composable, search `libraries/ui` for it, then search
   `../Sodogku/libraries/ui` and `../Cards/libraries/ui` for it. Building a second Button is a
   revert.
2. **New shared visual things go in `:libraries:ui`**, not in the feature that first needed them.
   A component used once still belongs there if it is a primitive.
3. **No Material directly.** The template wraps it. Use the wrappers.
4. **No comments in code.** Repo rule from `AGENTS.md`. Put the reasoning in KDoc on the thing
   itself, or in `docs/decisions.md`, not in inline comments.
5. **`Catching { }` from `:libraries:core`, never `runCatching`.**
6. **Previews use `org.jetbrains.compose.ui.tooling.preview.Preview`.** The androidx one fails the
   iOS link. This has already cost a downstream app 171 files of churn.
7. **Routes are `class`, never `data object`.** A `data object` route SIGSEGVs iOS at navigate
   time.
8. **Never read an animated value during composition.** `val x by animateFloatAsState(...)` read
   in a composable body is enforced against by a detekt rule that fails the build. Read it inside
   `graphicsLayer` / `drawBehind`.
9. **Verify before claiming done:** `./gradlew testDebugUnitTest :apps:compose:assembleDebug
   :apps:compose:compileKotlinIosSimulatorArm64 detekt`. Report what actually ran and what
   failed. Do not report green without running it.
10. **Say what you did not verify.** Skipped tests, untested platforms, environment gaps get
    written down. A subagent that glosses a gap costs more than one that fails loudly.
11. **Prefer an automated test to driving a simulator.** Owner instruction, 2026-09-09. A
    screenshot test or a ViewModel test runs in seconds, in CI, on every change, and by everyone
    after you. A simulator run is a 90-second cycle that only you ever benefit from. Use the
    device for what only a device can answer — feel, haptics, real gesture timing — and write a
    test for everything else. C3 measured this: a screenshot harness would have caught two of its
    three device bugs.
12. **Do not edit `ORCHESTRATION.md`, `OWNER-TODO.md` or `todos.md`.** The orchestrator owns all
    three and edits them concurrently with your run; your write will be silently lost (this has
    already happened once, L10). Put learnings, owner items and deferred work **in your report**
    instead. You *may* edit `SPEC.md`, `BUILD-PLAN.md` and `decisions.md` for your own chunk.

## Reference repos

Both are the same template, further along. Read them when unsure how something is done here.

- **`../Sodogku`** — the closest sibling. Same template, shipped-shape puzzle game. Its
  `libraries/ui` is the design system this one should grow toward. Its `docs/BUILD-PLAN.md`
  post-mortems are worth reading before repeating a mistake.
- **`../Cards`** — the other build from the same template.
- **`../KMPTemplate`** — presumably the template source itself. Check before assuming Drop2048's
  copy is canonical.

**Port, do not copy blind.** A port arrives shaped like the app it came from. Strip its domain,
rename it for what it does rather than what it did, and keep the *reason* comments.

---

## Design system state

Audited in full against both siblings. The headline: **less is portable than assumed, and the
foundation layer is already identical.**

`Colors`, `ColorResource`, `AppTheme`, the typography scale and the catalog scaffolding are
byte-identical across Drop 2048, Sodogku and Cards. There is nothing to port at that layer. What
Sodogku actually added is game-specific, and about half of it is welded to Sudoku.

### Port as-is

| Thing | From |
|---|---|
| Contrast / ΔE / composite colour maths | `Sodogku/libraries/ui/.../system/color/RegionPalette.kt:145-230` (free functions only) |
| `ReduceMotion` expect/actual reading the **OS** setting | `Cards/libraries/ui/.../ReduceMotion.kt` + 3 platform impls |
| `DeepSurface` (press-into-lip) | `Sodogku/.../system/DeepSurface.kt` |
| `Gloss` (`Modifier.glossy`) | `Sodogku/.../system/Gloss.kt` |
| `Focus` / spotlight / scrim | `Sodogku/.../system/Focus.kt` (C5, not C2) |
| `CoachMark` | `Sodogku/.../components/game/CoachMark.kt` (C5) |
| `drawStarburst` | `Sodogku/.../components/game/GameShapes.kt:73` (leave the paws) |
| `LevelProgressBar` | `Cards/.../components/LevelProgressBar.kt` |
| `pulsingBorder` | `Cards/.../components/PulsingBorder.kt` — resolves colour in draw, not composition |

### Port and strip domain

| Thing | From | Strip |
|---|---|---|
| `Motion` tokens | `Sodogku/.../system/Motion.kt` | Sudoku durations; add drop/lock/merge/burst |
| `Haptics` + `Feel` + `LocalHaptics` | `Sodogku/.../system/Haptics.kt` | Rename cases, add the audio pairing (see D3) |
| `BoardGeometry` + `dragAcrossCells` | `Sodogku/.../components/board/BoardDrag.kt` | Square-only `size` becomes `cols`/`rows` |
| `BoardSurface` | `Sodogku/.../components/board/BoardSurface.kt` | Square-only, Sudoku a11y string |
| `BoardCellLabels` hoisting mechanism | `Sodogku/.../components/board/BoardCellLabels.kt` | Every string |
| `ScoreCounter` + `FloatingPoints` | `Sodogku/.../components/game/ScoreCounter.kt:48-136, 206-243` | `FloatingPoints` becomes `CHAIN xN` |

### Write fresh, nothing to port

The 11-tier block face; the falling block and its ghost (**no sibling has a piece that moves
between cells** — every Sodogku animation is a per-cell `Animatable`, which cannot express
travel); the cascade transcript renderer; the row burst; the next/hold preview chips; the pause
blur; the three bottom buttons; the stacked-out sheet; the indexed block palette; **all five
colour palettes**.

### Already have it, do not touch

`Colors`, `ColorResource`, `AppTheme`, typography, catalog scaffolding, `BounceClick`, `Grid`,
`Screen`, `Surface`, buttons, dialogs, bottom sheets. And **`Pulsate`, where Drop 2048's version
is the better one** — Sodogku's still uses the deprecated `Modifier.composed {}`; ours is a
`@Composable Modifier` over `rememberLoopingFloat(previewValue = 1f)` that holds still under
preview. Do not port Sodogku's over it.

### C2 build order

1. **Widen `AppThemeProvider` first.** It takes no parameters in any of the three repos, and that
   is the structural reason Sodogku ended up providing `LocalReduceAnimations` from `App.kt` and
   `LocalHaptics` from two separate feature entry points. One signature: palette, reduce-motion,
   haptics, large-numbers. Everything below plugs into it; doing it last means retrofitting every
   component.
2. Colour maths, ported. Just the ruler, no design decisions yet.
3. `BlockPalette` interface + `BlockStyle(face, ink, edge)`, the default 11-tier ramp, ink
   **derived** via `inkFor` rather than hand-picked. Write the test before authoring more
   palettes.
4. The other four palettes, each landing green against the same test.
5. `Motion` + `Haptics` as one pairing (see D3).
6. `DeepSurface` + `Gloss`.
7. `BoardGeometry` generalised to cols/rows, with its test ported edge for edge.
8. `BoardSurface` de-squared, the block face, and a `BlockCatalog` matrix page.
9. `ScoreCounter`, `CHAIN xN`, `LevelProgressBar`, `pulsingBorder`.

---

## Decisions made

Design decisions live in `SPEC.md`. This section is for decisions made *during implementation*
that the spec does not cover.

### D1 · Engine scoring is not its own module

Sodogku split `:libraries:scoring` from `:libraries:puzzle`. Drop 2048 folds scoring into
`:libraries:cascade`. Score here is emitted per transcript step, so splitting it means two modules
that both have to know what a cascade step is, and a change to the cascade loop becomes a
two-module change. Revisit only if a second consumer of scoring appears.

### D2 · The engine module is `:libraries:cascade`, not `:libraries:drop2048`

`:libraries:drop2048` already exists as the template's app-wide library (`AppCache`, `AppEvent`,
`Telemetry`, `Session`). The game engine is a separate, dependency-free thing and gets its own
name. "Cascade" was the design doc's working title, so it stays as the internal name.

---

### D3 · Motion and haptics ship as one `Cue`, not two systems

SPEC 9 requires that a merge is one call, not two that drift apart. **Sodogku does not solve
this**: its `Feel` enum has no audio side, its `Motion` object has no haptic side, and the two
live in different packages (`com.sodogku.system` vs `com.sodogku.libraries.ui.system`) for no
reason, which is part of why they never got paired.

Drop 2048 builds a single `Cue` that owns the haptic intensity, the sound key and the pitch
offset, so `Cue.Merge` at cascade step 4 is one call that plays the right sound at the right
pitch with the right buzz. Sodogku's `Haptics` class *shape* is the right skeleton (immutable,
`Silent` companion, silent-by-default CompositionLocal so previews need nothing,
`rememberHaptics(enabled)`); its content is not.

### D4 · Reduce motion is read from a CompositionLocal inside the component, and only there

Sodogku has three incompatible idioms for this in one repo: a CompositionLocal read inside the
component, a pure function taking a `Boolean`, and an `animated: Boolean` parameter on `BoardCell`
that is actually a *battery* setting rather than the accessibility one. Drop 2048 picks the first
and enforces it.

It also ORs the in-app toggle with the **OS** setting, which Sodogku never reads. Cards has the
`expect fun isReduceMotionEnabled()` with real platform implementations; that gets ported.

### D5 · `EngineConfig` travels inside `GameState`

SPEC 4.1's field list omits it, and C1 added it deliberately. A seed only reproduces a run if the
numbers it was played under travel with it. Otherwise the day a remote spawn-table value moves,
every Daily Challenge replay and every seed-attached bug report silently replays *differently*
rather than failing loudly. Wrong answers that look right are the expensive kind.

The undo ring went the other way and is **not** a field on `GameState`: it holds whole
`GameState`s, so nesting it would make one serialized state carry eight boards, and SPEC 11
rewrites that blob on every drop. It is a sibling `UndoRing` value.

`level` is also stored rather than derived from `blocksDropped`, forced by SPEC 18.10: a derived
level snaps straight back on the next drop, so the continue the player watched an ad for would
last under a second.

### D6 · The merged block appears in the partner's cell, in both orientations

Owner ruling, 2026-09-09. Replaces two rules with one. See `SPEC.md` 4.3 for the full reasoning.

The contradiction was inherited from the original design document, which stated "the INITIATING
block's cell" as its rule and "one 8 in the left cell" (the partner's) in its own worked example.
C1 implemented the rule as literally written and pinned the losing branch as a test so the
decision would announce itself, which is exactly the right way to handle a spec contradiction you
cannot resolve yourself.

**This is a merge rule, so it is now fixed.** SPEC 4.3 says merge rules are not negotiable once
shipped.

Wildcard symmetry was confirmed in the same ruling: a value block landing beside a resting
Wildcard merges with it, not only the reverse. SPEC 5.2 permits an inert Wildcard, and without
symmetry that Wildcard is a permanent obstacle players read as a bug, because the obvious move
does nothing.

### D10 · The design handoff is canonical for visuals and interaction, and stale for gameplay

`/Users/elijahdangerfield/Documents/design_handoff_drop2048/` is a high-fidelity handoff. Owner
ruling, 2026-09-09:

**It wins on:** colour, type (Fredoka + Nunito), the hard-offset shadow system, radii, spacing,
motion timings, layout, overlays, and the control scheme.

**`SPEC.md` wins on:** merge rules, specials, the level clock, scoring, board dimensions, and v1
scope. The handoff's README claims it "reflects all the latest gameplay decisions"; it does not,
and its gameplay sections are to be read as prototype notes rather than requirements.

Specifically **rejected** from the handoff: N-way simultaneous merges (`value × 2^N`), the absence
of Wildcard / Bomb / Stone, `level = merges / 10`, its `850 - (level-1) * 65` speed curve, its row
burst scoring, its 5x7 board, and "no backend, no accounts, no network".

**5x8 stands.** Board dimensions are gameplay, and C3 settled it on a device with a measurement
(L25) rather than by taste. `board.rows` remains a remote key.

### D11 · Next preview, hold slot and hard drop are cut. The ▼ nudge replaces hard drop.

Owner ruling. The handoff says all three "were tried and cut. Don't reintroduce them." This is an
*interaction* change, so the handoff governs.

- **SPEC 5.4 is struck**, including its "the preview is non-optional" language.
- **Hard drop is gone.** ▼ (and a downward flick) advances the fall by **two ticks**. It is an
  accelerator, not an instant drop.
- Steering becomes drag-anywhere-on-board as primary, absolute from the grab point, with the
  arrow buttons as the secondary path.

**Four consequences, none of them obvious:**

1. **The engine loses two `Input` cases and gains one.** `HardDrop` and `Hold` go, `Nudge`
   arrives. That is an engine change, so **the determinism digest will move** and must be
   re-derived by running the engine, not pasted from the failure (L17).
2. **SPEC 7's hard drop bonus (`2 x rowsSkipped`) has nothing left to fire on.** Scoring lives in
   the engine, so striking it moves the digest too. Decide whether the nudge pays anything; the
   original bonus existed to reward confident play, and a nudge is a weaker claim to that.
3. **SPEC 5.3's board-aware cap no longer has to be read at draw time.** That was forced entirely
   by the preview: "a preview that can still change is a lie". With no preview, the cap can be
   evaluated at landing time against the real board. This is a genuine simplification the ruling
   makes available, and it removes the stale-data caveat L20 measured.
4. **Every balance number in the project assumed hard drop existed.** C1c measured that using it
   is the single biggest lever on early pacing: level 4 in **34 seconds** hard-dropping versus
   **289 seconds** patient (L29). Removing it makes *everyone* the patient player, and the nudge
   is only a partial substitute. **The opening pacing must be re-measured**, and D9's 500ms curve
   was tuned in a world where hard drop existed.

### D9 · The opening drop speed is 500ms, and `blocksPerLevel` stays 20

Measured in C1c. The curve for levels 1-8 moves from `700, 620, 550, 490, 430, 380, 340, 300` to
`500, 470, 440, 410, 380, 350, 325, 300`. Floor, tail step and soft drop unchanged.

**This is a pacing change, not a difficulty change, and the measurement says its risk is zero.**
Three candidate curves produced outcome columns identical to the digit: same median level, same
drop count, same 1024 and 2048 rates, same clutter. What it buys is a 28% cut in measured dead
time at level 1.

`blocksPerLevel` was measured and deliberately **left at 20**. Shortening it to 15 or 12 shortens
runs and thins the tail past 1024, the rising median is the counter moving rather than the player
improving, and it **moves the determinism digest** where the curve does not. A first-band-only
version would need a schedule instead of an integer, which is an engine change to level
advancement.

Keep `blocksPerLevel` in reserve as the remote knob if live data says runs feel long, but it must
not move once Daily Challenge scores exist (see the digest policy item in `OWNER-TODO.md`).

### D7 · "Highest tier" means highest tier *reached during the run*, never on-board-at-end

C1a found SPEC 4.4's "distribution of highest tier at run end" is not answerable literally: **a
2048 bursts its row, so it is never on the board at the end.** Read literally, the metric reports
0% for the game's defining moment.

Ruled: highest tier means the maximum over merge results and board contents across the whole run.
This applies to all three places that ask the question, and they must agree:

- `run_record.highest_tier` (SPEC 11)
- "BIGGEST" on the stacked-out sheet (SPEC 8.4, and the mockups draw it)
- the weekly-watched highest-tier distribution (SPEC 17)

Folded into `SPEC.md` once C2b lands and the file is free.

### D8 · Clutter counts number blocks only; obstruction is a separate count

C1a found `clutter` ignores Stones and inert Wildcards, which understates board congestion exactly
after level 12 when Stones start arriving.

Ruled: **leave clutter as number-blocks-only.** SPEC 17 defines it as the early warning for a
mistuned *spawn floor*, and Stones arrive from the special rate, not the spawn table. Folding them
in would blur two causes into one number and make it useless for the job it exists to do.

If board congestion needs measuring, that is a **second** count of permanent obstructions, not a
change to this one. Two metrics measuring two things beats one measuring neither.

The binding constraint either way: the harness and SPEC 17's live telemetry must compute this from
**the same function in `:libraries:cascade`**. The entire value of the metric is that offline and
live numbers are directly comparable.

---

## Learnings

Things discovered while building. Each one should save the next session time.

### L27 · A player's report of what is wrong is evidence about the symptom, not the cause

C3 played the game and said the early levels were slack because "the timer, not the player, places
most blocks". C1c measured it: **not one block in 600,000 opening drops was placed against the
policy's choice.** 0.00% timer-placed at levels 1, 2 and 3.

It is structural. Five columns with a centre spawn puts every cell at most two columns away, which
a modelled player covers in 370ms against a budget that never drops below ~780ms even at the
level-35 speed floor. **Time is not one of SPEC 5.5's three pressures at any level.**

And C3 was still right that something was wrong. After deciding, the player waits 4.8 seconds at
level 1 for the block to arrive. That is dead time, not difficulty — the cutscene is real, the
mechanism was not what it felt like.

**The general lesson, and it will recur:** take a play report as a true statement about the
experience and an untrusted statement about the cause. C3 was the only source that could have
found this, and measurement was the only thing that could have explained it. Neither alone was
enough.

### L28 · The early speed curve is a pacing dial, not a difficulty dial

Three curves for levels 1-8 (700-start, 600-start, 500-start), 10,000 runs each: median level,
drop count, 1024 rate, 2048 rate and clutter came out **identical to the digit** across all three.

Only the wall clock moved: level-1 slack 4,808ms → 4,142ms → 3,477ms. Even at 500ms the wait is
3.5 seconds, because seven rows of fall is seven rows of fall.

Do not reach for the opening curve to change difficulty. It cannot.

### L29 · Whether the player uses hard drop is the biggest lever on the opening, by an order of magnitude

Measured time to reach level 4: **34.4 seconds** for a hard-dropping player, **289 seconds** for a
patient one, on the same curve. The drop control is worth more than every speed-curve change
combined.

That makes it a **tutorial** problem, not a tuning problem. SPEC 13's drop 2-4 already introduces
hard drop; C5 should treat teaching it as load-bearing rather than incidental, because it is the
difference between a 30-second and a 5-minute opening.

### L30 · The clocked ceiling was real but modest, and Random got *better* with a clock

C1a's unclocked numbers were ceilings (L21). Clocked: Greedy's median level 22 → 19, Lookahead-1's
25 → 22, 2048 rate down about a third. So L21's direction was right and its size was ~12%.

The surprise: **Random's median went up, 4 → 5.** Not a bug. The columns a block can still be
steered into are partly the columns with room, because a tall column on the path blocks passage.
A random player forced to re-pick among reachable columns is a less random player. Do not quote
clocked Random as SPEC 4.4's floor.

Sensitivity to the player model is comparable to the clock itself (Greedy 21/19/17 across quick,
average, deliberate), which is why the model's two constants are now a telemetry item.

### L31 · A test can pass by luck at one constant and NPE at another

`GameViewModelTest.softDrop_shortensTheInterval` advanced a whole drop tick while soft-dropping. At
40ms per row that is twelve rows: the block landed, locked and resolved, so the assertion was
really about the *next* block. It passed at 700ms by coincidence and crashed at 500ms.

`GameScenario.tick()` advances one drop interval, which is **not a safe unit while soft-dropping**.
Worth scanning for other tests that advance a full tick and then assert on the falling block.

### L24 · A failed iOS link reports `** BUILD SUCCEEDED **` and silently runs the previous framework

`:apps:compose:linkDebugFrameworkIosSimulatorArm64` failed twice with a SKIE macro error in its own
generated Swift (`SkieSwiftCoroutineDispatcher`, "expressions are not allowed at the top level").
It looks like an Xcode 26 incompatibility and is not. `./gradlew --stop` plus
`rm -rf apps/compose/build/skie` fixed it immediately.

**The dangerous half:** the run-script phase failure does not fail the xcodebuild, so the app
installs and runs against the *previously* linked framework. C3 nearly drew the wrong conclusion
from a 7-row build that was still rendering 8 rows.

**If an iOS behaviour change does not appear on device, check the link task by name in the build
log before touching the code.** A green xcodebuild is not evidence your Kotlin made it into the
app.

### L25 · At five columns the board is width-bound, so more rows are nearly free

The mockups drew 7 rows and the case for it was "bigger blocks on a small phone". Measured on a
simulator: 8 rows drew a ~64pt cell, 7 rows drew ~66pt. The cell size is set by the phone's
**width** at five columns; the extra row of height just became gutter above and below the stack.

Seven rows costs a full row of danger-state reaction time and buys 3%. **Ruled 5x8**, and the
argument gets stronger on a narrower phone, not weaker.

This is exactly the class of question that cannot be settled by reasoning, which is why it was
deferred to the chunk that could run it.

### L26 · "Input during resolution is ignored" is two different games on a device

SPEC 6 conflated *not reaching the engine* with *thrown away*. C3 played three move-move-drop
sequences and all three blocks went down the middle column: **every input in the beat after a hard
drop was eaten**, and the game read as dropping inputs rather than enforcing a rule.

The last *sideways* move during a resolution is now buffered and replayed on the next block. Hard
drop and hold are deliberately **not** buffered, because replaying either acts on a board the
player has not looked at.

The general lesson: a rule that is correct at the engine boundary can still feel broken at the
finger. Spec language about input needs to say which of the two it means.

### L22 · Top-level `val`s initialise in declaration order, and a colour derived from one below it comes out transparent

C2b declared `SPECIAL_STYLES` above `DARK_INK` / `LIGHT_INK` in the same file. Kotlin initialises
top-level properties in declaration order, so the derivation ran while both inks were still zeroed
and **every special block got a fully transparent ink**.

No warning. No crash. Correct-looking code. It would have shipped as invisible numerals on three
block types.

It was caught only because `BlockPaletteTest` measures the *derived* ink rather than trusting the
derivation — the same property that caught Sodogku's three hand-picked inks. **That is twice now
the palette test has earned its keep on a bug nothing else could see.** L2 said write the test
before authoring the palettes; this is why.

The fix is a declaration-order move with a KDoc line saying why it has to stay there.

### L23 · There is no per-module detekt task

Only a root `:detekt`. `:libraries:ui:detekt` does not exist. Worth knowing before someone
concludes a module is unchecked because its task is missing.

### L18 · The merge ruling made the game harder, and it is what makes the spawn table fit

C1a restored the pre-ruling merge position locally, measured, and reverted. Greedy over 10,000
seeds:

| Policy | median level pre → post | 2048 rate pre → post |
|---|---|---|
| Random | 5 → 4 | 0% → 0% |
| Greedy | 26 → 22 | 36.7% → 3.8% |
| Lookahead-1 | 32 → 25 | 65.1% → 15.7% |

**Not via chain frequency.** Cascade depth is essentially unchanged (mean 0.88 → 0.87, share of
drops chaining ≥2 steps 21.8% → 20.8%). The mechanism is positional: a horizontal merge migrates
the result into the *partner's* column, and the partner's column is by construction the one that
already held a match. The board gets less level with every horizontal merge instead of more, and
on five columns that is what ends runs.

L17's single counterintuitive data point pointed straight at this and was right.

**The consequence that matters: the ruling and the spawn table are now coupled.** Under the old
position rule, the SPEC 5.3 table put a 2048 burst in 65% of Lookahead-1 runs, which is SPEC 17's
own definition of too easy. Anyone who revisits the merge position must retune the spawn table in
the same change.

### L19 · The spawn table sets the tier ceiling; the board geometry sets the level reached

C1a measured three alternative ramps against the shipped one. Median level moves by **at most one**
across tables that move the 1024 rate by 5x. Softening the ramp buys no survival and costs nearly
the whole tail past 1024.

This inverts the intuition in SPEC 5.3 that the table is the difficulty dial. It is the *tier*
dial. If runs need to last longer or shorter, the lever is the board, the speed curve or the
specials, not the spawn weights.

The table was therefore **kept unchanged**, and that is a measured decision rather than an
untested default. Changing it would also move the pinned determinism digest, for nothing
observable.

### L20 · The board-aware cap's staleness is a non-issue, measured

SPEC 5.3 notes the cap is evaluated at draw time, two drops before landing, forced by the
non-optional preview. The worry was that faster chains would compound it.

Drops whose block exceeds the cap the board would impose at landing time: **0.02%** (Greedy),
**0.04%** (Lookahead-1). Pre-ruling, when chains were expected to be faster, 0.09% and 0.16%. No
compounding at either end. Stop worrying about it.

### L21 · Every balance number so far is a ceiling, because the harness has no clock

Every policy hard-drops into the column it wants at every level. SPEC 5.5's speed curve is
untested by anything in C1a, and hold and soft drop are never used. Real medians will be **lower**
than the table above.

Do not quote these numbers as predictions of player behaviour. They are an upper bound for an
unhurried player, which is exactly the right thing for tuning the spawn floor and the wrong thing
for tuning difficulty.

### L17 · A pinned digest gets re-derived, never copied from the failure message

The merge-position ruling changed `PINNED_DIGEST`, `PINNED_SCORE` (942 → 862) and
`PINNED_BLOCKS_DROPPED` (27 → 25). It was the **only** failing test in the module afterwards,
which is itself the signal that the change was narrow.

C1b re-derived the new pin by running the engine rather than pasting the "actual" value out of the
assertion failure. Those look identical in the diff and are not: pasting the actual makes the test
agree with whatever the code now does, which is the same as deleting it.

It also added a paragraph to `DeterminismTest`'s KDoc recording that the pin was changed once, on
what date, for what reason, and that no scores existed yet. The next person who wants to change it
now inherits a precedent that demands a written reason.

**The digest got shorter, not longer** (25 drops, not 27), which is the opposite of what "more
cascades" naively predicts. One seed with a scripted random player is not balance data, but it is
a warning against assuming the ruling made the game easier. C1a measures it.

### L14 · Two agents compiling the same shared target will report each other's half-written files as failures

C1 reported `:apps:compose:compileKotlinIosSimulatorArm64` as broken by a `CHHapticPattern`
overload ambiguity in C2's `HapticEngine.ios.kt`. It was genuinely broken at the moment C1
compiled, and C2 fixed it before finishing. The orchestrator re-ran the gate after both agents
completed and it was green.

C1's handling was the correct pattern and worth copying: it grepped every build file to prove no
module depends on `:libraries:cascade` yet, concluded the engine could not be causal, and proved
its own iOS coverage independently via `:libraries:cascade:iosSimulatorArm64Test` rather than
relying on the shared app target.

**Orchestrator rule:** a build failure reported by one agent while another is mid-flight is
provisional. Re-run the gate yourself before acting on it.

### L15 · `:apps:compose:compileKotlinIosSimulatorArm64` does not run a single test

It compiles. The engine's determinism guarantee lives or dies on
`:libraries:cascade:iosSimulatorArm64Test`, which actually executes the suite on Kotlin/Native and
is **not** in the standard verification command from the working agreement.

C1 ran it by hand and confirmed all 5 `DeterminismTest` cases execute with `[iosSimulatorArm64]`
suffixes, asserting the same FNV-1a digest the JVM run asserts. That is the strongest correctness
claim in the project so far, and the standard gate would not have caught its absence.

Any chunk touching the engine runs the Native test task explicitly.

### L16 · detekt cannot say anything about a Compose-free module

Default rule sets are disabled repo-wide, and both custom rules (`VerifyStrings`,
`AnimatedStateReadInComposition`) are Compose-specific. On `:libraries:cascade` a clean detekt run
is structurally indistinguishable from no run at all.

This is the `AGENTS.md` landmine in a new shape: there, the risk was a rule failing to dispatch;
here, the rule dispatches fine and has nothing it could possibly match. Do not read "detekt green"
as coverage on a pure-Kotlin module.

### L1 · Sodogku has no colourblind palettes, so there is nothing to port

`SPEC.md` 16 asks for designed deuteranopia / protanopia / tritanopia palettes. I had assumed
these would port from Sodogku. **They do not exist.** Sodogku ships one palette and a `colorblind:
Boolean` that draws a per-region *glyph* watermark over the same fills.

That is a different and weaker answer than the spec asks for, though the glyph idea is worth
keeping as a second layer. It matters doubly little here because Drop 2048's blocks already carry
numerals, which is the strongest possible non-colour signal.

All five palettes are original work in C2. Budget for it accordingly.

### L2 · The palette test is what makes authoring five palettes cheap instead of terrifying

Sodogku's `RegionPaletteTest` asserts properties, not hexes: every ink is the higher-contrast
candidate, one ink is checked against a *derived* value to prove the derivation is not a
hardcoded constant, every glyph clears a **composited** contrast floor, no two fills are closer
than ΔE 20, and the luminance span clears a minimum.

Sodogku's own KDoc records that its first pastel pass shipped two purples 14.2 apart, which was
"invisible in code review and obvious the moment the palette was rendered as a strip". Drop 2048
has 11 tiers across 5 palettes, which is 55 chances to make that mistake.

Write the test against the default ramp **before** authoring the other four.

### L3 · `BoardCell` is a honeypot

436 lines, the file you would reach for first, and welded to dogs, crosses, Sudoku notation states
and a deliberate no-double-tap gesture rule. Port nothing from it. Read it for exactly two
lessons, both of which are comments rather than code:

- Reading an `Animatable` during composition subscribes the whole subtree to 60fps. Gate on
  `derivedStateOf { pop.value > 0f }`.
- A shake `Animatable` must `snapTo(0f)` **unconditionally** before any early return, or an
  interrupted animation strands the cell off its grid line.

`PlacementPulse` is the same trap in miniature: it looks like generic "which cells react"
infrastructure and its `roleOf` is literally the Sudoku row-and-column constraint.

### L4 · Compose Multiplatform exposes only two haptic types, and SPEC 9 promises five

`Sodogku/.../system/Haptics.kt:47-50` collapses four game events onto Compose's two
`HapticFeedbackType` values. SPEC 9 wants light, medium, heavy, a sustained burst pattern, and a
sharp double.

Compose's shared API cannot deliver that. It needs a platform haptics abstraction: Android
`VibrationEffect` composition, iOS `UIImpactFeedbackGenerator` plus Core Haptics for the sustained
burst pattern. That is expect/actual work in C2, not a one-line token.

The row burst haptic is called out in SPEC 21 as half of what makes the game feel good, so this
is not a corner to cut.

### L5 · The catalog has already been forked once; do not do it again

Sodogku's `GameCatalog.kt` declares its own file-private `CatalogPage` with a signature
incompatible with the real `Catalog.kt` one, and `DesignSystemPreview` never aggregates it. The
game half of that design system is browsable only by someone who knows the file exists.

Drop 2048's `Catalog.kt` and `DesignSystemPreview.kt` are already identical to Sodogku's good
version. Use them.

For the block ramp specifically, lay it out as a **matrix**: one row per tier, one column per
palette, plus a column carrying the measured ΔE to the neighbouring tier as text. Two tiers
colliding in one palette then shows up as two adjacent cells in a column, rather than needing five
separate previews compared from memory.

### L6 · `VerifyStrings` was baselined here too, and a baseline that covers everything enforces nothing

Confirmed in C0, exactly as Sodogku found. The baseline had 53 entries covering every template
screen, so the rule was inert. C0 pruned it to 28 (15 stale for deleted files, 10 replaced with
real resources) and made `OnboardingScreen` the worked example.

**Any chunk that replaces a template screen deletes that screen's baseline entries rather than
inheriting them.** The remaining 28 are unverified copy on screens nobody has rewritten yet:
`HomeScreen`, `BugReportScreen`, `FeedbackScreen`, `ShakeDialog`, `SplashScreen`,
`AccessDeniedScreen`, `BlockingErrorScreen`, and the UI catalog.

### L7 · A fresh clone of this repo does not build

Two one-time traps, both of which cost C0 time:

- The build fails with an install-hooks message until `./scripts/install_hooks.sh` is run.
- `local.properties` is gitignored and absent, so every Android task dies with "SDK location not
  found". C0 created one with `sdk.dir`.

### L8 · The generated resources package drops the leading namespace segment

It is `drop2048.libraries.resources.generated.resources`, **not**
`com.dangerfield.drop2048.libraries.resources.generated.resources`. Compose's resource generator
does this. Expect to get the import wrong once.

### L9 · A directory that does not match its `package` declaration compiles silently

`libraries/resources/.../com/dangerfield/drop2048e/.../Resources.kt` sat under a path with a stray
`e` while declaring the correct package. Kotlin allows the mismatch, so it compiled and nothing
complained. C0 fixed it. Worth a glance if another rename artifact surfaces, because the symptom
is nothing at all.

### L11 · SPEC 9's "haptics respect the OS setting" is not implementable

C2 built the Off / Light / Strong setting. **The OS half cannot be honoured as written.**

- iOS has no public API for whether system haptics are enabled. `UIFeedbackGenerator` silently
  no-ops and tells you nothing.
- Android exposes `Settings.System.HAPTIC_FEEDBACK_ENABLED`, but it governs
  `View.performHapticFeedback`, not direct `Vibrator` calls, so it does not apply to a real haptic
  engine.

Reduce motion is different and *is* readable from the OS on both platforms, which is why that half
works. The spec line has been amended. Do not let a later chunk "fix" this by reading a setting
that does not mean what it looks like it means.

### L12 · Authoring a palette numerically beats authoring it by eye, and the CVD simulation is the part that matters

C2 did not pick five palettes and then check them. It fixed a hue band per tier as the design
decision, then hill-climbed saturation and lightness inside those bands against the constraint
set. For the three colour-vision ramps the objective included ΔE measured **after a Viénot 1999
dichromat simulation**, so they are separated for the player they are for rather than for a
designer's monitor.

The simulation lives in `commonTest` only. The app ships designed palettes; a runtime filter would
be the wrong answer and is what most apps do.

Measured on the shipped Kotlin, not the authoring harness:

| Palette | min adjacent ΔE | min any-pair ΔE | luminance span | min ink contrast |
|---|---|---|---|---|
| Default | 26.06 | 24.57 | 0.757 | 4.66:1 |
| Deuteranopia | 34.53 | 19.51 | 0.679 | 4.77:1 |
| Protanopia | 37.34 | 18.82 | 0.667 | 5.28:1 |
| Tritanopia | 43.29 | 17.79 | 0.711 | 4.68:1 |
| HighContrast | 29.05 | 20.72 | 0.508 | 6.31:1 |

The Default ramp takes the light ink on 4 of 11 tiers, which is what stops the "ink is the
higher-contrast candidate" assertion from being vacuously true of a constant.

### L13 · The `AnimatedStateReadInComposition` detekt rule was proven to dispatch, not assumed

The watch list says a clean detekt run does not prove a custom rule ran. C2 actually did the
check: it planted a `val x by animateFloatAsState(...)` read in composition, confirmed the build
failed on the rule by name, removed it, and re-ran green.

**That is the standard.** Any chunk that relies on a custom rule proves dispatch the same way
rather than trusting a clean run.

### L10 · Two agents editing this file concurrently silently lose work

C0's learnings were written into this file while the orchestrator was editing the same section,
and the orchestrator's write won. Four learnings were lost and had to be recovered from the
agent's report.

**Subagents no longer edit this file.** They report, the orchestrator writes. Standing rule 12.

---

## Open questions

Things nobody has answered yet that will need answering.

- **5x7 vs 5x8.** Mockups say 7, spec says 8. Settled in C3 on a real device. `board.rows` is a
  remote key either way.
- **HUD layout conflict.** The mockups have nowhere for the next-two preview or the hold slot,
  and SPEC 5.4 says the preview is non-optional. Recommendation is in SPEC 8.1; settle it in C3
  before building it twice.
- **Buttons vs Drag as the shipped default.** Buttons ship first because they are testable. Play
  both in C3a and pick.
