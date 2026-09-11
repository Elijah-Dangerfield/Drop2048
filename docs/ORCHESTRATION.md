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
| C4 · Persistence + stats | **DONE** | `run_record`, resume incl. mid-cascade, stats screen |
| C2c · Design language + screenshot harness | **DONE** | `0f843f2`. 15 goldens. See D14, D15, L38-L39 |
| C1d · Cut hard drop and hold, add nudge | **DONE** | Digest re-pinned a 2nd time. See D13, L35-L37 |
| C1e · Re-measure pacing without hard drop | **DONE** | `6bfdb2c`. No change needed. See L40-L42 |
| C3b · Game screen to handoff fidelity | **DONE** | 11 goldens. See D16, D17, L43-L45 |
| D21 · ▼ becomes a hard drop | **DONE** | Digest re-pinned a 3rd time. Save format 5. See L64 |
| C3c · One product + player bugs | **DONE** | `e049f95`. Dark design system (D20). Found L56 |
| C3a · Feel + polish | **DONE** | `1e1959a`. Cascade re-paced (L58). Haptics fired (L59) |
| C12 · Debug menu | **DONE** | `d47bb2e`. First Room tests ever (L61). See L62-L63 |
| C5 · Tutorial | **DONE** | `ad992b6`. `:features:onboarding` deleted. See L49 |
| C6 · Daily Challenge | **DONE** | Config pinned (D18). See L51-L52 |
| C7 · Remote config | **DONE** | `96f7c40`. 26 keys. Integration harness ran at last (L46) |
| C8 · Telemetry | not started | |
| C9 · Achievements, leaderboards, sharing | **DONE** | `5fee174`+`af43fc6`. All 24 earnable. See L53-L55 |
| C10 · Ads + billing | not started | |
| C11 · Settings, legal, gates, a11y | **DONE** | `655167b`. Accessibility is live at last. 945 tests |
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
9. **Verify before claiming done:** `./gradlew testDebugUnitTest :apps:server:test
   :apps:compose:assembleDebug :apps:compose:compileKotlinIosSimulatorArm64 detekt`. Report what
   actually ran and what failed. Do not report green without running it.
   **The gate now expects 0 skips.** Docker was down for the project's first eight chunks and the
   5 skips that produced were quoted as a baseline; C7 started Docker and found one of those tests
   had been wrong since the day it landed (L46). If something skips, that is a finding. Start
   Docker Desktop rather than accepting it.
10. **Say what you did not verify.** Skipped tests, untested platforms, environment gaps get
    written down. A subagent that glosses a gap costs more than one that fails loudly.
11. **Prefer an automated test to driving a simulator.** Owner instruction, 2026-09-09. A
    screenshot test or a ViewModel test runs in seconds, in CI, on every change, and by everyone
    after you. A simulator run is a 90-second cycle that only you ever benefit from. Use the
    device for what only a device can answer — feel, haptics, real gesture timing — and write a
    test for everything else. C3 measured this: a screenshot harness would have caught two of its
    three device bugs.
12. **Every git operation is scoped to explicit files, always.** Two halves, both learned the
    hard way:
    - **No repo-wide command.** No bare `git stash`, `git checkout .`, `git clean`, `reset --hard`.
      One bare `git stash` swept up two agents' work and the orchestrator's docs (L38).
    - **`git add <directory>` is nearly as bad.** A wide add swept most of another agent's
      in-flight work into a commit that does not mention it (L57). **Stage named files.** Where two
      agents genuinely must touch one file, stage a reconstructed version with `git hash-object` +
      `git update-index` and leave the working tree alone (L52).

    To test whether a failure is pre-existing, build HEAD in a `git worktree` (C3c did exactly this
    to prove the tutorial deadlock predated it) or read `git log -p`. Never move the shared tree.
13. **Do not edit `ORCHESTRATION.md`, `OWNER-TODO.md` or `todos.md`.** The orchestrator owns all
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

## The design handoff

`/Users/elijahdangerfield/Documents/design_handoff_drop2048/` is canonical for visuals and
interaction and **stale for gameplay** (D10).

**Read `docs/reference/design-handoff-deltas.md` before you open it.** Its README opens by claiming
it reflects the latest gameplay decisions, and it does not — its merge rule, level formula, speed
curve, board size, scoring and scope have all been explicitly rejected. The deltas page lists every
one.

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

### D19 · Streak milestones stay cosmetic, and a Daily score cannot set the all-time best

Owner rulings, 2026-09-10.

**Streak milestones pay nothing.** Coins and cosmetics are both cut from v1, so SPEC 14's "rewards
at 3, 7, 14 and 30 days" had nothing behind it. A milestone is celebratory copy and a filled dot,
and the spec stops promising a payout it cannot deliver. Rejected: reintroducing coins solely for
streaks (a ledger and a sink, which is why coins were cut), and unlocking block palettes (gating an
accessibility feature behind a play streak is not defensible).

