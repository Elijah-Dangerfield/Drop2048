# Architecture decisions

Append-only log. Add an entry whenever you make a non-trivial architectural call
(new module boundary, library choice, scope cut, schema shape). Each entry: date,
the decision, alternatives considered, and *why*. Newest first.

---

## 2026-09-09 — C3b's five rulings on the parts of the game screen the handoff never drew

The design handoff is a 5x7 board with no special blocks, so it has nothing to say
about four of the states this game can actually be in. C2c found them while
building the components and left them open. Ruled here, with the argument, because
each one is a promise the screen makes to the player.

**1. The danger ring keeps its "row 1" threshold at 5x8, unchanged.** L25 bought a
row of warning by going from 7 rows to 8, and the tempting move was to spend it by
arming the ring a row lower. It is not spent, and the reason is that the threshold
is not a distance measurement. Row 0 occupied after a resolution *is* the end of
the run (SPEC 3.1), so anything resting in row 1 means the next block that lands
there without merging ends the game. That is the same sentence on a 7-row board
and on a 12-row one — the threshold is defined by the death rule, not by the board
height. The extra row is spent where it was earned: the player now gets one more
drop's worth of reaction time *after* the ring turns red, which is the thing that
was in short supply. It also costs nothing to leave alone: the engine already
computes `inDanger` as `isRowOccupied(1)` and the screen only reads it.

**2. A Stone's landing preview is always plain.** A Stone has no value and never
merges. A bright ghost on a Stone would be a lie the first time a player saw one,
and the first time is the time they are learning what a Stone is.

**3. A Wildcard's bright cell is the neighbour, not the landing cell.** SPEC 5.2
has the Wildcard take a neighbour's value doubled, so the cell that *changes* is
the neighbour's, and the question the player is asking is which of up to four
neighbours it will pick. Marking the landing cell answers a question nobody asked.
The label stays `×2` because the neighbour doubles. The landing cell keeps a plain
outline so the block's own destination is still visible.

**4. A Bomb outlines all five cells and labels none of them.** The footprint is the
message. A `×5` in the middle would read as a multiplier, which is what every
other mark on this board means, and a bomb multiplies nothing. Only *occupied*
neighbours are outlined: an empty cell does not visibly clear and scores nothing
(SPEC 18.4), so outlining it promises a bang that does not happen.

**5. Three callouts are added to the handoff's three.** It draws `CHAIN ×N`,
`ROW BUST!` and `LEVEL N`. SPEC 7 also pays for bomb detonations, Wildcard
resolutions and a cleared board, and a payout with nothing on screen to explain it
is a payout the player does not connect to what they did. They are `BOOM!`,
`WILD!` and `SWEPT!`. The board-cleared bonus is the strongest case of the three:
it is the rarest frame in the game and its only other evidence is the score having
moved more than expected.

Precedence between callouts is structural rather than a rule anybody has to
remember: one per playback frame, and a later frame's callout replaces the one
before it. A burst is a later frame than the merge that caused it, so `ROW BUST!`
beats `CHAIN ×N` without an `if`.

---

## 2026-09-09 — The board is bounded by height as well as by width

The handoff hardcodes `max-width: 370px` on a board with a 5/7 aspect ratio. This
game is 5x8 (SPEC 3, measured on a device in C3), which is a whole row taller for
the same width, and the handoff's `flex: 1; min-height: 8px` spacer is the only
thing absorbing the difference. On a 360x640 frame it collapses to its minimum and
then keeps going, taking the control row off the bottom of the screen.

**Decision:** the board takes the smallest of the available width, 370dp, and the
width whose 5x8 height fits the space it was given. The flex spacer is whatever is
left, which is the handoff's *intent* — push the controls into thumb reach —
rather than its arithmetic.

**Alternatives:** cap the board's height and letterbox it (leaves a gutter that
looks like a layout bug), or scroll the screen (a game screen that scrolls under a
drag gesture is unusable). Neither is better and both are more code.

This is now pinned by a golden captured at 360x640 rather than at a generous
device size, deliberately: a screenshot taken on a tall phone agrees with the
design and disagrees with the player.

---

## 2026-09-09 — The cheating policy stays, and the cheat moves onto the instrument

**Decision:** `Policy.Lookahead1` keeps reading the next block it cannot see, and
gains a `cheats` flag that `Report` prints on every line it renders, so its output
reads `policy=lookahead1 (ceiling, reads a block the player never sees)`.

Since D11 the next block is drawn at spawn time and never shown, so this policy
plans around a value that does not exist yet. Measured, that is worth two levels
of median and four times the 2048 rate over `Greedy`.

