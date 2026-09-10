# Agent TODO

The queue the orchestrating session works from: things found mid-chunk that do not belong in the
chunk that found them. Ideas, deferred fixes, cleanups, things to add or remove.

Not a changelog. **Delete entries as they land** rather than ticking them off, so this stays a
queue.

Human-only items go in `OWNER-TODO.md` instead.

---

## Now

### ▼ becomes a hard drop, soft drop is deleted (D21) — DO THIS FIRST

Owner ruling from play. `Input.Lock` already locks at the landing cell, so the engine side is
nearly free. Retire `Input.Nudge`, `EngineConfig.nudgeRows`, `GameAction.SoftDropStart/End`,
`softDropOnHold` and the `softDropping` field. Re-pin the determinism digest by **running the
engine**, not by pasting the assertion's actual (L17).

Also update: the tutorial teaches ▼ as "drop it" rather than "nudge it" (L49's mechanism survives —
with the clock frozen, ▼ is still the only way to make progress, and now it takes one tap per
drop); SPEC 6, 7 and 13; `docs/reference/design-handoff-deltas.md`, since the packet's control
scheme is now overridden on this point too.


### The first Room-backed test in the repo

`bestScore()`'s `WHERE mode = 'ENDLESS'` filter has **no direct coverage**, and there is no
Room-backed test anywhere in this project on any platform. `FakeRunRecordDao` mirrors the filter and
the fold is tested properly, so the Kotlin is covered and the SQL is not.

Needs an in-memory Room harness. Worth a chunk, not a line — and it would cover every DAO, not just
this one.

### `BarChart` draws one full-width bar at n=1

Looks wrong on the stats page after a player's first run, which is exactly when they look at it.
Cosmetic, in `:libraries:ui`.

### Tutorial drops 2-4 do not force a steer

A player who only taps ▼ drops everything down the spawn column, so the scripted merges never
happen and the boards read as arbitrary. It still teaches ▼, which is the point (L49), but the
lesson looks broken. Either force the steer or build those boards around the spawn column.

### `GameUiState.best` still absorbs the live score

`maxOf(best, score)`, so the header reads "best 736" during a run at 736 (L44). C3c fixed it where
it actively lied (the tutorial's score briefly became the player's best) and left the rest, because
changing it touches the score counter's animation.


### Decide whether the ▼ nudge pays any score

SPEC 7's hard drop bonus (`2 x rowsSkipped`) has nothing left to fire on once D11 lands. The bonus
existed to reward confident play; a two-tick nudge is a weaker claim to that, and paying nothing is
defensible.

Whichever way it goes, **scoring lives in the engine, so this moves the determinism digest** — fold
it into C1d rather than doing it separately.

### Move the board-aware cap to landing time