**`bestScore()` filters to Endless only.** A Daily run still writes a `run_record` with
`mode = DAILY` for lifetime totals, but it can no longer own the headline number. A best set on a
seed everyone else also played is not comparable to an Endless best, and one number meaning two
things is worse than two numbers.

### D21 · ▼ is a hard drop. Soft drop is deleted.

Owner ruling from **playing it**, 2026-09-10, and it supersedes the D11 half that cut hard drop.

The packet said hard drop was "tried and cut" and replaced by a two-tick accelerator. Play says
otherwise: the expectation at the control is "send this tile to the bottom", and the measurements
never disagreed — C1c put hard drop at level 4 in **33s** against **223s** patient (L29), and C1e
found the nudge recovered 88% of the clock but explicitly could not measure whether it recovered
the *decisiveness* (L40 note).

**Soft drop goes with it.** It was a *hold* of the same control, and holding is what produced the
bug below. One input, one meaning, no mode.

**The bug this fixes, which is the real argument.** `softDropOnHold` was keyed on `enabled = live`.
Hold ▼, let the block land, and the resolution phase flips `live` false — which re-keys the
`pointerInput` and **tears the gesture down mid-flight**, so `waitForUpOrCancellation()` is
cancelled and `onEnd()` never runs. `softDropping` then stays `true` with no path to clear it
except a new run. One accidental hold and the entire rest of the run falls at soft-drop speed.

Generalisable: **a `pointerInput` keyed on a value that changes during the gesture will drop the
release callback.** Any hold-to-do-X built this way leaks its "on" state. Prefer a gesture that
cannot be re-keyed mid-press, or reset the state on the phase change as well as at the callback.

