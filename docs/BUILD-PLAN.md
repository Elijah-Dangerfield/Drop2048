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

---

## C7 · Remote config

**Unblocked by** C1a (there is no point making numbers remote before knowing which numbers).

**Delivers** every key in `SPEC.md` 10, wired through the existing `:libraries:config`, with
compiled-in fallbacks and admin console entries.

**Done when** the app is fully playable with the server unreachable, a changed `spawn.table` in
the admin console reaches a device on next launch, and a malformed remote value falls back to the
compiled default instead of crashing.

Fly deploy is part of this chunk, not a follow-up.

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
