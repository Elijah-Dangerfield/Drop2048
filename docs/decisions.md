# Architecture decisions

Append-only log. Add an entry whenever you make a non-trivial architectural call
(new module boundary, library choice, scope cut, schema shape). Each entry: date,
the decision, alternatives considered, and *why*. Newest first.

---

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
