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

Nothing is built. The repo is the unmodified KMP template: `:features:home`,
`:features:onboarding`, Supabase identity, a Ktor + Postgres server, and a Compose-for-web admin
console. See `BUILD-PLAN.md` for the order of operations.

## 1. Pitch

A block falls into a narrow grid. You steer it. When it lands on a matching number the two
combine and double. Chains trigger chains. Build to 2048 and the row detonates. Blocks fall
faster as you go. Fill the grid and you're stacked out.

Tetris skeleton, 2048 brain.

## 2. v1 scope

**In.** Endless mode on one board. Every merge, cascade and burst rule. All three specials. Next
preview and hold. The rising spawn floor. Tutorial. Stats. Daily Challenge. Achievements.
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

**5 columns x 8 rows.** Row 0 is the top.

The mockups draw 5 x 7. That is the discrepancy to settle in C3 against a real device: 8 rows
gives one more row of reaction time in the danger state, 7 rows draws bigger blocks and reads
better on a small phone. **Default to 8** and make `board.rows` a remote-config key so it can be
changed without a release. Whichever wins, it is fixed for v1: the high score table is not
comparable across dimensions.

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

`GameState` is a `@Serializable` immutable data class holding the grid, the falling block, the
next queue, the hold slot, score, level, blocks dropped, and the RNG state. The RNG is a
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

### 4.3 Merge rules

Unchanged from the original spec, and they are not negotiable once shipped.

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

### 4.4 The balance harness

`tools/balance` (JVM only, ships nothing, build-time only, same shape as Sodogku's
`tools/level-generator`).

It plays runs headless with a set of scripted policies and prints distributions. The policies are
deliberately dumb, because the question is not "can a perfect player win", it is "does the board
clog":

- **Random**: uniform column, always hard drop. The floor. If this reaches level 6 the game is
  too easy.
- **Greedy**: takes the immediate merge if one exists, else the emptiest column.
- **Lookahead-1**: greedy, but uses the next-block preview.

It reports, per policy, over 10,000 runs: median and p90 level reached, distribution of highest
tier at run end, cause of death, cascade depth histogram, and the **clutter metric** (count of
blocks on the board with no matching partner anywhere, sampled every 10 drops). Section 19 of the
original spec names clutter as the early warning for a mistuned spawn floor. Here it is measured
before a single frame is rendered.

The harness is how the spawn table in 5.3 gets its real numbers. The values below are the
starting guess it is pointed at.

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

**This whole table is a remote-config key** (see 10). It will be wrong on launch day and the fix
should not need a store release.

### 5.4 Next and hold

- **Next preview shows two blocks.** Non-optional. Without it the game reads as random rather
  than skillful, and the lookahead-1 policy in the harness is the proof.
- **Hold slot.** Stash the falling block; the next block spawns immediately. If the slot is
  occupied the two swap. **One swap per drop**, so it cannot be used to stall. Legal on the very
  first drop, where holding an empty slot just pulls the next block.
- Hold can be disabled in Settings for a purist board.

### 5.5 Level and speed

Level increases every **20 blocks dropped**. No cap. Blocks dropped is the clock, not score:
score scales superlinearly with skill and would punish good players with runaway speed.

| Level | ms per row |
|---|---|
| 1 | 700 |
| 2-4 | 620, 550, 490 |
| 5-8 | 430, 380, 340, 300 |
| 9-12 | 270, 245, 220, 200 |
| 13-16 | 185, 170, 158, 148 |
| 17-20 | 140, 133, 127, 122 |
| 21+ | 118, minus 2 per level, floor 90 |

Soft drop is a flat 40ms per row at every level. **The floor is load-bearing.** Past roughly
level 20 speed stops being the pressure and the rising spawn floor carries the difficulty.
Without the floor this stops being a puzzle and becomes a reflex test, which is a different and
worse game.

Three layered pressures: time (L1-8), space (L9-18), obstacles (L19+, where Stones appear and
the burst becomes the only way to survive).

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
tracks your finger's column. Swipe down or tap to hard-drop.

**Tap Column** is v2 unless it is cheap once Drag exists.

Universal, all schemes:

- **Ghost outline** showing the exact landing cell. On by default, toggleable.
- **Hard drop** places the block in the lowest empty cell of its column instantly, small bonus.
- **Soft drop** accelerates to 40ms per row without placing.
- **Lock delay** of 150ms at the resting cell before locking, allowing a last-instant column
  change. **One reset per drop**, so it cannot stall.
- **Input during resolution is ignored.** Cascades play out uninterrupted.

Hard drop into a full column locks the block in row 0, resolution runs, and if row 0 is still
occupied the run ends. No special case.

## 7. Scoring

| Event | Points |
|---|---|
| Merge producing V | `V x cascadeStep`, step capped at 10 |
| Hard drop | `2 x rowsSkipped` |
| Row burst | `5000 + 250 x blocksCleared` |
| Bomb detonation | `50 x blocksDestroyed` |
| Level up | `100 x newLevel` |
| Survival | `10 x level` per block dropped |
| Board cleared | `1000`, plus a distinct sound |