**Costs, all accepted:** the determinism digest moves a third time (still free — no scores exist,
and D18's freeze does not bite until Daily scores are recorded); `Input.Nudge` and
`EngineConfig.nudgeRows` retire; every balance number shifts to the hard-dropping column, which
C1e already measured. `Motion.HardDropMillis` exists and was never used — now it has a caller.

### D20 · The design system grew a dark surface set; the meta screens did not adopt a backdrop

C3c's ruling. `defaultColors` now maps the handoff's game palette onto the existing role ramp, and
`rememberTypography` serves Fredoka and Nunito in place of DM Serif, Lust Script and Roboto.

The rejected alternative — have each meta screen draw the game's backdrop — leaves `Colors` light,
so every template surface nobody has rewritten (dialogs, sheets, snackbar, form fields, the launch
gates, the bug reporter) stays light and **the next screen anyone adds is light again.** That is
the third theme.

**No feature module changed a line to go dark.** They were already asking for
`AppTheme.colors.surfacePrimary` and simply got a different answer. It also caught the launch gates,
which nobody had listed.

One derived value was forced by a golden: `GameColors.ControlRaised`. The handoff's control system
only runs *downward* (control → quiet → shadow) and a role ramp needs one step **up**, for disabled
surfaces and unselected chart bars that sit *on* a card. Without it the stats bar chart was
invisible.

Evidence the change is contained: after re-recording, **the 16 board goldens are byte-identical**
and `:libraries:ui`'s 17 did not move at all.

### D18 · A Daily run pins `EngineConfig.Default` and ignores remote config entirely

C7's rule — sample config at run start, never mid-run — keeps a single run coherent and **does not
deliver SPEC 14's promise.** Two players opening the same seed ten minutes apart either side of a
config push would each get an internally coherent run and a *different board*. The seed would
match, the scores would not be comparable, and nothing anywhere would say so.

Comparability wins. Endless is unchanged and still takes remote config at run start.

**Accepted cost:** a live spawn-table fix does not reach the Daily until the next release. That is
the right boundary — a remote push is invisible and instant, a release is versioned and deliberate.

Rejected (in `decisions.md`): using whatever config the player has; a separately frozen
`EngineConfig.Daily` with its own literals, which would drift and be permanently worse-tuned than
Endless; stamping a config version into the seed, which creates invisible cohorts.

**What this makes immovable, and it is new:** `EngineConfig.Default` is now a leaderboard-visible
constant. From the first recorded Daily score, any release that moves a field of it splits that
day's board between app versions. So are `dailySeedFor`'s stride and salt (moving either re-rolls
every past and future day), `daily_result`'s ISO-UTC date format, and the `GameMode` enum names.

`PINNED_DIGEST` and `level.blocksPerLevel` were theoretical hazards while no scores existed. They
are now real.

### D16 · The danger ring arms on row 1, at any board height

C3b's ruling, and the reasoning is why it holds: **the threshold is defined by the death rule, not
by the board.** Row 0 occupied after a resolution ends the run, so anything in row 1 is one
non-merging landing from death. That sentence is height-independent, so 5x8 does not change it.

L25's extra row is spent on **reaction time after the ring turns red**, which is what was actually
short. The warning does not arrive earlier; it lasts longer.

### D17 · The three specials' landing ghosts

Undesigned in the handoff, which only knows will-merge and will-not. C3b's designs:

- **Stone:** always plain, never bright. It has no value and never merges, and a bright ghost
  would be a lie the first time a player meets one.
- **Wildcard:** the bright cell is the **neighbour that doubles**, labelled `×2`, with a plain
  outline on the landing cell. The neighbour is the cell that changes, and *which* of four it picks
  is the actual question the player is asking.
- **Bomb:** all five cells bright, **no label on any**. The footprint is the message, and a `×5`
  would read as a multiplier on a board where every other mark is one. Only *occupied* neighbours
  are outlined, because SPEC 18.4 pays per block destroyed and an empty cell promises a bang that
  never comes.

Three new toasts came with them: `BOOM!`, `WILD!`, `SWEPT!`. Precedence is **structural rather than
a rule** — one callout per playback frame, later frames replace earlier ones, so `ROW BUST!` beats
`CHAIN ×N` because the burst is the later frame.

### D14 · The design ramp ships as drawn, and its three failed floors are accepted

Owner ruling. The handoff's tile ramp fails three colour floors the authored ramp held: adjacent
tiers at ΔE 22.62 (worst pair 16/32), any-pair at **14.64 (2 against 2048)**, and a luminance span
of 0.187 against a 0.45 floor. Under simulated deuteranopia the closest pair collapses to **ΔE
0.67** (128/256).

The root cause is structural: `L` is constant across the ramp by design, so **lightness carries
nothing**, and lightness is the axis that survives every colour-vision deficiency.

**Accepted, for three reasons that are real rather than convenient:** every tile carries its
number and that cannot be turned off; a 2048 bursts its row on creation so it and a 2 are rarely
co-present; and the four accessibility palettes still hold every floor unchanged for players who
need them.

**The test was not loosened.** The four accessibility ramps still assert every floor. The default
is excluded from those three assertions and **pinned two-sided instead**, so a retune in either
direction fails loudly and lands back here.

If this is ever revisited, the highest-value single change is letting `L` climb with tier.

### D15 · The score renders in fixed-width digit slots

Owner ruling. Fredoka has **no `tnum` feature at all** and eight distinct digit advance widths; at
weight 700 its `1` is 379 units against the `2`'s 566, a 49% spread. A score ticking through a
cascade would visibly change width several times a second.

Each digit gets a slot as wide as the widest numeral. Fredoka stays everywhere the design asks for
it, the level number is fixed for free, and the cost is slightly looser letterfit than the
design's natural spacing.

Tiles were never affected — a tile draws one value, centred, and never animates between two.

Rejected: Nunito ExtraBold for the score (tabular in effect, but costs the most prominent number in
the game its Fredoka character), and a `tnum`-patched Fredoka (correct, but a font build pipeline
for one number).

### D13 · The ▼ nudge pays no score — **superseded by D21**

D21 put hard drop back and the bonus with it. The reasoning below was right about the nudge and is
kept because it is *why* the bonus is defensible now: it was always paying for commitment, and the
input it pays now is one. The guard the last paragraph describes survived under a new name,
`noTouchOfTheBoardScoresAnything`.

SPEC 7's hard drop bonus is struck rather than transferred. C1d's argument, and it is a good one:

- The bonus paid for **commitment**. Hard drop gave up the rest of the fall irrevocably. A nudge
  gives up two rows and can be pressed again, so a per-row payout rewards the tap rather than the
  decision, and obliges every player to mash a control the handoff deliberately drew as recessive.
- The nudge is now the only acceleration, so the term would fire on nearly every drop of nearly
  every run. That is a constant, and the score already has one in Survival.
- Every remaining row of SPEC 7 pays for something that happened **on the board**.

Guarded by `noInputScoresAnything` plus a test pinning the exact step list of a plain drop, so a
future award that pays for an input has to change a test on the way in.

### D12 · What a "run" is, and what "playtime" counts

SPEC 11 said "one row per completed run" and "duration" without defining either. C4 ruled both:

- **Completed means stacked out.** A run abandoned via Restart or Quit is not recorded. Quit leaves
  the save intact, so it resumes rather than dying.
- **Duration is time spent in `Playing` or `Resolving` only.** End-minus-start would count a run
  left on the lock screen overnight as eight hours of playtime, and SPEC 8.4 auto-pauses on
  backgrounding, which is the pause players actually use.

Also: the in-progress save is a **superset** of what SPEC 11 described. A `GameState` plus a
"transcript in flight" flag is not enough to resume mid-cascade — that needs the transcript itself
plus the board and score it started from. And a `GameState` alone loses the run's tallies, so a
resumed run would write a false `run_record` with nothing on screen to say so.

It is stored as a **JSON string**, not a typed field on `AppData`. A typed field creates a module
cycle, and worse, one decode failure would take all of `AppData` with it — losing the install id
and the onboarding flag over an abandoned run.

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

### D11 · Next preview, hold slot and hard drop are cut. The ▼ nudge replaces hard drop. — **hard-drop half superseded by D21**

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

**Measured in C1e, and the ruling holds comfortably.** The nudge recovers **88%** of the
wall-clock gap: level 1 goes 4.15s per drop patient, **0.96s nudging**, 0.54s under the old hard
drop; level 4 arrives at 223s / **56s** / 33s. C1d predicted 1.5-2.5s and was out by a factor of
two, because its arithmetic omitted that gravity keeps running underneath the taps rather than
pausing for them.

**Correction to consequence 1:** `nudgeRows` does **not** move the determinism digest. Measured,
not assumed — C1e set it to 3 and all five `DeterminismTest` cases passed, because kotlinx omits
values equal to their declared default and `Nudge` is behaviourally inert in a pinned script whose
every drop ends in `Lock`. It is a free live knob, unlike `blocksPerLevel`.

L29 transfers intact: 223s versus 56s makes **teaching ▼ a C5 tutorial problem**, exactly as it was
for hard drop.

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

### L38 · `git stash` is repo-wide, so in a multi-agent checkout it is destructive to other people's work

C2c ran `git stash push --include-untracked` to check whether a lint failure was pre-existing. Its
own changes were scoped to one module; **the stash was not.** It swept up two other agents' in-flight
work and the orchestrator's docs, and the `pop` then failed on `ORCHESTRATION.md` because it had
been rewritten in the meantime.

Nothing was lost, but recovering it cost a reconciliation pass, and the restore staged files that
were then swept into a commit whose message does not mention them.

**Rules now:** never bare `git stash` in this repo. Use `git stash push -- <paths>`, a scratch
clone, or do not stash. To test whether a failure is pre-existing, check out the file from HEAD to
a temp path, or read `git log -p` — do not move the working tree out from under a concurrent agent.

This is L10's lesson generalised: the failure mode is not "editing a shared doc", it is **any
repo-wide operation** in a checkout more than one agent is writing to.

### L39a · The goldens keep catching bugs on the change that introduced them

Running tally, because it is the argument for the harness: C2c found two constraint-propagation
bugs in its own components; C3b found `Modifier.blur` shaving the board ring (L43) and a layout
overflow at 360x640 (L45); C9 found `Modifier.border(Border)` drawing a **rectangle across a pill**,
leaving four accent stubs at the corners.

Every one was invisible in code review, and none would have crashed.

### L39 · A screenshot harness that captures nothing passes every test

Roborazzi's `captureRoboImage` is a **no-op unless a flag is set.** A plain `testDebugUnitTest` runs
every screenshot test, passes every one, and never compares a pixel.

C2c measured it rather than trusting it: it replaced a golden with a completely different image and
the task stayed green. The module now sets `roborazzi.test.verify=true` on its test tasks by
default, and the swapped-golden experiment was re-run afterwards to confirm the failure appears.

**This is the watch list's "a clean run does not prove the check ran" in a third shape** — after
detekt's cached classloader and a Compose-free module having nothing to match. Verify the verifier.

The harness immediately earned itself: it caught two real bugs in C2c's own components (glyphs
rendering top-left instead of centred, and the primary button inflating to fill the screen), both
invisible in code review.

