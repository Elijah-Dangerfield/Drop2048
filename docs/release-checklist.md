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

**This is the whole of it.** Apple's Paid Applications agreement is already
active, the bank account and W-9 with it, so nothing on the money side is
outstanding.

### 1. QA the build · **you** · needs 0

**Run `beta.yml` by hand. Do not merge the release PR for this.**

```bash
gh workflow run beta.yml
```

It is `workflow_dispatch` only, and it is the only path that builds with
`RELEASE_CHANNEL_OVERRIDE=beta`, which is what makes `AdUnits.useTestUnits`
true. A QA build off the release tag would be channel `store` and would request
**live** ad units, and requesting live units from a build being tested is what
gets an AdMob account suspended for invalid traffic. It lands on TestFlight
internal and the Play internal track.

Worth exercising specifically, because these have never run on a real device:
the Pro purchase on iOS (brand new, StoreKit 2), ads on iOS, and Core Haptics,
which compiles and links and has never once executed.

### 2. Submit on both platforms · **you** · needs 1

Merge the open release-please PR. That tags `v*`, which is what fires
`release.yml`; nothing else does. Because this is release one, it routes to the
**Play internal track and TestFlight internal** rather than production, so the
last move is by hand:

- Play: Internal testing → Promote → Production.
- Apple: TestFlight build → Submit for review, with `drop2048_pro` attached to
  the version (a first non-consumable must be submitted alongside its app
  version).

**This build is channel `store`, so it serves live ad units.** That is correct,
it is the binary that ships. Just do the ad testing in step 1 and not here.

From release two onward `release.yml` goes straight to production without you.

---

## Suggested

Nothing here blocks a submission, and none of it needs doing before release one.

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
handed to the graph beside `IOSAdNetwork`. `drop2048_pro` is complete in App
Store Connect: $2.99 base, 175 countries, English display name and description,
and "Add for Review" is enabled. **The iOS icon is flattened** (ITMS-90717, the reject Sodogku
lost a submission to) and **the iPad claim is dropped**, so no iPad screenshots
are owed.

**Game Center is complete.** Two leaderboards and 22 achievements, all carrying
the exact ids the code submits to, cross-checked against the `AchievementId`
enum rather than typed from a doc. Both boards are integer / best score / **high
to low** (App Store Connect defaults every board to ascending, which would have
ranked the worst run first); `score_weekly` is recurring from Mon 28 Sep 2026,
7-day duration and restart. Every achievement is 45 points, not hidden,
one-step, with an English localisation and a 512x512 badge rendered from the
same emoji the app already draws.

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
