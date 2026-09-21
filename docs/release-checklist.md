# Release checklist

Everything between the current tree and Doublestack being downloadable on both
stores. Verified against the code and both consoles on 2026-09-21, not copied
from `OWNER-TODO.md` or `todos.md`; where those disagreed with reality this file
follows reality and the reply that produced it says which.

## Context

| | |
|---|---|
| **Play** | `com.dangerfield.drop2048` · app `4973613941103364487` · Nightjar Labs org `9122554580643893683` |
| **Apple** | `com.dangerfield.drop2048.Drop2048` · Apple ID `6814213924` · SKU `doublestack` |
| **Store title** | `Doublestack: Merge Block Drop` on both. Launcher and in-app name is `Doublestack` |
| **Legal** | `legal/*.md` here, published at `nightjarlabs.llc/doublestack/{privacy,terms}` |

The identifiers keep the old `drop2048` spelling on purpose. They are permanent
on Play and no player sees either.

**Release channels** come from `RELEASE_CHANNEL_OVERRIDE`, not from `isDebug`:

- `dev`: the default in `versions.properties`, local builds.
- `beta`: `beta.yml`, to Play internal and TestFlight internal.
- `store`: `release.yml`, to Play production (10% staged) and TestFlight
  external, then App Store review with phased release.

**Long form:** [`docs/store/`](store/) has listing copy, the Data safety
derivation, age rating and icons. [`docs/release-automation.md`](release-automation.md)
is how the pipeline works. [`legal/README.md`](../legal/README.md) is how the
privacy policy gets published. [`OWNER-TODO.md`](OWNER-TODO.md) and
[`todos.md`](todos.md) hold everything that is not a release blocker.

---

## Required

Ordered by dependency. Items 2 to 7 are independent of each other and can run in
any order, or at once.

### 1. Eleven signing and store secrets · **you**

Nothing below that produces a binary can happen first. `gh secret list` returns
`SENTRY_AUTH_TOKEN` and `NIGHTJAR_SITE_TOKEN` and nothing else. Android:
`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`. Apple:
`APPLE_DIST_CERT_P12_BASE64`, `APPLE_DIST_CERT_PASSWORD`, `APPLE_TEAM_ID`,
`ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8_BASE64`. Yours because every value is
a file you download from a console or a password only you hold.

`SENTRY_DSN` is also read but is not needed: `resolve()` treats a blank env var
as absent and falls through to the DSN committed in `telemetry.properties`.

### 2. The iOS app icon has an alpha channel · **agent**

`Store Assets-selection.png` is RGBA, which is App Store delivery's ITMS-90717
hard reject. Sodogku lost a submission to exactly this. The alpha is fully
opaque (`min = max = 255`), so flattening is lossless and needs no re-export
from the design file, which is why this is not yours.

### 3. iOS shows a Pro paywall that cannot take money · **agent**, decision **you**

`IOSStoreBilling` does not exist. iOS falls through to `NoStoreBilling`, whose
`purchase()` returns `Unavailable` / `no_store_wired`, and nothing in
`features/paywall` is platform-gated. So on iOS the upsell renders, the button
does nothing, and that is App Review guideline 2.1. Either implement StoreKit 2
or hide Pro on iOS for v1. Hiding is hours; implementing is days and pulls item 7
in with it.

### 4. The iOS target claims iPad and nothing has ever rendered there · **agent**

`TARGETED_DEVICE_FAMILY = "1,2"`. Apple requires iPad screenshots for an app that
declares iPad, and the screenshot harness is Robolectric, so all 26 goldens are
Android phone. Either supply iPad shots or set the family to `"1"`. Dropping iPad
is the cheap answer and costs nothing a player would notice.

### 5. Ads are uncapped for a 13-year-old · **agent**

Play's target audience is filed as 13-15 / 16-17 / 18+, which makes Families
policy apply. `AdMobAdNetwork.kt:214` builds a `RequestConfiguration` with
`TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE` and never calls
`setMaxAdContentRating`, so AdMob may serve `MA` creative. One line on the
existing builder, `MAX_AD_CONTENT_RATING_G`, plus the iOS equivalent beside
`ageRestrictedTreatment` in `AdNetwork.swift:84`.

### 6. Game Center is empty · **agent**

Zero leaderboards, zero achievements in App Store Connect. The code submits to
two boards and 22 achievements by id; until the records exist every submission
fails silently, exactly like a signed-out player. Ids are listed in
`OWNER-TODO.md`'s credentials table and pinned by `PlatformAchievementIdTest`.
Tedious browser work, not owner work.

