# Orchestration log

The running record of what has been decided, what has been learned, and where each chunk stands.
Written by the orchestrating session, read by every subagent before it starts.

`SPEC.md` is what the game is. `BUILD-PLAN.md` is the order it gets built in. **This file is why
things are the way they are, and what bit us.** If you are a subagent, read all three.

---

## Chunk status

| Chunk | State | Notes |
|---|---|---|
| C0 · Template trim | **DONE** | Identity + auth + server trim. 320 tests, 5 skipped (Docker down) |
| C1 · `:libraries:cascade` | not started | Blocked on C0 |
| C1a · `tools/balance` | not started | Blocked on C1 |
| C2 · Theme + design system | not started | Can run parallel to C1 |
| C3 · `:features:game` | not started | Blocked on C1, C2 |
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

---

## Learnings

Things discovered while building. Each one should save the next session time.

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
