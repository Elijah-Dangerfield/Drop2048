# Client app events

The registry of structured events the client emits for product analytics. One event = one
`logEvent(name, attrs)` call (the extension in `:libraries:core` `logging/AppEvents.kt`) riding
the normal KLog tree system: it lands in logcat/os_log, as a Sentry breadcrumb, and — via
`GrafanaLogTree` in `:libraries:telemetry:impl` — as an OTLP log record in Grafana Cloud Loki.
Query conventions are in [`observability.md`](observability.md).

Dashboard queries treat this page as the source of truth for names and attributes. Names are
dot-namespaced snake_case; every record automatically carries `session_id` + `install_id` +
`is_offline` + `debug_session` (per-record) plus resource attributes (`service.name="drop2048-client"`,
deployment environment, version, platform). `is_offline` is `AppState.isOffline` captured **at
emit time** — records that ship later from the disk buffer still say what connectivity looked
like when the event happened, so reliability funnels can segment "emitted offline" without span
archaeology. `debug_session` is the QA latch (SPEC 19, L63) and is stamped by `GrafanaLogTree`
rather than by any call site — one place to be wrong instead of forty, and the forgotten one would
be invisible because QA data looks exactly like real data. **Every dashboard filters
`debug_session=false`.**

**Delivery is durable, effectively at-least-once.** The export chain is batch → disk buffer →
OTLP: every batch is written to a file-backed buffer (`<files>/telemetry/…`, via
`durableLogRecordProcessor`) before export and deleted only after the gateway acknowledges it, so
events emitted offline survive process death and ship on a later launch or flush tick. Retention
is the library's defaults — 100 buffered batches, 30-day max age — after which oldest batches are
dropped. A record can rarely ship twice (export acknowledged but the process dies before the
buffer delete), so dashboards counting events should tolerate the odd duplicate rather than
assume exactly-once. Two edges remain lossy by design: records ride in RAM for up to one flush
tick (5s) before reaching disk, and `TelemetryBackgroundFlusher` closes most of that window by
force-flushing the pipe (RAM → disk → export attempt) on every app background — the last reliable
moment before the OS suspends or kills the process.

**Deliberate pipeline calls** (so nobody re-litigates them blind):

- **Batch tuning stays at library defaults** (2048-record queue, 5s flush, 512-record export
  batches, 30s export timeout). Typical volume is a handful of events per user-minute; the
  defaults are sized far above it and the 5s RAM window is bounded by the background flush.
- **Exports are NOT gated on `AppState.isOffline`.** Tempting (skip doomed POSTs while offline),
  but `isOffline` also trips on *backend* unreachability — and surviving backend outages is the
  whole reason this pipe goes direct to Grafana rather than through our server. A failed export
  while offline just stays in the buffer; the DNS failure is contained by
  `FailSafeLogRecordExporter`.
- **`telemetry.appEventsEnabled` + `appEventsSampleRate` stay separate.** The flag is an instant
  kill switch for library bugs / ingest incidents and reads as one in the QA menu; the rate is a
  gradual volume dial. Collapsing them makes the emergency lever a magic number.
- **iOS `previous_exit` is a day-granular MetricKit sample, not per-run truth.** iOS has no
  per-launch exit API, so `IosPreviousExitProvider` subscribes to `MXAppExitMetric`, classifies
  each day-window's foreground exits to the most severe (crash > anr > oom > clean), persists the
  result, and the next launch reports it exactly once (re-reporting every launch would multiply
  one crash by launch frequency). Background jetsam kills are deliberately excluded — routine on
  iOS, they'd read as fake OOMs next to Android's user-perceived `REASON_LOW_MEMORY`. MetricKit
  never delivers on the simulator; only real devices produce non-unknown values.

**Rules for adding events:** emit through the `logEvent` extension only (never a raw
`EXTRA_APP_EVENT` extra), fire on user actions / state transitions — never per-frame, per-poll,
or per-flow-emission — and add the event here in the same change. Client events answer
intent/funnel/abandonment questions; the backend DB stays source-of-truth for anything already in
a ledger.

## Engagement & session shape

