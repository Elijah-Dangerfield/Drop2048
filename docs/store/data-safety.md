# Data safety and privacy declarations

Answers for Google Play's **Data safety** form and Apple's **App Privacy** nutrition label, derived
from what this tree actually does. Every row names the file and the mechanism that makes it true.
Where the honest answer is "not determined", it says so and says where to look, because a guess
here is a policy violation rather than a typo.

Derived 2026-09-10 for C13, against `75cb605`, re-checked against `5244bbe` when C8 landed
mid-chunk (§2.3a), and **revised in C13a**, which fixed the five findings §8 recorded rather than
leaving them for the store build. Every §8 entry now says what was done. The rows that moved as a
result are §2.1's lifetime, §2.5's feedback attachment, §2.7, §2.10 and §2.11.

**Do not fill a store form straight out of this file.** Treat the citations as a map of where to
look and re-open them. Sodogku's own history is the argument: it shipped a data-safety document
that did not match its code, twice, and both times the mismatch was a fact that had quietly moved
underneath a correct-looking row.

## Re-derive this before filing

1. **C8 (telemetry) landed at `5244bbe` while this was being written, and its diff was re-read
   against every row below before this file was committed.** §2.3's table is the pre-C8 tree at
   `75cb605`; §2.3a is what C8 added and what it does to the declarations. The short version:
   **no data type on either form changes.** Everything C8 added is gameplay and funnel
   measurement under App activity / Product Interaction, which were already declared. The one
   structural change worth knowing about is that every OTLP record now also carries a
   `debug_session` boolean, stamped once in `GrafanaLogTree` rather than at forty call sites.
2. **Neither telemetry backend has credentials on this machine.** `local.properties` holds only
   `sdk.dir`, so `Drop2048BuildConfig.SENTRY_DSN`, `GRAFANA_OTLP_BASE_URL`, `GRAFANA_OTLP_INSTANCE_ID`
   and `GRAFANA_LOGS_WRITE_TOKEN` all resolve blank, and both pipes are switched off in any build
   produced here (`GrafanaAppEvents.kt:147` requires all three non-blank; `SentryRuntimeConfig`
   treats a blank DSN as disabled). **The rows below are written for the build that will ship**,
   which is the build with those secrets set. That is the correct way round: a label that describes
   a credential-less build is wrong the first time CI supplies one.

The five things most likely to invalidate a row:

- a new `logEvent` attribute (`docs/practices/app-events.md`),
- a new SDK, which means two catalogues: `gradle/libs.versions.toml` for Android and common code,
  and the Swift package list in `apps/ios/iosApp.xcodeproj/project.pbxproj` for iOS,
- the iOS ad SDK arriving, which changes Apple's answers more than anything else here (§5, §7.2),
- a new destination for anything the player types,
- anything that starts handing an identity to Sentry. `Telemetry.setUser` **exists** in this tree
  and has no caller; see §8.1. Sodogku deleted its equivalent and pinned the deletion with a test.

Line numbers rot faster than facts. Check the symbol, not the number.

---

## 1. The facts that shape every answer

**There are no accounts of ours.** The Supabase identity stack was deleted in C0 (SPEC 20; there
is no sign-in, no user id, no server-side user record, and `apps/server` has no auth plugin). So
nothing the app collects can be linked by us to a name, an email or an account, because none
exists. `sendDefaultPii = false` (`AppTelemetry.kt:359`), so the Sentry SDK does not attach IP or
user agent on its own.

**Everything we do collect rides on one pseudonymous identifier**, `AppData.installId` (§2.1).

**The player can sever that link themselves.** Settings → "Delete local data" writes a fresh
`AppData` (`PlayerDataEraser.deleteLocalData`, `features/settings/impl/.../PlayerDataEraser.kt:65-87`),
which carries the accessibility settings across and leaves `installId` at its `null` default, so
the next read mints a new UUID. Prior telemetry is not deleted, but it is orphaned: nothing can
tie it to the install any more. This is a materially better position than Sodogku's and it is what
makes Play's deletion question answerable. See §7.1 for the exact wording to use.

**The two platforms are in very different states and the labels are not the same document.**
Android ships AdMob, UMP consent and Play Billing. iOS ships **neither an ad SDK nor a billing
implementation**: there is no `GoogleMobileAds` Swift package in `project.pbxproj` (only
`sentry-cocoa`), no `GADApplicationIdentifier` in `Info.plist`, no `NSUserTrackingUsageDescription`,
and `StoreBilling` falls through to `NoStoreBilling` on iOS. **So an iOS build today reads no IDFA
and does no tracking.** §5 is written for that build and §7.2 says exactly what changes when the
SDK lands.

