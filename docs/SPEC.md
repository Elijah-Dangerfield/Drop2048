# Drop 2048

Working title of the design doc was *Cascade*. The app is **Drop 2048**; the engine module is
`:libraries:cascade`, because the template already owns the name `drop2048`.

This document is the design source of truth. It starts from `cascade-game-spec.md` v1.0 and
differs from it in three ways: it resolves the open questions, it cuts v1 down to a shippable
loop, and it says how the thing is actually built on this template. Where a number appears here
and in the original spec, this one wins.

**Every number is a starting assumption to be tuned against the balance harness (see 4) and then
against real play data. None of them are truths.**

---

## 0. Status

C0, C1, C2 and C3 are done. The game is playable end to end and a run can be lost. The Supabase identity stack is gone; the Ktor + Postgres server
and the Compose-for-web admin console remain, for remote config only (see 10).

`:libraries:cascade` is the engine and is complete and tested, including on Kotlin/Native.
`:libraries:ui` has the five block palettes, the cue pairing, the board geometry and the four HUD
primitives.

`:features:game` is the playable screen: the drop timer, the lock delay, transcript playback, the
buttons scheme, the HUD, the danger state, pause and the stacked-out sheet. Audio is silent — the
cue calls are all in place and the sample bank is C3a. See `BUILD-PLAN.md` for the order and
`ORCHESTRATION.md` for where each chunk stands.

## 1. Pitch

A block falls into a narrow grid. You steer it. When it lands on a matching number the two
combine and double. Chains trigger chains. Build to 2048 and the row detonates. Blocks fall
faster as you go. Fill the grid and you're stacked out.

Tetris skeleton, 2048 brain.

## 2. v1 scope

**In.** Endless mode on one board. Every merge, cascade and burst rule. All three specials. The
rising spawn floor. Tutorial. Stats. Daily Challenge. Achievements.
Platform leaderboards. Full settings including accessibility. Pro IAP. Rewarded continue and
rewarded double-coins-equivalent. Interstitials under the section 12 gating. Debug menu.

**Out of v1, on purpose.**

| Cut | Why |
|---|---|
| Coins as a currency | Coins exist only to buy powerups and cosmetics. Both are cut, so the currency has no sink and would be a ledger with nothing to spend it on. |
| The five purchasable powerups | The largest single chunk of work in the spec, and the loop has to prove itself without them first. Freeze, Hammer, Bomb, Reroll and Undo are all designed against a board state the engine already exposes, so they are additive later, not a rewrite. **Undo ships anyway** as a debug and QA affordance, because the engine gets it for free (see 5.6). |
| Narrow and Wide boards | Each is a separate leaderboard, separate high score, separate balance curve. Triples the tuning surface to answer a question nobody has asked yet. |
| Cosmetic themes | The colorblind palettes ship as accessibility settings, not as purchases. |
| Zen mode | It is a one-line change to the engine (never tick) and a Pro perk. Add it when Pro exists and someone asks. |
| Goal levels | Already v2 in the original spec. |

**The consequence for monetization.** With coins and powerups gone, Pro's value is: no ads, all
palettes, two Daily attempts, two continues instead of one. That is thinner than the original
spec's Pro and it needs to be priced honestly. $2.99, not $3.99, until Zen and the powerup
economy land.

## 3. Board

**5 columns x 8 rows.** Row 0 is the top. **Settled in C3 on device; fixed for v1.**

The mockups drew 5 x 7. Both were built and played on an iPhone 16e. The case for 7 was that it
draws bigger blocks; it does not. At five columns the cell size is set by the **width** of the
phone, not its height, so 7 rows drew a cell about 3% larger and turned the rest of the height
into gutter above and below the stack. Seven costs a full row of reaction time in the danger
state and buys nothing.

`board.rows` stays a remote-config key so the question can be reopened with real data. The high
score table is not comparable across dimensions, so it does not move mid-version.

### 3.1 Row zero

Row 0 is a normal cell during play. Blocks pass through it and may rest there while a cascade
resolves.

**Stacked out:** after a resolution phase completes fully (every merge, every burst, all
gravity), if any cell in row 0 is occupied, the run ends. Not during. The distinction matters
and is the single most common way this genre gets it wrong.

Row 1 becoming occupied after a resolution triggers the danger state (see 8.3). One full row of
warning.

### 3.2 Coordinates

`(col, row)`, `(0, 0)` top-left, row index increases downward, gravity pulls toward increasing
row.

## 4. The engine

This is the whole game and it is the part that has to be right before anything is drawn.

### 4.1 Everything is a pure function of state and a seed

`:libraries:cascade` has **zero dependencies**, no coroutines, no clock, no logging, no Compose.
It is a value-in, value-out state machine:

```
GameState  x  Input  ->  Transition(newState, transcript, events)
```

`GameState` is a `@Serializable` immutable data class holding the grid, the falling block, score,
level, blocks dropped, draws made, and the RNG state. There is no next queue and no hold slot;
D11 cut both, and with them the reason to draw a block before it spawns. The RNG is a
splitmix64 carried *inside* the state, not injected. A `GameState` plus a sequence of `Input`s
determines the entire run, byte for byte, on every platform.

Six things fall out of that one decision, and they are why it is made:

- **Daily Challenge** is a seed. No pregenerated content pipeline, no server.
- **Undo** is `states[n - 1]`. No inverse operations, no restoring a burst by hand.
- **Save and resume mid-run** is serializing one object.
- **The balance harness** (4.4) can play a hundred thousand runs headless in seconds.
- **The debug menu's "replay last run with the same seed"** is free.
- **A bug report** can attach a seed and an input log, and the bug reproduces exactly.

The drop timer, animation, audio and haptics all live *outside* the engine, in the ViewModel and
the UI. The engine has no idea time exists; it advances one tick when told to.

### 4.2 The transcript