| Event | Attributes | Fires |
|---|---|---|
| `app.launched` | `cold_start` (always true), `previous_exit` (clean/crash/anr/oom/unknown) | Once per cold start, on the boot foreground (`AppLaunchedEmitter`) — after the session tracker rolls session #1, so it shares the boot's `session_id` with every other event (it used to fire at DI init and land orphaned on a pre-rollover id). Doubles as the pipeline smoke test. `previous_exit` comes from Android's historical exit reasons (API 30+; older devices report `unknown`); **iOS derives it from MetricKit**, day-granular and up to 24h late — most iOS launches say `unknown`. Always segment by platform before reading exit rates |
| `app.foregrounded` | `cold_start` | Every foreground (`LifecycleAppEventLogger`); `cold_start=true` on the boot foreground. Count users/sessions from this event, not `app.launched` |
| `app.backgrounded` | `session_duration_sec` | Every background; whole seconds since the matching foreground (monotonic clock), so session length is a direct query — no span join. Omitted in the (shouldn't-happen) case of a background with no prior foreground |

## Reliability from the client's chair

The events that motivated shipping direct-to-Grafana: what never reaches the backend.

| Event | Attributes | Fires |
|---|---|---|
| `net.backend_unreachable` | `operation`, `error_kind` (timeout / exception class) | Shared `NetworkCall` failure path, non-HTTP failures only — an HTTP status IS reachability |
| `net.offline_banner` | `visible`, `os_online`, `backend_reachable` | Each edge of the app-wide offline banner (`AppStateImpl`), carrying which signal drove it |
| `conn.regained` | — | Reserved for offline→online recovery signals (`ConnectivityEdgeDispatcher` drives the app event; emit here if you need it in Loki). Apps with a long-lived socket should extend the `conn.*` namespace: `conn.reconnecting` (`attempt`), `conn.recovered` (`attempts`, `downtime_ms`), `conn.reconnect_failed` (`attempts`) |

## Product funnels

SPEC 17's set, whole. Keep the pattern when adding to it: one row per event, name the attributes
and the exact fire site — and **assert the fire site, not the definition**. C8's own tests drive
`GameViewModel` and read a planted `LogTree`, so deleting a `logEvent` line reds the caller's test
and leaves `:libraries:core`'s green (L55). An event with no production call site is worse than no
event, because it looks like coverage on a dashboard that will never have data.

| Event | Attributes | Fires |
|---|---|---|
| `run.start` | `seed`, `level` | `GameViewModel.adopt` — every run that begins, including the one the tutorial hands over to |
| `run.resume` | `level`, `score` | A run restored from disk on launch (SPEC 11) |
| `run.sample` | `drop`, `level`, `tick_ms`, `fill_pct`, `highest_tier`, `clutter`, `steer_ms`, `tap_gap_ms`, `steps` | **Every 10th drop**, in `GameViewModel.sampleDrop` (SPEC 17). `clutter` is `GameState.clutter` from `:libraries:cascade` on `GameState.isSampleDrop` — *the same property and cadence `tools/balance` prints*, which is the whole point (SPEC 4.4). `steer_ms` / `tap_gap_ms` are the decision-time instruments below and are **absent**, not zero, on a drop with fewer than one / two column steps. Tutorial drops are not sampled |
| `run.end` | `score`, `level`, `blocks`, `duration_ms`, `highest_tier`, `cause`, `bursts`, `merges`, `longest_cascade`, `cascades_1`/`_2`/`_3`/`_4plus`, `seed`, `recorded`, `steer_ms_p50`, `steer_ms_p90`, `tap_gap_ms_p50`, `drops_steered`, `drops_unsteered`, `steps` | Every completed run, in `GameViewModel.endRun` (SPEC 17). A run abandoned via Restart or Quit is not a completed run and does not fire (D12). `highest_tier` is the tier **reached** (D7) — a 2048 bursts its own row, so "at rest" would report zero of them. `recorded` says whether the three writes that claim a player did something happened; it is narrower than the `debug_session` stamp and answers "no row was written" vs "no row reached us" |
| `funnel.first_run_completed` | `score`, `level` | The first run this install ever played to the end, once ever (`AppData.hasCompletedARun`) |
| `funnel.return_day` | `day` (1/3/7), `days_since_install` | `RetentionReporter` on a foreground, once per milestone. **Reached-or-passed**: a player away for five days reports day 1 and day 3 together, because the curve asks "were they still here by day N" |
| `tutorial.started` / `tutorial.step_reached` / `tutorial.skipped` / `tutorial.completed` | `drop` (all but `started`) | SPEC 13's guided run, in `GameViewModel`. `step_reached` also fires on arrival at lesson one — without it, a player who quit on the first beat and one who never opened the app are the same row |
| `engine.fault` | `fault` | The engine reporting a bug in itself (SPEC 18.1, 18.14). Should be zero; if it is not, that is the finding |
| `leaderboard.submitted` | `board`, `value` | A value the platform **accepted**, in `RealLeaderboards.send`. Deliberately not fired on a refusal: signed out, restricted and offline are the normal state for most players, and an event on every one of them would drown the one that means something. Zero of these on iOS while `run.end` climbs is the shape of the bug this chunk exists to prevent |

## The two decision-time instruments

`decisionMillis` — the beat between a block appearing and the player's first
sideways input — is the difficulty dial nobody has measured (L41). Swept from
150ms to 500ms it moves the balance harness's median level by five and its 1024
rate by a factor of three, which is more than the drop clock and the entire
spawn table put together. Every clocked balance number in this project is
conditional on it and on `tapMillis`, the gap between consecutive column steps.
`DecisionTimer` in `:features:game:impl` is what turns the two guesses into
measurements.

**They measure what the harness models, not something adjacent.** `DropClock`
issues one `Input.MoveLeft`/`MoveRight` per `tapMillis` after an initial
`decisionMillis` wait, so the live instrument counts **accepted engine column
steps**. A drag across three columns is three steps here exactly as it is three
taps there. Count gestures instead and the live tap rate reads three times
slower than the number it is compared against, on the control most players use.

**A drop the player never steered is censored, not zero.** The modelled player
always reaches its target, so "never steered" does not exist offline; live it is
common. Zeroing it would make the population read three times faster than it is
and dropping it would throw out every easy board, so `drops_unsteered` ships
beside `drops_steered` and the time attribute is simply absent. Query the
steered-only median and quote the censoring rate with it.

**A drop that spanned a pause is discarded.** One backgrounded phone would
otherwise sit in the tail of the histogram forever.

**Two reporting rates, and neither is a firehose.** Per-drop timings ride on the
every-10th-drop `run.sample` — no extra records, and arriving next to `level` is
what lets the dashboard ask whether players slow down as the board speeds up.
Run-level quantiles ride on `run.end`, so a four-drop run still contributes. A
200-drop run produces twenty samples and one summary, against the two thousand a
per-drop event would have been.

| Attribute | On | Means |
|---|---|---|
| `steer_ms` | `run.sample` | Millis from the block becoming the player's to their first column step, this drop. Absent if they never steered |
| `tap_gap_ms` | `run.sample` | Millis between the first and second column step, this drop. Absent below two steps |
| `steer_ms_p50` / `_p90` | `run.end` | The run's decision time, from a 50ms-bucket histogram. Absent if no drop was steered |
| `tap_gap_ms_p50` | `run.end` | The run's tap rate, same shape |
| `drops_steered` / `drops_unsteered` | `run.end` | The denominator and the censoring rate |

## Ads and monetization

| Event | Attributes | Fires |
|---|---|---|
| `ads.rewarded_requested` | `placement` | `RealAdGate.rewarded`, before the network is asked |
| `ads.rewarded_result` | `placement`, `outcome`, `latency_ms`, `error_kind` | The same call returning. `outcome` covers rewarded / dismissed / no-fill / offline / failed |
| `ads.continue_offered` / `ads.continue_declined` / `ads.continue_result` | `continues_used`, `outcome` | SPEC 12's rewarded continue, in `GameViewModel` |
| `ads.interstitial_blocked` | `reason` | `RealInterstitialGate`, carrying **which named gate refused** — that is why the reason is there, so a live app can be asked which rule is doing the work rather than guessed at |
| `ads.interstitial_result` | `outcome`, `error_kind`, `runs_this_session` | An interstitial actually shown |
| `iap.paywall_shown` | `trigger` | `RealPaywallCoordinator.requestOffer`, on an **accepted** request |
| `iap.upsell_tapped` | `surface`, `offered` | The stacked-out card's control, in `GameViewModel`. Recorded at the control rather than in the coordinator, because a tap the coordinator refuses produces no `paywall_shown` and would read downstream as a card nobody touched |
| `iap.purchase_started` / `iap.purchase_result` | `trigger` / `outcome`, `error_kind`, `trigger` | `RealEntitlements.purchasePro`. The pair is what separates an abandoned store sheet from one that never opened |
| `iap.restore_started` / `iap.restore_result` | — / `outcome` | `RealEntitlements.restore`. The result event cannot answer "attempted": a restore hanging on an unreachable store never produces one |

## Warn+ log forwarding (not events)

Besides events, `GrafanaLogTree` forwards plain KLog lines at Warn and above to Loki as ordinary
OTLP logs — client errors visible without waiting on a Sentry crash. Query them with

```
{service_name="drop2048-client"} | detected_level=~"warn|error"
```

These records have **no `event_name`** (that's how you tell them apart from events); they carry
`session_id`/`install_id`, the logger `tag`, and `exception_type`/`exception_message` when a
throwable was attached. Gated by `telemetry.klogForwardingEnabled` (remote config, default **on**)
and still behind the `telemetry.appEventsEnabled` kill switch + per-session sampling — flipping
the forwarding flag off never affects events.

In-app feedback is not a Loki event: `FeedbackRepository` sends it straight to Sentry via
`Telemetry.captureUserFeedback` (verbatim message, screenshots, session-log attachment).