### L35 · A rejection test without a positive control proves nothing

C1d had to prove an old saved-run blob is refused after `GameState` changed shape. The test loads a
real pre-D11 blob (with `preview`, `hold`, `holdUsedThisDrop`, no version) and asserts it is
refused.

**Then it loads the same bytes with nothing changed but a `version` spliced in, and asserts that
one resumes.** Without that second half, the test would pass on any malformed field and prove only
that *something* was wrong with the blob, not that the version check is what caught it.

The same shape applies anywhere a test asserts a refusal: prove the refusal is caused by the thing
you think, by changing only that thing and watching it pass.

### L64 · A playback hold is an input to the balance model, so a new one can move the game

D21 added `ResolutionStep.HardDropBonus` and gave it the obvious 60ms score-only hold, the same one
survival and level-up get. `theDropControlChangesTheWallClockAndNothingElse` then failed on one seed
in forty: 319 drops against 320, from a board that diverged on drop 312.

The chain is three steps long and none of them is in the engine. The bonus step **only exists on a
hard-dropped block**, so a hard-dropped resolution was 60ms longer than a timer-placed one;
`DropClock.resolutionMillis` mirrors the playback holds to decide whether the resolution lasted
long enough for the player to have queued C3's buffered sideways move; a flipped buffer gives the
*next* block a free sideways step, which changes what the reachability model thinks is reachable,
which changes the column it lands in. **The drop control changed where a later block landed** —
exactly the thing L40 says it cannot do.

The fix is that the bonus gets a hold of **zero**, which is also right on its own terms: the press
was the beat, the block is already on the floor, and a pause between the drop and its consequences
is the opposite of what the control is for.

Two general things. **A playback duration is not a cosmetic number** — this project feeds it into a
model that decides placement, so adding a step type to the transcript is a balance change until
proven otherwise. And **the test that caught it was a property, not an assertion about the feature
being added**. Nothing in the D21 brief would have suggested checking whether a scoring step could
move a block three hundred drops later.

### L61 · Room prefers a migration to a drop, so "the row survived" proves nothing