**Alternatives:** demote it to a curiosity, or rewrite it to search the
distribution of next draws instead of the actual one. The first leaves the project
with no upper bound at all — `Greedy` is a competent player, not a bound, and
SPEC 4.4 asks for a ceiling. The second is a different and far more expensive
policy answering a question nobody asked.

**Why a flag rather than a KDoc paragraph:** the failure mode is somebody quoting
a lookahead median as a prediction of play, and that happens when they read the
output, not when they read the source.

**One landmine on the way in.** Declaring `cheats` on the sealed interface with a
`get() = false` default turned it into a JVM default method, which makes
initialising `Policy.Random` initialise `Policy`, which builds the companion and
evaluates `Policy.All` while the object it is reading is still half-built. `All`
then held a null and two tests died on a non-null parameter check, with no
compiler warning. It is abstract now and each object states its own answer. Same
shape as L22: a declaration order nobody can see, and correct-looking code.

---

## 2026-09-09 — `nudgeRows` does not move the determinism digest, measured

**Decision:** treat `EngineConfig.nudgeRows` as a freely tunable remote key, unlike
`blocksPerLevel`.

D11 and `EngineConfig`'s own KDoc imply the opposite, on the reasonable grounds
that it travels inside `GameState`. Checked rather than reasoned: the default was
set to 3 and all five `DeterminismTest` cases passed on the JVM.

**Two reasons, both independent.** `kotlinx.serialization` omits a value equal to
its declared default, so moving the default moves nothing in the bytes. And
`Input.Nudge` is behaviourally inert in the pinned script, because every drop ends
in `Input.Lock` and `Lock` places at the block's *landing* cell — the engine cannot
tell how far down a nudge already moved it.

That second reason is the general one and it is worth stating on its own: **the
vertical position of a falling block is not an input to anything the engine
records.** It is why the drop control is a pure pacing dial, why `patient`,
`softie` and a nudging player produce identical outcome distributions over 10,000
runs, and why `nudgeRows` can be tuned live without invalidating a score.

---

## 2026-09-09 — The screenshot harness is Roborazzi, and it had to be forced to actually compare

**Decision:** `:libraries:ui` gets a Roborazzi + Robolectric screenshot harness.
Fifteen goldens live in `libraries/ui/screenshots/`, committed.
`./gradlew :libraries:ui:recordRoborazziDebug` re-records them; every other test
run compares them.

**Why Roborazzi and not the alternatives.** Paparazzi renders through layoutlib
and does not read Compose Multiplatform's `composeResources`, so the bundled
Fredoka and Nunito would not load and every golden would be of a design nobody
ships. The JetBrains Compose Multiplatform screenshot tooling is still tied to
the Android Gradle plugin's own `screenshotTest` source set and cannot see a
`commonMain` composable in a KMP module. Roborazzi runs inside the Android
target's ordinary unit tests, reads the module's real assets, and needed no
build-logic changes beyond one plugin and a `testOptions` flag. Neither
`../Sodogku` nor `../Cards` has solved this, so there was nothing to port.

**The trap, and it is the part worth remembering.** Out of the box
`captureRoboImage` is a **no-op** unless a Roborazzi flag is set. A plain
`testDebugUnitTest` runs every screenshot test, passes every one of them, and
never looks at a pixel. This was measured rather than assumed: a golden was
replaced with a completely different image and the task stayed green.

The module therefore sets `roborazzi.test.verify=true` on its test tasks by
default and stands it down only for the record task. The reason it is done this
way rather than by hanging `verifyRoborazziDebug` off `check` is that the
project's standard verification command is fixed at four tasks, and a check that
is not in the command people run is a check that does not run.

**What it does not cover.** Robolectric implements the *Android* framework, so
this is the Android target only. There is no equivalent for iOS, and a green run
means "the composition and its colours are unchanged", not "it looks right
everywhere".

---

## 2026-09-09 — Fredoka ships as the numeral face, and its figures are proportional

**Decision:** Fredoka 500/600/700 and Nunito 600/700/800 are bundled as static
instances cut from the variable originals. `DigitFontFamily` points at Fredoka,
as the handoff specifies. **The score counter's wobble is not fixed and must not
be quietly fixed by swapping the face.**

**The measurement.** The shipped Fredoka carries no `tnum` feature at all, and
its ten digits have eight distinct advance widths at every weight the design
uses. At 700 the `1` is 379 units against the `2`'s 566 — a 49% spread. Nunito,
by contrast, has all ten digits at exactly 600 units: tabular in effect without
needing the feature.

**Why ship it anyway.** On a *tile* the wobble does not exist — a tile draws one
value, centred, and never animates between two — and tiles are where almost every
numeral in the game is. The wobble is confined to the score counter and the level
number.