`RESOLVE` does not return a board. It returns a **transcript**: an ordered list of resolution
steps, each one a merge, a burst, a bomb detonation or a gravity settle, tagged with its cascade
step number, the cells involved, and the points awarded.

The UI animates the transcript. The engine is already done by the time the first block moves on
screen. This is what makes "input during resolution is ignored" free rather than a special case,
makes the cascade-step audio pitch (see 9) a trivial `transcript.map { it.step }`, and makes the
whole of section 6's algorithm testable without a renderer.

**The transcript carries more step kinds than the four named above**: survival, level up and
board cleared are steps too, tagged with cascade step 0. That is deliberate.
It buys the invariant `next.score == previous.score + transcript.points` after every transition,
which is asserted over a whole run. The alternative was a second scoring channel that the floating
numbers on screen could drift away from. Anything that awards points goes down this one channel.

### 4.3 Merge rules

The priority order and the initiation rule are unchanged from the original spec. The merge
*position* rule was changed once, in the ruling recorded below, because the original contradicted
itself. **All of it is now fixed and not negotiable once shipped.**

**Priority order**, first match only, never up:

1. Down
2. Last input direction this drop (skipped if the player never moved it)
3. Left
4. Right

**Only a block that just moved or was just created by a merge may initiate.** Two blocks sitting
untouched never spontaneously combine. This is the rule that keeps the board legible: the player
is the cause of every change.

Resolution loop, per cascade step: merge phase (queue ordered bottom-to-top, then left-to-right),
then burst phase, then gravity, then the queue becomes the newly merged plus everything gravity
moved. Blocks that gravity moved use priority Down, Left, Right; there is no "last input
direction" for them. Terminate when the queue is empty. **Hard cap of 100 steps**, and hitting it
is an error the engine reports, not a silent break.

**New block position: the partner's cell.** One rule, both orientations.

