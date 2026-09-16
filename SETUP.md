# Setup checklist — Drop 2048

Action items after running `./scripts/init_project.main.kts`. Work through these in order. Tick off as you go.

**Hour 1 — a running app:**
- [ ] [Local dev](#local-dev) — hooks + first build
- [ ] [Server deploy](#server-deploy-flyio) — dev Fly app + secrets + `/_health`

**Day 1 — pipelines + visibility:**
- [ ] [GitHub secrets](#github-secrets-only-if-you-enabled-ci) (only if you enabled CI)
- [ ] [Repo settings](#repo-settings) — Pages, Actions, branch protection, the `production` Environment
- [ ] [Day-1 verification](#day-1-verification) — prove telemetry + deploys actually work

**Before shipping:**
- [ ] [Store listings](#store-listings) — Play Console + App Store Connect
- [ ] [First release](#first-release) — the manual-promotion gotcha
- [ ] [App icons](#app-icons)

---

## Local dev

```sh
./scripts/install_hooks.sh   # commit-msg hook for Conventional Commits
./gradlew build              # first sync + build
```

The Gradle build fails with an install-hooks message if you skip `install_hooks.sh`. That's intentional — release-please derives version bumps from commit history, so every commit must be in Conventional-Commits form (`feat:`, `fix:`, etc.).

To bypass in scripted contexts (not CI — `CI` env var is honored): `-Ddrop2048.skipGitHooksCheck=true`.

---

## Sentry (one script)

The app ships with crash reporting off: `telemetry.properties` has a blank
`sentry.dsn`, nothing initialises, and nothing costs anything at runtime. Turn
it on once and it is on for every developer and every build type:

```sh
./scripts/setup_sentry.main.kts
```

It asks for one thing, a Sentry **user** auth token, then creates or adopts the
project, writes the DSN into `telemetry.properties`, sets the CI variables, and
finishes by sending a real event and waiting for it to arrive. It prints
whether that worked. If it says the event did not arrive, the setup is not done
— the whole point of the check is that a broken Sentry setup otherwise looks
identical to a working one.

Re-run it any time; every step is an upsert.

**Get the right token.** Sentry has two kinds and they are not interchangeable:

| Token | Where | Scopes | Use it for |
| --- | --- | --- | --- |
| **User** | Settings → Account → API → [Auth Tokens](https://sentry.io/settings/account/api/auth-tokens/) | Selectable — tick `project:read`, `project:write`, `org:read` | This script. Used during the run, never stored. |
| **Organization** (`sntrys_…`) | Settings → Organization Tokens | Exactly `org:ci`, not selectable | CI only (`SENTRY_AUTH_TOKEN`). 403s every read endpoint, so the script cannot use it. |

Handing the script an organization token gets a 403 from the first call and
looks like a broken token. It isn't; it's the wrong one.

Then **commit `telemetry.properties`.** The DSN belongs in git. It is a
write-only ingest endpoint that ships inside every store binary — anyone can
read it out of a published build in minutes — so it is not a secret, and any
scheme where each developer configures it locally means fresh clones silently
report nothing.

This repo has no GitHub remote, so the CI step prints "No GitHub repo reachable
via gh" and skips. That is the expected path here, not a failure — the DSN and
the test event are the parts that matter.

Dev versus prod needs no extra work. One project takes everything; the
`environment` tag is `{releaseChannel}-{platform}-{buildType}`, giving
`dev-android-debug` through `store-ios-release`.

---

## GitHub secrets (only if you enabled CI)

Set under **Settings → Secrets and variables → Actions**. All are required for `release.yml` to ship.

**Do this once, not once per app.** Every value below except `SENTRY_PROJECT` belongs to your Apple team, your Play developer account, or your Sentry org, and is identical for every project this template generates. Keep the certificate, the `.p8`, the upload keystore and the service-account JSON in one private folder outside any repo, with a script that runs `gh secret set` against a named repo. A new app then costs two values, not fifteen. See [release-automation.md → Secrets and variables](../docs/release-automation.md#secrets-and-variables).

The one item that cannot be reissued is the Android upload keystore: once an app has shipped a build signed with it, losing it means asking Google to reset the upload key. Everything else on this page can be regenerated from a console in minutes.

### Android signing

| Secret | How to get it |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -i upload-keystore.jks \| pbcopy` |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias inside the keystore |
| `ANDROID_KEY_PASSWORD` | Key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Play Console → Setup → API access → create service account with *Release apps to testing tracks* + *Release apps to production*. Download the JSON. |

### Apple signing + App Store Connect

| Secret | How to get it |
| --- | --- |
| `APPLE_TEAM_ID` | Apple Developer → Membership → Team ID |
| `ASC_KEY_ID` | App Store Connect → Users and Access → Keys → Key ID |
| `ASC_ISSUER_ID` | Same page — Issuer ID (top of the Keys tab) |
| `ASC_KEY_P8_BASE64` | `base64 -i AuthKey_XXX.p8 \| pbcopy` |
| `APPLE_DIST_CERT_P12_BASE64` | Export your Apple Distribution cert from Keychain as .p12, then `base64 -i dist.p12 \| pbcopy` |
| `APPLE_DIST_CERT_PASSWORD` | Password you set when exporting the .p12 |
| `FASTLANE_APPLE_ID` *(optional)* | Apple ID email, for `fastlane deliver` |

### Sentry

| Secret | Notes |
| --- | --- |
| `SENTRY_AUTH_TOKEN` | Sentry → **Settings → Organization Tokens** → Create New Organization Token (`sntrys_…`). Used by `beta.yml`/`release.yml` to create releases + upload mappings/dSYMs. Organization tokens carry exactly one scope, `org:ci`, and it is not selectable: release creation, source map upload, code mappings. That is all CI needs. Do **not** go looking for `org:read` or `project:releases` to tick; those belong to personal tokens and are not offered here. A healthy org token also answers 403 to Sentry's read endpoints, so do not treat that as a broken token. |

`./scripts/setup_sentry.main.kts` sets this one for you, along with the two
variables below, under **Settings → Secrets and variables → Actions →
Variables** (not secrets):

| Var | Value |
| --- | --- |
| `SENTRY_ORG` | Your Sentry org slug |
| `SENTRY_PROJECT` | Your Sentry project slug |

There is no `SENTRY_DSN` secret. The DSN lives in the committed
`telemetry.properties` — see [Sentry](#sentry-one-script) for why. The
environment variable is still read first, so you can point one workflow
somewhere else, but you should not need to, and setting it silently overrides
the committed file for every build in the repo (see the 2026-09-16 entry in
`docs/decisions.md`).
`release.yml` fails before it builds anything if neither is set: a store build
with crash reporting off ships blind, and that is not something to discover
from a review rejection.

### Grafana Cloud telemetry (optional)

Used by `beta.yml` and `release.yml` to bake client app-event credentials into
store builds. Leave unset and the telemetry pipe stays dormant — the app builds
and runs fine.

| Secret | Notes |
| --- | --- |
| `GRAFANA_OTLP_BASE_URL` | Grafana Cloud → OpenTelemetry → OTLP endpoint base URL |
| `GRAFANA_OTLP_INSTANCE_ID` | Same page — instance id (the numeric user) |
| `GRAFANA_LOGS_WRITE_TOKEN` | A Grafana Cloud access-policy token with logs:write. Grafana auto-revokes `glc_` tokens it finds in public repos — never commit one. |

### Server deploy (Fly.io)

`server-deploy.yml` auto-deploys the dev server on pushes to `main` that touch
server paths; `server-deploy-prod.yml` queues a prod deploy behind a manual
approval. Requires the two Fly apps from `apps/server/DEPLOY.md`.

| Secret | Notes |
| --- | --- |
| `FLY_API_TOKEN_DEV` | `fly tokens create deploy -a <project>-server-dev --expiry 8760h` |
| `FLY_API_TOKEN_PROD` | `fly tokens create deploy -a <project>-server-prod --expiry 8760h` |
| `ADMIN_API_TOKEN_DEV` *(optional)* | The dev server's `ADMIN_API_TOKEN` — lets the deploy upload the per-version config manifest (see `apps/admin/README.md`). Skipped with a warning if unset. |
| `ADMIN_API_TOKEN_PROD` *(optional)* | Same, for prod. |

Also create the **`production` GitHub Environment** (Settings → Environments →
New environment → `production` → Required reviewers → add yourself).
`server-deploy-prod.yml` pauses on it until a human approves the run — without
the environment the prod deploy runs unguarded.

---

## Repo settings

- **Actions** → enable workflows.
- **Pages** → Source: `Deploy from a branch`, Branch: `main` / folder: `/pages`. (The `pages.yml` workflow can also publish on push.)
- **Branch protection** on `main`:
  - Require PR.
  - Require status checks: `CI / Build + test`, `commitlint / Validate PR title`.
  - Require linear history (so release-please squash-merges cleanly).

---

## Store listings

Before `release.yml` can ship:

1. **Play Console** → Create app → fill out store listing, data-safety form, content rating, pricing/distribution. Create at least one internal track tester.
2. **App Store Connect** → My Apps → New App → pick the bundle ID that matches `apps/ios/fastlane/Appfile`. Fill out app info, pricing, privacy details. Note: Apple checks the binary's bundle name / display name for uniqueness at *delivery* time (ITMS-90129), not here — if your app name is a common word, the first upload may bounce; pick a more distinctive `CFBundleName`/`CFBundleDisplayName` in `apps/ios` and re-upload.
3. **TestFlight** external group: create a group named `External Testers` (or change `TESTFLIGHT_EXTERNAL_GROUP` in `release.yml`).
4. Privacy policy + terms of service URLs — the `pages/` folder generates these; once Pages is enabled they're at `https://<you>.github.io/<repo>/privacy.html` etc. Paste the URLs into both store listings.

---

## First release

> **!!! READ THIS BEFORE YOUR FIRST RELEASE !!!**
>
> Google Play and Apple both reject automated production uploads until a manually-promoted build exists. `release.yml` detects this and routes the **first** release to:
>
> - **Play internal track** (not production).
> - **TestFlight internal** (not external, not App Store submission).
>
> You must then, **once**:
>
> 1. **Play Console** → Internal testing → Promote release → Production. Fill out the production rollout form manually.
> 2. **App Store Connect** → TestFlight build → Submit for review manually.
>
> From release #2 onward, the pipeline uploads straight to Play production (10% staged rollout) and submits to the App Store with phased release.

The release-please PR shows a `!!! FIRST RELEASE !!!` banner the first time around so this is hard to miss.

---

## App icons

Drop your icons into:

- **iOS** → `apps/ios/iosApp/Assets.xcassets/AppIcon.appiconset/` (replace the placeholder set).
- **Android** → `apps/compose/src/androidMain/res/mipmap-*/` (replace `ic_launcher*.webp`).
- **Shared** (used by Compose splash, about screens, etc.) → `libraries/resources/src/commonMain/composeResources/drawable/`.
- **GitHub Pages** → `pages/app-icon.png`, `pages/favicon.png`, `pages/apple-touch-icon.png`.

---

## Networking

`:libraries:networking` ships a single configured `HttpClient` (plus an
authenticated variant) for every repo and data source to share.

**Set your base URL.** Bind your own `NetworkConfig` (see
`DefaultNetworkConfig`) somewhere in your app — typically a class that reads
the URL from BuildConfig per build variant:

```kotlin
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [DefaultNetworkConfig::class])
@Inject
class AppNetworkConfig : NetworkConfig {
    override val baseUrl = AppBuildConfig.API_BASE_URL
}
```

**Authenticated calls.** Inject `NetworkClient` and use
`networkClient.authenticatedClient` for endpoints that need a Bearer token.
The token comes from your `AuthTokenProvider` binding (default is no-op).
401s trigger `refreshAccessToken()`.

**Wrap calls with `Catching { }`** at the call site. Ktor throws on non-2xx
and network errors; the rest of the codebase already uses this pattern.

**JSON config.** `NetworkJson` is strict in debug (unknown keys/missing
fields throw) and lenient in release (so a backend tweak can't crash users).

## Deep links

Compose NavHost handles the routing once URLs reach it. Per-route deep
links go on `screen<Route>(deepLinks = ...)` — use `routeDeepLink<T>()`,
never bare `navDeepLink` (iOS crashes on the missing base-route NavTypes).

The custom-scheme wiring is already enabled on both platforms: the intent
filter in
`apps/compose/src/androidMain/AndroidManifest.xml` and `CFBundleURLTypes`
in `apps/ios/iosApp/Info.plist`. For https App/Universal Links, add the
`assetlinks.json` / `Associated Domains` + AASA setup alongside.
`iOSApp.swift` forwards every `.onOpenURL` event to the Kotlin
`DeepLinkBridge`; `App.kt` routes OAuth callbacks to the auth layer and
everything else into the nav graph.

## In-app review

Inject `ReviewPrompter` and call `requestReview()` from a delighted-user
moment (e.g. after the user completes a meaningful task, or after N
sessions). The OS owns the throttling decision — both stores rate-limit how
often the dialog actually shows. Don't show your own UI before/after.

---

See `docs/release-automation.md` for the full pipeline runbook.

---

## Day-1 verification

Prove the observability + deploy story end to end while everything is fresh.
Each check has a definitive pass signal; if one fails, fix it now — these are
the tools you'll be debugging with later.

1. **Server is live.**
   ```sh
   curl https://<your-app>-server-dev.fly.dev/_health
   ```
   Expected: `{"ok":true}`.
2. **Client → server round trip.** Launch the app (device or simulator).
   Expected: a `GET /v1/config` in the server logs, and the values the app
   renders match the `app_config_values` rows.
3. **Find your session in Loki** (if Grafana is wired). In Grafana → Explore →
   Loki, query your client logs by the app's service name and filter
   `session_id="<id>"` — grab the id from the app's debug shake dialog or
   logcat (`Session started`). Expected: the `app.launched` event and your
   request logs, and the SAME `session_id` on the server's request logs.
4. **Trigger a test crash → Sentry.** Debug builds: shake → QA dialog → the
   test-crash affordance (or add a temporary `error()` behind a button).
   Expected: the event in Sentry within a minute, tagged with `session_id`,
   `commit_sha`, and a trace link that opens Tempo.
5. **Config round trip.** Open the admin console (`/admin` on the dev
   server, paste your `ADMIN_API_TOKEN`), flip `upgrade.maintenanceMessage`
   to a test string, foreground the app twice (refresh is throttled).
   Expected: the value changes in the QA config dashboard; the audit tab
   records your change.
