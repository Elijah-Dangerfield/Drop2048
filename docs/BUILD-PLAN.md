# Drop 2048 build plan

Chunks are ordered by risk, not by visibility. The riskiest logic in this game (merge resolution,
cascade termination, the spawn floor) is pure Kotlin with no UI, so it goes first and gets fully
tested and *balanced* before anything is drawn. By the end of C3 there is a playable game with no
monetization and no network, which is the only point at which "is this actually fun" can be
answered.

Each chunk says what unblocks it, what it delivers, and how we know it is done.

Read `SPEC.md` first. Where this file and the spec disagree, the spec is right and this file is
stale.

---

## Working agreement

Every chunk closes the same way, and none of it is optional.

1. **Verify.** `./gradlew testDebugUnitTest :apps:server:test :apps:compose:assembleDebug
   :apps:compose:compileKotlinIosSimulatorArm64 detekt` all green. Run on a real device whenever
   the chunk changed anything visible. A simulator does not tell you whether a 148ms drop tick
   feels fair.

   **Any chunk touching `:libraries:cascade` also runs `:libraries:cascade:iosSimulatorArm64Test`.**
   `compileKotlinIosSimulatorArm64` compiles and runs zero tests, so the standard command cannot
   tell you whether the determinism digest still holds on Kotlin/Native, and that digest is what
   protects Daily Challenge and every seed-attached bug report.

   Count skipped tests explicitly. With Docker down, exactly one skip is expected
   (`:apps:integration` `HarnessSmokeTest`); anything else skipping is a finding, because a
   skipped test is indistinguishable from a passing one in the summary.
2. **Update the docs in the same change.** `SPEC.md` when behaviour or a number changed, this
   file with the chunk's outcome and anything it discovered, `decisions.md` for any non-obvious
   call, `docs/practices/app-events.md` for any new event.
3. **Record what was measured, not just what was chosen.** Balance numbers especially. The whole
   point of C1a is that nobody re-litigates the spawn table blind.
4. **Say what is not verified.** Skipped tests, untested platforms, environment gaps get written
   down, not glossed.
5. **Commit to `main`** with a Conventional Commits message. The `commit-msg` hook enforces it;
   run `./scripts/install_hooks.sh` if the build complains.

---

## C0 · Template trim — **DONE** (2026-09-09)

**Delivers** a template with nothing in it that this game will never use.

- Delete `:libraries:identity` and `:libraries:identity:impl` entirely.
- Delete the auth half of `:features:onboarding:impl`: `AuthScreens`, `SignIn/SignUp/
  ForgotPassword/VerifyEmail` view models and their tests, `AuthRoutes`, `DisplayNameSuggester`.
  Keep the module and `OnboardingRoute`; C5 rewrites its body as the tutorial.
- Strip auth from `:features:home:impl` (`HomeViewModel` reads `AuthState` today).
- Strip Supabase JWT verification, `MeRoutes` and the profile/moderation repositories and
  migrations from `:apps:server`. Keep app config, the admin console and `/_health`.
- Remove the OAuth redirect handling from `App.kt` and the `drop2048://login-callback` scheme.
- Rewrite `:apps:integration` to drive `RemoteConfigRemoteDataSource` over real TCP against the
  real server on a Testcontainers Postgres. That is the only server surface left and it is the
  one C7 needs.

**Done when** all four Gradle verification tasks are green, the app boots on both simulators to
the template home screen, and there is zero Supabase traffic on the launch path.

**Watch for:** Sodogku did this exact trim and found string resources were being baselined rather
than verified. Check `VerifyStrings` is honored here too.

**Outcome.** All of the above landed. Verification is green:
`./gradlew testDebugUnitTest :apps:server:test :apps:compose:assembleDebug
:apps:compose:compileKotlinIosSimulatorArm64 detekt` — 320 tests, 0 failures, **5 skipped**,
detekt clean, both platforms compile.

Beyond the listed scope, because it was cheapest now:

- The user-scoped machinery (`UserScopedClearer`/`WorkStopper`/`DataReset`, `UserScopedSyncer` +
  coordinator + registry, `TelemetryUserBinder`, `AppData.resetAccountScoped()`,
  `AppEvent.UserChanged`) was deleted too — every one of those types is defined in terms of a
  departing user id. `ClearableDao` survives for C11's "reset progress". See `decisions.md`.
- `SupabaseInfo` and the whole `SUPABASE_*` build-config path in `build-logic` are gone, along
  with the `supabase-auth` and `auth0-*` catalog entries and the server's JWT dependencies.
- The server also lost `SupabaseConfig`, `AccessControlConfig` (`APPEAL_URL`), three dead
  rate-limit buckets, and the `auth.users` stub SQL.
- `VerifyStrings` is honored for new copy. `OnboardingScreen` is the worked example, backed by a
  new `libraries/resources/.../composeResources/values/strings.xml`. The baseline dropped from
  53 to 28 entries (15 stale, 10 replaced).

**Not verified.** Docker was not running, so all 5 skipped tests self-skipped: the rewritten
`:apps:integration` `HarnessSmokeTest` and the server's four Testcontainers Postgres tests
(`PostgresAppConfigSourceTest`, `PostgresAppConfigAdminRepositoryTest`,
`PostgresAppConfigManifestRepositoryTest`, `DatabaseSchemaTest`). Start Docker and re-run
`./gradlew :apps:integration:testDebugUnitTest :apps:server:test` before trusting C7's config
path. The app was also **not launched on either simulator** — Kotlin compiles for both targets
and the Android APK assembles, but nothing has been run.

---

## C1 · `:libraries:cascade` — the engine

**Unblocked by** C0.

**Delivers** pure Kotlin, **zero `implementation` dependencies**, targeting android + ios + jvm.
No coroutines, no clock, no logging, no Compose. See `SPEC.md` 4.

- `Board`, `Cell`, `BlockValue`, `Special`, `GameState` (`@Serializable`, RNG state carried
  inside), `Input`, `Transition`, `Transcript` and its step types.
- Merge priority resolution, the full cascade loop, gravity, row burst, bomb detonation, wildcard
  resolution, the 100-step cap as a reported error.
- Spawn: the level table, the board-aware cap, special rates with the first-three-drops and
  no-back-to-back suppression.
- Scoring folded in, emitted per transcript step rather than computed separately. Score is a
  property of a resolution, not a parallel system, and splitting it into its own module would
  mean two places that both have to know what a cascade step is.
- Level advancement and the speed curve as a lookup the ViewModel reads. The engine stores the
  level; it never sleeps.
- The 8-deep undo ring.

**Done when** the module has no dependencies at all and tests cover:

- Every worked example in `SPEC.md` 4.3 and every case in `SPEC.md` 18, one test each.
- **Determinism**: the same seed and input sequence produces byte-identical `GameState` on JVM,
  Android and iOS. This is the test that protects Daily Challenge and every bug report.
- **Termination**: a property test over thousands of random boards asserting the cap is never
  hit and block count strictly decreases across a merge phase.
- **No spontaneous merges**: a property test asserting that a block which did not move and was
  not created by a merge never initiates one. This is the rule that keeps the board legible and
  it is the easiest one to break with a refactor.
- **Priority order**: the "4 lands between two 4s with an 8 below the left one" case from the
  original spec produces an 8 and an untouched 4, then cascades to a 16 in the lower cell.

