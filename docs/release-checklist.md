# Release checklist

Everything between the current tree and Doublestack being downloadable on both
stores. Every item was checked against the code or the console rather than
copied from `OWNER-TODO.md` or `todos.md`, both of which carry stale entries.

**Two things are left, plus the credentials that unlock them.** Everything else
on this list has been done and moved to "Already done" at the bottom.

## Context

| | |
|---|---|
| **Play** | `com.dangerfield.drop2048` · app `4973613941103364487` · Nightjar Labs org `9122554580643893683` |
| **Apple** | `com.dangerfield.drop2048.Drop2048` · Apple ID `6814213924` · SKU `doublestack` |
| **Pro** | `drop2048_pro` · Apple IAP `6814609426` · $2.99 non-consumable |
| **Store title** | `Doublestack: Merge Block Drop` on both. Launcher and in-app name is `Doublestack` |
| **Legal** | `legal/*.md` here, published at `nightjarlabs.llc/doublestack/{privacy,terms}` |

The identifiers keep the old `drop2048` spelling on purpose. They are permanent
on Play and no player sees either.

**Release channels** come from `RELEASE_CHANNEL_OVERRIDE`, not from `isDebug`:

- `dev`: the default in `versions.properties`, local builds. Test ads.
- `beta`: `beta.yml`, to Play internal and TestFlight internal. Test ads.
- `store`: `release.yml`, to Play production (10% staged) and TestFlight
  external, then App Store review with phased release. **Live ads.**

**Releases go through release-please.** Merge the open `chore(main): release
vX.Y.Z` pull request and the tag, the GitHub release and both store uploads
follow on their own. See [`docs/release-automation.md`](release-automation.md).

**Long form:** [`docs/store/`](store/) has listing copy, the Data safety
derivation, age rating and icons. [`legal/README.md`](../legal/README.md) is how
the privacy policy gets published. [`OWNER-TODO.md`](OWNER-TODO.md) and
[`todos.md`](todos.md) hold everything that is not a release blocker.

---

## Required

### 0. Eleven signing and store secrets · **you**

The one hard prerequisite. Nothing can be built signed, uploaded or QA'd until
these exist, so both steps below wait on it. `gh secret list` currently returns
`SENTRY_AUTH_TOKEN` and `NIGHTJAR_SITE_TOKEN` and nothing else.

