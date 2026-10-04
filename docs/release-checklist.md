# Release checklist

Everything between the current tree and Doublestack being downloadable on both
stores. Every item was checked against the code or the console rather than
copied from `OWNER-TODO.md` or `todos.md`, both of which carry stale entries.

**Release one is submitted on both stores, 2026-10-04.** Nothing is left but
waiting on review, plus one device check that was never recorded as done (below).
Everything else has moved to "Already done" at the bottom.

## Context

| | |
|---|---|
| **Play** | `com.dangerfield.drop2048` · app `4973613941103364487` · Nightjar Labs org `9122554580643893683` |
| **Apple** | `com.dangerfield.drop2048.Drop2048` · Apple ID `6814213924` · SKU `doublestack` |
| **Pro** | `drop2048_pro` · Apple IAP `6814609426` · Play one-time product, purchase option `buy` · $2.99 non-consumable |
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

### In review · nothing to do

Submitted 2026-10-04. Both stores release on approval with no further click.

- **Apple:** version 0.3.0, build `202610022036`, submitted together with
  `drop2048_pro`, both leaderboards and all 22 achievements (26 items in one
  submission). Apple usually takes one to three days.
- **Play:** production release `132 (0.3.0)`, full rollout, all 177 countries
  plus rest of world, sent with the first-launch store listing and App content
  declarations (12 changes). Managed publishing is off, so it goes live the
  moment Google approves it. Google quotes up to 7 days for a new app.

If either store rejects, the fix goes through the normal release-please flow.
From release two onward `release.yml` goes straight to production without you.

### Confirm on a device: ads with tracking denied · **you**

Not recorded as done before submitting, and it is the one QA item that was a
release blocker. On a physical iPhone, set Settings, Privacy & Security,
Tracking to deny for Doublestack, then confirm an interstitial still loads.
`PrivacyInfo.xcprivacy` lists `googleads.g.doubleclick.net` and
`pagead2.googlesyndication.com`, which it has to, because
`NSPrivacyTracking = true` is invalid without at least one domain and Apple bars
the build from review otherwise (ITMS-91064, hit on build 202609252110).

The catch is that iOS blocks listed domains when tracking is denied. If that
enforcement is real, ads stop loading entirely for those users instead of
falling back to non-personalised, and `legal/privacy.md` promises the opposite
in as many words. Apple documents the blocking; developers report it not firing.
Ads loading with tracking allowed and failing with it denied means the domain
list is the cause.

**QA builds are `beta.yml` only** (`gh workflow run beta.yml`). It is the one
path that builds with `RELEASE_CHANNEL_OVERRIDE=beta` and serves test ads. A
build off a release tag is channel `store` and requests live units, which is how
an AdMob account gets suspended for invalid traffic.

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
- **`release.yml`'s "Attach artifacts to GitHub Release" job fails** with
  `fatal: not a git repository`: it calls `gh` without a checkout. Run
  `37238038415` shows it. The store uploads ran before it and succeeded, so
  only the GitHub release is missing its files. **agent**
- **Accessibility:** paused-overlay rows are 39.5dp against a 48dp target
  (`todos.md`). **agent**

---

## Already done

Do not redo any of this.

**Release one was filed by hand on 2026-10-04**, and these are the answers it
carries:

- **Apple age rating is 13+**, overriding a calculated 4+, because
  `legal/terms.md` requires 13 or older and App Store Connect says a EULA age
  minimum must be matched. Advertising is answered Yes. Full answers in
  [`store/age-rating.md`](store/age-rating.md).
- **Apple content rights:** no third-party content. **Price:** free, in all 175
  countries or regions. **App Review:** sign-in not required, contact copied
  from Sodogku. **Game Center** is switched on for the version.
- **`drop2048_pro` on Apple** has a review screenshot (the paywall, padded to
  1290x2796, because Apple rejects anything that is not an iPhone screenshot
  size). Its description now reads "No ads between runs, and your continue
  without an ad.", which replaced "No ads, every palette, two continues", a
  promise Pro does not keep.
- **`drop2048_pro` on Play did not exist** until this release, despite earlier
  notes saying it was created on both stores. It is now active: same name and
  description, $2.99 US with Google's local prices, 174 countries, purchase
  option `buy`, backwards compatible so the billing library finds it by product
  id.
- **Play production** had no countries set. It now carries all 177 plus rest of
  world. The release reused the bundle CI had already put on the internal track
  rather than promoting the track.

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