**The kids-versus-general-audience question is still open** (`OWNER-TODO.md`, "Kids theming / age
rating"), and it changes these answers more than anything else in this document. §6 gives the
answer set for each branch rather than picking one. Everything in §4 and §5 is written for the
**general audience** branch, which is what the code implements
(`TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE`, `AdMobAdNetwork.kt:209-216`).

---

## 2. Data inventory, traced

### 2.1 Install ID, the one identifier everything hangs off

| | |
|---|---|
| **What** | A random UUID v4, app-generated. Not a hardware id, not the advertising id, not derived from anything about the device. |
| **Minted** | `libraries/drop2048/impl/.../CachedInstallIdProvider.kt`, on first read, then persisted. |
| **Stored** | `AppData.installId` (`libraries/drop2048/src/commonMain/.../AppCache.kt:28`), written to the app's files directory by the persistent cache. |
| **Leaves the device, 1** | `X-Install-Id` request header on every call to our own server (`ClientHeaders.kt:48`, attached in `NetworkClientImpl`). **The only endpoint a shipping client calls is `GET /v1/app-config`** (`RemoteConfigRemoteDataSource.kt:55`). Grepped for every `client.get` / `client.post` under `libraries`, `features` and `apps/compose`: the only other one is the OTLP exporter below. The rest live in `apps/admin`, a separate web app that ships in no player binary. |
| **Leaves the device, 2** | As the `install_id` attribute on **every** OTLP log record sent to Grafana Cloud (`GrafanaLogTree.kt`). |
| **Leaves the device, 3** | As a Sentry scope **tag** named `install_id` (`AppTelemetry.kt:150-152`, wired by `SessionTelemetryBinder.kt:55`). Because it is on the scope, a native crash symbolicated on the next launch still carries it. |
| **What our server does with it** | Bucketing key for config rollouts and allow/deny targeting, and lifted into logging MDC / OTel spans / the server's Sentry scope. Not written to Postgres. |
| **Lifetime** | Until the app is uninstalled **or** the player uses "Delete local data". Unqualified since C13a set `android:allowBackup="false"`; before that Auto Backup could restore it onto a second device. See §7.3. |
| **Shown to the user** | Never. `grep -rn "installId" features apps/compose/src` returns no UI reference, including the debug menu. This matters for §7.1. |

### 2.2 Advertising identifier: Android only, today

Collected by the Google Mobile Ads SDK, not by our code. We never read it directly.

- Android SDK: `com.google.android.gms:play-services-ads` (`libs.versions.toml`), used in
  `libraries/ads/impl/src/androidMain/.../AdMobAdNetwork.kt`. The SDK's own manifest contributes
  `com.google.android.gms.permission.AD_ID` through manifest merge; it is not written in
  `apps/compose/src/androidMain/AndroidManifest.xml`.
- Child-directed flag: `setTagForChildDirectedTreatment(...FALSE)` at `AdMobAdNetwork.kt:209-216`.
  `tagForUnderAgeOfConsent` is deliberately left unset, with the reason in the class KDoc.
- EEA/UK consent runs before the first request: `consentThenInitialise()` (`AdMobAdNetwork.kt:186-222`),
  gated on `consent.canRequestAds()` at `:203`, form at `:240-246`. Ordering is enforced inside
  `prepare()` rather than at call sites.
- **iOS reads no advertising identifier at all.** No ad SDK is linked (§1), so there is nothing on
  that platform to disclose today.
- **Today the app requests Google's published *test* ad units**, `AdUnits.useTestUnits = true`
  (`libraries/ads/src/commonMain/.../AdUnits.kt`), and `AndroidManifest.xml`'s
  `com.google.android.gms.ads.APPLICATION_ID` is Google's sample app id. **This does not change the
  Android disclosure.** The SDK is still initialised and still reads the advertising identifier, so
  from the first public Android release the ad-id rows are required.

### 2.3 Product analytics to Grafana Cloud

- Transport: `GrafanaLogTree` emits OTLP log records; `OtlpJsonLogRecordExporter.kt:42` POSTs them
  to `{GRAFANA_OTLP_BASE_URL}/v1/logs` with a build-time basic-auth write token. Deliberately
  direct to Grafana rather than through our own backend, so reliability events survive a backend
  outage (`docs/practices/app-events.md`).
- Every record carries `session_id`, `install_id`, `is_offline`, plus resource attributes
  `service.name="drop2048-client"`, version, deployment environment and platform.
- Buffered to disk before export under `<files>/telemetry/…`, retained to the library defaults of
  100 batches / 30 days.
- **What is actually in the payloads**, read off the `logEvent` call sites in the tree rather than
  off the registry page, because the registry is behind the code right now:

  | Area | Events seen in the tree | Fire site |
  |---|---|---|
  | Session | `app.launched`, `app.foregrounded`, `app.backgrounded`, `app.startup` | `AppLaunchedEmitter`, `LifecycleAppEventLogger.kt:37,45`, `StartupReporter.kt:83` |
  | Gameplay | `run.start`, `run.resume`, `run.end` (`score`, `level`, `blocks`, `highest_tier`, `cause`, `debug_session`), `engine.fault` | `GameViewModel.kt:339,580,1065,1704` |
  | Tutorial | `tutorial.started`, `tutorial.step_reached`, `tutorial.skipped`, `tutorial.completed` | `GameViewModel.kt:382,387,407,448` |
  | Ads | `ads.rewarded_requested`, `ads.rewarded_result`, `ads.continue_offered`, `ads.continue_result`, `ads.continue_declined`, `ads.interstitial_blocked` | `RealAdGate.kt:106-133`, `RealInterstitialGate.kt:134,146`, `GameViewModel.kt:1173,1227,1277` |
  | Monetization | `iap.paywall_shown`, `iap.purchase_result` (`outcome`, `error_kind`, `trigger`), `iap.restore_result` | `RealPaywallCoordinator.kt:65`, `RealEntitlements.kt:133,164` |
  | Leaderboards | `leaderboard.submitted` (`board`, `value`), `leaderboard.achievement_reported` (`achievement`) | `RealLeaderboards.kt` |
  | Reliability | `net.backend_unreachable`, `net.offline_banner`, jank | `NetworkCall.kt:159`, `AppStateImpl.kt:66`, `AndroidJankMonitor.kt:68` |
  | Launch gates | `gate.raised` (`gate`, `blocking`) | `LaunchGateViewModel.kt:151` |

  All of it is gameplay and funnel measurement. **None of it introduces a data type beyond the ones
  declared in §4 and §5.**
- **There was a Daily row here.** It listed `daily.start`, `daily.end`, `daily.refused` and
  `daily.retry`, fired from `GameViewModel` and from `DailyViewModel`, which no longer exists. D27
  deleted the mode on 2026-09-20 and nothing emits those names now. Removing them takes no declaration with them,
  because every one was App activity and the other events still cover that box. The reason to record
  it is the opposite direction: a form that still lists them is declaring collection the app does not
  do, and that is the kind of wrong a store holds you to.
- **A second mode forwards plain Warn-and-above log lines**, not just events. Those carry the log
  body, the logger `tag`, and `exception_type` / `exception_message`. The body is whatever our own
  code passed to `KLog`.
- Kill switches exist but are **operator-side, not user-side**: `telemetry.appEventsEnabled`,
  `telemetry.appEventsSampleRate`, `telemetry.klogForwardingEnabled` in remote config. There is no
  in-app analytics opt-out. `PlayerSettings.diagnosticsOptIn` is **not** one, see §2.5.
- **Grafana Cloud is a processor on our own account, not a data recipient.** Under Play's
  definition, transfer to a service provider processing on our behalf is not "sharing". Same for
  Sentry. AdMob is different: see §4.

### 2.3a What C8 added, and why no declaration moves

Re-read against the diff `75cb605..5244bbe` and `docs/practices/app-events.md` as it now stands.

| C8 added | Carries | Declaration effect |
|---|---|---|
| `run.sample`, every 10th drop | `drop`, `level`, `tick_ms`, `fill_pct`, `highest_tier`, `clutter`, `steer_ms`, `tap_gap_ms`, `steps` | None. More volume, same type. SPEC 17 asked for exactly this and `clutter` is deliberately the same property `tools/balance` prints |
| **The decision-time instruments** `steer_ms`, `tap_gap_ms`, and the `run.end` percentiles `steer_ms_p50` / `_p90`, `tap_gap_ms_p50`, `drops_steered`, `drops_unsteered` | Millisecond timings of the player's own taps | None. This is the one worth pausing on, because "how fast does this person react" sounds like biometric or sensitive data and is not: it is interaction timing inside one game screen, it is bucketed into 50ms histograms before it leaves, and it is **App activity → App interactions** on Play and **Usage Data → Product Interaction** on Apple. Neither store has a narrower box for it |
| `run.end` grown to carry `duration_ms`, `bursts`, `merges`, `longest_cascade`, `cascades_1`…`_4plus`, `seed`, `recorded` | Gameplay outcomes | None. `mode` and `daily_date` were on this list and went with D27 |
| `funnel.first_run_completed`, `funnel.return_day` | `score`, `level`; `day`, `days_since_install` | None. Retention is App activity. `days_since_install` is derived from `AppData.firstLaunchAt`, a local timestamp, not from anything about the person |
| `tutorial.step_reached`, `iap.upsell_tapped`, `iap.purchase_started`, `ads.interstitial_result`, `ads.rewarded_result` latency | Funnel positions | None |
| **`debug_session` on every OTLP record** | A boolean | None, and it is an improvement: stamped once in `GrafanaLogTree` from `DebugSessionFlag` rather than at forty call sites, and every dashboard filters it false |
| Three new `AppData` fields: `firstLaunchAt`, `returnDaysReported`, `hasCompletedARun` | Local only | None. §2.7, device-local, and cleared by "Delete local data" like everything else in `AppData` |

**So the answer is the same on both forms.** What changed is that §4's App-activity row and §5's
Product Interaction row now cover a good deal more, which is what those rows are for.

### 2.4 Crash and diagnostics (Sentry)

- `AppTelemetry` initialises the Sentry KMP SDK; `isEnabled` is `dsn.isNotBlank()`, and the DSN is
  injected at build time (`build-logic/.../Versioning.kt:153`, from env `SENTRY_DSN` then
  `local.properties` key `sentry.dsn`). **Blank on this machine**, so Sentry reports nothing from a
  build produced here. See the header note.
- `attachStacktrace = true`, `sendDefaultPii = false`, traces sampled at 0.15 in release,
  `enableAutoSessionTracking = true` (so Sentry session records ship alongside events), error
  events never sampled away (`SentryRuntimeConfig.forApp`, `AppTelemetry.kt:335-370`).
- Scope carries `platform`, `build_type`, `release_channel`, commit sha and branch, `route` on
  every navigation, `session_id`, `install_id`.
- Breadcrumbs are KLog entries at Info and above in release.
- iOS gets the SDK through the `sentry-cocoa` Swift package (`project.pbxproj:398-412`).

### 2.5 In-app feedback and bug reports, the row people forget

- `features/home/impl/.../feedback/FeedbackRepository.kt` forwards to
  `Telemetry.captureUserFeedback`, implemented at `AppTelemetry.kt:163-250`. There is no feedback
  backend of ours; a report is a Sentry event, and if Sentry is disabled it is dropped with a log
  line.
- What is sent, on a carrier event minted by `captureMessage`:
  - the player's **verbatim free text**, in the `UserFeedback.comments` body,
  - build version, commit sha and branch,
  - **the in-memory session log buffer as a `session-log.txt` attachment, only when the player
    has turned `diagnosticsOptIn` on.** C13a gated it; it used to ride on every report. The buffer
    holds Debug and above in release (`logPolicy.minBufferLevel`) and writes one line per entry as
    timestamp, level, tag and **message** — never the entry's context. An app event's message is
    its *name*, so a `run.end` buffers as "run.end" and its `score`, `level` and `highest_tier`
    do not. `SentryLogTreeTest` pins that.

    C13a also **clears breadcrumbs on the feedback carrier event**. Breadcrumbs are Info-and-above
    in release, `logEvent` is Info, and a breadcrumb *does* carry the event's attributes — so every
    feedback report was shipping scores by that route regardless of the switch. That was the larger
    of the two leaks and the one §8.2 had not spotted.
- Callers: `FeedbackViewModel.kt` and `BugReportViewModel.kt`, both of which now read
  `diagnosticsOptIn` and pass it. **The `email` and `screenshots` parameters are gone**, deleted in
  C13a along with `Telemetry.setUser`, and `NoIdentitySeamsTest` fails the build if any of the three
  returns. See §8.1.
- **`diagnosticsOptIn` is still not an analytics opt-out and must not be described as one on a
  form.** It is off by default and has no effect on Grafana or on Sentry's own crash pipeline. What
  it now governs, and did not before C13a, is what a **feedback report** carries: with it off, the
  message text alone; with it on, the device model, OS version, platform, version and channel
  appended to the message, plus the `session-log.txt` attachment. That is what
  `settings_diagnostics_hint` says, and `DiagnosticsOptInTest` asserts both directions on both the
  feedback and the bug-report form. See §8.2.
- The free-text box is user-typed. A player may put their name or email in it. Both stores treat
  that as user-generated content, which is why the "User content" rows are present even though the
  app asks for nothing identifying.

### 2.6 Purchases

- `libraries/billing/impl/src/androidMain/.../PlayStoreBilling.kt`. One managed product,
  `drop2048_pro` (`StoreBilling.kt:104`), non-consumable, $2.99 at current scope (SPEC 2, SPEC 12).
- The app never sees a payment instrument; the purchase token stays inside the store flow and is
  used only for acknowledgement. Nothing of ours transmits it.
- The only persisted trace is a boolean, `AppData.isProEntitled`, device-local, written only by
  `RealEntitlements`.
- Telemetry records the **outcome only**: `iap.purchase_result` (`outcome`, `error_kind`,
  `trigger`) and `iap.restore_result` (`outcome`). No price, no order id, no token. `product_id` is
  not carried, and there is exactly one product anyway.
- **iOS has no billing.** `NoStoreBilling` answers `Unknown`, never `Owned` (`StoreBilling.kt:111-126`),
  so Pro is unbuyable there and there is no iOS purchase row to declare until StoreKit is wired.

### 2.7 Device-local, and it genuinely does not leave (subject to §7.3)

- Room, at database version 9 (`libraries/storage/impl/.../db/AppDatabase.kt`): `run_record`,
  `achievement_fact`, `achievement_unlock`, and the template's leftover `example_user_data`.
  Version 9 is D27's hand-written `MIGRATE_AWAY_FROM_THE_DAILY`, which drops `daily_result`
  outright and deletes the Daily rows from the other two tables. An installed app that had one
  loses it on the next launch rather than carrying it forward.
- `AppData`: settings, the in-progress run snapshot (`savedRun`, one slot since D27 deleted the
  second one, `savedDailyRun`), the Pro boolean, legal acceptance versions, the install id.
- There is no sync, no account of ours and no server-side copy (SPEC 20). Settings says as much on
  screen, "There is no backup and no way to undo this" (`strings.xml:210`), and since C13a set
  `android:allowBackup="false"` that sentence is true on both platforms (§7.3).

### 2.8 Locale and country, and what is *not* location

- `X-Country-Code` (`ClientHeaders.kt:47`) and the standard `Accept-Language` header are sent to
  our config endpoint. Both come from the OS locale the user set.
- Not GPS, not the SIM, not IP geolocation. No location permission is declared on either platform.
  **Neither store's "Location" category applies.**

### 2.9 IP address

- Our server reads the client IP as an in-memory rate-limit bucket key only. It is not stored and
  not used to derive location.
- AdMob, Sentry, Grafana Cloud and, on iOS, Apple's Game Center necessarily observe the IP as the
  origin of the requests they receive. That is disclosed in the privacy policy rather than as a
  Data safety data type: Play's form has no IP-address type and only asks about location if IP is
  used to derive it. We do not.

### 2.10 Permissions, and the one that should not be there

- `apps/compose/src/androidMain/AndroidManifest.xml` declares **one** permission:
  `android.permission.VIBRATE`, for SPEC 9's haptics. Correct and needed.

  It declared `android.permission.CAMERA` and a matching `uses-feature` until C13a, which deleted
  both along with the template scaffolding they existed for: `CameraPreview`, `PhotoSaver` and
  `PermissionLauncher` are gone from `:libraries:ui` in all three source sets. See §8.3 for what
  survives.
- iOS declares one usage string, `NSUserNotificationsUsageDescription` (`Info.plist:19`). There is
  no remote-notification registration, no device token, no camera or photo-library usage string,
  and **no `NSUserTrackingUsageDescription`**, which is consistent with there being no ad SDK.

### 2.11 Game Center, iOS only

- `libraries/leaderboards/impl/src/iosMain/.../GameCenterServices.kt` authenticates via
  `GKLocalPlayer.local.authenticateHandler`, submits through `GKLeaderboard.submitScore`, and
  reports badges through `GKAchievement.reportAchievements`.
- Two boards (`Leaderboard.kt`): `…leaderboard.score_alltime` and `…score_weekly`. The Daily board
  was cut by D24 and the mode it would have ranked by D27, so do not go looking for a third. The
  value submitted is a score and nothing else. It goes to Apple, under the
  player's Game Center identity; we receive nothing back and store nothing.
- **Achievements are also sent** (D24), as an id and a completion of 100%, nothing more. The ids
  are the badge names already in the app's own catalog, which describe the badge and not the player.
  The set of badges a player has earned is a thing we already hold locally, so nothing new about
  them leaves the device beyond the fact that they earned it, sent to Apple under their own Game
  Center identity.
- We never read the Game Center player id, alias or display name. The only trace on our side is
  `leaderboard.submitted` with `board` and `value`, and `leaderboard.achievement_reported` with an
  achievement name. Neither carries an identity.
- On Android there is no path at all: `NoGameServices` reports unavailable from construction.
- **`iosApp.entitlements` now asks for Game Center and no longer asks for Sign in with Apple**
  (C13a, §8.4). That is a file edit, not a build: nobody on this machine can open Xcode
  (`OWNER-TODO.md`'s first blocking item), so the entitlement is **unproven** until someone
  archives the app against a provisioning profile that carries the Game Center capability. If it
  is wrong, authentication fails and every submission is silently refused, which is
  indistinguishable from a signed-out player and is what the fail-open design is for.
- What this does to the Apple label is the one open question in §7.4.

---

## 3. What is *not* collected, stated for completeness

Name, email, phone, address, contacts, calendar, photos or videos from the library, audio, files,
SMS, call logs, health, fitness, location of any kind, browsing history, search history,
installed-app inventory, payment instruments, credit info, push tokens. None of these has a read
path in the tree with a call site, and none has a usage string declared on iOS.

There is no longer a qualification. `android.permission.CAMERA` was declared without being used
until C13a removed it (§2.10, §8.3); a declared permission is not collected data, but it put a
camera line on the Play listing and invited the question.

---

## 4. Google Play, Data safety form

Answer **Yes** to "Does your app collect or share any of the required user data types?"

Answer **Yes** to "Is all of the user data collected by your app encrypted in transit?" Every
egress is HTTPS in practice: the OTLP exporter posts to the Grafana Cloud gateway, Sentry's SDK to
its DSN host, and `NetworkClient` to the app server over TLS. **One caveat, §7.5.**

"Do you provide a way for users to request that their data be deleted?" See §7.1. There is a
defensible **Yes** here, unlike in the sibling project.

| Data type | Collected | Shared | Ephemeral | Required/optional | Purposes | Why, and what makes it true |
|---|---|---|---|---|---|---|
| **Device or other IDs**, install id | Yes | No | No | Required | App functionality, Analytics | Random UUID from `CachedInstallIdProvider`, sent as `X-Install-Id`, as the `install_id` OTLP attribute and as a Sentry tag. Required because there is no in-app switch that turns it off; the only control is "Delete local data", which rotates it rather than disabling it. |
| **Device or other IDs**, advertising id | Yes | **Yes** | No | Required | Advertising or marketing, Analytics | Collected by `play-services-ads`, not by our code (`AdMobAdNetwork.kt`). Shared because AdMob is a third party, not a processor acting on our behalf. |
| **App activity → App interactions** | Yes | No | No | Required | Analytics, App functionality | The events in §2.3: run starts and ends, per-drop samples, tutorial steps, ad and paywall funnels, launch gates. |
| **App info and performance → Crash logs** | Yes | No | No | Required | Analytics (Crash reporting) | Sentry, `attachStacktrace = true`. True of any build with a DSN. |
| **App info and performance → Diagnostics** | Yes | No | No | Required | Analytics | Sentry performance traces at 0.15, the jank monitor and startup reporter in `:libraries:telemetry:impl`, and the Warn+ log forwarding in `GrafanaLogTree`. |
| **App info and performance → Other app performance data** | Yes | No | No | Required | Analytics | `app.launched` carries `previous_exit` (crash / anr / oom), from `AndroidPreviousExitProvider`. |
| **Messages → Other in-app messages** | Yes | No | No | **Optional** | App functionality, Developer communications | Free-text feedback and bug reports (§2.5). Optional because it only exists if the player types and submits it. The `session-log.txt` attachment rides with it. |
| **Financial info → Purchase history** | Yes | No | No | Optional | App functionality, Analytics | The conservative answer. We never see a payment method and Play's own purchase records are out of scope, but `iap.purchase_result` records to our analytics that a purchase succeeded or failed against an `install_id`. Declaring it costs nothing; not declaring it is a judgement call you would have to defend. |

**Rows that are deliberately absent:** Location (§2.8), Personal info (§1, §3), Photos and videos
(the app can send no image at all, no caller passes `screenshots`, §2.5), Contacts, Files and
docs, Health and fitness, Web browsing.

### "Linked to the user", read this before you fill the form

Play's definition of collected-and-linked is broad: data is linked if it is collected with, or
associated with, a persistent identifier. Everything above rides with `install_id`, which is
persistent for the life of the install. So none of it qualifies for "processed ephemerally" or
anonymous treatment, and the per-type follow-ups should be answered on the basis that the data is
tied to a device-scoped identifier.

**Confidence: high on the mechanism, medium on Google's current wording.** Verify against Play
Console Help, "Provide information for Google Play's Data safety section", at the time you file.
The wording has moved before.

### Ads, audience and content, Play side

- **Ads declaration:** Yes, the app contains ads.
- **Target audience:** 13+ on the general-audience branch. Do not enrol in Designed for Families.
  **Blocked on the kids decision** (§6).
- **Content rating (IARC):** see `age-rating.md`.
- Government app, news app, COVID app: no.

---

## 5. Apple, App Privacy nutrition label

Filed per app version in App Store Connect. Categories use Apple's own names.

**This section describes the iOS build that exists**: Sentry, Game Center, local storage, our
config endpoint, the Grafana pipe. **No ad SDK, no IDFA, no ATT, no billing.** The moment the
Google Mobile Ads Swift package is added, three rows change and the tracking answer flips. §7.2 is
that diff, written out, so it can be applied rather than re-derived.

| Apple category → type | Collected | Linked to the user | Used for tracking | Purposes | Mechanism |
|---|---|---|---|---|---|
| **Identifiers → Device ID** | Yes | See note | **No** | Analytics, App Functionality | Our own install id (`CachedInstallIdProvider`), device-scoped, not user-scoped. No IDFA is read today. |
| **Usage Data → Product Interaction** | Yes | See note | No | Analytics, App Functionality | The events in §2.3, via `GrafanaLogTree`. |
| **Diagnostics → Crash Data** | Yes | See note | No | App Functionality, Analytics | Sentry, through `sentry-cocoa` (`project.pbxproj:398-412`). |
| **Diagnostics → Performance Data** | Yes | See note | No | Analytics | Sentry traces, `IosJankMonitor`. Note `IosProcessStartTimeProvider` is a deliberate no-op, so no startup number is collected on iOS. |
| **Diagnostics → Other Diagnostic Data** | Yes | See note | No | Analytics | Warn+ log forwarding with `tag`, `exception_type`, `exception_message`. |
| **User Content → Other User Content** | Yes | See note | No | App Functionality | Free-text feedback plus the `session-log.txt` attachment (§2.5). No image can be attached: no caller passes `screenshots`. |

**One trap when copying the Play answers across.** Apple's purpose vocabulary is six values: Third-Party Advertising,
Developer's Advertising or Marketing, Analytics, Product Personalization, App Functionality and
Other Purposes. It has no equivalent of Play's **Developer communications**.
The feedback row is App Functionality on Apple and App Functionality + Developer communications on
Play, and that is not an inconsistency.

**Not collected, so leave unticked:** Contact Info (all), Health & Fitness, Financial Info,
**Purchases → Purchase History** (there is no iOS billing, §2.6), Location (precise and coarse),
Sensitive Info, Contacts, Browsing History, Search History, Photos or Videos, Usage Data →
Advertising Data, Identifiers → User ID.

**Identifiers → User ID** is not applicable: there is no account of ours and no user-scoped id
anywhere in our records. Game Center complicates the sentence but not, on my reading, this row,
because the score goes to Apple and no Game Center identifier enters anything we hold. That
reading is the open question in §7.4.

### The "linked to the user" note

Apple's "Data Linked to You" means linked to the user's identity via account, **device**, or other
details, and "Not Linked to You" requires that the data be de-identified and that you not attempt
to relink it to a user *or a device*. Every record we send carries `install_id`, which is exactly a
device-scoped relinkable identifier, so the strict reading puts all of the above under **Data
Linked to You** even though no account of ours exists.

**Confidence: medium**, and it is the cheapest answer to get wrong in the safe direction. Filing
everything as Linked is conservative and cannot be a violation; filing as Not Linked while shipping
a persistent install id can be. Recommendation: **file as Linked to You** and say in the privacy
policy that the link is to an install, not a person.

### Tracking

Apple defines tracking as linking user or device data collected from your app with third-party data
for targeted advertising or ad measurement, or sharing it with a data broker.

**Today the answer is No on every row**, because no ad SDK is linked and no IDFA is read. Do not
tick "Used for Tracking" on anything, and do not add `NSUserTrackingUsageDescription`. An ATT
prompt in an app that does no tracking is itself a review problem.

**This answer has a short shelf life.** §7.2.

### The privacy manifest

Apple requires a `PrivacyInfo.xcprivacy` for the app, and requires that bundled third-party SDKs
ship signed manifests. **One has been written this chunk** at
`apps/ios/iosApp/PrivacyInfo.xcprivacy`. Read its header comment: it is written for the
no-ad-SDK build, it must be **added to the iOS target's Copy Bundle Resources phase** (nobody can
do that from outside Xcode, so it is an owner task), and §7.2 says what to change in it.

---

## 6. The kids branch, and exactly what each answer becomes

Unresolved, and on `OWNER-TODO.md`. It does not merely adjust a row.

### Branch A: general audience, 13+ (what the code implements)

Everything in §4 and §5 stands as written. The code is already correct for this branch:
`TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE`, `tagForUnderAgeOfConsent` left unset, UMP before the
first request. Do not enrol in Designed for Families; do not select the Kids Category.

The residual risk is presentation, not code: a heavily kid-appealing icon plus a 13+ declaration can
still draw a Play review flag. Since the icon does not exist yet, this is a live input to the art
brief: the art should read "bright", not "preschool".

### Branch B: child-directed (Play Families) and/or Apple Kids Category

**On Google Play:**

- No advertising id. `com.google.android.gms.permission.AD_ID` must be *removed* by an explicit
  `<uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="remove"/>`
  in `apps/compose/src/androidMain/AndroidManifest.xml`, because it arrives by manifest merge from
  `play-services-ads` and is not written there today.
- `setTagForChildDirectedTreatment` flips to `TRUE` at `AdMobAdNetwork.kt:209-216`.
- Only Families-certified ad SDKs, no personalised ads. Revenue per impression falls sharply.
- The Data safety **advertising id row disappears**. The install-id row survives, but Families
  policy restricts using a persistent identifier for advertising, so its purpose list loses
  "Advertising or marketing".
- COPPA and GDPR-K attach: verifiable parental consent obligations, and the UMP flow is no longer
  sufficient on its own.

**On Apple, Kids Category:**

- Third-party analytics and third-party advertising are **banned outright**. That removes Sentry
  and the Grafana Cloud pipeline in one stroke; on iOS there is no ad SDK to remove yet, which is
  the one way this branch is currently cheaper here than it was in the sibling project.
- The nutrition label collapses to **Data Not Collected**.
- `:libraries:telemetry` and the Sentry tree in `AppTelemetry.kt` would have to be compiled out or
  no-op'd on iOS, and the crash pipeline replaced with something first-party.
- Whether Game Center survives the Kids Category, and what a Kids app may do with a leaderboard, I
  could not settle from Apple's published wording. **Not determined.**
- SPEC 12's rewarded model would need rewriting: no rewarded ads means no continue, which is the
  whole of that model since D27 took the Daily retry with the mode.

**Branch B is not a settings change; it is a different app.** Decide before the ad units go live.

---

## 7. Not determined, and what each one needs

### 7.1 Deletion, which is answerable here, but not the way the form asks

Play asks whether users can request deletion of their data.

What is true:

- Device-local data goes on uninstall, and **also** on Settings → "Delete local data", which empties
  every Room table and writes a fresh `AppData` (`PlayerDataEraser.kt:65-87`). That rotates the
  install id, so nothing already sent can be tied to the install any more.
- Telemetry already in Loki ages out at Grafana Cloud's retention; Sentry has its own.
- **Neither can be deleted on request in practice**, because the only key is `install_id` and the
  app never shows it to the player (§2.1).
- There is no in-app analytics opt-out (§2.3). `diagnosticsOptIn` is not one (§2.5).

**Recommended answer, and what it costs.** Answer **Yes** to "users can request that their data be
deleted", on the basis of the in-app control, and provide a support email in the free-text field.
Then make one cheap change so the claim is fully true: **show the install id somewhere the player
can copy it** (Settings → About, or the debug menu, or appended to a feedback report), so a
deletion request has a key to name. Without it, "request deletion" resolves to "we cannot find your
rows".

**Not determined:** whether you want the support email in the loop at all. That is a product call
and it needs a support address to exist first (`OWNER-TODO.md`).

### 7.2 The iOS ad SDK diff, pre-written

When `GoogleMobileAds` is added to `apps/ios/iosApp.xcodeproj`, apply all of this in one pass:

1. **Info.plist:** add `GADApplicationIdentifier` (the real AdMob iOS app id, or
   `AdUnits.IosTest.applicationId` for a test build) and `NSUserTrackingUsageDescription`.
2. **Code:** the iOS `AdNetwork` binding has to exist and must fire the ATT prompt from the ad
   `prepare()` path, not at launch. Today iOS answers `NotShown` with
   `errorKind = "ad_network_not_wired"` and **cannot** return `Rewarded`, pinned by a test (L66).
   Do not weaken that until the real path exists.
3. **Nutrition label, §5:** tick **Used for Tracking** on Identifiers → Device ID; add
   **Usage Data → Advertising Data** (collected, linked, used for tracking, Third-Party
   Advertising); set Device ID's purposes to include Third-Party Advertising.
4. **`PrivacyInfo.xcprivacy`:** flip `NSPrivacyTracking` to `true`, add Google's published tracking
   domains to `NSPrivacyTrackingDomains`, and add the `NSPrivacyCollectedDataTypeAdvertisingData`
   and device-ID-for-tracking entries. The file's header comment says this too.
5. Confirm the Google Mobile Ads and Sentry Swift packages each ship their **own signed** privacy
   manifest. That half of the requirement is theirs, not ours, and our file cannot satisfy it.

### 7.3 Android Auto Backup was on, and is now off

**Settled in C13a.** `apps/compose/src/androidMain/AndroidManifest.xml` sets
`android:allowBackup="false"`, with the reasoning in an XML comment on the line itself. Everything
below is what the decision was made against; it is kept because the reasoning is what a reviewer
will ask for, not the answer.

The deciding argument was the one the rest of this document rests on. `installId` is the only
identity this app has: it is the bucketing key our config server targets rollouts on, the
`install_id` on every OTLP record, and a Sentry scope tag. Auto Backup restoring it onto a second
device means two phones reporting as one install and sharing one targeting bucket — an identifier
this document describes as device-scoped, silently becoming person-scoped. The Settings copy is the
second argument and it is the one a player can read.

The cost is real and accepted: a player who changes phones loses their run history. With no account
and no server-side copy, that is exactly what the Settings copy already promises them.

#### The state it was in, and why it mattered

`apps/compose/src/androidMain/AndroidManifest.xml` has `android:allowBackup="true"`, with no
`fullBackupContent` or `dataExtractionRules` exclusion anywhere in the tree. So on Android:

- `AppData` (including `installId` and the Pro boolean) and the Room database are eligible for
  Google's Auto Backup to the user's Drive, and restore onto a new device.
- **The install id can outlive the install and reach a second device.** §2.1's "lifetime" row and
  the Apple "device-scoped" reasoning both get weaker.
- The Settings copy "There is no backup and no way to undo this" (`strings.xml:210`) is **false on
  Android** in the case that matters: a player who deletes local data and then restores from backup
  gets it back.
- Whether Auto Backup is itself a declarable transfer is a question you then have to answer.

Sodogku set `allowBackup="false"` and wrote up the reasoning. Doing the same here removed all four
problems at once.

### 7.4 Game Center, which neither form has an obvious row for

Clearly true: we collect nothing from it, store nothing, and never read the player's Game Center id
or alias, so no row in §5 gains a value and **Identifiers → User ID** stays unticked.

What I could not settle from Apple's published wording: whether App Privacy expects a developer to
declare data the app causes to flow into a **first-party Apple service** under the user's Apple
identity, given the framework is Apple's own and the data never reaches us. The two plausible
answers are "no row" (what most Game Center apps appear to do) and "a Gameplay Content or Other
User Content row" (conservative, cannot be a violation).

**What this needs:** one check against App Store Connect Help, "App privacy details", before the
label is filed. The privacy policy has to describe the flow in plain words either way.

### 7.5 "Encrypted in transit" for the Grafana endpoint

`GrafanaCloud.OTLP_BASE_URL` is injected at build time from `local.properties` / CI secrets.
Nothing in the code forces `https`. It will be an HTTPS Grafana Cloud gateway in practice, but the
claim on the form is only true if the configured value is. The Sentry half is settled by
construction, because a DSN is an HTTPS URL. **Confirm the Grafana value when the account exists.**

---

## 8. Findings for whoever owns the code

C13 wrote these down rather than fixing them, because a manifest edit and a telemetry-seam deletion
were outside a documentation chunk's scope and the gate is shared. **C13a fixed all five.** Each
entry below keeps the finding as it was written and adds what was done, because the finding is the
part worth reading: it is the shape of the mistake, and the fix is only ever an instance of it.

### 8.1 `Telemetry.setUser` exists, with no caller, in an app with no users

`Telemetry.setUser(email, name, id)` is declared (`libraries/drop2048/src/.../Telemetry.kt:6`) and
implemented against `Sentry.setUser` (`AppTelemetry.kt:115-120`). Nothing calls it. The same is
true of `captureUserFeedback`'s `email` parameter and its `screenshots` parameter.

That is a worse state than it sounds: a one-line call is the natural thing to write the day someone
adds a contact field, and nothing would fail. Sodogku deleted both seams and added
`NoIdentitySeamsTest`, which fails the build if either returns. **Doing the same here would turn
"no identity reaches Sentry" from a claim into a property.**

**Done, C13a.** `Telemetry.setUser` and its `Sentry.setUser` implementation are deleted, and so are
`captureUserFeedback`'s `email` and `screenshots` parameters and the attachment code behind the
latter. `NoIdentitySeamsTest` asserts by JVM reflection that the seam declares no `setUser*`, that
`captureUserFeedback` takes five parameters, and that none of them is a `List` — reflection rather
than a compile-time shape, because a re-added parameter *with a default value* breaks no caller and
is exactly the way this comes back.

### 8.2 The diagnostics toggle promises something the feedback path does not honour

`settings_diagnostics_hint` reads: *"Attaches your device model and build number. Never your board,
your scores or anything you typed elsewhere."*

Every feedback submission attaches `session-log.txt`, unconditionally, holding Debug-and-above KLog
output, which includes the `logEvent` lines carrying score, level, highest tier and run cause
(§2.3). The toggle does not gate it; the toggle only appends a version string to the message body.

Either the copy changes, or the attachment gets gated on the toggle. The copy is the cheaper fix
and the attachment is the more useful behaviour, so the copy is probably what should move, but
"never your scores" is an explicit promise on screen and it is currently not kept.

**Done, C13a, and the code moved rather than the copy.** The toggle is opt-in, so the copy is what
the player consented to; weakening it after the fact is not a fix. Four changes:

1. The `session-log.txt` attachment rides only when the player opted in. `attachSessionLog`
   defaults to `false`, so a caller that forgets it sends *less*.
2. `BugReportViewModel` reads the preference at all, which it never did — the bug report form was
   attaching a session log from players who had left the switch off.
3. The carrier event clears its breadcrumbs. **This was the bigger leak and the finding above
   missed it:** breadcrumbs are Info-and-above in release, `logEvent` is Info, and
   `SentryLogTree.addBreadcrumb` copies the entry's extras onto the breadcrumb. Every feedback
   report ever filed would have carried `extra.score`, `extra.level` and `extra.highest_tier` from
   the last `run.end`, switch or no switch.
4. The buffered line format is now `internal` and pinned by a test, because the attachment's safety
   rests entirely on it writing the message and not the context. An app event's message is its
   name.

And the half of the copy nobody had noticed was also unkept: it promises "your device model", and
the appended line carried the build and not the model. `DeviceInfo` in `:libraries:core` exists for
that one caller.

### 8.3 `android.permission.CAMERA` is declared and nothing uses a camera

`AndroidManifest.xml:4` declares `android.permission.CAMERA` and `:21` declares the matching
`uses-feature`. No call site exists: `CameraPreview`, `PhotoSaver` and
`rememberCameraPermissionLauncher` live in `:libraries:ui` as template scaffolding, and grepping
`features/` and `apps/` for any of them returns nothing.

Three consequences, in order of cost:

1. Play shows "Camera" in the app's permission list. For a falling-block puzzle that reads as
   spyware, and it is the first thing a privacy-minded reviewer notices.
2. It invites a Data safety question the app has no honest use for.
3. `uses-feature ... required="false"` is the mitigation for device filtering, so at least
   distribution is not narrowed. That is the only part that is already handled.

Deleting both lines is a one-line-each change with no code impact. It was not done here because it
is a manifest change outside this chunk's scope and the gate is shared, but it should be done
before any store build.

**Done, C13a.** Both manifest lines are gone, and so is the scaffolding they existed for:
`CameraPreview`, `PhotoSaver` and `PermissionLauncher` are deleted from `:libraries:ui` in
commonMain, androidMain and iosMain — nine files. `PermissionLauncher` went with them because its
other half, `rememberMicrophonePermissionLauncher`, was equally uncalled and `RECORD_AUDIO` was
never declared.

**What survives, and why.** `NativeViewFactory` in `:libraries:ui`'s `iosMain` still declares
`createCameraPreview`, `capturePhoto`, `toggleCameraFlash`, `CameraGuidanceState` and the Apple
Sign In button factory, and `IOSNativeViewFactory.swift` still implements all of them in 544 lines.
Nothing in Kotlin calls any of it. It was left because it is a bridge protocol implemented on the
Swift side, nobody on this machine can open Xcode to prove a build after editing it, and a large
unverifiable Swift deletion is a bad trade in a chunk whose subject is correctness. It ships dead
code in the iOS binary and declares no permission, so it is a tidiness item rather than a store
one. `docs/todos.md` should carry it.

### 8.4 The iOS entitlements file still asks for Sign in with Apple

`apps/ios/iosApp/iosApp.entitlements` contains exactly one key,
`com.apple.developer.applesignin`, left over from the identity stack C0 deleted. There is no
sign-in anywhere in the app.

**Done, C13a.** The file now contains exactly `com.apple.developer.game-center` set to `true`, and
`plutil -lint` passes. That is the capability C9's `GKLeaderboard.submitScore` path needs and could
never have had.

**Unproven, and say so.** An entitlements file is a claim about a provisioning profile, and neither
half can be checked here: `xcode-select` points at nothing on this machine, so no agent can build,
archive or sign the iOS target. What has been verified is that the file is valid plist and that
`project.pbxproj` points `CODE_SIGN_ENTITLEMENTS` at it from both configurations. What has not is
that the App ID has the Game Center capability enabled, which is an Apple Developer portal setting
and an owner item (§7 of `release-checklist.md`, item 23).

This is an App Review risk of exactly the kind SPEC 20 warns about ("dead auth code in a game with
no accounts is a liability at App Store review"), and it will also require the capability to exist
on the App ID. It should be replaced by the one entitlement the app actually needs, Game Center.

### 8.5 The hosted privacy policy contradicts the app, in the most expensive possible direction

`pages/privacy.html` is still the template's, and it states:

> the app does not *"Use advertising or analytics SDKs (no Google Analytics, no Facebook SDK, no
> ad networks)."*

The app ships AdMob and an OTLP analytics pipeline to Grafana Cloud. It also still carries the
literal template phrase "(unless your project adds one)", says the only permission is iOS
notifications, and does not mention the install id, the session-log attachment, Game Center or
purchases.

The URL is already wired: `legal.privacyUrl` defaults to the live
`https://elijah-dangerfield.github.io/Drop2048/privacy.html`
(`LaunchGateConfigValues.kt`), the launch gate links it, and the gate does legal re-accept, so the
version matters and not only the text. **A live privacy policy that denies serving ads while the
app serves them is both a store problem and a trust problem**, and it is exactly the failure this
chunk exists to prevent. The text is owner-owned and is not written here; §2 is the material it
has to be written from.