The original spec had two rules ("the lower cell if vertical, the initiating block's cell if
horizontal") and then contradicted itself in its own worked example, which put the new 8 in the
left cell — the partner's. Only the worked example produces the cascade the design was pitched
on.

They unify. A vertical merge's "lower cell" *is* the partner's cell, because the initiator falls
onto the partner from above. Stating it as the partner's cell in both orientations is one rule
instead of two, matches both worked examples, and makes a horizontal merge pull the result toward
the match, which is what makes chains happen. Section 21 says chains are what the game is for.

Ruled 2026-09-09. This is a merge rule, so it is now fixed.

**Worked example.** A 4 lands between two 4s, with an 8 already sitting below the left one. `v` is
the block the player dropped.

```
        v
.  4    4    4   .          .  4    4    4   .          .  .    8    4   .
.  8    S    S   .          .  8    S    S   .          .  8    S    S   .
   before                      landed                      step 1: merged left
```

The landing 4's priority order is Down (a Stone, not eligible), then Left. The left 4 is the
partner, so the new 8 lands in **its** cell, directly above the existing 8.

```
.  .    8    4   .          .  .    .    4   .
.  8    S    S   .          .  16   S    S   .
   step 1                      step 2: cascades down
```

Step 2: the 8 that step 1 created is in the queue, its Down is the 8 below, and they make a 16 in
the lower cell. The right 4 is never touched — first match only. Two merges, and the chain the
design is pitched on.

Move the pre-existing 8 under the *landing* cell instead and the same drop produces one merge and
stops, because the new 8 goes to the partner's cell and is then diagonal to it. Both arrangements
are pinned as tests (`PriorityOrderTest`) so this rule cannot change quietly.

### 4.4 The balance harness

`tools/balance` (JVM only, ships nothing, build-time only, same shape as Sodogku's
`tools/level-generator`).

It plays runs headless with a set of scripted policies and prints distributions. The policies are
deliberately dumb, because the question is not "can a perfect player win", it is "does the board
clog":

- **Random**: uniform column. The floor. If this reaches level 6 the game is
  too easy.
- **Greedy**: takes the immediate merge if one exists, else the emptiest column.
- **Lookahead-1**: greedy, but plans one block ahead. **Since D11 this policy cheats**: the
  block it looks at is not shown to the player and is not even drawn until the current one lands.
  It is kept as the ceiling — SPEC 4.4 asks whether the board clogs against something better than
  anyone will play — and must not be quoted as a prediction of play.

It reports, per policy, over 10,000 runs: median and p90 level reached, the distribution of
**highest tier reached**, cause of death, cascade depth histogram, and the **clutter metric**
(count of number blocks on the board with no matching partner anywhere, sampled every 10 drops).
Section 19 of the original spec names clutter as the early warning for a mistuned spawn floor.
Here it is measured before a single frame is rendered.

**"Highest tier" always means reached during the run, never on the board at the end.** A 2048
bursts its own row, so it is never at rest when a run ends; the literal reading reports 0% for the
game's defining moment. Every place that asks this question uses the same reading: the harness
here, `run_record.highest_tier` (11), "BIGGEST" on the stacked-out sheet (8.4), and the weekly
distribution (17).

**Clutter counts number blocks only.** Stones and inert Wildcards are excluded on purpose. Clutter
exists to warn that the *spawn floor* is mistuned, and Stones arrive from the special rate rather
than the spawn table, so folding them in blurs two causes into one number that cannot diagnose
either. If board congestion needs measuring, that is a second count of permanent obstructions, not
a change to this one.

The harness and the live telemetry in 17 must compute clutter from **the same function in
`:libraries:cascade`**. The entire value of the metric is that offline and live numbers are
directly comparable.

It used to count the drops whose block exceeded the cap 5.3B would impose at *landing* time, which
was 0.02% of Greedy's drops while the cap was read two drops early. D11 moved the draw to spawn
time, so that count can now only ever be zero and the counter was removed with it. A metric that
cannot report anything but zero is worse than no metric.

The harness is how the spawn table in 5.3 gets its real numbers. **It has been run and the table
below is the one it measured** — see `BUILD-PLAN.md`'s C1a outcome for the full distributions and
the three alternatives that were rejected.

**The clock is optional and both halves are kept.** Clock-free, every policy places a block in the
column it wants at every level, and everything it reports is a ceiling for an unhurried player.
C1c added a `PlayerProfile` — a decision time and a tap rate — and a simulation of 5.5's drop
timer and 6's controls, so a policy that cannot reach its preferred column in the time it has
takes the best one it can reach. Running both is what says how big the ceiling was: three levels
of median for Greedy and Lookahead-1, and a third off the 2048 rate. Numbers in `BUILD-PLAN.md`
C1c.

**D11 moved less than expected, and not where expected.** C1e re-ran everything. Outcome
distributions are unchanged clocked and clock-free, to within about half a percentage point — and
that residual is the spawn draw moving to spawn time, not the drop control, because a drop control
decides *when* a block locks and never where. What moved is the wall clock: a level-1 drop is 0.96s
for a player who taps ▼ against 4.15s for one who never does and 0.54s under the hard drop that no
longer exists.

The clocked half also reports, per level, the share of drops the lock delay placed rather than a
drop input, the share where the policy did not get the column it asked for, and the **slack** —
how long the policy sat with nothing to do while the block kept falling. The last of those is the
only one that is not near zero in the opening, and it is what 5.5's early band was retuned on.

**Every clocked number is conditional on `decisionMillis`, and that constant is unmeasured.** Swept
from 150ms to 500ms it moves Greedy's median level by five and its 1024 rate by a factor of three —
more than the clock itself and more than the spawn table. Until 17's telemetry answers it, no
clocked median should be quoted more precisely than "about twenty".

## 5. Blocks

### 5.1 Values

Powers of two, 2 through 2048. 2048 is terminal: it cannot exist at rest because creating it
bursts its row. 4096 is unreachable by design.

Every tier has a distinct color **and** the number printed on the face, always. Color is never
the only signal.

### 5.2 Specials

Special blocks arrive as the falling block, replacing a value block. They cost nothing and are
not powerups.

| Block | Behavior | From level | Rate |
|---|---|---|---|
| **Wildcard** | On landing, merges with the first neighbor found via the priority order and takes that neighbor's value doubled. Value blocks only: it skips Stones and other Wildcards. With no eligible neighbor it rests inert. **Wildcard merges are symmetric**: a value block landing beside a resting Wildcard merges with it too. | 5 | 3% |
| **Bomb** | Does not merge. Destroys itself and its four orthogonal neighbors, then gravity and normal cascade resolution continue. | 8 | 3% |
| **Stone** | No value, cannot merge, cannot be destroyed except by a Bomb or a row burst. | 12 | 5% |

Suppressed for the first three drops of a run, and never two specials back to back.

A Wildcard next to a 1024 makes a 2048 and bursts the row. That is intentional and it is the
moment people screenshot.

### 5.3 Spawn values

Two constraints stack, and together they are the most important numbers in the game.

**A: the level table.** Weighted draw.

| Level band | 2 | 4 | 8 | 16 | 32 | 64 |
|---|---|---|---|---|---|---|
| 1-3 | 65% | 35% | | | | |
| 4-6 | 35% | 45% | 20% | | | |
| 7-9 | 10% | 45% | 35% | 10% | | |
| 10-12 | | 30% | 40% | 25% | 5% | |
| 13-15 | | 15% | 35% | 35% | 15% | |
| 16-18 | | | 25% | 35% | 30% | 10% |
| 19+ | | | 15% | 30% | 35% | 20% |

**B: the board-aware cap.** The spawned value may not exceed `max(4, highestOnBoard / 16)`.
Clamp down to the highest allowed tier when the draw exceeds it.

A is what makes 2048 reachable inside one run instead of a thousand-merge grind. B is the safety
valve that stops a 64 landing on a board of 2s.

**The cap is evaluated when the block spawns**, against the board it will land on. The board only
changes at a lock, so those are the same board and the rule is exact.

It used to be evaluated two drops early, at the moment a block entered the next-block preview: a
preview that can still change is a lie, so the value had to be fixed before it could be shown.
That was the *only* reason, and D11 removed the preview. C1a measured the staleness it cost at
0.02-0.04% of drops (L20), so this simplification is expected to change nothing observable; what
it removes is a caveat, not a bug.

**Measured, not guessed.** Every number here predates the 4.3 merge-position ruling, so C1a
re-measured it against the shipped engine over 10,000 runs per policy and kept it. Greedy reaches
a median level of 22 with 34% of runs passing 1024 and 3.8% bursting; a softer ramp costs almost
the entire tail past 1024 and buys no survival, because the spawn table sets the tier ceiling and
the board geometry sets the level. Numbers in `BUILD-PLAN.md` C1a.

Those are the clock-free numbers and they are ceilings. With C1c's clock in the loop the same
table gives Greedy a median level of **19** with 26% of runs passing 1024 and 2.2% bursting, which
is still inside both of 17's failure modes. The table was re-read against the clocked numbers and
kept again.

**This whole table is a remote-config key** (see 10). It will be wrong on launch day and the fix
should not need a store release.

### 5.4 Next and hold — struck (D11)

The next-block preview and the hold slot are **cut**. The design handoff, which governs
interaction, says both "were tried and cut. Don't reintroduce them."

This section said the two-block preview was non-optional. It was the load-bearing claim behind
three other things, and all three moved with it: the spawn draw now happens at spawn time (5.3),
the HUD has no chips (8.1), and the engine's `Input` alphabet lost `Hold` and gained `Nudge` (6).
Section 18.11's "hold on the first drop" edge case went with it.

**D21 took `Nudge` back out again.** The preview and the hold stay cut; what returned is hard
drop, which is the same `Input.Lock` the engine has always had. See 6.

### 5.5 Level and speed

Level increases every **20 blocks dropped**. No cap. Blocks dropped is the clock, not score:
score scales superlinearly with skill and would punish good players with runaway speed.

| Level | ms per row |
|---|---|
| 1 | 500 |
| 2-4 | 470, 440, 410 |
| 5-8 | 380, 350, 325, 300 |
| 9-12 | 270, 245, 220, 200 |
| 13-16 | 185, 170, 158, 148 |
| 17-20 | 140, 133, 127, 122 |
| 21+ | 118, minus 2 per level, floor 90 |

**The floor is load-bearing.** Past roughly
level 20 speed stops being the pressure and the rising spawn floor carries the difficulty.
Without the floor this stops being a puzzle and becomes a reflex test, which is a different and
worse game.

**Levels 1-8 were re-cut in C1c and the rest of the curve is untouched.** They were
`700, 620, 550, 490, 430, 380, 340, 300`. C3 played the shipped screen and reported the opening as
slack; the clocked harness in 4.4 then measured it, and the diagnosis and the fix came apart:

- The timer never took a block off the player in the opening. Over 200,000 level-1 drops, the
  share placed against the policy's choice was **0.00%**, at every level of the first sixty drops
  and for every policy.
- The experience behind the complaint is real and is **dead time**, not pressure. Having decided
  where the block goes, the policy then waited **4.8s** at level 1, 3.9s at level 2 and 3.3s at
  level 3 for it to arrive. A 500ms opening cuts that to 3.5 / 3.0 / 2.7s.
- Everything else was **identical to the digit** across the old curve, a 600ms one and this one:
  median and p90 level, drops, the whole tier distribution, cause of death, cascade depth and
  clutter. The early curve is a pacing dial, not a difficulty dial.

So this change is a feel change made on a measurement that says its risk is zero, and it is not
claimed to make the game harder. **The lever that would is `blocksPerLevel`**, and it was measured
and left alone: 20 to 15 took a hard-dropping player to level 4 in 26s instead of 34s, but it
shortens runs by 11%, thins the tail past 1024, and — unlike the curve — it feeds level
advancement, so it moves the pinned determinism digest and every score with it.

**Three layered pressures, as amended.** Space (L1-18) and obstacles (L19+, where Stones appear
and the burst becomes the only way to survive). Time is not one of them: on five columns with a
centre spawn, a block is at most two columns from anywhere, which the measured player covers in
370ms against a budget that never falls below about 780ms even at the speed floor. That is why
6's drop control, not the curve, sets how fast a run actually goes.

**The curve was kept at 500ms through C1e and through D21, and the pacing column is hard drop's.**
A level-1 drop takes **0.54s** for a player who uses ▼ against **4.15s** for one who never does,
and time to level 4 is **33s** against **223s**. C1e measured the intermediate nudge at 0.96s and
88% of the gap; D21 returns the game to the fast end of that bracket, and no balance pass was
re-run for it because a drop control moves the wall clock and nothing else (L40).

Two things follow. The curve matters *less*, not more, because ▼ rather than gravity carries the
block for anyone who uses it. And a faster opening was measured
(`400, 385, 370, 355, 340, 325, 312, 300`) and rejected — it changes no outcome column and flattens
the first band's acceleration from 40% to 25%. What is left of the opening's dead time belongs to a
player who never presses ▼, which is a tutorial problem (13), not a curve problem. Full tables in
`BUILD-PLAN.md` C1e.

### 5.6 Undo

The engine keeps a bounded ring of the last 8 pre-drop `GameState`s. In v1 undo is **not a player
feature**; it is the debug menu's step-back and the crash-recovery path. When the powerup economy
lands, the Undo powerup is a call to an API that already exists and is already tested.

Undo is a full snapshot restore, so a burst is restored correctly by construction. Undo on the
drop that ended the run is refused: Continue exists for that.

## 6. Controls

**Buttons ship first** and are the default, matching the mockups: fixed left, right and drop
controls along the bottom, with a left-handed mirror option. They are the easiest scheme to drive
from an automated test, which matters while the loop is still being tuned.

**Drag** lands in the same phase once the loop is proven, and becomes the default at that point
if it feels better on device. Touch anywhere on the board, slide horizontally, the falling block
tracks your finger's column. The handoff makes it absolute from the grab point rather than
incremental. A downward flick is the hard drop.

**Tap Column** is v2 unless it is cheap once Drag exists.

Universal, all schemes:

- **Ghost outline** showing the exact landing cell. On by default, toggleable.
- **Hard drop (▼, or a downward flick)** sends the block to the bottom of its column and locks it
  there, in one press. It pays `2 x rowsSkipped` (see 7). **D21**, from playing it: the expectation
  at that control is "send this tile down", D11's two-tick nudge was not that, and the hold that
  produced soft drop was where the bug was.
- **There is no soft drop.** It was a hold of ▼, and holding is a mode. D21 deleted it along with
  `EngineConfig.nudgeRows` and `speed.softDropMsPerRow`. One input, one meaning.
- **Lock delay** of 150ms at the resting cell before locking, allowing a last-instant column
  change. **One reset per drop**, so it cannot stall.
- **Input during resolution is ignored.** Cascades play out uninterrupted. **Amended in C3:** no
  input reaches the engine mid-cascade, but the *last sideways move* is held and applied to the
  block that spawns afterwards. Discarding it turned out to be a different rule and a bad one —
  on device, every move tapped in the beat after a block landed vanished and the new block went
  straight down the middle. The drop is deliberately not buffered: replaying it would commit a
  block to a board the player has not looked at yet.

A block locking in a full column lands in row 0, resolution runs, and if row 0 is still occupied
the run ends. No special case.

**System back does nothing while a block is falling or a cascade is playing.** Added in C3a on an
owner ruling: the game screen is the launch destination, so back closed the app mid-drop. It does
not pause either — a gesture made by accident should not stop the clock and raise a menu. Back on
the pause overlay closes it. The start overlay and the stacked-out sheet are not the board and are
left to the system.

## 7. Scoring

| Event | Points |
|---|---|
| Hard drop | `2 x rowsSkipped`, zero when the block was already resting |
| Merge producing V | `V x cascadeStep`, step capped at 10 |
| Row burst | `5000 + 250 x blocksCleared` |
| Bomb detonation | `50 x blocksDestroyed` |
| Level up | `100 x newLevel` |
| Survival | `10 x level` per block dropped |
| Board cleared | `1000`, plus a distinct sound |

Deep chains must dwarf flat play or nobody builds for them. A five-step cascade ending in a 512
is worth 2560 for that step alone, on top of everything under it.

The cascade multiplier **does not reset across a burst**. Merges caused by post-burst gravity keep
counting up.

**Exactly one row pays for an input, and it is the hard drop.** D11 struck the bonus when it cut
hard drop, and D13 declined to hand it to the ▼ nudge; **D21 reinstated both**, because the bonus
was always paying for *commitment* and a hard drop is the commitment it was paying for. A nudge
could be pressed again a moment later, so per-row would have rewarded the tap; a hard drop is one
press per block, so it rewards the decision.

It cannot become a strategy. On a 5x8 board the ceiling is seven skipped rows, so a drop is worth
at most **14 points** against merges worth hundreds and a burst worth 5,000 — a whole level of
maximal hard drops is worth less than one chained merge. And a lock the player did not hurry skips
no rows and emits no step at all, so the bonus can never pay for the lock delay expiring.

Every other row of this table pays for something that happened on the board, and
`noTouchOfTheBoardScoresAnything` pins it. (Original spec's open question 5 is resolved.)

**Survival on a drop that levels up pays the level the block was dropped at**, before advancement.
The drop was survived under the old level's speed, so that is the level it earned. The level-up
bonus for the new level is awarded on the same drop and is where the new level shows up.

**Bomb detonation counts neighbours only, not the bomb itself.** That is what makes 18.4's "a bomb
with no neighbours scores nothing" fall out of `50 x blocksDestroyed` instead of needing a special
case. Read the two together or they look like they disagree.

## 8. In-run presentation

### 8.1 HUD

The mockups show score and best top-left, level with a progress bar top-right, pause button, and
the board filling the rest. The conflict this section used to describe — nowhere to put the next
preview or the hold slot — **is gone rather than solved**: D11 cut both chips. The two rows stay:

- **Row one:** SCORE left, abbreviated and counting up; BEST right, smaller and secondary.
- **Row two:** `LEVEL n` with its thin bar taking the free width, then pause at the far right.

Two rows rather than one, which is the one place this departs from the recommendation. Score,
best and level are three numbers of unpredictable width; on one row a preview chip is pushed
sideways by a score rolling from 9,999 to 10,240, so the preview moves while the player is
reading it. The second row costs about 40dp of a screen where the board turned out to be
width-bound anyway (see 3), so it is height that had nothing else to do.

No powerup tray in v1, which is what buys the space.

Nothing else on screen. No banner ad. No timer bar. No clutter.

### 8.2 Cascade feedback

`CHAIN x2` floats over the board at the merge that caused it, per the mockups, scaling with the
step number. This is the visual half of the thing section 22 says to keep; 9 is the audio half.

### 8.3 Danger state

Triggered when any cell in row 1 is occupied after a resolution. Row 0 gains a pulsing red
border, the background desaturates slightly, music shifts to the tense variant, one light haptic
on entry. Cleared automatically when row 1 empties.

### 8.4 Pause and stacked out

Pause halts the drop timer and **blurs the board**, so the layout cannot be studied while paused,
exactly as the mockup draws it. Options: Resume, Restart, Settings, How to Play, Quit. Auto-pauses
on backgrounding, calls and notification interaction. A cascade in progress when the app
backgrounds is snapshotted and completes on resume before input is accepted.

Game over reads **"Stacked out"**, with score, biggest tier, and a single primary button that
says **"Drop again"**. Continue and any ad offer sit below it, never above.

## 9. Audio and haptics

**The merge sound is pitched up per cascade step.** This is the most important audio decision in
the game. A six-step cascade should be an ascending musical run that is a reward on its own. It
is one of the two things section 22 says to keep if everything else is cut.

Effects: spawn (subtle), move per column (very short click), hard drop (the press, heavier than a
move), lock
(soft), merge
(pitched by step), merge into 256+ (heavier, distinct), row burst (the longest sample in the
game), bomb, danger enter/exit, stacked out, level up, UI tap and back.

Music: one track, three intensity layers keyed to level bands, plus a filtered variant for the
danger state, ducking under the burst.

Haptics: light on move and lock, medium on merge, heavy on 256+ merges, bombs and level ups, a
sustained pattern on the burst, a sharp double on stacked out. All respect the Off / Light /
Strong setting and the OS setting.

**These five intensities need a platform haptics abstraction, not Compose's.** Compose
Multiplatform exposes two `HapticFeedbackType` values; Sodogku collapses four game events onto
them and it is the ceiling it hit. Drop 2048 needs expect/actual over Android `VibrationEffect`
composition and iOS `UIImpactFeedbackGenerator` plus Core Haptics for the sustained burst pattern.
The burst haptic is half of what section 21 says makes this game feel good, so it is not a corner
to cut.

Sound and haptic ship as **one `Cue`**, never as two calls that can drift apart. `Cue.Merge` at
cascade step 4 is a single call that knows its pitch offset and its buzz.

The burst is the release valve for the whole game. Do not undersell it.

**The playback path is built and the samples are not.** C3a put a real engine behind `SoundPlayer`
on both platforms — Android `SoundPool`, iOS pooled `AVAudioPlayer` — pitched per cascade step by
playback rate, wired to the effects switch, and covered by tests that pin which cue fires at which
transcript step. What is missing is a sample bank, which is the owner's and is the only remaining
step: one `.ogg` per `Sound`, named after its `key`, in a folder called `audio` —
`libraries/ui/src/androidMain/assets/audio/` on Android and the app bundle on iOS. A sample that is
absent is logged by name at launch and leaves that one effect silent; nothing else changes. See
`SoundBank`'s KDoc and `decisions.md`.

The music track and its intensity layers are not built either, and are not a sample bank.

## 10. Remote config

The server and admin console stay for exactly this reason. The rule from Sodogku applies: **the
binary must be fully playable with the server unreachable**, so every key has a compiled-in
fallback and remote values only ever override.

Keys for v1:

| Key | Why it is remote |
|---|---|
| `spawn.table` | The single highest-leverage number in the game and it will be wrong at launch. |
| `spawn.cap.divisor` | The safety valve's aggressiveness. |
| `speed.curve` and `speed.floorMs` | Where the game stops being a puzzle. |
| `special.rates` and `special.firstLevel` | Per type. |
| `board.rows` | Settles the 7-vs-8 question post-launch with data. |
| `level.blocksPerLevel` | The clock. |
| `ads.interstitial.*` | Every gate input in 12.3: session run count, cooldown, days-since-install suppression. |
| `ads.enabled`, `ads.rewarded.caps` | Kill switch and per-placement caps. |
| `pro.price.tier`, `pro.upsell.enabled` | |
| `feature.dailyChallenge`, `feature.leaderboards` | Kill switches for anything with a server or platform dependency. |

**Never remote:** the merge priority order, the resolution algorithm, the burst rule, scoring
formulas. Changing those mid-flight silently invalidates every high score on the board.

## 11. Persistence

Room, in `:libraries:storage:impl` which owns the `AppDatabase`, with DAOs contributed from the
owning library per the template's rules.

| Table | Holds |
|---|---|
| `run_record` | One row per completed run: score, level, blocks placed, duration, highest tier, cause of death, longest cascade, bursts, mode, seed. This table is the stats page and the analytics backup. |
| `daily_result` | Date (UTC) as the primary key, seed, score, attempts used, completed, retries used. Drives the streak. The one table in the app that is updated in place, because a row is the running state of a day rather than a finished fact — see `decisions.md`. |
| `achievement_fact` / `achievement_unlock` | One row per finished run, and one per badge already announced. The engine shape is Sodogku's; the columns are this game's. The facts overlap `run_record` on purpose and are not a join onto it — a badge's evidence must not disappear with a row it never owned. |

In-progress run lives in `AppData` (the template's `AppCache`) as a serialized `GameState` plus
the transcript-in-flight flag, not in Room. It is one value, it is overwritten constantly, and it
is worthless once the run ends.

Best scores are derived from `run_record`, not stored separately. One source of truth.

## 12. Monetization

**Pro, one-time IAP.** Removes all interstitial advertising permanently, unlocks every palette,
two Daily attempts instead of one, two continues per run instead of one. $2.99 at v1 scope (see
2). Rewarded video stays available to Pro holders as an opt-in, because removing it is taking
something away.

Upsell surfaces: a small persistent entry in Settings, and one non-modal card on the stacked-out
screen at most once per session. Never a full-screen popup on launch.

**Rewarded video**, all player-initiated:

| Placement | Reward | Cap |
|---|---|---|
| Continue after stacked out | Clears the top three rows, drops level by one, resets the drop timer to the start of its interval, preserves score. | 1 free per run, a 2nd at higher friction, hard cap 2 |
| Daily Challenge retry | One extra attempt | 1 per day |

Double-coins and free-powerup placements are cut with the economy.

**Interstitials.** Every one of these must hold: stacked-out screen only and only after the
player dismisses the results; never during a live run, not once; not before the 4th run of a
session; 180s minimum since the last one; never within 45s of a rewarded ad in either direction;
suppressed entirely for the first 3 days after install; preloaded, and skipped silently if not
ready rather than showing a spinner.

**Banners: none.** The board is tall and narrow, vertical space is what makes the danger row
readable, and a banner that shifts layout mid-run reads as the game cheating. If one is ever
added against this advice, its space is reserved from app launch so nothing moves.

**The governing principle:** the player never sees an ad they did not choose while a run is
alive. Everything else here is negotiable. That is not.

**Built in C10.** Six rulings the section did not make, all in `decisions.md`. The load-bearing
ones: there is now **one** `ProEntitlement` (`Entitlements` in `:libraries:billing`) because the
two that existed could never agree and the debug grant silently failed to reach the Daily;
interstitials are their own type with one caller rather than a third placement, so the governing
principle has no expressible call site to violate; a **Daily run is never offered a continue**,
because a cleared board is a larger change than any config key and D18 pins the config to keep two
players' runs comparable; and SPEC 12's "2nd at higher friction" is implemented as *not offered* —
the sheet carries it and the player has to reach for it.

Every gate in 12.3 is a branch of one pure function (`InterstitialPolicy`), with a test per rule
against a positive baseline that does show. The 8-second countdown is a ViewModel rule rather than
an animation, which is also what lets the offer be screenshot-tested at all.

**iOS serves no ads yet.** The Android network is real (AdMob + UMP); iOS falls through to a
binding that answers `NotShown` and **cannot** report a reward. Sodogku shipped an iOS stub that
paid out for free and every log read it as a watched ad; this one is honest, and the rewarded
call sites pay the player anyway because that is a deliberate rule applied to a real outcome.
`OWNER-TODO.md` carries what iOS needs.

## 13. Onboarding

First launch drops straight into a scripted run. No menus, no video, no wall of text. The timer
is frozen throughout.

**The frozen timer is what teaches ▼, and that is the whole design.** With gravity switched off, ▼
is the only thing that moves a block downward, so the player cannot reach the end of six drops
without pressing it and never once watches a block come down on its own. C1c measured whether a
player reaches for the drop control at 223 seconds against 33 to reach level 4 (L29) — a bigger
lever on the opening than the drop clock, the spawn table and every speed-curve change combined. A
tutorial that *mentions* ▼ teaches a fact; one that cannot be finished without it teaches a habit,
and the habit is what the number is about. Built in C5.

**Amended by D21: one tap per drop, not several.** ▼ is a hard drop, so drop 1's two ▼ beats
collapsed into one — "press it again" is not something you can ask of a control that finishes the
drop on the first press. The mechanism is untouched and the script is a beat shorter.

1. One 2 spawns, one 2 is already placed. "Slide it over." They merge it — and the same drop
   introduces ▼, because it is the only way to land it.
2. Drops 2-4 are ▼ again, until it is a habit. They build to 16. **Amended in C5**: this step used
   to introduce "the Next preview and hard drop", and D11 cut both. D21 put hard drop back, so the
   slot now teaches exactly what SPEC 13 originally wanted it to.
3. Drop 5 is pre-seeded so a single placement triggers a 3-step cascade. Zero explanation. Let
   them watch it.
4. Drop 6 is pre-seeded with a 1024 next to two 512s. They trigger a 2048 burst on their sixth
   ever drop. Full spectacle.
5. Timer unfreezes. "Now for real." Endless begins at level 1, at score zero. The scripted run is
   never recorded and never written to the saved-run store.

Skippable from drop 3. Replayable from `GameRoute(replayTutorial = true)`, which is Settings'
entry point when C11 builds Settings. Powerups and specials each get one contextual tooltip the
first time they become relevant, not during the tutorial. Hold is struck with D11.

Because the engine is seeded and pure, the tutorial is a list of forced `GameState`s and asserted
inputs, which makes it testable rather than a pile of UI flags. Each scripted drop also carries
the **columns the block may occupy**, chosen so that every allowed placement resolves to the same
board: without that, drop 5's showpiece cascade is one mis-steer away from never happening.

There is no separate onboarding screen. C5 deleted `:features:onboarding` rather than renaming it
— the tutorial is this screen with the clock off, so a welcome screen with a Play button in front
of it was a second front door to one room.

## 14. Daily Challenge

One seed, identical worldwide, rotating at 00:00 UTC. One attempt per day, a rewarded ad buys one
retry, Pro gets two free. Scored on final score, global and friends leaderboards, reset daily.
Streak counter with rewards at 3, 7, 14 and 30 days.

Powerups are disabled here. With none shipping in v1 that costs nothing now and is the correct
rule when they arrive: same seed, same tools, pure skill.

Cheap to build relative to what it buys, because the engine is already seeded. It ships in v1 for
that reason.

**Built in C6.** Five rulings the section did not make, each of them forced once the thing was
real. All five are in `decisions.md` with their alternatives.

**The day is UTC everywhere.** The seed, the row, the attempt allowance and the streak all key on
the same UTC date. The player's own zone is used for one thing — rendering the countdown to the
next board — because the fairness question the boundary raises is answered by making the boundary
visible, not by adding a second clock.

**A Daily run pins `EngineConfig.Default` and never reads remote config.** This is the single most
consequential call in the mode and it overrides 10 for this one path. Section 10's keys are all
remote; C7 ruled a fetched config is sampled at the start of a run and never during one, which is
enough to keep a single run coherent and is *not* enough to make two players' runs comparable.
Comparability wins. The consequence is that from the first recorded Daily score,
`EngineConfig.Default` joins `PINNED_DIGEST` and `level.blocksPerLevel` on the list of things that
cannot move without invalidating scores.

**The attempt is spent when the run starts**, not when it ends, or force-quitting a bad board is a
free reroll. Restart is refused for the same reason.

**The day's score is the best of its attempts.** "Scored on final score" says the score a run ends
on, not which of two attempts counts.

**One column beyond 11's list.** `daily_result` also stores `retriesUsed`, because a rewarded-retry
cap that is not on disk is a cap a force-quit resets.

## 15. Meta

**Stats.** Runs played, best score, average, highest tier, total merges, total blocks placed,
longest cascade, most bursts in a run, lifetime bursts, total playtime — all derived from
`run_record`. Daily streak current and best are derived from `daily_result`, in their own section
on the page rather than folded into Lifetime, because the two are folds over different tables and
sitting them together would imply a relationship neither has. C6 added the row C4 deliberately
left out.

**Achievements**, exactly 24, on the engine shape ported from Sodogku. First merge, reach 64,
first burst, two bursts in a run, 5-step cascade, 10-step cascade, clear the board, reach level 20,
500 blocks in one run, burst a row with three Stones, Wildcard into a 2048, survive 10 drops in the
danger state, 7-day and 30-day streaks, plus five score and five playtime milestones.

The coin payouts attached to achievements in the original spec are cut with the economy. They
unlock, they post to the platform, they do not pay.

**The five score targets are derived, not written down.** Each is the score a run is guaranteed to
have banked by the time it reaches a given level — survival plus level-up bonuses out of 7's
table — so they move with the coefficients instead of going stale behind them. C9 ruled it in
`decisions.md`; Sodogku shipped the typed version and stranded three badges behind a rescale.

**Every badge is checked against the engine.** `AchievementReachabilityTest` either measures what
the shipped engine produces over forty fixed-seed greedy runs or poses a board and locks a block
into it. A badge nobody can earn is worse than no badge, because it looks exactly like a working
one. Two of them were only provable the second way: a 10-step cascade turns up four times in
three-quarters of a million drops, and a row holding three Stones is not a board a random run
builds.

**Leaderboards.** Game Center and Play Games, plus the platform's own dashboard as the in-app view.
All-time high score, weekly high score, Daily Challenge. Sodogku shipped a Game Center
implementation and then had zero production call sites for `submit` for a while; do not repeat
that. The submit call site is part of the same chunk as the integration, and a test asserts it
fires on run end.

**Which board depends on the mode, and it is not a preference.** An Endless score goes to the
all-time and weekly boards; a Daily score goes to the Daily board and nowhere near the other two
(D19). Nothing below the ViewModel knows what mode a value came from, so the call site is the only
place that rule can live.

**Play Games is not in v1.** Android binds an inert seam, which is enough for the whole feature to
be silent there, and the board ids do not exist in either console yet.

## 16. Accessibility

Not an afterthought and not a settings-screen checkbox exercise.

- Numbers are always on block faces. Color never carries meaning alone.
- Deuteranopia, protanopia and tritanopia palettes, designed rather than filtered.
- High contrast palette.
- All five palettes (default plus four) are **original work**. Sodogku ships one palette and a
  glyph watermark, so there is nothing to port. Each is authored against a property test that
  pins derived ink contrast, a minimum ΔE between adjacent tiers, and a luminance span, because
  11 tiers across 5 palettes is 55 chances to ship two colours nobody can tell apart.
- Larger block numbers toggle.
- **Reduce motion** disables screen shake, cuts particle density, and shortens cascade animation.
  The engine is unaffected: the transcript still plays, it plays faster. Nothing about the game
  changes, only its rendering.
- Screen reader labels on every menu element, and a spoken description of the falling block, its
  column, and its landing cell.
- Minimum touch targets on every control. The three bottom buttons are the primary interaction
  surface and should be generous.

## 17. Telemetry

Minimum set to actually tune the thing.

- **Run lifecycle.** `run_start` (mode, level), `run_end` (score, level, blocks placed, duration,
  highest tier, cause of death, cascades by depth, bursts, seed).
- **Per-drop sampling.** Every 10th drop: level, tick interval, board fill percentage, highest
  tier, and the **clutter count**. That last one is the early warning that the spawn floor is
  mistuned, and it is the same metric the balance harness prints, so offline and live numbers are
  directly comparable.
- **Ads.** Offered, started, completed, dismissed, failed, per placement.
- **Monetization.** Upsell shown, upsell tapped, purchase started, completed, restore attempted.
- **Funnel.** Tutorial step reached, completed, skipped; first run completed; day 1 / 3 / 7
  return.

**The two numbers watched weekly:** median level reached, and the distribution of highest tier
**reached** (see 4.4 — a 2048 bursts, so "at run end" would report zero of them). Nobody reaching
1024 means too hard. Most runs reaching 2048 means too easy.

Every event goes in `docs/practices/app-events.md` in the same change that adds it.

## 18. Edge cases

Decided now, because every one of them will come up.

1. **Spawn into an occupied cell.** Impossible if the stacked-out check is correct. If it happens,
   treat as game over and log it as an error.
2. **Wildcard beside a Wildcard.** They do not merge. Both stay inert.
3. **Wildcard beside a Stone.** Stone is not eligible. Skip it in the priority order.
4. **Bomb with no neighbors.** Destroys only itself, no score. Slightly disappointing, and fine.
   Section 7's `50 x blocksDestroyed` only agrees with that if the bomb's own cell is excluded from
   the count, so it is.
5. **Burst clears a Stone.** Yes. Without this, Stone-heavy late game is unwinnable.
6. **Two 2048s in one cascade step.** Both rows burst, both bonuses award. Same row, it bursts
   once and counts once.
7. **Undo after a burst.** Full snapshot restore, so it is already correct.
8. **Undo on the run-ending drop.** Refused.
9. **Backgrounded mid-cascade.** Snapshot, complete on resume before accepting input.
10. **Continue taken.** Clear the top three rows, drop level by one, reset the drop timer to the
    start of its interval, preserve score.
11. **The first input of a run.** Nothing is queued, drawn or cached ahead of the falling block,
    so there is no first-drop special case. (Was "hold on the first drop", struck with D11.)
12. **A lock in a full column.** Lands in row 0, resolution runs, stacked out if row 0 is still
    occupied.
13. **Board completely emptied.** Bonus, distinct sound, keep going.
14. **Infinite cascade.** Total block count strictly decreases per merge so it must terminate.
    Hard cap at 100 steps anyway, and report it as an error if hit.

## 19. Resolved open questions

The original spec's section 21, answered.

1. **Spawn floor: level-driven, with the board-aware cap as the only board input.** Board-driven
   floors are more elegant and self-correcting, and much harder to make feel fair or to reason
   about when a player says the game cheated. The cap in 5.3 already gives most of the benefit.
   Revisit only if the harness shows clutter that the cap does not absorb.
2. **A burst clears its row only.** Row-plus-neighbours is more spectacular and makes late game
   forgiving enough to blunt the difficulty curve the whole design is built around. It also
   muddies a rule players have to hold in their head. The burst chains through gravity anyway,
   which is where the spectacle actually comes from.
3. **Terminal value on Wide.** Moot: Wide is cut. Revisit with the variant.
4. **Stones removable by adjacent merges.** No. If late game proves unwinnable, lower
   `special.rates.stone` remotely first. Changing the rule is the last resort, not the first.
5. **Hard drop bonus.** Resolved: it stands at `2 x rowsSkipped`. D11 removed it with hard drop and
   D13 declined to give it to the nudge; D21 brought back the input and the bonus together, on the
   argument the bonus was always making — it pays for commitment. See 7.

## 20. Non-goals for v1

No accounts, no cloud save, no social features beyond platform leaderboards, no replays, no
tournaments, no seasonal events, no friend challenges, no user-generated boards, no web build.

The Supabase identity library is deleted rather than left dormant. Dead auth code in a game with
no accounts is a liability at App Store review and a permanent source of confusion.

## 21. The one thing

If everything else here is cut, keep the **rising spawn floor** and the **cascade-step audio
pitch**.

The first is what makes 2048 reachable inside a single run instead of a thousand-merge grind. The
second is what makes a long chain feel good enough that people build for it on purpose. Everything
else is packaging around those two.