**The three fixes, for the owner to choose between:** lay the score out digit by
digit in fixed-width slots (keeps Fredoka, costs one composable, recommended);
draw the score in Nunito ExtraBold (one line, costs the most prominent number on
the screen its Fredoka look); or ship a `tnum`-patched cut of Fredoka (correct,
and a font pipeline nobody wants to own for one number).

---

## 2026-09-09 — The design's tile ramp replaces the hill-climbed one, and three floors go with it

**Decision:** `BlockPalettes.Default` is now the handoff's ramp — one hue per
tier at `L/C = 0.78/0.15`, and `0.85/0.17` at 2048 — rather than C2's
hill-climbed set. All five palettes are now authored as `L C H` triples and get
their ink and their hard shadow from one derivation.

**What it costs, measured on the shipped Kotlin:**

| floor | holds at | design ramp | worst pair |
|---|---|---|---|
| neighbouring tiers | ΔE 24 | **22.6** | 16 / 32 |
| any pair | ΔE 17 | **14.6** | 2 / 2048 |
| luminance span | 0.45 | **0.187** | — |
| numeral contrast | 4.5:1 | 7.16:1 | — |

The luminance span is not a mistake, it is the ramp's defining property: `L` is
constant so the board reads as one set of objects lit the same way. It also means
lightness carries nothing, which is the axis that survives every colour vision
deficiency. Under the Viénot simulation the default ramp's closest pair collapses
to **ΔE 0.67** (128 and 256, under deuteranopia). C2's ramp was not much better
there — 6.15 under protanopia — so this is a deepening rather than a new problem.

**The floors were not loosened.** The four accessibility palettes still hold all
of them. The default ramp's numbers are *pinned* instead, two-sided, in
`BlockPaletteTest.theDesignRampIsWhereTheHandoffPutIt` and
`theDesignRampCollapsesUnderEachDeficiency`, so a retune in either direction
fails loudly and has to be argued for. **Whether ΔE 14.6 between the 2 and the
2048 is acceptable is an owner call and has not been made.** The mitigation is
real: every tile carries its numeral, no setting can turn it off, and a 2048
bursts its row immediately so the pair is rarely co-present.

---

## 2026-09-09 — The tile's ink is the design's hue-matched one, with a fallback the design never needed

**Decision:** `inkFor` prefers the handoff's `oklch(0.26 0.07 H)` — the tier's
own hue taken almost to black — and falls back to the higher-contrast of the two
flat inks only when that ink cannot clear 4.5:1 on the face.

**Why prefer rather than take the best of three.** The flat near-black beats the
hue-matched ink on contrast on every one of the eleven default tiers. A
best-of-three would therefore reject the handoff's ink everywhere, silently, and
the design would lose a tie-break it never entered. Preferring it means the
shipped ramp gets exactly the ink that was drawn.

The fallback fires on twelve tiers, every one of them on the dark half of one of
the three colour-vision ramps, and never on the default or high-contrast ones.
The handoff only ever draws light tiles, so its ink rule has never met a face
dark enough to swallow it — which is precisely why the four accessibility
palettes cannot inherit the rule unmodified.

---

## 2026-09-09 — The saved run carries a format version, and the version is what invalidates it

**Decision:** `SavedRun` gains `version: Int` with **no default**, and
`SavedRunStore.load()` returns null unless it equals `SAVE_FORMAT_VERSION`. Bump
the constant whenever `GameState`, `RunTally` or `SavedResolution` change shape.

**Context:** D11 removed `preview`, `hold` and `holdUsedThisDrop` from
`GameState`, which is the first engine change since C4 that could invalidate a
blob sitting on a real device.

**Alternative, and the reason it was rejected:** rely on `ignoreUnknownKeys`,
which is already set. It would have worked here — a pre-D11 blob decodes cleanly
into the new shape, losing only the held block — and that is exactly the problem.
It only works while every change is subtractive, it decides silently, and the
first change it cannot absorb is a field that changes *meaning* rather than
disappearing. That one would restore a run into the wrong rules with nothing on
screen to say so, and a run restored wrong is worse than a run lost.

Having no default is the load-bearing half. A default would make a pre-D11 blob
decode as "version 2" and be accepted, which is the silent path again.

The cost is bounded and was already the accepted cost of storing this as a
string: the player loses the run in flight and nothing else. The install id, the
onboarding flag and the whole `run_record` history live outside the blob.

The test that guards it loads a hand-written pre-D11 blob twice, once as-is and
once with nothing changed but a `version` spliced in. The first must be refused
and the second must resume. Without the second half the test would pass on any
malformed field and prove nothing.

---

## 2026-09-09 — The in-progress run is a string in `AppData`, not a typed field