L33 narrowed `fallbackToDestructiveMigration` so a failure from `AppDatabase` v6 on fails loudly
rather than wiping a player's history. C12 wrote the first test of that promise and found the
obvious assertion is vacuous:

**Room migrates wherever a path exists, list or no list.** So a v6 → v8 run keeping its rows proves
only that the auto-migrations work. What the narrow list actually decides is **versions with no
migration path**: v4 is emptied and rebuilt; anything off the list refuses to open and leaves every
row intact.

C12 proved both directions bite by mutating the production configuration and watching exactly one
go red each time.

It also found *why* the promise went untested for eight chunks: `sqlite-bundled`'s Android variant
cannot load on a host JVM, and `setDriver` lived inside `RealAppDatabaseProvider` where a test could
not reach it. Moving it into the platform factories is what made any of this testable.

### L62 · Gradle reported `BUILD SUCCESSFUL` through a Kotlin compiler crash

A JVM backend ICE in `SyntheticAccessorLowering` (a `private companion object` const read from
inside a `buildMap` lambda in an enum) killed the daemon. Gradle retried the compile out of process
and **reported the build green**, so it surfaced as an alarming stack trace over a passing build.

Third member of the family with L24 (a failed iOS link reporting `BUILD SUCCEEDED` and silently
running the previous framework) and L46 (a test that never ran). **A green build is a claim, not a
proof** — read the log when something looks wrong, even when the summary line says otherwise.

### L63 · Make the debug *session* the taint, not the run

A debug menu can pollute `run_record`, `daily_result`, leaderboard submissions and achievement
facts. C12 latched a process-wide flag when the menu opens; `RunFactory` reads it once and it
travels on `StartedRun.debug`, gating all four writes at `endRun`.

Per-run tainting was rejected on a good argument: **a tester who has had their hands on a seed
switch is not a source of real data afterwards either**, and per-run needs remembering at four call
sites. Relaunching is the reset.

The second hazard — a debug-forced `EngineConfig` leaking into a saved run or a shared Daily — is
answered **by absence rather than a guard**: `DebugOverrides` contains no `EngineConfig` at all, so
there is nothing that could travel inside a `GameState`. A guard you can forget to apply is weaker
than a shape that cannot express the mistake.

### L58 · The cascade was too fast, and only a frame count could say so

C3a recorded the tutorial's scripted cascade and its 2048 burst off the emulator and counted frames
at 20fps. Three findings, all measured:

- Three merges and two chain callouts fitted inside **500ms**. A later callout replaces the earlier
  one, so **consecutive chain steps, not `ToastMillis`, are the real ceiling on readability** —
  `CHAIN ×2` was on screen for *3 frames*. The number the player is being congratulated on was
  never legible.
- **The 2048 existed for 400ms**, between the merge that made it and the burst that took it away.
  It is terminal, so it is the one tile in the game that can never be looked at — and it is the
  tile the game is named after.
- `ROW BUST!` and `SWEPT!` were both announced **over an already-empty board.**

Playback is now paced per step kind: first merge gets the handoff's hold, a chained merge 300ms, a
merge producing a 2048 its own 520ms beat, a burst 240 → 420, gravity unchanged. Re-measured:
`CHAIN ×2` holds ~350ms over 7 frames and the three-step cascade runs 1.1s and reads as three
things in order.

**The general lesson:** one duration constant covering merge, chain, terminal and burst was making
the common case fast at the cost of the rare one, which is backwards — the rare one is the payoff.
And "does it feel fast" is not a judgement anyone can make reliably at 60fps; count frames.

### L59 · Run the control before claiming the fix

`VIBRATE` was missing from the manifest and C3a added it — then **deleted it again and confirmed
the haptics still played.** On API 36 the missing permission was never what kept them quiet;
nothing had, because nobody had ever executed the code.

The declaration stays, because the API contract asks for it and a platform that *does* enforce it
fails silently. But the report says "this was not the cause", which is the difference between a fix
and a coincidence.

Haptics fired for the first time in the project's life, and `dumpsys vibrator_manager` confirmed
the Off/Light/Strong setting scaling end to end: `amplitude=0.17` for Light, `0.32` for Medium,
exactly the configured values halved.

### L60 · Goldens are not task inputs, so swapping one leaves the test UP-TO-DATE

The sharper edge under L39. With verification enabled, C3a swapped a golden and re-ran — **green and
UP-TO-DATE**, because the PNGs are not declared inputs to the test task, so nothing invalidated it.
Only `--rerun` surfaced the failure.

So "prove the verifier runs" needs `--rerun`, or the proof proves nothing. L39 was about the
capture being a no-op; this is the layer below it, where the capture works and the comparison never
executes.

### L56 · Nobody installed a build for eight chunks, and the app was unplayable the whole time

C3c built and installed, and found that **a fresh install could not get past the tutorial.** A
player who keeps tapping ▼ while a coach mark is up lands the next scripted drop before answering
the card; the beat then waits forever for a landing that already happened, on a frozen clock, with
no coach mark left to offer the skip. Empty board, no falling block, no exit but clearing app data.