Deep chains must dwarf flat play or nobody builds for them. A five-step cascade ending in a 512
is worth 2560 for that step alone, on top of everything under it.

The cascade multiplier **does not reset across a burst**. Merges caused by post-burst gravity keep
counting up.

Hard drop's bonus stays a token. On a 5x8 board the maximum is 14 points against merges worth
hundreds, which is a nudge toward confident play, not a strategy. (Original spec's open question
5, resolved: leave it small.)

## 8. In-run presentation

### 8.1 HUD

The mockups show score and best top-left, level with a progress bar top-right, pause button, and
the board filling the rest. They have **nowhere for the next preview or the hold slot**, and 5.4
says the preview is non-optional. That conflict is C3's first design task.

Recommended resolution: next-two as small chips on the right of the header row where the level
bar sits, level collapses to a number plus a thin bar underneath, hold slot as a single chip left
of them, pause moves to the far right. No powerup tray in v1, which buys back the space.

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

Effects: spawn (subtle), move per column (very short click), hard drop (thud), lock (soft), merge
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
| `daily_result` | Date (UTC), seed, score, attempts used, completed. Drives the streak. |
| `achievement_fact` / `achievement_unlock` | Ported from Sodogku unchanged. |

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

## 13. Onboarding

First launch drops straight into a scripted run. No menus, no video, no wall of text. The timer
is frozen throughout.

1. One 2 spawns, one 2 is already placed. "Drag to move." They merge it.
2. Drops 2-4 introduce the next preview and hard drop. They build to 16.
3. Drop 5 is pre-seeded so a single placement triggers a 3-step cascade. Zero explanation. Let
   them watch it.
4. Drop 6 is pre-seeded with a 1024 next to two 512s. They trigger a 2048 burst on their sixth
   ever drop. Full spectacle.
5. Timer unfreezes. "Now for real." Endless begins at level 1.

Skippable from drop 3. Replayable from Settings. Hold, powerups and specials each get one
contextual tooltip the first time they become relevant, not during the tutorial.

Because the engine is seeded and pure, the tutorial is a list of forced `GameState`s and asserted
inputs, which makes it testable rather than a pile of UI flags.

## 14. Daily Challenge

One seed, identical worldwide, rotating at 00:00 UTC. One attempt per day, a rewarded ad buys one
retry, Pro gets two free. Scored on final score, global and friends leaderboards, reset daily.
Streak counter with rewards at 3, 7, 14 and 30 days.

Powerups are disabled here. With none shipping in v1 that costs nothing now and is the correct
rule when they arrive: same seed, same tools, pure skill.

Cheap to build relative to what it buys, because the engine is already seeded. It ships in v1 for
that reason.

## 15. Meta

**Stats.** Runs played, best score, average, highest tier, total merges, total blocks placed,
longest cascade, most bursts in a run, lifetime bursts, total playtime, Daily streak current and
best. All derived from `run_record`.

**Achievements**, around 24, ported from Sodogku's engine. First merge, reach 64, first burst,
two bursts in a run, 5-step cascade, 10-step cascade, clear the board, reach level 20, 500 blocks
in one run, burst a row with three Stones, Wildcard into a 2048, survive 10 drops in the danger
state, 7-day and 30-day streaks, plus tiered score and playtime milestones.

The coin payouts attached to achievements in the original spec are cut with the economy. They
unlock, they post to the platform, they do not pay.

**Leaderboards.** Game Center and Play Games, plus an in-app view. All-time high score, weekly
high score, Daily Challenge. Sodogku shipped a Game Center implementation and then had zero
production call sites for `submit` for a while; do not repeat that. The submit call site is part
of the same chunk as the integration, and a test asserts it fires on run end.

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

**The two numbers watched weekly:** median level reached, and the distribution of highest tier at
run end. Nobody reaching 1024 means too hard. Most runs ending above 2048 means too easy.

Every event goes in `docs/practices/app-events.md` in the same change that adds it.

## 18. Edge cases

Decided now, because every one of them will come up.

1. **Spawn into an occupied cell.** Impossible if the stacked-out check is correct. If it happens,
   treat as game over and log it as an error.
2. **Wildcard beside a Wildcard.** They do not merge. Both stay inert.
3. **Wildcard beside a Stone.** Stone is not eligible. Skip it in the priority order.
4. **Bomb with no neighbors.** Destroys only itself, no score. Slightly disappointing, and fine.
5. **Burst clears a Stone.** Yes. Without this, Stone-heavy late game is unwinnable.
6. **Two 2048s in one cascade step.** Both rows burst, both bonuses award. Same row, it bursts
   once and counts once.
7. **Undo after a burst.** Full snapshot restore, so it is already correct.
8. **Undo on the run-ending drop.** Refused.
9. **Backgrounded mid-cascade.** Snapshot, complete on resume before accepting input.
10. **Continue taken.** Clear the top three rows, drop level by one, reset the drop timer to the
    start of its interval, preserve score.
11. **Hold on the first drop.** Legal.
12. **Hard drop into a full column.** Locks in row 0, resolution runs, stacked out if row 0 is
    still occupied.
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
5. **Hard drop bonus.** Stays a token. See 7.

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