**Decision:** `AppData.savedRun` is a `String?` holding JSON. `SavedRun`, the type
inside it, lives in `:features:game:impl` and is the only thing that knows the
format.

**Alternative:** a typed `GameState` field on `AppData`, which is what SPEC 11
reads like. Rejected for two reasons that point the same way.

The dependency is wrong: `AppData` is the app-wide cache, and typing this field
would put the engine on its classpath and make the app-wide cache depend on a
feature's private save format. It also creates a cycle — `:libraries:progress`
holds `GameMode` and depends on `:libraries:drop2048:storage`, which depends on
`:libraries:drop2048`, which is where `AppData` is.

The blast radius is worse. The engine's serial shape moves with every chunk. A
nested field that fails to decode takes **the whole of `AppData`** with it
through `VersionedCacheJsonSerializer`, so a change to `EngineConfig` costs the
player their install id and their onboarding flag over an abandoned run. A string
decodes on its own, and a failure is "no saved run".

## 2026-09-09 — Resume rides the pause seam, and the snapshot is the frame index

**Decision:** the app dying is treated as a pause nobody got to handle. A
`SavedRun` is written at every lock and at every pause; when a cascade is on
screen it carries the board and score the resolution started from, the
transcript, and the playback frame index. Restoring re-derives the frames with
`framesFor` and relaunches the driver from that index.

**Why:** C3 already made the frame index the mid-cascade snapshot for pause, and
SPEC 18.9 asks for the same behaviour for a backgrounding. Building a second
mechanism would mean two things that can disagree about what "mid-cascade" means.
The frames are re-derived rather than stored because they are a pure function of
the transcript — storing them would be a second copy of the same truth, and the
copy is the one that goes stale.

**Written at locks and pauses, not on every tick.** The value only changes
meaningfully per drop. The cost is that a force-quit loses the sideways nudges of
the drop in progress; a disk write twice a second for the whole of every run is
the alternative.

## 2026-09-09 — A run's duration is time played, not wall time

**Decision:** `run_record.durationMs` accumulates only while the phase is
`Playing` or `Resolving`. Every pause closes the stretch, and every save banks
the stretch underway so a force-quit does not lose it.

**Why:** SPEC 15 shows the sum of these as "total playtime", and SPEC 8.4
auto-pauses on backgrounding — which is the pause players actually use. End minus
start would report a run left on the lock screen overnight as eight hours, and
one such run would dominate the lifetime total forever.

The clock is the injected `kotlin.time.Clock` the app component already provides.
The engine has none and does not get one (SPEC 4.1); the ViewModel owns time, as
it already does for the drop timer.

## 2026-09-09 — The board is 5x8, settled on device, because the board is width-bound

**Decision:** `EngineConfig.DEFAULT_ROWS` stays at 8. SPEC 3's seven-versus-eight
question is closed.

**How it was settled:** both were built and run on an iPhone 16e simulator, which
is the smallest device this Mac has a runtime for. The mockups' case for 7 was
that it "draws bigger blocks and reads better on a small phone". It does not.

At five columns on a 6.1" phone the cell size is set by the **width**, not the
height: 8 rows drew a ~64pt cell and 7 rows drew a ~66pt cell. The extra row's
worth of height did not become a bigger block, it became gutter above and below
the stack. So 7 costs a full row of reaction time in the danger state and buys
about 3% of block size.

This holds for any phone narrower than roughly 4:3 against its board area, which
is every phone. If a tablet layout ever ships, it is the first place the
constraint could flip, and `board.rows` is a remote key either way (SPEC 10).

## 2026-09-09 — Transcript playback is a driver coroutine feeding the action channel

**Decision:** `GameViewModel` keeps the resolved `GameState` unpublished, rebuilds
the boards *between* transcript steps with a pure `framesFor`, and hands the
frames to a coroutine that feeds them back in as `GameAction.ShowFrame`. Nothing
animates the engine; the engine is finished before the first frame is drawn.

**Alternatives considered.** Playing the transcript *inside* `handleAction` would
have blocked the action channel for the length of the cascade, which queues player
input rather than ignoring it — the opposite of SPEC 6. Publishing the resolved
board immediately and overlaying effects on top would have meant the board on
screen and the board in the engine disagreeing about where blocks are, which is
the class of bug this whole architecture exists to avoid.

**Why it matters beyond C3.** Because everything still arrives on one channel,
there is exactly one writer of the engine state and no lock anywhere; SPEC 6's
"input during resolution is ignored" is one guard; and SPEC 18.9's pause-mid-
cascade is the frame index that already exists. C5's tutorial and C12's replay
both drive the same seam.

`framesFor` is a pure function in its own file with its own test, and the
assertion that matters is that its **last frame equals the board the engine
returned**. Every other way the replay could drift is a specific bug; that one
catches the ones nobody thought of.

