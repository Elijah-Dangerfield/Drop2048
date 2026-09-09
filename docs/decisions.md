# Architecture decisions

Append-only log. Add an entry whenever you make a non-trivial architectural call
(new module boundary, library choice, scope cut, schema shape). Each entry: date,
the decision, alternatives considered, and *why*. Newest first.

---

## 2026-09-09 — The horizontal merge position contradicts the priority worked example

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