It confirmed the bug was pre-existing by **building HEAD in a separate worktree and reproducing it
there.** From C5. Every chunk since had a green gate, 1,000+ passing tests, and dozens of goldens.

Standing rule 11 (prefer tests to a simulator) is right and this is its boundary: **tests prove the
parts, and only a build proves the app.** A frozen clock plus a pass-through scrim plus an eager
tap is an interaction between three chunks, and no unit test owns it.

**Install and play the app at the end of any chunk that changes the first-run path.** Not to verify
what a test could verify — to find the thing no test is watching.

### L57 · A broad `git add` in a shared checkout is as destructive as a stash

C9's commit swept up most of C3c's in-flight work: its `strings.xml` edits, a file rename, and most
of `GameViewModel.kt` and `GameScreen.kt`. Nothing was lost and the tree is correct, but that
commit's message describes none of it, and C3c's own commit is correspondingly thin.

This is L38 in the other direction — not a destructive command, just `git add` with a wide path.
Standing rule 12 now says so as loudly as it says the stash rule.

**Stage explicit files, not directories, when another agent is live.** Where that is impossible,
use C6's technique (L52): `git hash-object` + `git update-index` to stage a reconstructed version.

### L53 · Derive a constant from the system that owns it, or it strands the feature built on it

SPEC 15's tiered score achievements needed five score targets. C9 did not type five numbers: it
derived each rung as the score a run is **guaranteed** to have banked reaching level 5/10/15/20/25
(survival plus level-up, straight out of SPEC 7), floored to two significant figures — 3,400 /
14,000 / 32,000 / 58,000 / 92,000.

The reason is a scar from the sibling repo: **Sodogku stranded three badges behind a scoring
rescale, twice.** A typed target silently becomes unreachable the moment a coefficient moves, and
nothing fails.

Same shape as D5 (config travels with the seed) and the `BlockPalette` ink derivation (L2): when a
number is a *consequence* of another number, compute it.

### L54 · An achievement can be made unearnable by a rule three sections away

"Burst a row containing three Stones" is only earnable **because SPEC 18.5 says a burst clears
Stones too.** Five columns wide, the 2048 takes one cell, so three of the remaining four can be
Stones — and if 18.5 were ever reversed, the badge would become impossible with nothing else in the
game visibly changing.

C9's reachability test records that coupling in a KDoc, which is the only thing that would tell the
person who reverses 18.5 what they just broke.

The test itself is worth copying: every achievement stat is answered through an exhaustive `when`
with no `else`, one of three ways — **measured** over 40 fixed-seed greedy runs, **witnessed** on a
posed board, or explicitly **off the board** (wall clock, calendar). The witnessed cases exist
because a 10-step cascade occurs roughly 4 times in 740,000 drops, so no affordable number of
played runs would ever find one.

### L55 · Prove a call site by deleting it and checking *which* test goes red

`BUILD-PLAN.md` warned that Sodogku shipped `Leaderboards.submit` with zero production callers.
C9 deleted its `postToLeaderboards(score)` line and confirmed the two **ViewModel** tests failed
while `RealLeaderboardsTest` **stayed green**.

That distinction is the whole lesson. A unit test of the API passes happily whether or not anything
calls it — it is exactly what made the original bug invisible. The test that guards a call site has
to live at the caller.

### L51 · Assert on outputs, not on the inputs you think produce them

`DailyConfigPinningTest` proves a Daily run ignores remote config by comparing **block sequences**,
not config objects.

Comparing configs would pass a refactor that reads remote config and happens to get the same
numbers back — which is the exact bug the test exists to catch, since that refactor would break the
day a remote value actually differs.

It also carries a positive control (L35): the same fetched config demonstrably **does** change an
Endless run, so the rejection assertion cannot be vacuously true.

Both halves generalise. Test the thing the player experiences, and prove your negative by showing
the positive.

### L52 · How to commit without clobbering a concurrent agent

C6 needed to commit seven files that C11 was editing at the same time. Rather than a repo-wide
operation (banned, L38) or committing the other agent's half-finished work, it staged
**reconstructed** versions — HEAD's content plus its own edits — directly into the index with
`git hash-object` and `git update-index`, leaving the working tree untouched.

Then it verified the *committed* tree in a separate `git worktree`, because the shared checkout at
that moment had another agent's uncompilable module in it.

That is the technique when two agents genuinely must touch the same file. It also explains a
confusing class of report: C6 saw `:features:gate:impl` failing to compile and three
`:features:settings:impl` goldens failing, and correctly attributed both to the concurrent agent
rather than to itself (L14).

### L49 · Teach a habit, not a fact: make the thing you are teaching the only way forward

The tutorial's job was to put the ▼ nudge in the player's hands, because using it is worth 223s
versus 56s to reach level 4 (L29). The obvious approach is a card saying "tap ▼", and a card
teaches a fact that is forgotten by drop seven.

C5's answer was one line:

```kotlin
if (tutorial.isRunning) return   // in restartTicker()
```