## 2026-09-09 — A sideways move made during a cascade is replayed, not discarded

**Decision:** SPEC 6's "input during resolution is ignored" is amended. Input
still never reaches the engine mid-cascade. But the **last sideways move** is held
and applied to the block that spawns afterwards.

**Why:** playing the first build on device, every move tapped in the beat after a
hard drop vanished, and the block that had just spawned went straight down the
middle of the board. Three separate columns' worth of intended play ended up in
one. The player did the right thing and the game did nothing, which reads as the
game dropping inputs rather than as a rule.

**What is deliberately not buffered.** Hard drop, because replaying one would end
a run the player has not looked at yet. Hold, for the same reason. And it is one
move rather than a queue: a queue would let a burst of panicked taps march the new
block across the board on its own.

---

## 2026-09-09 — The SPEC 5.3 spawn table is kept, and the merge ruling is what made it fit

**Decision:** C1a measured the spawn table in SPEC 5.3 with `tools/balance` and
changed nothing. Three alternatives were measured beside it and all three are
worse against the criterion `BUILD-PLAN.md` set for C1a. The numbers are in that
file's C1a outcome; the alternatives live on in `Tables.kt` so the comparison can
be re-run rather than re-argued.

**Why it needed measuring at all:** every number in that table was written before
the merge-position ruling, so it was a guess made under different physics.

**What the measurement found.** The ruling made the game materially *harder*, and
not by changing how often chains happen. Greedy's cascade-depth histogram is
almost identical either side of it — mean depth 0.88 before, 0.87 after; share of
drops reaching depth 2 or more, 21.8% before, 20.8% after. What moved was where
the merged block ends up. A horizontal merge now migrates the result into the
partner's column instead of leaving it in the column the player just dropped
into, and the partner's column is by construction the one that already had a
matching block in it. The board therefore gets *less* level with every horizontal
merge rather than more, and an uneven board on five columns is what ends a run.
Greedy's 2048 rate fell from 36.7% to 3.8% and Lookahead-1's from 65.1% to 15.7%
on the same table and the same seeds.

**Consequence for the ruling itself:** it stands, and it is now the reason the
table fits. Under the old position rule that same table put a burst in the
majority of Lookahead-1 runs, which is SPEC 17's definition of too easy. Anyone
revisiting the merge position needs to retune the spawn table in the same change.

**Consequence for SPEC 5.3B.** The board-aware cap is evaluated two drops early
and can therefore only ever be too *loose*. The harness counts the drops where
the block that landed exceeds the cap the board would impose at landing time:
0.02% of Greedy's drops and 0.04% of Lookahead-1's. The staleness is real and it
is not worth engineering around. Under the pre-ruling engine it was 0.09% and
0.16% — still nothing, which is the answer to the worry that faster chains would
make the cap compound.

**Not measured:** the harness has no clock. Every policy hard-drops into the
column it wants, so these are ceilings for an unhurried player and say nothing
about SPEC 5.5's speed curve. Real medians will be below them.

## 2026-09-09 — Ruled: the merged block lands in the partner's cell, both orientations

**Supersedes the entry below it.** The owner ruled on the contradiction C1 found,
and ruled the other way: SPEC 4.3 now says **the partner's cell**, in every
orientation, and the vertical "lower cell" wording is gone because a vertical
merge's lower cell already *is* the partner's cell.

**Why the ruling goes this way.** One rule instead of two. It matches both worked
examples rather than only one. And a horizontal merge that pulls the result
*toward* the match is what lines the new block up over whatever sits under the
partner, which is how a chain gets a second step. SPEC 21 says chains are what
the game is for, so a rule that quietly suppresses them is the wrong rule even
where it is the more literal reading. The legibility worry in the superseded
entry stands but is smaller than it looked: the result still lands on a cell
adjacent to where the player put the block, and it lands on the block the player
was aiming at.

**In the code:** the two orientations collapsed into one code path.
`Resolver.merge` no longer branches on direction; the result is written to the
partner's cell and the initiator's cell is cleared, which is now also one board
write fewer.

**Tests:** `PriorityOrderTest` is inverted. The "8 below the left 4" arrangement
is the canonical worked example and must cascade to a 16; the "8 below the
landing cell" arrangement is pinned as the one that must *stop* after one merge.
Either direction of future change fails loudly and names this decision.

**Determinism:** the pinned digest in `DeterminismTest` moved, because merge
outcomes moved. `PINNED_SCORE` 942 → 862, `PINNED_BLOCKS_DROPPED` 27 → 25,
`PINNED_DIGEST` re-derived. Level unchanged at 2. No Daily Challenge scores exist
yet, so this was free; it will not be free again.