D11 removes the preview, which is the *only* reason SPEC 5.3 evaluates the cap at draw time ("a
preview that can still change is a lie"). With no preview it can read the real board at landing.

Simplifies the rule and removes the stale-data caveat L20 measured. Fold into C1d; it moves the
digest too, so it should ride along with the other engine changes rather than moving it twice.

### `FuseReach` could come up ~10%

The special marks render correctly at real cell size — the star reads, the bomb fuse is legible
but is the smallest of the three marks. One constant in `BlockFace.kt`.

### C5: teach hard drop as load-bearing, not incidental

Measured in C1c: time to reach level 4 is **34 seconds** for a hard-dropping player and **289
seconds** for a patient one, on the same curve. The drop control is worth more than every
speed-curve change combined (L29).

SPEC 13's drops 2-4 already introduce hard drop. C5 should treat it as the tutorial's most
important job rather than one of three things it mentions.

### Scan for other tests that advance a full tick and assert on the falling block

`GameScenario.tick()` advances one drop interval, which is not a safe unit while soft-dropping — at
40ms per row a full tick is twelve rows, so the block lands, locks and resolves. C1c found one such
test that passed at 700ms by luck and NPE'd at 500ms (L31). There may be others.

### A second metric for permanent obstructions

D8 deferred this. It matters now: Stones start arriving at level 12 and clocked runs regularly
reach 19-22, so board congestion is real and `clutter` deliberately does not see it. Not a change
to `clutter` — a second count.

### Confirm `Cue.Move` does not machine-gun

It fires on every column step, including when the buffered move replays. A fast left-left-left
could stutter. Needs real audio to judge, so it belongs with C3a.

### A screenshot-test harness

Everything C2b built holds still under `LocalInspectionMode` **precisely so a capture would work**,
and no capture harness exists.

**Two chunks have now asked for this, and C3 quantified it:** a harness would have caught two of
its three device bugs (the ghost that read as a Stone, the non-modal pause scrim) without a
90-second build-and-launch cycle. That is the argument, not the coverage.

### Widen `AnimatedStateReadInComposition` to raw `Animatable.value` reads

The rule only catches `by animateFloatAsState`. A raw `Animatable.value` read during composition is
the same bug with the same 60fps consequence and is not covered. C3's `BoardView` does this
correctly (reads inside `graphicsLayer`), but the board is now the most animation-dense screen in
the app and nothing enforces it.

### `AppData.bestScore` must be deleted by C4

C3 added it to get a best score on the stacked-out sheet. SPEC 11 says best score is derived from
`run_record` and stored nowhere else. **Two numbers that can disagree about the same run is exactly
the bug that wording guards against.** C3 marked it for deletion in its own KDoc.

### Wire the real settings into `AppThemeProvider`

C2 widened the signature and `apps/compose/App.kt` still calls it on defaults, so palette choice,
reduce motion, large numbers and haptics are **inert end to end**. The plumbing exists and nothing
feeds it. Belongs to C11 (Settings), but it means no accessibility setting actually works until
then — worth knowing before someone tests one and reports a bug.

### Sweep the 28 `VerifyStrings` baseline entries

Audited in C5 (correcting an earlier claim of mine): **none of them are onboarding.** They break
down as `HomeScreen` 4, the colour catalog 9, `BugReportScreen` + `FeedbackScreen` 6, plus
error / dialog / splash strays. All template leftovers.

Probably a C11 job, since Settings is the chunk that touches most of those screens anyway.

### Regrow the scenario-harness pattern demo in `:features:game` (C3)

C0 deleted `HomeScenario` / `HomeScenarioTest` along with the profile they demonstrated.
`docs/practices/testing.md` still names the scenario harness as a pattern, and now points at
`OnboardingViewModelTest` for the ViewModel recipe with **no live example of the harness itself**.
C3 is the natural place to regrow it, since the game screen is the one that most needs it.

### Call `deleteAll()` on `Set<ClearableDao>` from Settings (C11)

C4 wired the multibinding and validated it — two DAOs are in the set, and `AppComponent` exposes a
read accessor, checked against the generated kotlin-inject code. All C11 has left is the "reset
progress" call site.

### The saved run blob carries a full `EngineConfig` copy, ~3KB

Correct for determinism: a seed only replays if the numbers it was played under travel with it
(D5). But when C7 makes the config remote, the saved run should probably carry a config **version**
rather than a copy. Not urgent; noting it before it becomes a surprise.

### C6 needs a streak seam on the stats page

`RunRecord.mode` already carries `DAILY`, but the stats screen has no streak row and the fold has no
streak field. SPEC 15 lists it. C4 deliberately omitted it rather than drawing a zero, because a
zero for an absent feature reads as a broken stat.

### Decide whether `SessionRejectionBus` and `AccessDenied` earn their keep

Both survived C0 because they are structurally welded to `NetworkClientImpl`'s 401 path and
`App.kt`'s routing, and removing them meant surgery for no gain. But **nothing can trigger either
one today** — the server's ban gate went with the auth stack. Revisit once the server surface is
final in C7. Keep only if something can actually produce the envelope.

## Soon

### Measure hold, which no policy has ever used

SPEC 5.4's stash is unmeasured, clocked or unclocked. Every balance number in the project assumes
a player who never holds. It is the last unmodelled mechanic.

### Measure how often the board-aware cap actually clamps a draw

C1a measured the *staleness* of SPEC 5.3's constraint B and found it a non-issue (L20), but not how
often it fires at all. It is almost certainly inert once a 1024 is on the board, since the ceiling
then exceeds the table's maximum of 64, making it an early-game valve only. Needs a small hook in
`Spawn` exposing the pre-cap value on `Draw`.

Worth knowing before C7 makes `spawn.cap.divisor` a remote key nobody can reason about.

### Add `--csv` output to the balance harness

The text report is right for a one-off read and wrong for trends. Worth doing at the second
measurement, not the first.

### `:tools:balance:test` is not in the standard verification gate

It runs under `check` but not under `testDebugUnitTest`, so the harness's own six guard tests do
not run in the normal loop. Either add it to the working agreement or accept it is CI-only.

### Stop the convention plugin injecting coroutines into every KMP module

`configureKotlinMultiplatform` puts `kotlinx-coroutines-core` in every KMP module's `commonMain`,
so SPEC 4.1's "zero dependencies" is true of `:libraries:cascade`'s build file but **not of its
compile classpath**. C1 left it alone rather than editing shared build-logic while another agent
had Gradle running, which was the right call.

The file already has a `path == ":libraries:core"` special case to mirror. Worth doing before the
next pure module, so "zero dependencies" means what it says.

### Give blocks a stable identity across a transcript

The transcript gives from/to cells, which is enough for C3 to animate. A stable block id would
make "this specific tile travelled through the cascade" trivial instead of inferred. **Cheap now,
invasive later** — the transcript step types are young and nothing consumes them yet.

### Acknowledge cascade's test-only serialization dependency

`:libraries:cascade` has a test-only `kotlinx-serialization-json` dependency. It is what makes the
byte-level determinism pin and the SPEC 18.9 round-trip possible, so it earns its place. Recorded
so it is a conscious choice rather than a surprise to whoever next audits "zero dependencies".

### Build the real `SoundPlayer` (C3a)

C2 built the seam (`SoundPlayer`, defaulting to `Silent`) and the pitch decision, and no audio
engine exists behind it. Also confirm `Cue.MaxPitchSteps = 12` sounds right on a long cascade —
C2 capped the climb at an octave, which SPEC 9 does not mention and nobody has agreed to.

### `dragAcrossCells` has no test

The gesture cannot be unit tested; `BoardGeometry` underneath it is, thoroughly. Worth a UI test
in C3a when Drag ships as a control scheme.

### Pull the remaining Sodogku design-system pieces as their chunk arrives

The C2 foundations are done. What is left in `ORCHESTRATION.md`'s port table is chunk-gated:
`Focus` and `CoachMark` for C5's tutorial, the streak and daily components for C6, `UnlockToast`
and `ShareButton` for C9.

**Do not port them up front.** An unported component costs nothing; a ported-and-unused one costs
maintenance forever.

### Soft drop is dead during the tutorial

It is a *hold* of ▼, and soft drop is the ticker running faster — and the tutorial's ticker is off
(L49). If C3a's feel pass wants soft drop taught, it needs its own beat after the handoff.

### Check `:libraries:review` is still wanted

The template ships an in-app-review library. SPEC 15 (original spec 15) wants the rate prompt
surfaced only after a run that set a personal best. That is a real trigger and worth keeping, but
confirm the library is wired rather than assuming.

### `docs/PORT-CANDIDATES.md` writeback

`AGENTS.md` says anything learned here that a brand-new app would want goes back to the template
repo's `docs/PORT-CANDIDATES.md`, which lives in `../KMPTemplate` and not here. Collect candidates
as they appear; do not write them back one at a time.

## Later

### Zen mode is nearly free

SPEC 2 cuts it. It is a one-line engine change (never tick) plus a Pro gate. Worth revisiting the
moment Pro exists in C10, because it makes Pro's value proposition much less thin.

### The powerup economy re-entry

All five powerups in the original spec are designed against board state the engine already
exposes, and Undo already ships internally (SPEC 5.6). When the economy comes back, it is
additive. Keep it that way: no engine change should make a powerup harder to add later.

---

## Watch list

Not tasks. Things that have bitten this template before and should be checked when the relevant
code lands.

- **A clean detekt run does not prove a custom rule ran.** The Gradle daemon caches detekt's
  worker classloader, so an edited rule keeps running its previous jar until `./gradlew --stop`.
  Prove dispatch by making a rule report unconditionally, confirm the flood, revert.
- **Skipped tests read as passing.** Docker-dependent tests self-skip. Count them.
- **An event with no production call site looks like coverage.** Sodogku shipped
  `Leaderboards.submit` with zero callers and telemetry events emitted by nothing. Assert call
  sites, not just definitions.
- **`UIApplication.canOpenURL` needs its scheme in `LSApplicationQueriesSchemes`**, or every
  outbound link silently opens nothing with no error.
- **Infinite animations hang preview and screenshot capture.** Anything looping forever returns a
  fixed value under `LocalInspectionMode`.
- **A `published()` helper that copies everything except one field.** `restart()` omitted the phase
  and started a real game underneath the game-over scrim: invisible, untouchable, live for two
  chunks on the most-pressed button in the app (L32). The symptom is not a crash, it is a screen
  that looks like it did nothing.
- **A migration fallback that destroys data.** Reasonable when a server holds a copy,
  unrecoverable here (L33). Anything added to `AppDatabase` from version 6 on must fail loudly
  rather than wipe.
- **A test that advances a full drop tick and then asserts on the falling block.** At 40ms/row a
  full tick is twelve rows, so the block has landed and resolved and the assertion is really about
  the next one. One such test passed at 700ms by luck and crashed at 500ms (L31).