SPEC 13 already froze the clock, for the stated reason that a scripted run should not be a race.
**That is not the valuable reason.** With no ticker, gravity never moves a block, so ▼ becomes the
only input that makes progress. A player cannot finish six drops without pressing it five to
fifteen times, and the 2048 burst — the game's biggest moment — is delivered by that button.

It also cost one line instead of a lesson, a gate and a nag. Rejected alternatives (a scrim that
only lets ▼ through, a "you didn't use ▼" prompt) are in `decisions.md`.

**Known limitation, recorded rather than hidden:** soft drop is a *hold* of ▼ and soft drop is the
ticker running faster, so soft drop does nothing during the tutorial.

### L50 · A brief can carry a wrong fact, and the agent should check rather than inherit it

My C5 brief told the agent that C0 left 28 `VerifyStrings` baseline entries "for the onboarding
screen" and that the chunk should shrink that count. **That was my misreading.** All 28 are in
`HomeScreen`, the colour catalog, `BugReportScreen`, `FeedbackScreen`, `ShakeDialog`,
`SplashScreen` and `AccessDeniedScreen`. Not one is onboarding — `OnboardingScreen` used
`stringResource` correctly, so deleting it shrank nothing.

C5 checked and said so instead of quietly reporting 28 → 28 as a failure to deliver.

**Orchestrator rule:** a brief is written from the orchestrator's memory of other agents' reports,
which is exactly the place a fact gets garbled. Agents should verify load-bearing claims in a brief
against the repo, and say so when the brief is wrong.

### L46 · A test that has never run can be wrong for months and look green

`:apps:server`'s `DatabaseSchemaTest` asserted `app_config_values` was empty. Migration V4's own
seed made that false **the day it landed**. It never failed, because Docker had been down for the
entire project and it had never once executed.

C7 started Docker Desktop and ran it. Every Testcontainers test passed after that one fix, and
`:apps:integration`'s `HarnessSmokeTest` — the real config data source over real TCP against the
real Ktor server on a real Postgres — **passed for the first time.**

The watch list said "skipped tests read as passing". This is the sharper version: **a skipped test
also stops being maintained**, and it rots silently against the code it is supposed to guard. The
5-skip baseline this project quoted for eight chunks was never a baseline, it was a blind spot.

**The gate now expects 0 skips.** Anything skipping is a finding.

### L47 · A console that warns about everything is a console nobody reads

C7 wired `blocksPerLevel` to remote config despite it moving the determinism digest, and made the
admin surface warn loudly on it — red banner, typed confirmation in prod, warning on revert too.

The half that makes it work is the other test: **`DangerousWarningTest` pins that the ten keys
measured safe do *not* warn.** The speed curve (L28), `nudgeRows` (L40) and the spawn table (L19)
are all known-safe by measurement, so warning about them would train the operator to click through
the one warning that matters.

A warning's value is set by how often it is absent.

### L48 · A malformed boolean silently reads `false` instead of falling back

`getValueRecursive` coerces any garbage to `false` via `toString().toBoolean()`. So a corrupted
`ads.enabled` reads as **off** rather than falling back to its compiled default of on.

For ads that is arguably the safe direction. For `feature.dailyChallenge` or
`feature.leaderboards` it silently removes a feature with no error anywhere, which is the exact
failure mode SPEC 10's "a malformed remote value falls back to the compiled default" was written
to prevent.

Pre-existing template behaviour. It matters before C10 leans on these keys.

### L43 · `Modifier.blur` clips to bounds at **any** radius, including zero

Applying `blur(0.dp)` as a no-op is not a no-op. It was silently shaving the board well's 4dp ring
and its danger glow off three sides. Apply the modifier **conditionally** rather than passing zero.

**Caught by a screenshot golden**, not by review, and it is exactly the class of bug that has no
symptom in code and no crash — the ring just quietly is not there.

### L44 · A derived "best" that already includes the current score can never detect a new best

`GameUiState.best` is `maxOf(best, score)` on every publish, so by the time a run ends it **equals**
the score. Comparing against it to light "new best!" would have fired on every single run,
forever.

C3b added `bestBeforeRun` for the comparison. The general shape: **a value derived to always
include the current one cannot also be the baseline you compare the current one against.** Worth
suspicion anywhere a `max` is folded into display state.

### L45 · Screenshot goldens are captured at 360x640 on purpose

The tightest frame the app ships to, not the roomiest. A golden taken on a tall phone agrees with
the design and disagrees with the player.

C3b found a real layout bug this way: the handoff hardcodes `max-width: 370px` against a 5x7
aspect, and at 5x8 the board is taller, so on a short screen it overflowed and the flex spacer
collapsed. The board is now bounded by height as well as width, and the goldens pin it.

### L40 · Vertical position is not an input to the engine, so every drop control is pure pacing

`Input.Lock` places at the block's **landing** cell, so how high the block was when the player
committed is not an input to anything. C1e measured the consequence: `patient`, `softie`, `average`
and a restored hard-drop finish produce **outcome columns identical to the digit** — same median
level, same tier distribution, same clutter, same cascade depth.