**Also confirmed, unchanged:** Wildcard merge symmetry stays, for the reason in
the entry two below. SPEC 5.2 now states it explicitly rather than leaving it as
an implementation divergence.

## 2026-09-09 — The horizontal merge position contradicts the priority worked example

**SUPERSEDED 2026-09-09 by the owner ruling above. Kept for the reasoning.**

**Decision:** SPEC 4.3 wins. A horizontal merge puts the new block in the
**initiating** block's cell, as 4.3 says, and the `PriorityOrderTest` worked
example is written against a board where that produces the outcome the build
plan describes.

**The conflict.** `BUILD-PLAN.md` C1 asks for "the *4 lands between two 4s with
an 8 below the left one* case produces an 8 and an untouched 4, then cascades to
a 16 in the lower cell". That chain is only reachable if the 8 the merge creates
lands where the **left 4** was, i.e. in the *partner's* cell. Under SPEC 4.3 it
lands where the falling 4 did, and an 8 sitting under the left 4 is then
diagonal to it and unreachable. The two statements cannot both hold.

**Why 4.3 wins:** `SPEC.md` says it is the source of truth and that where it and
the original spec disagree, it wins; 4.3 additionally marks the merge rules "not
negotiable once shipped". Moving the result to the partner's cell would also mean
a falling block can trigger a merge two cells away from where the player put it,
which is the legibility the rest of 4.3 is built to protect.

**Cost accepted:** both arrangements are pinned as tests. The "8 below the left
4" variant asserts the chain *stops*, so anyone who later changes the position
rule gets a failure that names the decision rather than a silent behaviour swap.

## 2026-09-09 — A Wildcard merge is symmetric

