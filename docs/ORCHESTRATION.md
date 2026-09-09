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
| C1a · `tools/balance` | **IN PROGRESS** | Spawn table is now untuned against the new merge rule |
| C2 · Theme + design system | **MOSTLY DONE** | `e6e95b3`. Steps 1-8 done |
| C2b · HUD primitives | **IN PROGRESS** | C2's step 9 remainder |
| C3 · `:features:game` | not started | Blocked on C1a + C2b |
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
11. **Do not edit `ORCHESTRATION.md`, `OWNER-TODO.md` or `todos.md`.** The orchestrator owns all
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

**Subagents no longer edit this file.** They report, the orchestrator writes. Standing rule 11.

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