Android: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`.
Apple: `APPLE_DIST_CERT_P12_BASE64`, `APPLE_DIST_CERT_PASSWORD`,
`APPLE_TEAM_ID`, `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8_BASE64`.

Yours because every value is a file you download from a console or a password
only you hold. `SENTRY_DSN` is also read but is not needed: `resolve()` treats a
blank env var as absent and falls through to the DSN committed in
`telemetry.properties`.

Two small console jobs ride along, both a couple of clicks and neither
delegable:

- **Apple's Paid Applications agreement**, with banking and tax details. Without
  it no in-app purchase can be sold, and the Pro price below cannot be set.
- **`drop2048_pro` price and availability.** The record exists with the right
  type, reference name and product id; App Store Connect's price combobox
  reverts every value an automated click gives it, so the last field is yours.
  Set $2.99, all 175 regions, then add an English localisation.

### 1. QA the build · **you** · needs 0

Merge the release-please PR. `beta.yml` puts it on TestFlight internal and the
Play internal track, both of which build with `RELEASE_CHANNEL_OVERRIDE=beta`
and therefore serve **test** ads, so nothing you do here touches AdMob
inventory.

Worth exercising specifically, because these have never run on a real device:
the Pro purchase on iOS (brand new, StoreKit 2), ads on iOS, and Core Haptics,
which compiles and links and has never once executed.

### 2. Submit on both platforms · **you** · needs 1

Both stores refuse an automated first production upload, so release one is by
hand. Play: Internal testing → Promote → Production. Apple: TestFlight build →
Submit for review, with `drop2048_pro` attached to the version (a first
non-consumable must be submitted alongside its app version).

From release two onward `release.yml` does both without you.

---

## Suggested

Nothing here blocks a submission, and none of it needs doing before release one.

- **Game Center is one of three records in.** `score_alltime` is created and
  complete: classic, integer, best score, **high to low**, with an English
  localisation. Until the rest exist those submissions fail silently, exactly
  like a signed-out player. The app ships and reviews fine either way, which is
  why this is not Required.
  - **`score_weekly`** needs about a minute of clicking. App Store Connect's
    date picker never commits the value an automated click gives it (the field
    stays empty in the accessibility tree while showing a date), so `Next` stays
    disabled. Recurring, start **Mon 28 Sep 2026 00:00**, duration 7 days,
    restart every 7 days, integer, best score, high to low. **you**
  - **The 22 achievements are blocked on badge art, not on typing.** Apple marks
    the achievement image required, unlike the leaderboard's, so a record
    created now is one that cannot be completed. The ids are in the
    `AchievementId` enum, 22 of them, pinned by `PlatformAchievementIdTest`.
    Make the art first, then the records are mechanical. **you**, then **agent**
- **The Grafana pipe ships dark.** `GRAFANA_OTLP_BASE_URL`, `_INSTANCE_ID` and
  `LOGS_WRITE_TOKEN` are unset, so `resolve()` returns `""` and analytics are
  off in every build. The build does not fail; it just measures nothing. **you**
- **`NIGHTJAR_SITE_TOKEN` is scoped wrong.** Legal Sync fails with a 404 on the
  website repo, so a future edit to `legal/*.md` will not publish. The pages
  live today are correct, which is why this is not Required. **you**
- **Two contact addresses.** `legal/*.md` and Play's listing say
  `contact@nightjarlabs.llc`; the site footer says `hello@`. Pick one. **agent**
- **The sound bank is 15 generated placeholders**, not sound design. The merge
  sample is the one that matters; it is pitched up an octave. **you**
- **iOS has no banner surface** (`NoBannerSurface`), which is a coherent state
  rather than a bug: interstitials and rewarded video work on both platforms.
  **agent**
- **`tagForUnderAgeOfConsent` is unset** at `AdMobAdNetwork.kt:62`, deliberately,
  so an under-16 EEA player may get personalised ads. Worth a ruling. **you**
- **Nobody but an agent has played the game.** Three feel questions are in
  `OWNER-TODO.md`, and step 1 above is the moment to answer them. **you**
- **Accessibility:** paused-overlay rows are 39.5dp against a 48dp target
  (`todos.md`). **agent**

---

## Already done

Do not redo any of this.

**Both store records exist** with titles, descriptions, keywords, icons and
screenshots, and Play's App content page reads "You're all caught up": Data
safety, content rating, target audience, advertising ID and the app access
declarations are all filed. **The five filed URLs** were re-pointed at
`nightjarlabs.llc` and all return 200; the privacy policy and terms are live and
describe the app that actually exists, including iOS ads.

**iOS can take money.** `IOSStoreBilling` is StoreKit 2, ported from Sodogku and
handed to the graph beside `IOSAdNetwork`. The `drop2048_pro` record exists in
App Store Connect. **The iOS icon is flattened** (ITMS-90717, the reject Sodogku
lost a submission to) and **the iPad claim is dropped**, so no iPad screenshots
are owed.

**Ads are release-ready without a flip.** `AdUnits.useTestUnits` reads
`BuildInfo.releaseChannel`, so a `store` build serves live units and every other
build serves Google's samples. The manifest carries the real app id, content is
capped at `G` on both platforms, and iOS has UMP, ATT, 50 `SKAdNetworkItems` and
a correct privacy manifest.

**The app name is settled** and iOS `PRODUCT_NAME` is `Doublestack`, which was
itself a submission blocker. `xcode-select` is fixed, CI is green, and the iOS
target builds clean under `xcodebuild`.

**`PrivacyInfo.xcprivacy` needs nothing.** Four documents say to add it to Copy
Bundle Resources; all four are wrong. `iosApp` is a
`PBXFileSystemSynchronizedRootGroup` whose only membership exception is
`Info.plist`. Proved empirically, not by reading: the built `Doublestack.app`
contains `PrivacyInfo.xcprivacy` at its root.