**Decision:** a value block landing beside a resting Wildcard merges with it, not
only the other way round. SPEC 5.2 describes only the Wildcard-initiated
direction ("on landing, merges with the first neighbor found via the priority
order").

**Why:** the Wildcard's whole job is to be the tile that fits. A Wildcard that
can only ever be consumed on the drop it arrives on becomes dead weight the
moment it rests inert — which SPEC 5.2 explicitly allows it to do — and the
board acquires a permanent obstacle that reads like a bug. "A Wildcard next to a
1024 makes a 2048" is a property of the pair as a player sees it, not of which of
the two moved last. Eligibility is otherwise unchanged: value blocks only, so
SPEC 18.2 (Wildcard beside Wildcard) and 18.3 (Wildcard beside Stone) still hold.

**Consequence:** SPEC 5.2's "re-evaluated whenever an adjacent cell changes"
becomes a rarely-observed safety net rather than the main path, because the
newly-adjacent block usually initiates first. It is implemented and tested
anyway (`SpecialsTest.anInertWildcardIsReEvaluatedWhenAnAdjacentCellChanges`),
since it is the only thing that reaches a Wildcard the arriving block ignored.

## 2026-09-09 — `EngineConfig` lives inside `GameState`, and the undo ring does not

**Decision:** every tunable SPEC 10 marks remote (spawn table, cap divisor, speed
curve, special rates, `board.rows`, `blocksPerLevel`) is carried as an
`EngineConfig` field *on* `GameState`. The eight-deep undo ring is a separate
`UndoRing` value held alongside a state, not a field on it. SPEC 4.1's field list
matches neither exactly.

**Why config goes in:** a seed only reproduces a run if the numbers it was played
under travel with it. Daily Challenge, replay and "attach a seed to the bug
report" all break silently the day a remote value moves, and they break in the
worst way — the run replays, it just replays *differently*.

**Why the ring stays out:** the ring holds whole `GameState`s. Nesting it would
make serialising one state carry eight boards, and SPEC 11 puts the in-progress
run in `AppData`, which is rewritten on every drop.

## 2026-09-09 — The level is stored, not derived from `blocksDropped`

**Decision:** `GameState.level` is incremented when a drop crosses a
`blocksPerLevel` boundary, rather than computed as `blocksDropped / 20 + 1`.

**Why:** SPEC 18.10's rewarded continue drops the level by one. A derived level
snaps straight back on the very next drop, so the reward the player paid an ad
for lasts less than a second. SPEC 4.1 also lists level as something the state
*holds*.

## 2026-09-09 — No accounts: the identity stack is removed, not disabled

**Decision:** `:libraries:identity` (+ impl), the Supabase auth screens in
`:features:onboarding:impl`, the session-expired recovery route, the user-scoped
sync/reset machinery, and the server's `/v1/me` + player-report + moderation
surface are all deleted rather than left dormant. `AuthGate` keeps its seam in
`:libraries:core` with a new `AlwaysReadyAuthGate` default binding in
`:libraries:networking`, next to `NoOpAuthTokenProvider` and for the same
boundary reason.

**Alternatives:** leave identity in place and never call it. **Why delete:**
dormant auth still runs at boot — `GuestAccountCreator` and `GuestSessionHealer`
fire on the launch path and would make doomed Supabase calls on every cold start,
muddying logs and telemetry for the whole project. Every screen built on top
would also have to decide whether to consult a session that can never exist. And
`SPEC.md` 20 calls dead auth code a liability at App Store review. Drop 2048's
only backend surface is public remote config.

**Cost accepted:** progress is device-local and cannot survive a reinstall.

## 2026-09-09 — The user-scoped machinery goes; `ClearableDao` stays

**Decision:** `UserScopedClearer` / `UserScopedWorkStopper` / `UserScopedDataReset`,
`UserScopedDaoCleaner`, `UserScopedSyncer` / `UserScopedSyncCoordinator` /
`UserScopedWorkRegistry`, `TelemetryUserBinder`, `AppData.resetAccountScoped()`
and `AppEvent.UserChanged` are all deleted. `ClearableDao` and the
`ProvideExampleUserDataDao` multibinding pattern stay.

**Why:** every one of those types is defined in terms of a *departing user id*,
which cannot exist here — keeping them would mean an API that lies about what the
app is. `ClearableDao` is the exception because the mechanism is genuinely
useful without the concept: Settings' "reset progress" (C11) injects
`Set<ClearableDao>` and gets every table for free. Renaming the rest into
account-free equivalents was rejected as speculative work for a chunk that
doesn't exist yet.

**Consequence:** nothing consumes `Set<ClearableDao>` today. That is recorded in
its KDoc so C11 finds it.

## 2026-09-09 — Rollout bucketing keys on install id, not user id

**Decision:** `AppConfigSource.read` drops its `UserId?` parameter and the
targeting engine buckets rollouts (and evaluates allow/deny lists) on
`ClientContext.installId`.

**Why:** the engine already fell back to `installId` when no user was resolved,
so this deletes a branch rather than adding one. Staged rollouts and A/B tests on
spawn rates and ad frequency — the main reason the config server exists (`SPEC.md`
10) — work fine keyed on a stable per-install id, and there is no user id to key
on.

**Cost:** a reinstall re-buckets that device. Acceptable for tuning; it would not
be acceptable for a billing experiment.

## 2026-09-09 — User-facing copy goes through `:libraries:resources` from day one

**Decision:** the `VerifyStrings` detekt rule gets honored rather than baselined
for new screens. `OnboardingScreen` is the worked example: strings live in
`libraries/resources/src/commonMain/composeResources/values/strings.xml` and
resolve through `stringResource(Res.string.…)`.

**Why:** the template ships the rule active but baselines every screen that
predates it, so nothing actually followed it. Drop 2048 will have a lot of copy
(HUD, tutorial coach marks, achievements, paywall, settings, legal), and
retrofitting string extraction across twenty screens costs far more than writing
the first one correctly. The baseline keeps the template's leftover screens
(28 entries, down from 53); each gets converted as real game UI replaces it.

## 2026-06-21 — Server mirrors client conventions

**Decision:** `:apps:server` reuses the client's stack — kotlin-inject + anvil DI
(`ServerScope`/`ServerComponent`), the `domain/` interface + `data/` impl split,
conventional commits, the version catalog. It's a plain JVM `application` module
(no convention plugin; those are KMP-only).

**Why:** one mental model across client and server. An agent (or human) moving
between them doesn't re-learn DI, error handling, or module layout. The cost —
the server can't use the KMP `:libraries:core` (`Catching`, logging) because that
module has no JVM target — was accepted; the server keeps a couple of small local
equivalents rather than forcing a `jvm()` target onto every client library.

## 2026-06-21 — Graceful degradation over required config

**Decision:** `DATABASE_URL`, `SENTRY_DSN`, and the OTLP endpoint are all
optional. With none set, the server boots and serves `/_health` + `/v1/example`;
DB-backed routes simply aren't mounted, Sentry no-ops, and OpenTelemetry exports
to stdout.

**Alternatives:** require `DATABASE_URL` like the Cards origin
(fail-fast). **Why optional:** this is a template — "clone and run, see it boot"
beats a fail-fast error on first run. The fail-fast discipline still applies per
field via `Env.require` when a future field genuinely can't be defaulted.

## 2026-06-21 — Auth is JWKS verification, never a shared secret

**Superseded 2026-09-09** — the server has no auth at all now. Kept for the
reasoning, which still applies if auth ever returns.

**Decision:** the server verifies Supabase JWTs against the project's public keys
(JWKS / ES256). The `JwtVerification` sealed seam has `Jwks` (prod) and `Static`
(tests mint HS256 tokens against a known verifier).

**Why:** no Supabase secret ever lives on the server, and auth — the highest-risk
surface — is fully testable offline (route tests + `FullStackMeTest` run the real
validate/challenge path with no network).

## 2026-06-21 — `NoOpAuthTokenProvider` lives in the `:networking` api module

**Decision:** the default no-op `AuthTokenProvider` binding sits in
`:libraries:networking` (api), not `:impl`.

**Why:** the module-boundary rule forbids one `:impl` depending on another, but
an auth library's `:impl` must reference `NoOpAuthTokenProvider` to override it
with `@ContributesBinding(replaces = [NoOpAuthTokenProvider::class])`. Putting the
default binding next to the interface it defaults keeps the replacement
boundary-clean. (See also the `enforceModuleBoundaries` self-edge fix in
`build-logic`.)

## 2026-06-21 — `serverOnly` build slimming

**Decision:** `-Ddrop2048.serverOnly=true` makes `settings.gradle.kts` include
only `:apps:server`, so a Docker image build needs no Android/iOS toolchain.

**Why:** this is a KMP monorepo; without slimming, a server image build would
configure every client module and need the Android SDK + Kotlin/Native. The
server has no client-library deps today, so the gate is a pure settings change;
if it gains one, add an always-included `include(...)` + a Dockerfile `COPY`.

## 2026-06-21 — Flyway SQL is the schema source of truth

**Decision:** migrations under `resources/db/migration` define the schema; the
Exposed `Tables.kt` objects are read-side projections kept honest by
`DatabaseSchemaTest`. Repositories treat a unique-violation (SQLSTATE `23505`) as
the arbiter rather than pre-checking for races.

**Why:** one procedure for schema change (add the next `V##__name.sql`, never edit
an applied one), and idempotency that's correct under concurrency.

## 2026-09-09 — Ports considered and rejected

**Decision:** three things a downstream app offered back are deliberately not in
this template. Recorded so they don't get re-proposed each time someone reads
that app's setup and notices the gap.

- **Macrobenchmark `FrameTimingMetric` in CI.** The right tool for catching jank
  regressions, and it needs a real device. Emulator frame timing on a shared CI
  runner varies more run-to-run than the regressions worth catching, so any
  threshold produces flaky red and gets disabled within a month. Only worth it
  for a project with a device farm. Real-user frame timing (`app.jank`) covers
  the same question continuously and is in the template instead.
- **The Grafana dashboards themselves.** The queries encode one app's event
  names. The *conventions* are portable and already carried; the boards are not.
- **The observability routine and its skills.** Genuinely useful, and shaped
  entirely around one project's dashboards, alert ids and inbox. Revisit only if
  a second app wants the same thing — that is the point at which the generic
  shape becomes visible.

**Why here rather than the port queue:** the queue is work waiting to happen, and
these are closed questions. Keeping them there made an empty queue impossible.

## 2026-09-09 — The three specials are one colour set, not five

**Decision:** `Wildcard`, `Bomb` and `Stone` (SPEC 5.2) get one `BlockStyle` each,
shared by all five palettes, rather than a per-palette entry like the eleven
numeric tiers. Each is held to the *neighbour* floor (ΔE 24) against every tier
face in every palette, which is stricter than the floor tiers hold against each
other at distance, and each is marked on the face by drawn geometry rather than
by a glyph.

**Why:** a special is not a rung on the ramp. A ramp asks the player to order
eleven colours, which is a job colour has to do alone; a special asks only "is
this one of them", and the mark answers that before the colour is consulted.
Authoring five Stones would be fifteen more hexes held against fifteen more
floors to express a difference no player can act on.

The mark is geometry because a star or a bomb typed as a character is a bet that
every font on every platform has that codepoint, and losing that bet puts a tofu
box in the middle of the board with no error anywhere.

`Stone` draws **nothing at all** and is the only achromatic block in the game.
Both follow from the same thing: a Stone is defined by the absence of a number,
so giving it a mark would make it look like a special that does something.

**Measured, on the shipped Kotlin:** the closest any of the fifty-five tier faces
comes is Stone at ΔE 26.1 (tritanopia's 2), then Wildcard at 36.0 (default's 128)
and Bomb at 41.8 (deuteranopia's 2). Mark contrast is 4.85:1, 15.79:1 and 4.99:1.
Under the Viénot simulation the tightest pair is Wildcard against deuteranopia's
8 at 21.5, which is under the 24 the ramps hold and above the 20 floor the test
sets for specials — see `BlockPaletteTest.theSpecialsSurviveEachDeficiency`.
