# Observability: one id, three systems

The stack is Sentry (crashes, user feedback, stack traces), Loki (logs — client app events and
server request logs), and Tempo (server traces). What ties them together is a single correlation
id: **`session_id`**, the UUID of the current client app session.

## The session_id pivot

A "session" is a user-perceptible run of the app: it starts on cold boot and rolls over after
15 minutes in the background (`SessionTracker` in `:libraries:drop2048`). Each session mints a
fresh UUID, and that one value is stamped everywhere:

- **Client → Sentry.** `SessionTelemetryBinder` writes it onto the crash-reporting scope as a
  `session_id` tag on every rollover, so every crash, error event, and user-feedback report
  carries it. `install_id` (stable per install) rides along as a second tag.
- **Client → server.** `DefaultClientHeadersProvider` sends it on every request as `X-Session-Id`
  (plus `X-Install-Id`).
- **Server → Tempo.** `installHttpServerTracing` (apps/server `plugins/Tracing.kt`) pins
  `session_id`/`install_id` onto the HTTP root span from the headers, and carries them in OTel
  Baggage so `BaggageAttributeSpanProcessor` copies them onto **every child span** — the whole
  trace tree matches `{ .session_id = "…" }`, not just the root.
- **Server → Loki.** CallLogging lifts the same headers into MDC (`plugins/Observability.kt`),
  and the logback OTel appender forwards MDC as log attributes (`captureMdcAttributes` in
  `logback.xml`), so every backend log line for the request is filterable by `session_id`.
- **Server → Sentry.** `captureToSentry` tags server errors with the MDC `session_id`/`install_id`
  plus the active `trace_id`/`span_id`, so a backend error links to both the client session and
  its Tempo trace.
- **Client → Loki.** `GrafanaLogTree` (`:libraries:telemetry:impl`) stamps `session_id` /
  `install_id` / `is_offline` / `debug_session` on every app-event record it exports.

The key naming rule: it is always the underscore form `session_id`, in all systems, so one query
string works everywhere. The same rule applies to any context you add — if a key exists on backend
spans and client Sentry tags, spell it identically (`Telemetry.setContext(key, value)` client-side,
`SpanAttrs` server-side).

## Loki label conventions

Stream labels are only `service_name` + `deployment_environment`. Everything else — `event_name`,
`session_id`, `install_id`, `debug_session`, event attributes, `detected_level` — is **structured
metadata**: filter with pipes, never line filters.

**`debug_session="false"` belongs on every product query.** It is the SPEC 19 latch: true for the
whole life of a process in which the debug menu was opened, stamped by the tree rather than by any
call site so no event can be missing it (L63).

```
# All app events from prod clients
{service_name="drop2048-client", deployment_environment="prod"} | event_name != ""

# One event type
{service_name="drop2048-client"} | event_name="app.launched"

# Client Warn+ logs (no event_name — that's how you tell them from events)
{service_name="drop2048-client"} | detected_level=~"warn|error"

# Server logs for one session
{service_name="drop2048-server"} | session_id="<uuid>"
```

Client records also carry resource attributes: `service.version`, `platform` (android/ios),
`build_number`, `commit_sha`, `release_channel`, and `deployment.environment` (dev for debug
builds, prod for release).

## How to find a session

Start from wherever the report landed and pivot on the id:

1. **From a Sentry issue or feedback report:** copy the `session_id` tag.
2. **Client side of the story:** `{service_name="drop2048-client"} | session_id="<uuid>"` in
   Loki — the app events and Warn+ logs for that session, each stamped with `is_offline` *at emit
   time* (a record that shipped later from the disk buffer still says what connectivity looked
   like when it happened). Feedback reports also carry a `session-log.txt` attachment — the
   in-memory ring buffer of fine-grained logs that never left the device.
3. **Backend side:** `{service_name="drop2048-server"} | session_id="<uuid>"` for logs;
   `{ .session_id = "<uuid>" }` in Tempo for every request trace the session produced.