**This is the chunk to be slow on.** Everything after it assumes it is right.

**Outcome (2026-09-09).** `:libraries:cascade` landed with the API above. **89
tests, 0 failures, 0 skipped, on all three of JVM, Android and iOS.** Verification:
`./gradlew testDebugUnitTest :apps:compose:assembleDebug detekt` — 340 tests, 0
failures, **1 skipped** (`:apps:integration` `HarnessSmokeTest`, Docker down),
detekt 0 findings over 551 files.

Notes for whoever builds on it:

- **The transcript is the only channel points travel down.**
  `next.score == previous.score + transcript.points` after every transition, and
  a test asserts it over a whole run. That means the transcript carries four step
  kinds beyond the four SPEC 4.2 originally listed (hard drop bonus, survival,
  level up, board cleared). The alternative was a second scoring channel the
  floating numbers could drift from. SPEC 4.2 now says so.
- **Determinism is pinned as a digest, not compared in-process.** A seed plus 600
  scripted inputs is serialized and reduced to an FNV-1a constant asserted in
  `commonTest`, so the same number has to come out on JVM, Android and iOS. It
  does. Changing that constant is a breaking change to every recorded Daily
  Challenge score.
- **`./gradlew testDebugUnitTest` does not run the iOS tests.** The engine's iOS
  coverage comes from `:libraries:cascade:iosSimulatorArm64Test`, which has to be
  run explicitly. It is the only thing that proves the determinism pin holds on
  Kotlin/Native. Run it whenever the engine changes.
- Three spec conflicts surfaced and are written up in `decisions.md`: the
  horizontal merge position vs. the worked example in this file, Wildcard merge
  symmetry, and where `EngineConfig` and the undo ring live. **The first was
  ruled on the same day**: the merged block lands in the partner's cell in both
  orientations, so the worked example above is now literally what the engine
  does, and SPEC 4.3 carries the example itself. Wildcard symmetry was confirmed
  and is now in SPEC 5.2. Re-pinning the determinism digest was part of that
  change.
- The module has no `implementation` dependencies. It has one `api` dependency,
  `kotlinx-serialization-core`, which `@Serializable` requires; and the
  `drop2048.kotlin.multiplatform` convention plugin still injects
  `kotlinx-coroutines-core` into every KMP module's `commonMain`, so "zero
  dependencies" is true of the build file and not yet true of the compile
  classpath. Nothing in the module uses it.
- A `jvm()` target is declared so C1a's `tools/balance` can play the shipped
  engine rather than a copy of it.

---

## C1a · `tools/balance` — the numbers

**Unblocked by** C1.

**Delivers** a JVM-only module that ships nothing, plays runs headless under the Random, Greedy
and Lookahead-1 policies, and prints the distributions in `SPEC.md` 4.4.

**Done when** 10,000 runs per policy complete in under a minute, and the output includes median
and p90 level, highest-tier distribution, cause of death, cascade depth histogram, and the
clutter metric sampled every 10 drops.

**Then use it.** Tune `spawn.table` until Greedy's median level lands somewhere defensible and the
highest-tier distribution has a real tail past 1024 without most runs bursting. Write the
before-and-after numbers into this file. If the answer is that the table in `SPEC.md` 5.3 was
already fine, write that down too, with the numbers that say so.

**Done when, part two:** the shipped spawn table is one the harness measured, not one that was
guessed.

**Outcome (2026-09-09). The table in `SPEC.md` 5.3 was measured and kept unchanged.** It is now a
measured table rather than a guessed one, and three alternatives were measured beside it so the
decision has something to be a decision *between*. `tools/balance:test` — 6 tests, 0 failures, 0
skipped.

All numbers below: 10,000 runs per policy, seeds 1..10000, `EngineConfig.Default` with only
`spawnTable` varied, engine as shipped after the merge-position ruling. Reproduce any row with
`./gradlew :tools:balance:run --args="--runs 10000 --table <name>"`. Timing on an M-series laptop:
random 1.3s, greedy 8.6s, lookahead-1 40s, so each policy is inside the one-minute budget and all
three together are about 50s of compute.

**Baseline — `SPEC.md` 5.3 as written (`--table spec`, identical to `SpawnTable.Default`).**

| Policy | level median / p90 / max | drops median | 256 | 512 | 1024 | 2048 | clutter mean / p90 |
|---|---|---|---|---|---|---|---|
| Random | 4 / 5 / 11 | 63 | 0.07% | — | — | — | 0.93 / 2 |
| Greedy | 22 / 26 / 48 | 421 | 11.8% | 53.8% | 30.5% | 3.8% | 2.42 / 4 |
| Lookahead-1 | 25 / 32 / 69 | 482 | 3.0% | 32.2% | 49.0% | 15.7% | 2.79 / 5 |

Highest tier is the highest reached during the run, not what is left on the board — a 2048 bursts
its row and would otherwise be invisible. Cause of death was `row_zero_occupied` on 30,000 of
30,000 runs; no run hit the 3,000-drop cap, no run faulted the engine, and `SPAWN_BLOCKED` never
fired, which is SPEC 18.1 holding over 13 million drops.

Random's median level is 4 and its p90 is 5, so it does not reach level 6 and SPEC 4.4's
too-easy tripwire is clear. Greedy has a real tail past 1024 (34.3% of runs) and bursts on 3.8%,
which is neither of SPEC 17's two failure modes.

**The three alternatives, Greedy only, same seeds.**

| Table | level median | 256 | 512 | 1024 | 2048 | clutter mean |
|---|---|---|---|---|---|---|
| `spec` (kept) | 22 | 11.8% | 53.8% | 30.5% | 3.8% | 2.42 |
| `slowed` | 21 | 33.8% | 55.8% | 9.3% | 0.34% | 2.34 |
| `lowfloor` | 22 | 39.6% | 52.1% | 7.0% | 0.23% | 2.34 |
| `steep` | 21 | 7.9% | 49.7% | 36.4% | 6.1% | 2.46 |

**The spawn table sets the tier ceiling; the board geometry sets the level.** Median level moves by
at most one across a range of tables that moves the 1024 rate by a factor of five. Softening the
ramp buys nothing in survival and costs almost the whole tail past 1024, which is SPEC 17's
"nobody reaching 1024 means too hard". Steepening it buys a slightly richer tail for a slightly
shorter run and is a defensible alternative rather than an improvement. On that evidence, changing
the table would be churn: it would move the pinned determinism digest, invalidate the SPEC 5.3
numbers everyone has already read, and buy nothing measurable.