A drop control decides *when* a block locks and never *where*. It is a wall-clock dial and nothing
else. Pinned as `theDropControlChangesTheWallClockAndNothingElse`.

Two things follow. `nudgeRows` is **digest-free** (see the D11 correction below), so it is a
genuinely free live knob. And no future control scheme can be argued to change difficulty without
first changing what `Lock` does.

### L41 · `decisionMillis` is the difficulty dial, and it is an unmeasured guess

C1e swept the modelled player's decision time across a plausible range:

| ms | median level | 1024 rate | to level 4 |
|---|---|---|---|
| 150 | 21 | 28.5% | 49.5s |
| 250 (shipped) | 19 | 24.2% | 55.8s |
| 350 | 17 | 18.3% | 61.1s |
| 500 | 16 | 10.8% | 70.9s |

**Five levels of median and a 3x swing in the 1024 rate.** For scale: the drop clock itself was
worth three levels (L30) and the entire spawn table is worth one (L19).

Every clocked number in this project is conditional on a constant nobody has measured. C1e left it
at 250 deliberately — the two arguments for moving it (less to read without a preview / more of the
choice now comes from the board) point opposite ways and neither has evidence, so picking would
replace an honest assumption with a dressed-up one.

**This is the top telemetry priority for C8.**

### L42 · A `get() = false` on a sealed interface is a JVM default method, and it can half-build a companion

C1e declared `cheats` on the sealed `Policy` interface with a default getter. That made it a JVM
default method, so initialising `Policy.Random` initialised `Policy`, which built the companion and
evaluated `Policy.All` **while that object was still half-built**. `All` held a null. Two tests died
on a non-null parameter check. No compiler warning.

It is abstract now, with a KDoc saying why it must stay abstract.

Same family as L22 (declaration-order initialisation) and worth the same suspicion: **anywhere a
companion holds a list of its own enclosing type's objects.**

### L36 · Removing hard drop cost the engine no code path

`Input.Lock` already locked at the block's **landing** cell rather than its current one, so
`hardDrop` was `lock` plus a score prefix. Every "put this block down now" call site became
`Input.Lock` with zero behavioural change.

Worth noticing as a design signal: when deleting a feature turns out to be free, the feature was
probably a thin policy over a primitive that was already right.

**Amended by C1e: right in substance, wrong as stated.** Placement *is* identical, but C1d also
moved the board-aware cap to spawn time, which changes the drawn block on the 0.02-0.04% of drops
L20 measured. Over ~400 drops that reseeds roughly one run in twelve.

So every C1a and C1c number is good to about **0.5pp, not to the digit.** Nothing in the
conclusions moves; quote them with that precision.

### L37 · `Lookahead-1` now cheats, and that may be fine

With the preview cut, the lookahead policy reads a block that is neither shown to the player nor
drawn until the current one lands. C1d left it that way deliberately and documented it: SPEC 4.4
wants it as a **ceiling**, and a ceiling may cheat as long as everyone knows.

C1e has to decide whether to keep reporting it as the ceiling or demote it, and **must never quote
it as a prediction of play**. Also note `PlayerProfile.decisionMillis = 250` was calibrated to
include "a glance at the preview" and is now a guess about a different game.

### L32 · A state copy that omits one field started an invisible game, and it shipped for two chunks

`restart()` published a board **without a phase**. "Drop again" started a real run underneath the
stacked-out sheet: invisible, and untouchable because the scrim is a genuine input barrier. It was
also reachable from the pause menu's Restart from the day C3 landed.

Nobody hit it. It took C4 writing a test to find a bug on the most-pressed button in the game.

**The shape to watch: a `published()` helper that copies everything *except* one field.** That will
do this again, and the symptom is not a crash — it is a screen that looks like it did nothing.

This is the strongest argument yet for standing rule 11. A screenshot test of "tap Drop again"
catches it instantly; two rounds of human play did not.

### L33 · `fallbackToDestructiveMigration` is an unrecoverable wipe when there is no account

The template shipped a blanket destructive fallback on `AppDatabase`, which is a reasonable default
for an app whose data also lives on a server. **This app has no account and no server copy.** A
failed migration would silently delete the player's entire history with nothing to restore from.

C4 narrowed it to `fallbackToDestructiveMigrationFrom(1, 2, 3, 4)` with a
`FIRST_PLAYER_DATA_VERSION = 6` constant marking where real player data begins. Versions before
that held nothing worth keeping; from 6 onward a migration failure must be a crash, not a wipe.

### L34 · A multibinding nothing reads is never validated

`Set<ClearableDao>` had no consumer, so nothing proved the bindings were even wired. C4 added an
accessor on `AppComponent` **and read the generated kotlin-inject code** to confirm both DAOs are
actually in the set.

Declaring a multibinding is not the same as having one. If nothing consumes it yet, expose it and
assert on it, or you find out at the moment you first need it to work.

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

**Subagents no longer edit this file.** They report, the orchestrator writes. Standing rule 13.

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
