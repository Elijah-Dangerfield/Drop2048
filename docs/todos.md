# Agent TODO

The queue the orchestrating session works from: things found mid-chunk that do not belong in the
chunk that found them. Ideas, deferred fixes, cleanups, things to add or remove.

Not a changelog. **Delete entries as they land** rather than ticking them off, so this stays a
queue.

Human-only items go in `OWNER-TODO.md` instead.

---

## Now

### Finish C2 step 9 — the four HUD primitives

C2 delivered steps 1 through 8 and stopped short of step 9's component set. **C3 needs all four**
and should not hand-roll them:

- `ScoreCounter` + `abbreviateScore` — port and strip from
  `Sodogku/.../components/game/ScoreCounter.kt:48-136`, with its test. Sodogku's delta-scaled
  duration beats Cards' fixed one for a game where a merge pays 40 and a burst pays 4,000.
- `FloatingPoints` → the `CHAIN xN` callout — `ScoreCounter.kt:206-243`. Nonce-keyed rise-and-fade
  is exactly right; swap the copy and scale by cascade step.
- `LevelProgressBar` — port as-is from `Cards/.../components/LevelProgressBar.kt`.
- `pulsingBorder` — port as-is from `Cards/.../components/PulsingBorder.kt`. This is what SPEC 8.3's
  danger state needs, and its KDoc cites four production ANR traces from resolving the colour in
  composition instead of in draw.

### Add the three specials to the block palette

`Wildcard`, `Bomb` and `Stone` (SPEC 5.2) have no `BlockStyle` and are absent from `TIER_VALUES`.
The palette currently covers the 11 numeric tiers only, so C3 has nothing to draw a special with.

### Wire the real settings into `AppThemeProvider`

C2 widened the signature and `apps/compose/App.kt` still calls it on defaults, so palette choice,
reduce motion, large numbers and haptics are **inert end to end**. The plumbing exists and nothing
feeds it. Belongs to C11 (Settings), but it means no accessibility setting actually works until
then — worth knowing before someone tests one and reports a bug.

### Convert the remaining 28 `VerifyStrings` baseline entries

C0 pruned the baseline from 53 to 28 and made `OnboardingScreen` the worked example. The rest are
unverified copy on template screens nobody has rewritten: `HomeScreen`, `BugReportScreen`,
`FeedbackScreen`, `ShakeDialog`, `SplashScreen`, `AccessDeniedScreen`, `BlockingErrorScreen`, the
UI catalog.

**Not a chunk of its own.** Each screen's entries get deleted by whichever chunk replaces that
screen. Tracked here so the count is visible and it does not quietly stay at 28 forever.

### Regrow the scenario-harness pattern demo in `:features:game` (C3)

C0 deleted `HomeScenario` / `HomeScenarioTest` along with the profile they demonstrated.
`docs/practices/testing.md` still names the scenario harness as a pattern, and now points at
`OnboardingViewModelTest` for the ViewModel recipe with **no live example of the harness itself**.
C3 is the natural place to regrow it, since the game screen is the one that most needs it.

### Write the `Set<ClearableDao>` consumer (C11)

The multibinding survived C0 specifically for Settings' "reset progress", and it currently has
**zero consumers**. That is the exact shape of thing this repo has shipped before and not noticed
(see the watch list). Either C11 uses it or C11 deletes it.

### Decide whether `SessionRejectionBus` and `AccessDenied` earn their keep

Both survived C0 because they are structurally welded to `NetworkClientImpl`'s 401 path and
`App.kt`'s routing, and removing them meant surgery for no gain. But **nothing can trigger either
one today** — the server's ban gate went with the auth stack. Revisit once the server surface is
final in C7. Keep only if something can actually produce the envelope.

## Soon

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

### Port the design system pieces Sodogku already proved

`ORCHESTRATION.md` has the table. C2 takes the color, motion and haptics foundations; the rest
gets pulled as the chunk that needs it arrives. Do not port the whole thing up front: an unported
component costs nothing, a ported-and-unused one costs maintenance forever.

### Decide whether `:features:onboarding` keeps its name

C0 guts it down to the route. C5 refills it with the tutorial. If nothing auth-shaped ever comes
back, `:features:tutorial` is the honest name and the rename is cheapest before C5 fills it.

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