**The interesting number: the merge-position ruling made the game harder, and not via chain
frequency.** The pre-ruling engine (horizontal merges landing in the initiator's cell) was
restored locally, measured, and reverted; the engine in the repo is untouched.

| Policy | pre-ruling level median | post | pre 1024 | post | pre 2048 | post |
|---|---|---|---|---|---|---|
| Random | 5 | 4 | 0.01% | 0% | 0% | 0% |
| Greedy | 26 | 22 | 49.5% | 30.5% | 36.7% | 3.8% |
| Lookahead-1 | 32 | 25 | 30.6% | 49.0% | 65.1% | 15.7% |

The tier buckets are exclusive, which is why Lookahead-1's pre-ruling 1024 share is the *lower*
one: two thirds of those runs went past 1024 and are counted in the 2048 column instead.

Cascade depth, Greedy, share of drops at each depth:

| depth | 0 | 1 | 2 | 3 | 4 | 5 | 6+ | mean | >=2 |
|---|---|---|---|---|---|---|---|---|---|
| pre-ruling | 44.5% | 33.7% | 14.1% | 4.9% | 1.8% | 0.75% | 0.17% | 0.88 | 21.8% |
| post-ruling | 43.8% | 35.4% | 13.3% | 5.1% | 1.7% | 0.56% | 0.07% | 0.87 | 20.8% |

Chains are as frequent as they ever were. What changed is that a horizontal merge now moves the
result into the partner's column, and the partner's column is by definition the one that already
held a match, so the board gets less level with each horizontal merge instead of more. On five
columns that is what ends runs. The single pinned-determinism data point in L17 (25 drops, down
from 27) pointed at this and was right.

**The stale board-aware cap is a non-issue.** SPEC 5.3B is evaluated at draw time, two drops
early, so it can only ever be too loose. The harness counts drops whose block exceeds the cap the
board would impose at landing: **0.02% of Greedy's drops, 0.04% of Lookahead-1's**. Under the
pre-ruling engine, where chains were the thing being worried about, it was 0.09% and 0.16%. The
compounding failure mode does not appear at either end.

**What this does not measure.** The harness has no clock, so every policy places its block exactly
where it wants at every level. These are ceilings for an unhurried player, not predictions:
SPEC 5.5's speed curve is untested by anything here, and the real median level will be lower.
Hold is never used by any policy and neither is soft drop. C3 is the first honest read on
difficulty, and the two numbers to compare it against are the Greedy row above.

**Superseded in part by C1c**, which put the clock in the loop and measured the size of the
ceiling: three levels of median off Greedy and Lookahead-1, and about a third off the 2048 rate.
Hold is still never used by any policy.

---

## C2 · Theme and design system

**Unblocked by** C0. Can run in parallel with C1.

**Delivers** the palette from the mockups in `:libraries:ui`: the deep indigo-to-near-black
background, the amber/orange tier ramp, the violet accent on the level bar and controls.

- Tier colors for all 11 values, as a ramp that stays distinguishable at small sizes.
- The three colorblind palettes and the high-contrast palette, designed against the same tier
  ramp rather than filtered from it.
- Block, board, HUD and control-button components.
- A `Feel` token set that pairs each haptic with its sound, so a merge is one call and not two
  that drift apart.
- Catalog entries for all of it, so the palettes can be eyeballed side by side.

**Done when** the catalog renders every tier in every palette, and each palette has been checked
for adjacent-tier distinguishability rather than assumed.

**Make the right thing the easy thing.** If drawing a block the correct way takes more code than
drawing it wrong, the wrong way ends up in the codebase.

---

## C3 · `:features:game` — playable

**Unblocked by** C1, C2.

**Delivers** the loop, on device, with no monetization and no network.

- `:features:game` (route) and `:features:game:impl` (screen, `GameViewModel` on `SEAViewModel`).
- The drop timer lives here, not in the engine. Tick, soft drop, lock delay with its one reset.
- **Transcript playback.** The ViewModel receives a resolved transcript and animates it step by
  step, ignoring input throughout. Get this right and the "input during resolution" rule costs
  nothing.
- Buttons control scheme, left-handed mirror, ghost outline.
- HUD: score, best, level with progress bar, next-two, hold, pause. **Resolve the mockup conflict
  in `SPEC.md` 8.1 first**, on a real device, before building it twice.
- Danger state, pause with board blur, the "Stacked out" sheet with score, biggest tier, and
  "Drop again".
- **Settle 5x7 vs 5x8 here**, on a phone in a hand, and write down which and why.

**Done when** the game is playable end to end on an Android device and an iPhone, a run can be
lost, and `GameViewModelTest` covers: timer ticks advance the block, input during resolution is
dropped, lock delay resets exactly once, and the run ends on the right frame.

**This is the "is it fun" checkpoint.** Play it for an hour before starting C3a. If it is not fun
here, no amount of C4 through C13 fixes it, and this is the cheapest moment to change the design.

**Outcome (2026-09-09).** Everything above landed. `:features:game` + `:features:game:impl` are
wired into the nav graph and are now the app's start destination for a returning player;
onboarding hands off to them too. Verification:
`./gradlew testDebugUnitTest :apps:compose:assembleDebug
:apps:compose:compileKotlinIosSimulatorArm64 detekt` — **377 tests, 0 failures, 1 skipped**
(`:apps:integration` `HarnessSmokeTest`, Docker down, which is the expected count), detekt clean
with both custom rules proven to dispatch, both platforms build.
`:libraries:cascade:iosSimulatorArm64Test` re-run for free: 89 tests on Kotlin/Native, green.

**The two design questions are settled**, both on an iPhone 16e simulator, both written up in
`decisions.md` and folded into `SPEC.md`:

- **5x8.** The board is *width*-bound at five columns, so seven rows drew a cell ~3% larger and
  turned the rest into gutter. Seven costs a row of reaction time and buys nothing.
- **HUD.** SPEC 8.1's recommendation, on two rows rather than one, because on one row the preview
  chips get shoved sideways by the score counter rolling.

**What playing it found**, in order of how much it mattered:

1. **Every input in the beat after a hard drop was eaten.** Three columns' worth of intended play
   ended up in one. SPEC 6 was amended: the last sideways move is buffered and replayed on the
   next block. This is the single change that made the game feel like it was listening.
2. **The ghost read as a Stone.** A translucent grey filled cell is exactly what a Stone looks
   like — the one block with nothing on its face. It is an outline now.
3. **The pause scrim was not modal.** The board and the pause button stayed hit-testable and in
   the accessibility tree underneath it.

**Balance, one honest read.** Greedy-ish play with the timer running reached level 3 and ~2.3K in
about ninety seconds, tier 64. The early game is not the problem; the drop speed at levels 1-3
(700-550ms) is slack enough that the timer, not the player, places most blocks. `tools/balance`
should be re-run with a clock before anyone tunes the curve.

**Measured in C1c, and the diagnosis did not survive it.** The timer places 0.00% of the blocks in
the opening — the policy gets the column it asks for every time. What is real is 4.8 seconds a
drop of waiting once the steering is done. The curve was re-cut anyway, on that number rather than
on this one. See C1c.

---

## C1c · `tools/balance` with a clock — **DONE** (2026-09-09)

**Unblocked by** C1a and C3.

**Delivered** a clock-aware harness and a ruling on the early speed curve. Every C1a number was a
ceiling (L21): the harness had no timer, so every policy hard-dropped into the column it wanted at
every level. `DropClock` puts SPEC 5.5's drop timer and SPEC 6's controls between the policy and
the board — ticks at `msPerRow(level)`, a 150ms lock delay with one reset per drop, soft drop flat
at 40ms a row, the shared tap/hold drop control, and C3's one-slot sideways buffer. A policy that
cannot reach its preferred column in the time available takes the best one it can reach.

The clock-free policies are unchanged and still runnable. `--clock off` is C1a exactly.

**The player is a modelled assumption, not a measurement.** A `PlayerProfile` is a decision time
and a tap rate; the headline numbers use `average` (250ms to read the board, 120ms between taps,
finishes with a hard drop) and `quick` / `deliberate` bracket it. Nobody has instrumented a real
player, and every number below moves if those two constants are wrong. `patient` never touches the
drop control and exists only to put the other end on the wall clock.

All numbers: 10,000 runs per policy, seeds 1..10000, `EngineConfig.Default`, engine as shipped.
Reproduce with `./gradlew :tools:balance:run --args="--runs 10000 --policy greedy --clock average"`.
`:tools:balance:test` — 13 tests, 0 failures, 0 skipped.

### 1. How big the ceiling was

| Policy | median level | p90 | drops | 512 | 1024 | 2048 | clutter mean |
|---|---|---|---|---|---|---|---|
| Random, no clock | 4 | 5 | 63 | — | — | — | 0.93 |
| Random, clocked | **5** | 7 | 80 | 0.07% | — | — | 0.89 |
| Greedy, no clock | 22 | 26 | 421 | 53.8% | 30.5% | 3.8% | 2.42 |
| Greedy, clocked | **19** | 23 | 376 | 57.3% | 23.8% | 2.2% | 2.49 |
| Lookahead-1, no clock | 25 | 32 | 482 | 32.2% | 49.0% | 15.7% | 2.79 |
| Lookahead-1, clocked | **22** | 27 | 422 | 41.6% | 43.1% | 10.1% | 2.86 |

Three levels of median off the two merge-seeking policies, about 12%, and roughly a third off the
2048 rate. Cause of death stayed `row_zero_occupied` on 30,000 of 30,000 runs and no run hit the
drop cap or faulted the engine. L21 was right about the direction and the size is modest.

Sensitivity to the player model, Greedy: `quick` 21, `average` 19, `deliberate` 17, against a
clock-free ceiling of 22. So the assumed hand is worth about as much as the clock is.

**Random got *better* with a clock, 4 to 5, and that is not a bug.** The columns a block can still
be steered into are partly the columns with room in them, because a tall column on the way blocks
the path. A random player forced to re-pick among reachable columns is a slightly less random
player. It is the only policy with that back door, and it is worth knowing before anyone reads a
clocked Random number as the floor SPEC 4.4 describes.

### 2. The early curve: C3's read was half right, and the half it got wrong is the diagnosis

C3 reported that at levels 1-3 "the timer, not the player, places most blocks". Measured, per
level, over the first 60 drops of every run:

| Level | drops | placed by the timer | off preferred column | slack | wall clock per drop |
|---|---|---|---|---|---|
| 1 | 200,000 | 0.00% | 0.00% | 4,808ms | 569ms |
| 2 | 200,000 | 0.00% | 0.00% | 3,916ms | 577ms |
| 3 | 200,000 | 0.00% | 0.00% | 3,302ms | 584ms |

(Greedy, `average`, the 700ms curve. Lookahead-1 is the same to two decimal places; Random differs
only because it aims at far columns, and even it is 0.03% at level 1.)

**Not one block in 600,000 was placed against the policy's choice in the opening.** Over a whole
run it is 0.0% timer-placed and 2.2% off-preferred, and what causes even that is a tall column
blocking the path rather than the timer being fast. The reason is structural: five columns with a
centre spawn puts every cell at most two columns away, which the modelled player covers in 370ms
against a budget that never falls below about 780ms even at the level-35 speed floor.

**What is real is the dead time.** `slack` is the wait between the policy finishing its steering
and the block arriving: 4.8 seconds a drop at level 1. That is C3's cutscene, and it is the number
the complaint was actually about.

Three curves, Greedy, `average`, same seeds. Levels 9+ identical in all three:

| Curve (levels 1-8) | median level | drops | 1024 | 2048 | clutter | L1 slack | to level 4 |
|---|---|---|---|---|---|---|---|
| `700, 620, 550, 490, 430, 380, 340, 300` | 19 | 376 | 23.78% | 2.24% | 2.49 | 4,808ms | 34.4s |
| `600, 560, 520, 480, 430, 380, 340, 300` | 19 | 376 | 23.78% | 2.24% | 2.49 | 4,142ms | 34.4s |
| `500, 470, 440, 410, 380, 350, 325, 300` | 19 | 376 | 23.78% | 2.24% | 2.49 | 3,477ms | 34.4s |

Every outcome column is identical to the digit. The early curve moves nothing except how long the
player waits, and it cannot move that very far: even at 500ms the level-1 wait is 3.5 seconds,
because seven rows of fall is seven rows of fall.

For a player who never touches the drop control the curve is the whole pacing story — level 4 at
289s, 261s and 223s under the three curves. For one who hard-drops it is 34.4s under all three.

### 3. What changed

**SPEC 5.5's levels 1-8, from `700, 620, 550, 490, 430, 380, 340, 300` to
`500, 470, 440, 410, 380, 350, 325, 300`.** Level 9 on is untouched, and the floor, the tail step
and soft drop are untouched.

This is a **feel change made on a measurement that says its risk is zero**, not a difficulty fix,
and it is written down that way so nobody later reads it as one. It cuts the measured dead time in
the opening by 28% and changes no other number in the report.

**`blocksPerLevel` was measured and left at 20.** It is the only lever that changes the opening's
pace for a player who does use the drop control, and it costs more than it is worth:

| blocksPerLevel | median level | drops | score | 1024 | 2048 | to level 4 (hard-dropping) |
|---|---|---|---|---|---|---|
| 20 | 19 | 376 | 88,872 | 23.78% | 2.24% | 34.4s |
| 15 | 23 | 333 | 95,476 | 22.97% | 2.06% | 25.7s |
| 12 | 26 | 310 | 105,902 | 19.82% | 1.66% | 20.6s |

Runs get 11% and 18% shorter, the tail past 1024 thins, and the rising median level is the counter
moving faster rather than the player doing better. It also **moves the pinned determinism digest**,
because the engine reads it to advance the level — verified by setting it to 15 and watching
`DeterminismTest` fail on the score (862 to 912). A first-band-only `blocksPerLevel` would need a
schedule instead of an int, which is an engine change to level advancement, so it is not a free
experiment either.

**The spawn table was re-read against the clocked numbers and kept.** Greedy at a clocked median
of 19 with 26% of runs past 1024 and 2.2% bursting is inside both of SPEC 17's failure modes, so
L19 survives the clock: the table sets the tier ceiling, the board sets the level.

### 4. The digest did not move, and that was checked rather than assumed

Changing the curve does not touch `DeterminismTest`'s pin. The engine never reads the curve — it
stores the level and the ViewModel asks how long a row takes — and `EngineConfig` is serialized
with defaults omitted, so a run played under the default curve encodes no curve at all. Confirmed
by making the change and running `:libraries:cascade:jvmTest`, not by reasoning about it. The
contrast with `blocksPerLevel` above is the point: two knobs in the same SPEC section, one free and
one not.

**Not verified.** The player model is the whole soft underbelly: 250ms and 120ms are assumptions,
the sideways buffer is modelled at its optimistic end (one free column toward the target whenever
the previous resolution ran longer than the decision time), and a tie between a player input and a
lock is resolved in the player's favour because on a device that ordering is genuinely undefined.
No policy uses hold, so SPEC 5.4's stash is still unmeasured. Nothing here was played on a device;
the wall-clock figures are the model's, and C3's ninety seconds to level 3 sits between the
hard-dropping player's 23s and the patient player's 208s, which is the closest thing to a
validation this has.

---

## C1d · Cut hard drop and hold, add the nudge — **DONE** (2026-09-09)

Decision D11 applied to the engine and to its two consumers. Four changes, shipped as one because
the first three each move the determinism digest and moving it three times is three chances to
paste an "actual" instead of deriving one.

**`Input` lost `HardDrop` and `Hold` and gained `Nudge`.** `Nudge` advances the fall by
`EngineConfig.nudgeRows` (2) and does nothing else — it does not lock, does not score, and stops
against the stack rather than being rejected. The rows are a config value rather than a constant
because the nudge is now the only acceleration a player has and C1e will want to sweep it.

Removing hard drop cost the engine **no code path**. `Input.Lock` already locked at the block's
*landing* cell rather than its current one, so hard drop was `Lock` plus a score prefix. Every
call site that meant "put this block down now" — two test fixtures and the balance harness —
became `Input.Lock` with no behaviour change at all.

**The nudge pays nothing.** SPEC 7's `2 x rowsSkipped` is struck rather than inherited; the
argument is in `Scoring`'s KDoc and in SPEC 7. Short version: the bonus was paying for
commitment, a nudge is not a commitment, and a free reward on a repeatable input obliges every
player to mash a control the handoff drew as recessive.

**The board-aware cap moved to spawn time.** SPEC 5.4's non-optional preview was the *only* reason
it read the board two drops early, and the preview is gone. `GameState` kept no invisible queue:
a draw is a pure function of the rng, level, board and draw count already in the state, so
drawing at spawn is exactly as reproducible and reads the real board. This deletes the caveat L20
measured at 0.02-0.04%, and with it the harness's `staleCapDrops` counter, which could now only
ever report zero.

**The digest was re-pinned, for the second time.** `862 / 25 drops` became `592 / 22 drops`,
digest `9182672379078956347` → `-9016281694182991228`. Re-derived by running the engine in a
throwaway program and reading the values out, never by copying the assertion's "actual" (L17). The
arithmetic is the check that it is the *right* number: 25 drops at roughly six skipped rows was
about 300 points of bonus, which is most of the 270-point fall.

**The saved-run blob is now versioned.** See `decisions.md`. A blob written by the C4 build would
have decoded silently under `ignoreUnknownKeys`; it is refused instead, and the test that proves
it loads the same bytes twice, once with a `version` spliced in, so a pass cannot come from the
blob merely being malformed.

**Not done here:** drag steering and the handoff's visual language (C3b), and re-measuring pacing
(C1e). The `Cue` for the nudge is `Cue.Move` because `:libraries:ui` was being edited concurrently
and `Cue.HardDrop` is now unreferenced.

---

## C1e · Pacing re-measured without hard drop — **DONE** (2026-09-09)

**Unblocked by** C1d.

Every clocked number in this file was measured in a game where one press ended a fall. This chunk
re-ran all of them against the ▼ nudge and swept the three dials that were left open. **Nothing was
changed.** The opening curve stays at 500ms, `nudgeRows` stays at 2, `decisionMillis` stays at 250,
and Lookahead-1 stays as the declared ceiling. Four measured non-changes and one correction to a
handover prediction is the whole delivery, which is the point of measuring before tuning.

All numbers: 10,000 runs per policy, seeds 1..10000, `EngineConfig.Default`, engine as shipped.
`:tools:balance:test` — 16 tests, 0 failures, 0 skipped.

### 1. The headline: the nudge is a pure pacing control

**The drop control changes when a block locks and never where.** `Input.Lock` places at the
*landing* cell, so a block's height when the player commits is not an input to anything. Measured:
`patient` (never touches ▼), `softie` (holds for soft drop), `average` (taps ▼) and the restored
hard-drop finish all produce **identical outcome columns to the digit** — median level 19, p90 23,
372 drops, 84,716 score, 24.2% past 1024, 2.31% bursting, clutter 2.5, the same cascade histogram.
Only the wall clock moves, by a factor of four. Pinned as
`HarnessTest.theDropControlChangesTheWallClockAndNothingElse`.

That is the fact the rest of this section rests on: **hard drop was never a difficulty lever and
neither is the nudge**. L29 said "the biggest lever on the opening"; it was the biggest lever on the
*clock*, and it is worth restating that way now that the control it described is gone.

### 2. Hard-drop era against nudge era, clocked, all three policies

The "hard drop" column is C1c's published table. The "nudge" column is this chunk. Both are
`average` (250ms / 120ms), the shipped 500ms curve, `blocksPerLevel` 20.

| Policy | median level | p90 | drops | 512 | 1024 | 2048 | clutter mean | death |
|---|---|---|---|---|---|---|---|---|
| Random, hard drop | 5 | 7 | 80 | 0.07% | — | — | 0.89 | row_zero 100% |
| Random, nudge | 5 | 8 | 84 | 0.04% | — | — | 0.88 | row_zero 100% |
| Greedy, hard drop | 19 | 23 | 376 | 57.3% | 23.78% | 2.24% | 2.49 | row_zero 100% |
| Greedy, nudge | 19 | 23 | 372 | 56.47% | 24.2% | 2.31% | 2.5 | row_zero 100% |
| Lookahead-1, hard drop | 22 | 27 | 422 | 41.6% | 43.1% | 10.1% | 2.86 | row_zero 100% |
| Lookahead-1, nudge | 21 | 27 | 419 | 41.15% | 43.36% | 9.68% | 2.86 | row_zero 100% |

Cascade depth, Greedy, share of drops: 43.91 / 35.18 / 13.24 / 5.19 / 1.77 / 0.59 / 0.06% at depths
0-6, mean 0.87, depth>=2 20.9% — against C1c's 20.8% pre-D11. Highest tier **reached** (D7) is the
column labelled 512/1024/2048; buckets are exclusive. No run in 30,000 hit the drop cap, faulted the
engine, or died of `SPAWN_BLOCKED`.

**The residual differences are not the control, they are the spawn draw.** The clock-free harness
was re-run too, and it does *not* reproduce C1a to the digit either — Greedy's 1024 rate moved
30.5% → 31.27%, Lookahead-1's 2048 rate 15.7% → 16.21%, medians unchanged. L36 said the clock-free
numbers survive C1d untouched because `Lock` lands where `HardDrop` landed. The placement half is
right; the *stream* half is not. C1d also moved the board-aware cap from draw time to spawn time,
and L20 measured that as changing the drawn block on 0.02-0.04% of drops. At ~400 drops a run that
reseeds roughly one run in twelve, which is exactly the size of the drift seen. **Every C1a and C1c
number is now good to about half a percentage point, not to the digit.**

### 3. The wall-clock pacing table

Greedy, 500ms curve, `average` hand. The hard-drop row is the restored control measured on the
*current* engine, so all three rows are the same seeds and the same spawn stream.

| Player | level-1 s/drop | level-2 | level-3 | to level 4 | median run |
|---|---|---|---|---|---|
| Patient (never presses ▼) | 4.15s | 3.67s | 3.31s | **222.6s** | 760s |
| Nudging (▼ at 120ms) | 0.96s | 0.93s | 0.91s | **55.8s** | 308s |
| Hard drop (removed, reference) | 0.54s | 0.55s | 0.55s | **32.8s** | 206s |

**C1d predicted a nudged level-1 drop at 1.5-2.5s. It is 0.96s, so the prediction was wrong by
roughly a factor of two, in the game's favour.** The arithmetic C1d did was right and one term was
missing: three taps at 120ms do replace the fall, but the *decision* time and the lock delay are
paid by every player on every drop regardless, and gravity keeps running underneath the taps rather
than pausing for them. At level 1 the last row is usually delivered by an ordinary drop tick that
arrives while the thumb is between presses.

The nudge recovers **88%** of the wall-clock gap hard drop used to open, on both measures
independently: (4.15 - 0.96) / (4.15 - 0.54) = 88% per drop, and (222.6 - 55.8) / (222.6 - 32.8) =
88% to level 4. What is not recovered is the *feel* of one decisive press, which no harness measures.

L29 restated for the current game: **whether the player uses ▼ is still the biggest lever on the
opening, and it is still a tutorial problem.** 223 seconds to level 4 against 56. C5 should treat
teaching ▼ as load-bearing, exactly as C1c said of hard drop.

### 4. L28 survives, and it got stronger

Four curves, Greedy, same seeds, levels 9+ identical in all four. `fast400` is new in this chunk,
because C1c only ever bracketed *slower* than what it adopted.

| Curve (levels 1-8) | median | drops | 1024 | 2048 | clutter | L1 slack | L1 s/drop nudging | to L4 nudging | to L4 patient |
|---|---|---|---|---|---|---|---|---|---|
| `700, 620, 550, 490, 430, 380, 340, 300` | 19 | 372 | 24.2% | 2.31% | 2.5 | 4,808ms | 0.99s | 57.8s | 289.3s |
| `600, 560, 520, 480, 430, 380, 340, 300` | 19 | 372 | 24.2% | 2.31% | 2.5 | 4,142ms | 0.96s | 56.8s | 261.4s |
| `500, 470, 440, 410, 380, 350, 325, 300` (shipped) | 19 | 372 | 24.2% | 2.31% | 2.5 | 3,477ms | 0.96s | 55.8s | 222.6s |
| `400, 385, 370, 355, 340, 325, 312, 300` | 19 | 372 | 24.2% | 2.31% | 2.5 | 2,708ms | 0.93s | 55.3s | 185.9s |

Every outcome column is identical to the digit across a 300ms spread, at both ends of the drop
control. **The opening curve still cannot change difficulty**, and the reason is now stronger than
it was: it cannot change difficulty *and* it barely changes the clock either, for anyone who
presses ▼. 300ms off the level-1 interval buys the nudging player 62ms a drop — 6% — because the
nudge, not gravity, is carrying the block.

**Ruled: the curve stays at 500ms.** The case for `fast400` is real but small and it is aimed at the
wrong player. It buys 37 seconds off the patient opening, which is a sixth of what teaching ▼ buys,
and it costs the levels 1-8 band most of its sense of acceleration: 500 → 300 is a 40% ramp across
the first band, 400 → 300 is 25%, and level 1 would sit closer to level 8 than to anything the
player can feel change. There is no measured reason to move it and one unmeasured reason not to.

**Before and after: no change.** SPEC 5.5 levels 1-8 remain `500, 470, 440, 410, 380, 350, 325, 300`.
`Curves.Fast400` is kept in the harness so this row stays re-runnable.

### 5. `nudgeRows` swept, and it stays at 2

Greedy, `average`, 500ms curve. Outcome columns omitted because **all four are identical to the
digit** — median 19, p90 23, 372 drops, 24.2% / 2.31%, clutter 2.5.

| `nudgeRows` | level-1 s/drop | level-2 | level-3 | to level 4 | median run | saved vs previous |
|---|---|---|---|---|---|---|
| 1 | 1.25s | 1.19s | 1.15s | 71.7s | 359s | — |
| 2 (shipped) | 0.96s | 0.93s | 0.91s | 55.8s | 308s | 292ms/drop |
| 3 | 0.87s | 0.83s | 0.83s | 50.7s | 290s | 85ms/drop |
| 4 | 0.81s | 0.82s | 0.81s | 48.7s | 278s | 61ms/drop |

**The returns collapse after 2.** Going from 1 to 2 is worth 292ms a drop; every row after that is
worth under 90ms, because past two rows a press the binding constraint stops being rows and becomes
the three fixed costs — 250ms to decide, 120ms between taps, 150ms of lock delay. The floor is the
hard-drop row's 0.54s, and 2 is already 88% of the way there. Three would buy 5 seconds off the
opening at the price of a control that moves a block most of the board in two presses, which is hard
drop with extra steps and is the thing D11 removed on purpose.

**Recommendation: leave it at 2.** The handoff's number is the measured one.

### 6. The digest does not move, and that was checked rather than reasoned

D11 and `EngineConfig`'s KDoc both imply that sweeping `nudgeRows` moves the pinned digest because
it travels inside `GameState`. **It does not.** `DEFAULT_NUDGE_ROWS` was set to 3, all five
`DeterminismTest` cases were run on the JVM, and all five passed.

Two independent reasons, and both are worth knowing:

- `kotlinx.serialization` omits values equal to their declared default, so moving the default moves
  nothing in the bytes. A run played at a *non-default* `nudgeRows` does encode it and would digest
  differently, but nothing in `DeterminismTest` plays one.
- `Input.Nudge` is one input in eight in the pinned script and it is **behaviourally inert**, for
  the same reason section 1 gives: every drop ends in `Lock`, and `Lock` places at the landing cell
  no matter how far down the block already is. The engine cannot tell how many rows a nudge moved.

So `nudgeRows` is a genuinely free remote knob in a way `blocksPerLevel` is not. That is a better
position than C1d assumed it was in, and it should be recorded before someone declines to tune it
live on a digest argument that does not apply.

### 7. `decisionMillis` swept, and it is now the largest uncertainty in every clocked number

C1d left the 250ms at its hard-drop-era value deliberately. Swept, Greedy, `average` tap rate,
500ms curve:

| `decisionMillis` | median level | p90 | drops | 1024 | 2048 | off preferred | timer-placed | L1 s/drop | to level 4 |
|---|---|---|---|---|---|---|---|---|---|
| 150 | 21 | 25 | 406 | 28.5% | 2.95% | 1.35% | 0.0% | 0.84s | 49.5s |
| 200 | 19 | 24 | 378 | 24.73% | 2.57% | 1.88% | 0.0% | 0.89s | 52.5s |
| **250 (shipped)** | **19** | **23** | **372** | **24.2%** | **2.31%** | **2.36%** | **0.0%** | **0.96s** | **55.8s** |
| 300 | 18 | 21 | 349 | 20.1% | 1.44% | 2.1% | 0.39% | 0.98s | 58.3s |
| 350 | 17 | 21 | 333 | 18.28% | 1.34% | 2.9% | 0.43% | 1.03s | 61.1s |
| 400 | 17 | 20 | 330 | 16.95% | 0.82% | 3.13% | 0.53% | 1.09s | 64.9s |
| 500 | 16 | 19 | 308 | 10.81% | 0.51% | 2.93% | 1.01% | 1.19s | 70.9s |

**This one is not a pacing dial. It is the difficulty dial.** Across a range no more implausible
than the assumption itself, the median moves five levels and the 1024 rate moves by a factor of
nearly three. For comparison, the whole clock was worth three levels (L30) and the spawn table is
worth one (L19). Every clocked number in this file is conditional on a constant nobody has measured,
and that dependence is steeper than any of the levers the project has actually argued about.

**Left at 250, deliberately, because there is no basis for moving it.** The two arguments point
opposite ways and neither has evidence: there is no preview to read any more, which is less work,
and there is no lookahead either, which puts more of the choice on the board. Picking between them
by feel would replace an honest assumption with a dressed-up one. What changes is its status — it
should be the first thing SPEC 17's telemetry answers, and until it does, no clocked median in this
file should be quoted to better than "about twenty".

### 8. Lookahead-1 keeps the ceiling, and now says so out loud

L37: the policy reads a block that is drawn at spawn time and never shown, so since D11 it plans
around information that does not exist yet. **Kept as the declared ceiling.** Demoting it does not
produce an honest ceiling, it produces none — Greedy is a competent player, not a bound, and a
lookahead that did not cheat would have to search the distribution of next draws, which is a
different and far more expensive policy answering a question SPEC 4.4 never asked.

What changed is that the declaration is now on the instrument instead of in a doc: `Policy.cheats`,
and every report line it prints reads
`policy=lookahead1 (ceiling, reads a block the player never sees)`. The failure mode is somebody
quoting a lookahead median as a prediction of play, and that happens at the moment they read the
output, not at the moment they read the KDoc. Measured, the cheat is worth **two levels of median
and four times the 2048 rate** over Greedy.

### 9. What could not be verified

The player model is still the whole soft underbelly and section 7 now puts a number on how much that
matters. Nothing here was played on a device: the wall clock is the model's. The sideways buffer is
still modelled at its optimistic end and an input/lock tie is still resolved in the player's favour.
The hard-drop row in section 3 is a control restored in the harness for one measurement and reverted
— it is not in the tree, and hard drop is not in the game.

Two things this chunk cannot see at all. **How the nudge feels** — 88% of the clock back is not 88%
of the decisiveness back, and only a device answers that. And **whether players press it**: the
entire 223s-to-56s spread is a behavioural question, and the harness models both ends rather than
predicting which one a real player sits at.

---

## C3a · Feel

**Unblocked by** C3.

**Delivers** the half of the game that is not rules.

- **Merge sound pitched per cascade step.** `SPEC.md` 21 says this is one of the two things to
  keep if everything else is cut. Build it properly: a six-step chain should be an ascending run
  that is a reward on its own.
- The `CHAIN xN` float, scaling with step.
- The burst: screen shake, white flash, sustained haptic, the longest sample in the game, music
  ducking under it. Do not undersell it.
- The full haptic set from `SPEC.md` 9.
- **Reduce motion** honored throughout: shorter transcript playback, no shake, fewer particles,
  identical game.
- The Drag control scheme. Play both for a day and pick the default.

**Done when** reduce-motion has been tested by actually turning it on and playing a full run, and
someone who has not seen the game can tell a 2-chain from a 6-chain with their eyes shut.

---

## C4 · Persistence and stats

**Unblocked by** C3.

**Delivers** runs that survive the app dying.

- Room `run_record` in `:libraries:progress` with the DAO contributed to `AppDatabase`.
- In-progress `GameState` serialized into `AppData`, restored on launch, with the mid-cascade
  resume path from `SPEC.md` 18.9.
- Best score and every stat in `SPEC.md` 15 derived from `run_record`. No separate best-score
  field anywhere.
- Stats screen.

**Done when** force-quitting mid-run and mid-cascade both resume correctly, and the stats page
numbers match a hand-counted sequence of runs.

---

## C5 · Tutorial

**Unblocked by** C3, C4.

**Delivers** the six scripted drops from `SPEC.md` 13, replacing the body of
`:features:onboarding:impl`.

Because the engine is a pure state machine, each step is a forced `GameState` plus an expected
input, not a pile of UI flags.

**Done when** a test drives all six steps and asserts the burst fires on drop 6, the tutorial is
skippable from drop 3, and it is replayable from Settings.

---

## C6 · Daily Challenge

**Unblocked by** C1 (seeded engine), C4 (`daily_result`).

**Delivers** one seed per UTC day, one attempt, the streak with rewards at 3/7/14/30, and the
Daily screen.

The seed is derived from the date, so no server and no content pipeline. Take Sodogku's
`DailyCalendar` / `DailyStreak` / `DeviceTimeZone` shape; the timezone-boundary bugs are already
solved there.

**Done when** the date-rollover tests pass across timezone changes and a device clock moved
backwards, and two devices on the same UTC day produce identical block sequences.

**Outcome.** `daily_result` in `:libraries:progress`, keyed on the UTC date as its primary key and
carrying seed, score, attempts used, completed and retries used. `AppDatabase` is version 7 with
an `AutoMigration(6, 7)`; the destructive fallback stays narrowed to the pre-game schemas (L33),
which matters more here than it did for `run_record` — a run history can be re-earned by playing
and a streak cannot, because the boards it was built on are in the past.

The streak is a fold over the rows, never a counter, ported in shape from Sodogku's
`DailyStreak.kt` with the freeze and restore mechanics stripped: Drop 2048 has neither, so the
walk is a straight run of completed days. Both the current and the best number ignore future-dated
rows, which is what a device clock pushed forward and pulled back leaves on disk.

`:features:daily` is the screen — today's state, attempts remaining, the streak with SPEC 14's
3/7/14/30 track, and a leaderboard placeholder that says in words that C9 owns it. The board it
opens is `:features:game` with a `mode` route argument rather than a second game screen.
`StreakTrack` is the one new `:libraries:ui` primitive: a milestone track rather than Sodogku's
month calendar, because a calendar answers "which days did I play" and the mechanic is asking "how
far to the next reward".

**The ruling that mattered: a Daily run pins `EngineConfig.Default` and never reads remote
config.** C7's start-of-run sampling keeps one run coherent and does not make two players' runs
comparable. `DailyConfigPinningTest` moves every gameplay key and asserts the block sequence does
not budge, with a positive control (L35). See `decisions.md` for the alternatives and for what this
makes immovable.

Full detail of the other four rulings — UTC everywhere, the attempt spent at start, best-of-the-day
scoring, and the `retriesUsed` column SPEC 11 does not list — is in `decisions.md`.

**Not verified.** The app was not launched on either simulator, and no ad or billing path exists to
exercise (C10): `ProEntitlement` and `DailyRetryAd` are seams with shipped bindings that answer
"not Pro" and "unavailable", and both are covered by tests on the seam rather than through a real
network.

---

## C7 · Remote config

**Unblocked by** C1a (there is no point making numbers remote before knowing which numbers).

**Delivers** every key in `SPEC.md` 10, wired through the existing `:libraries:config`, with
compiled-in fallbacks and admin console entries.

**Done when** the app is fully playable with the server unreachable, a changed `spawn.table` in
the admin console reaches a device on next launch, and a malformed remote value falls back to the
compiled default instead of crashing.

Fly deploy is part of this chunk, not a follow-up.

**Outcome.** Twenty-six keys wired through `:libraries:gameconfig`, a new leaf module between
`:libraries:config` and `:libraries:cascade` — one `ConfiguredValue` per key, every default read
off `EngineConfig`'s own companion rather than retyped. The fifteen gameplay keys are assembled by
`RemoteEngineConfig`; the eleven ad / Pro / kill-switch keys have no consumer until C10 and are
seams with tests on the seam.

**A fetched config takes effect at the start of the next run, never during one.**
`EndlessRunFactory` is the only reader, the value goes into `GameState.config` (D5), and every
in-run read already came off `state.config`. `RemoteConfigRunBoundaryTest` moves the map underneath
a live run and asserts nothing budges until Restart. See `decisions.md`.

`level.blocksPerLevel` is wired **with** the digest warning D9 asks for, at three places in the
admin console: the flag description, a red banner on open, and `dangerousWarning` on every write
path (which on prod also forces typing the environment name). Reverting it warns too.
`DangerousWarningTest` pins that the ten keys measured safe — the curve, the nudge, the spawn
table, the rest — do **not** warn, because a console that warns about everything is one nobody
reads.

Range checks are per-key: a bad value falls back to that key's own default and leaves its
neighbours remote. `EngineConfig`'s `require` blocks are the backstop, inside `Catching`.
`NeverRemoteTest` feeds the assembler every plausible path for `Scoring`, the cascade caps, `cols`,
`spawnCapFloor` and `continueRowsCleared` and asserts none of them moves.

The server gained `ConfigCatalog`, used only until CI uploads a real manifest: without it a fresh
deploy's admin console has an empty flag table and `ConfigSchema` has nothing to type-check
against. An uploaded manifest replaces it outright. It is a hand-maintained mirror and that cost is
recorded in `decisions.md`.

**Docker was up for the first time in this project, and the integration harness ran.**
`:apps:integration`'s `HarnessSmokeTest` — the real client `RemoteConfigRemoteDataSource` over real
TCP against the real in-process Ktor server on a Testcontainers Postgres — **passed**. So did all
four server Testcontainers tests, after one of them was fixed: `DatabaseSchemaTest` asserted
`app_config_values` was empty, which V4's own seed had made false the day it landed. It had never
failed because it had never run.

Full gate green with **0 skips**: 828 tests, 0 failures, detekt clean, both platforms compile.

**Not verified.** The Fly deploy — there is no Fly app yet (`OWNER-TODO.md`), so nothing has been
exercised against a deployed server: no real `GET /v1/app-config` over the internet, no admin
console served from `/admin`, no change made in a browser reaching a device. The in-process harness
covers the same code path over real TCP, which is the strongest claim available without the app.
The app was also not launched on either simulator.

---

## C8 · Telemetry

**Unblocked by** C3, C6.

**Delivers** every event in `SPEC.md` 17 through the existing `:libraries:telemetry:impl`, plus
the dashboards for median level reached and highest-tier distribution.

**Done when** the per-drop clutter metric appears on a dashboard next to the same metric from the
balance harness, and both are computed by the same function in `:libraries:cascade`.

**Do not repeat Sodogku's S15.** Every event that exists is emitted from a production call site,
and a test asserts it. An event with no call site is worse than no event, because it looks like
coverage.

---

## C9 · Achievements, leaderboards, sharing

**Unblocked by** C4.

**Delivers** the ports: `:libraries:achievements` (+ impl), `:libraries:leaderboards` (+ impl),
`:libraries:sharing` (+ impl), `:features:achievements` (+ impl). All four exist in Sodogku and
arrive shaped like that game; strip the domain and keep the reasoning.

- The 24 achievements from `SPEC.md` 15, with a reachability test proving every one is actually
  earnable given the engine's rules. Sodogku has this test; it is the one that catches an
  achievement whose condition the game can never satisfy.
- Game Center on iOS, Play Games on Android, all-time and weekly boards plus Daily.
- **The submit call site ships in this chunk.** Sodogku shipped `Leaderboards.submit` with zero
  production callers and did not notice for a while. A test asserts submit fires on run end.

---

## C10 · Ads and billing

**Unblocked by** C7 (the gates are config-driven), C9.

**Delivers** `:libraries:ads` (+ impl), `:libraries:billing` (+ impl), `:features:paywall`
(+ impl), all ported.

- Rewarded continue with the two-per-run cap and the higher-friction second offer, and the
  8-second auto-decline on the offer screen with the board visible behind a scrim so the player
  sees what they are saving.
- Rewarded Daily retry.
- Interstitials under every single rule in `SPEC.md` 12, with a test per rule.
- Pro: no interstitials, all palettes, two Daily attempts, two continues. Restore purchases.

**Done when** `AdPolicyTest` covers every gate independently and in combination, and the
governing principle holds: no unchosen ad while a run is alive. Sodogku shipped an iOS ad stub
that paid out rewards for free; check the iOS network is real before this chunk closes.

---

## C11 · Settings, legal, gates, accessibility

**Unblocked by** C10 (Pro status and consent both live in Settings).

**Delivers** `:features:settings` (+ impl) and `:features:gate` (+ impl), ported.

- Every section of the original spec's 15 minus the cut items: audio, haptics, reduce motion,
  control scheme, left-handed, ghost, hold toggle, confirm-before-quit, the accessibility block
  from `SPEC.md` 16, restore purchases, reset progress with double confirmation, delete local
  data, privacy policy, terms, ad consent (GDPR / CCPA / ATT), licenses, feedback form with
  opt-in diagnostics, version and credits.
- Launch gates: force update, maintenance, legal re-accept. Rendered instead of the nav host, not
  navigated to.
- **The accessibility pass is real work in this chunk**, not a checkbox: screen reader over a live
  board, touch target audit on the three control buttons, dynamic type through the whole HUD.

---

## C12 · Debug menu

**Unblocked by** C11 (seven taps on the version number lives in Settings).

**Delivers** `:features:debug` (+ impl), gated behind a build flavor flag or passphrase in
production, flagging the session as `debug_session: true` so QA never pollutes live metrics.

Most of the original spec's section 16 is nearly free given a pure seeded engine: set level, set
tick, force next block, queue a sequence, load a preset board, place and delete cells, trigger
game over, trigger a burst, force an N-step cascade, invincibility, freeze timer, autoplay soak
(reuse the C1a policies), grant entitlements, toggle Pro, reset IAP and daily state, force ads,
show cooldowns, set and copy the seed, replay the last run.

The diagnostics overlay is the valuable half: FPS, actual vs intended tick, cell coordinates,
merge priority arrows on the active block, current cascade step, and a scrollable text log of the
last resolution transcript step by step. That log is the fastest way to debug a merge that looked
wrong, and the transcript from C1 means it is a formatter, not a new system.

---

## C13 · Store prep

**Unblocked by** everything.

Icons, screenshots, store listings, privacy manifests and data-safety declarations that describe
the app that actually exists, age rating, ATT copy, review notes for a game with no account.

---

## Critical path

```
C0 → C1 → C1a → C7
      ↓
     C3 → C3a → C4 → C6 → C8
      ↑         ↓
     C2        C5, C9 → C10 → C11 → C12 → C13
```

C2 runs alongside C1. C1a gates C7 because remote config for a number nobody has measured is
just a slower way to guess.

**The gate that matters is C3.** Everything from C4 onward is infrastructure around a loop, and
infrastructure around a loop that is not fun is wasted. Play it before continuing.

## Deliberately not in this plan

Coins, the five purchasable powerups, Narrow and Wide boards, cosmetic themes, Zen mode, goal
levels, cloud save, accounts, replays, tournaments, friend challenges. See `SPEC.md` 2 and 20 for
why each one is out and what it would cost to add back.