4. **The reverse direction works too:** a server error in Sentry carries `trace_id` (paste into
   Tempo) and `session_id` (pull the client's events), so backend-first investigations reach the
   client story in one hop.

Build provenance closes the loop: client Sentry events are tagged `commit_sha`/`commit_branch`
(injected at build time — see `loadVersionMetadata` in build-logic), so a report pins to the exact
code that produced it.

## Credentials and kill switches

All client telemetry credentials are build-time injected and env-optional (`loadTelemetryMetadata`
in build-logic → `TelemetryInfo` in `:libraries:core`): blank `SENTRY_DSN` disables crash
reporting; blank Grafana values leave the OTLP pipe dormant. Nothing breaks in a fresh clone.

At runtime, remote config owns the levers (`:libraries:telemetry:impl` `TelemetryConfigValues`):
`telemetry.appEventsEnabled` (instant kill switch), `telemetry.appEventsSampleRate` (per-session
sampling, stable-hashed so a session's events are all-or-nothing), and
`telemetry.klogForwardingEnabled` (Warn+ log mirroring). The server's OTel pipeline is gated by a
single env var: `OTEL_EXPORTER_OTLP_ENDPOINT` unset → stdout exporters, set → OTLP/HTTP.

## The two dashboards watched weekly

SPEC 17 names two numbers, and nothing else on a dashboard matters as much:
**median level reached** and the **distribution of highest tier reached**.
Nobody reaching 1024 means too hard; most runs reaching 2048 means too easy.

Both are defined here concretely enough to be created the day the Grafana
credentials land (they are on `OWNER-TODO.md`). Both read `run.end`, both filter
`debug_session=false`, and both segment by `platform` — an Android median and an
iOS median are two different games until proven otherwise.

### 1 · Median level reached

A time series, one point per day, over the last 30 days.

```logql
quantile_over_time(
  0.5,
  {service_name="drop2048-client", deployment_environment="prod"}
    | event_name="run.end"
    | debug_session="false"
    | mode="ENDLESS"
    | unwrap level [1d]
) by (platform)
```

**Endless only.** A Daily run is one seeded board that everybody plays and its
level distribution is a property of that day's seed, not of the balance. Mixing
the two puts a spike in the curve every time a day happens to be generous.

Second panel, same query at `0.9`, because a median that holds while p90
collapses is a different finding from both of them moving.

**The number to compare it against** is `tools/balance`'s clocked Greedy median
— about twenty, and L41 says no clocked median should be quoted more precisely
than that until `steer_ms_p50` has answered for `decisionMillis`. A live median
well under the harness's is the model being too generous; well over it is the
policies being dumber than real players, which is what they are for.

### 2 · Distribution of highest tier reached

A bar chart over the last 7 days, one bar per tier, as a share of runs.

```logql
sum by (highest_tier) (
  count_over_time(
    {service_name="drop2048-client", deployment_environment="prod"}
      | event_name="run.end"
      | debug_session="false"
      | mode="ENDLESS" [7d]
  )
)
```

**`highest_tier` is the tier *reached*, never the one at rest** (D7, SPEC 4.4).
A 2048 bursts its own row, so it is never on the board when a run ends and the
literal reading reports 0% of the game's defining moment. The client already
sends the right number — `RunTally.highestTier` folds the merge results, not the
final board — and this is the sentence that stops someone "fixing" it.

The two rungs to watch are 1024 and 2048. The same shape is printed by
`tools/balance`'s highest-tier histogram, so the offline and live bars go on one
axis.

### Supporting: the clutter panel

A heatmap of `clutter` from `run.sample` by `level`. The point of it is that the
balance harness prints the same metric from the same function in
`:libraries:cascade`, on the same every-10th-drop cadence — SPEC 4.4 requires
that and `ClutterParityTest` plus `RunSampleTest` pin both ends of it. Clutter
climbing with level is the early warning that the spawn floor is mistuned.

### Supporting: the decision-time panel

`steer_ms_p50` and `tap_gap_ms_p50` off `run.end`, plus `steer_ms` off
`run.sample` bucketed by `level`. This is the answer to L41, and it is the one
panel whose first week of data retires an assumption rather than confirming one.
Quote the steered-only median together with
`drops_unsteered / (drops_steered + drops_unsteered)` — the times are censored
data and a median without its censoring rate is a number with no error bar.

### Supporting: which interstitial gate is doing the work

`ads.interstitial_blocked` by `reason`. C10 put a **named** reason on that event
for exactly this: a live app can be asked which of SPEC 12's rules is refusing,
rather than the rules being tuned by argument.

The event registry lives in [`app-events.md`](app-events.md).
