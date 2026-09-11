# Agent TODO

The queue the orchestrating session works from: things found mid-chunk that do not belong in the
chunk that found them. Ideas, deferred fixes, cleanups, things to add or remove.

Not a changelog. **Delete entries as they land** rather than ticking them off, so this stays a
queue.

Human-only items go in `OWNER-TODO.md` instead.

---

## Now

### No in-app QA config overrides, and it is now blocking verification

`ConfigOverrideRepository` exists and **nothing in the debug menu writes to it.** That is why the
interstitial has never been seen: the three-day install suppression makes it unreachable on a fresh
install, and an emulator refuses `date` on a non-userdebug build.

A QA config screen would also unblock the launch gates and every kill switch. This is the cheapest
remaining unlock for on-device verification.


### The ▼ button is drawn recessive and is now the decisive control

The handoff painted it quiet because it was an accelerator. D21 made it the only irreversible input
in the game, and C1c measured that using it is the biggest lever on the opening (33s vs 289s to
level 4). Visual change only, and it is the handoff's call to override.

### `hard_drop.ogg` is the sample people will miss first

`Cue.HardDrop` existed with no caller for four chunks and now fires on **every drop of every run**.
It is on the missing-samples list with the other fifteen, but it is the one whose absence is most
audible.


### A haptic can be cancelled by the next one a millisecond later

Observed in `dumpsys`: a `Move` and the `Merge` behind it landed 1ms apart and the first came back
`cancelled_superseded`. The `Cues` throttle covers Move→Move and does **not** cover Move→Merge.
Needs a priority or a queue, not a longer gate.

### Decide whether a step-1 merge should pop the score louder

With the new per-step pacing (L58) the chain steps read well, and the single merge is now the one
beat that looks plain by comparison.


### Move the board-aware cap to landing time

D11 removes the preview, which is the *only* reason SPEC 5.3 evaluates the cap at draw time ("a
preview that can still change is a lie"). With no preview it can read the real board at landing.

Simplifies the rule and removes the stale-data caveat L20 measured. Fold into C1d; it moves the
digest too, so it should ride along with the other engine changes rather than moving it twice.

### `FuseReach` could come up ~10%

The special marks render correctly at real cell size — the star reads, the bomb fuse is legible
but is the smallest of the three marks. One constant in `BlockFace.kt`.

### A second metric for permanent obstructions

D8 deferred this. It matters now: Stones start arriving at level 12 and clocked runs regularly
reach 19-22, so board congestion is real and `clutter` deliberately does not see it. Not a change
to `clutter` — a second count.

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

### `dragAcrossCells` has no test

The gesture cannot be unit tested; `BoardGeometry` underneath it is, thoroughly. Worth a UI test
in C3a when Drag ships as a control scheme.

### Pull the remaining Sodogku design-system pieces as their chunk arrives

The C2 foundations are done. What is left in `ORCHESTRATION.md`'s port table is chunk-gated:
`Focus` and `CoachMark` for C5's tutorial, the streak and daily components for C6, `UnlockToast`
and `ShareButton` for C9.

**Do not port them up front.** An unported component costs nothing; a ported-and-unused one costs
maintenance forever.

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