### 7. No in-app purchase exists on either store · **agent**, after 3 and 8

Apple's IAP list is empty, and its own note is the constraint: a first
non-consumable must be submitted **with** the app version, so it cannot be an
afterthought. On Play, `drop2048_pro` does not exist and cannot be created until
a build carrying the billing library is on a track, so that half waits for item 8.
Skip the Apple half entirely if item 3 is resolved by hiding Pro on iOS.

### 8. First build to internal and TestFlight · **agent** to trigger, **you** to approve · needs 1

`beta.yml` with `RELEASE_CHANNEL_OVERRIDE=beta`. This is the first time the app
has ever been built signed, so expect the first run to fail on something in item
1 rather than on code. It also unblocks item 7's Play half.

### 9. Flip the ads to live · **you** to time, **agent** to edit · needs 8

`AdUnits.useTestUnits` is `true` and `AndroidManifest.xml` still carries Google's
sample app id `ca-app-pub-3940256099942544~3347511713`. They flip **together**;
a real app id with test units is the half-migrated state both files warn about.

Do not flip before you intend to ship: requesting a live unit from a development
build is what gets an AdMob account suspended. **And note the flip is
all-or-nothing today.** `useTestUnits` is a compile-time constant, so once it is
`false` your TestFlight and Play-internal testers request live units too, because
those are release builds. If that matters, key it off `BuildInfo.releaseChannel`
being `store` rather than off the constant. Do not key it off `isDebug`, which is
false for both tester channels.

### 10. Promote the first release by hand · **you** · needs everything above

Both stores refuse an automated first production upload. Play: Internal testing →
Promote → Production. Apple: TestFlight build → Submit for review. From release
two onward `release.yml` does it. This is in `docs/release-automation.md` under
"First release".

---

## Suggested

Nothing here blocks a submission.

- **The Grafana pipe ships dark.** `GRAFANA_OTLP_BASE_URL`, `_INSTANCE_ID` and
  `LOGS_WRITE_TOKEN` are unset, so `resolve()` returns `""` and analytics are off
  in every build. The build does not fail; it just measures nothing. **you**
- **`NIGHTJAR_SITE_TOKEN` is scoped wrong.** Legal Sync fails with a 404 on the
  website repo, so a future edit to `legal/*.md` will not publish. The pages live
  today are correct, which is why this is not Required. **you**
- **Two contact addresses.** `legal/*.md` and Play's listing say
  `contact@nightjarlabs.llc`; the site footer says `hello@`. Pick one. **agent**
- **The sound bank is 15 generated placeholders**, not sound design. The merge
  sample is the one that matters; it is pitched up an octave. **you**
- **iOS haptics have never executed** and iOS has no banner surface
  (`NoBannerSurface`), which is a coherent state, not a bug. **agent**
- **`tagForUnderAgeOfConsent` is unset** at `AdMobAdNetwork.kt:62`, deliberately,
  so an under-16 EEA player may get personalised ads. Worth a ruling. **you**
- **Nobody but an agent has played the game.** Three feel questions are in
  `OWNER-TODO.md`. **you**
- **Accessibility:** paused-overlay rows are 39.5dp against a 48dp target
  (`todos.md`). **agent**

---

## Already done

Do not redo any of this. **Both store records exist** with titles, descriptions,
keywords and screenshots, and Play's App content page reads "You're all caught
up". Data safety, content rating, target audience, advertising ID and the app
access declarations are all filed. **The five filed URLs** were re-pointed at
`nightjarlabs.llc` on 2026-09-21 and all return 200; the privacy policy and terms
are live and describe the app that actually exists, including iOS ads.
**AdMob is fully set up**: two apps, six live unit ids in `AdUnits`, and iOS
wired with `GoogleMobileAds` 13.9.0, UMP, ATT, 50 `SKAdNetworkItems` and a
correct `PrivacyInfo.xcprivacy`. **The app name is settled** and iOS
`PRODUCT_NAME` is `Doublestack`, which was a submission blocker. `xcode-select`
is fixed, CI is green, and the sound files, app icons and store screenshots are
all in place.

**`PrivacyInfo.xcprivacy` needs nothing.** Four documents say to add it to Copy
Bundle Resources; all four are wrong. `iosApp` is a
`PBXFileSystemSynchronizedRootGroup` whose only membership exception is
`Info.plist`, so the manifest is already in the target and already ships.
