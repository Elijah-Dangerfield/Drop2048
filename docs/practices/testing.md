# Testing approach

How this codebase tests, layer by layer, and the conventions every new test
follows. The goal is a pyramid where each bug class is caught at exactly one
layer — the cheapest layer that can fail when it breaks.

## The layers

- **Library / feature unit tests** (`commonTest` in each module) — pure logic,
  repositories over fakes, and SEAViewModel behaviour. `OnboardingViewModelTest`
  is the reference recipe for VM tests: extend `CoroutineTest` (from
  `:libraries:flowroutines:testing`), hand-roll a fake per repository
  dependency, drive actions, assert on `vm.state`.
- **Scenario harness** — when a feature's tests keep re-wiring the same fakes,
  grow a tiny builder + verbs + `assertState {}` DSL next to them so tests read
  as user scenarios. The template's worked example lived on Home's profile
  loading and went with the identity stack in C0; the first feature that earns
  one (`:features:game`, C3) should grow it. It's a pattern, not a framework —
  copy and adapt, don't generalize.
- **Server unit + route tests** (`:apps:server` `src/test`) — plugins and
  routes through Ktor's `testApplication`, repositories over Testcontainers
  Postgres (`DatabaseTest` base class).
- **End-to-end integration** (`:apps:integration`) — the real *client* stack
  against the real server. See below.

## The integration harness (`:apps:integration`)

An Android-library module whose tests run as **host-JVM Android unit tests**
(`./gradlew :apps:integration:testDebugUnitTest`) — the same compilation path
the feature view models already use, so the harness can construct them
directly. `commonMain` is deliberately empty: nothing ships from this module,
and the iOS target must never try to link the JVM-only server.

What runs where in a harness test:

- **In-process, real:** a Netty engine on an ephemeral port booted through the
  production `installApp(component, …)` seam (`InProcessServer`), and a real
  `ServerComponent` over a Testcontainers Postgres with the real Flyway
  migrations. There is no auth plugin — the app has no accounts.
- **In the client, real:** `NetworkClientImpl` with the real headers provider,
  reachability tracker and buses, and the real `RemoteConfigRemoteDataSource`
  (`TestClient`). Requests travel real TCP — real serialization, real headers,
  real status codes.
- **Fake, on purpose:** exactly the seams a device would own — on-disk
  persistence (an in-memory `CacheFactory` running the real store logic).

`HarnessSmokeTest` pins the worked example: seed a config row, boot the server,
and assert the value the real client read back came from the database — real
client → real TCP → real server → real DB.
New end-to-end flows should follow its shape: add a client surface to
`TestClient`, a probe or seed helper to `InProcessServer` if the server side
needs one, and await state with `awaitState` / `awaitUntil` — never fixed
sleeps. Docker down → the suite skips (JUnit `Assume`), not fails.

**Fault injection** is worth knowing about even though this template doesn't
ship it: when a project grows a long-lived transport (a WebSocket, a sync
loop), wrap the real transport in a *decorator* that can drop, block, or delay
frames on command, and give the harness a switch to install it. The decorator
implements the transport interface and forwards to the real one, so reconnect
and presence machinery is exercised over real plumbing with surgically induced
failures — no mock transport that quietly diverges from the real one.

## Which layer catches which bug (don't duplicate)

When you're tempted to assert the same thing at two layers, the lower one wins
and the higher one doesn't get written.

| Bug class | Owning layer | NOT here |
|---|---|---|
| Pure logic, data mapping, validation rules | library unit tests | never re-tested above |
| Action → state derivation in a VM | feature `commonTest` (VM unit / scenario) | not integration |
| Route status codes and error envelopes | `:apps:server` route tests | not integration |
| SQL, migrations, repository contracts | `:apps:server` Testcontainers tests | not route tests |
| Client↔server contract drift (serialization, headers, real HTTP semantics) | `:apps:integration` | not unit tests with canned JSON |

Integration tests aren't a substitute for unit tests — they're slower and
harder to debug. Use them for the seam contract (real wire, real plumbing),
not for every rule the lower layers already own.

## Conventions

- **Hand-rolled fakes only.** No Mockito / MockK anywhere. Fakes go in
  `commonTest` next to their consumer, or in a shared `:libraries:x:testing`
  module if reused.
- **Dispatcher choice:** `UnconfinedTestDispatcher` (the `CoroutineTest`
  default) for most tests — continuations run eagerly, mirroring
  `viewModelScope` under `Main.immediate`. `StandardTestDispatcher` only for
  time-sensitive tests (timeouts, debounce, backoff) that need an explicit
  dispatch-then-advance cadence. Integration tests use a **real** Main
  dispatcher (`Dispatchers.setMain(Dispatchers.Default)`) because real sockets
  run on real threads.
- **`runCurrent()` not `advanceUntilIdle()`** when there's an unwanted
  future-scheduled task (e.g. a `withTimeout`) — `advanceUntilIdle()` fires it.
- **`runCatching { … }` not `assertFailsWith { … }` around suspend bodies.**
  `assertFailsWith` has subtle suspend-context issues where an async-thrown
  exception reaches the test scope before the assertion runs. Call the suspend
  function inside `runCatching`, then assert on the captured result.
- **Every test file gets a top-level KDoc** explaining what's covered and
  what's intentionally NOT covered (and where that lives instead).
- **Integration tests belong in `:apps:integration`.** Don't inline a real
  server into a feature-module test.
- **No fixed sleeps.** Await a flow (`awaitState`), or poll with a timeout
  (`awaitUntil`) when the state has no push signal.
