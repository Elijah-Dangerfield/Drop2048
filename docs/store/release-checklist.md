# Release checklist

Runnable, in order. Every command has been checked against this tree; where one has actually been
executed and what it did is recorded, because a checklist nobody has run is a wish list.

Derived 2026-09-10 for C13, against `75cb605`.

**Nothing in this project has ever been released.** `versions.properties` says `versionName=0.1.0`,
`versionCode=1`, `buildNumber=1`, `releaseChannel=dev`, and there is no store listing on either
platform. So §1 is not a formality; it is a list of things that do not exist yet.

---

## 0. The gate, before anything else

```
./gradlew testDebugUnitTest :apps:server:test :apps:compose:assembleDebug \
    :apps:compose:compileKotlinIosSimulatorArm64 detekt
```

**0 skips expected.** Docker must be up or the Testcontainers tests skip, and a skip is a finding
(L46). This is the project's standing verification and it is not optional before a release.

---

## 1. Blocked on the owner: none of this can be done by an agent

Ordered by what blocks first. The consolidated version of this list, with everything from every
other document in `docs/store/`, is §7.

| # | Thing | Why it blocks |
|---|---|---|
| 1 | **The app name** | Decides the Play title, the App Store name, the bundle name and half the listing copy. Also has to resolve the `Drop2048` / `Drop 2048` split between `apps/compose/src/androidMain/res/values/strings.xml` and everything else. |
| 2 | **The kids / age-rating decision** | Decides the ad SDK configuration and the consent flow, and on Apple whether third-party analytics may exist at all. `age-rating.md` §2. |
| 3 | **Play Console app + App Store Connect app** | Everything below needs the listings to exist. |
| 4 | **The upload keystore** | §3. Nothing signs without it. |
| 5 | **Icon art** | Blocks both listings, and blocks the Play feature graphic. |
| 6 | **The privacy policy and terms pages** | The URLs are already compiled in and the hosted text is wrong (`data-safety.md` §8.5). |
| 7 | **AdMob account, app id and unit ids** | §4. |
| 8 | **The IAP product** | §4. |
| 9 | **Game Center leaderboard ids and the capability** | §5. |
| 10 | **Sentry DSN and Grafana OTLP credentials** | Not release-blocking, but a release shipped without them reports nothing, and `data-safety.md`'s declarations assume they are set. |

---

## 2. Version bumping

Three numbers, three owners, and only one of them is edited by hand.

- **`versionName`** is owned by **release-please** and lives between the
  `x-release-please-start-version` markers in `versions.properties`. Do not edit it by hand. Read
  the comment above it before touching that file: a file listed in `extra-files` **without** the
  markers is skipped silently, exit 0, and that is how a downstream project shipped new code
  labelled with the old version.
- **`versionCode`** is owned by CI. `release.yml` passes `VERSION_CODE_OVERRIDE` as the commit count
  at the tag, which is monotonic and shared with `beta.yml` and the iOS Fastfile, so the stores
  never see it go backwards. The `versionCode=1` in the file is what local builds see and it does
  not matter.
- **`buildNumber`** is the same shape, via `BUILD_NUMBER_OVERRIDE`.

Precedence is in `Versioning.kt`: CI env overrides, then `versions.properties`, then hard-coded
defaults. The Android `versionCode` and the in-app About string both read from that one resolution,
which is what keeps the installed binary and the Settings screen in lockstep.

**Manual check before tagging:** `git log` since the last tag has Conventional Commit prefixes that
produce the version bump you expect. A `feat!` is a major, a `feat` a minor, a `fix` a patch.

---

## 3. Signing

Resolved in `build-logic/src/main/java/com/dangerfield/drop2048/util/Signing.kt`, from env first
then `local.properties`:

| Env | `local.properties` | |
|---|---|---|
| `ANDROID_KEYSTORE_PATH` | `android.keystore.path` | Either this |
| `ANDROID_KEYSTORE_BASE64` | none | or this (CI decodes to `build/keystore/release.keystore`) |
| `ANDROID_KEYSTORE_PASSWORD` | `android.keystore.password` | |
| `ANDROID_KEY_ALIAS` | `android.key.alias` | |
| `ANDROID_KEY_PASSWORD` | `android.key.password` | |

**If any of them is missing, the release variant falls back to the debug signing config**
(`ApplicationConventionPlugin.kt:112`). It does not fail. A release build produced without the
keystore is a debug-signed artefact that Play will reject on upload, which is the right place for
it to fail but a confusing message to receive.

`release.yml` has a "Verify Android signing secrets" step in front of the build, so CI catches it.
A local release build does not.

Local release build:

```
./gradlew :apps:compose:bundleRelease -Prelease.signing=true
./gradlew :apps:compose:assembleRelease -Prelease.signing=true
```

The keystore itself is an owner artefact. **Back it up somewhere that is not this machine.** Losing
the upload key is recoverable through Play's reset process and losing the app-signing key is not.

---

## 4. Flags that must be flipped before a real build

This is the list the chunk was asked for. Every one is currently on its development value, and
every one of them is silent if forgotten.

| # | Where | Now | Must become | If forgotten |
|---|---|---|---|---|
| 1 | `libraries/ads/src/commonMain/.../AdUnits.kt`, `useTestUnits` | `true` | `false` | Every impression is a Google test ad. Revenue is zero and nothing anywhere says so. |
| 2 | Same file, `AndroidLive.rewarded` / `AndroidLive.interstitial` | `""` | Real unit ids | `pick()` falls back to the test unit when a live id is blank, and that is **deliberate**: a blank id is an SDK error and an SDK error on the rewarded path pays the player anyway. So a half-filled migration silently serves test ads rather than crashing. Fill both. |
| 3 | Same file, `IosLive.*` | `""` | Real unit ids, **when iOS ads exist at all** | iOS serves no ads today (§5). |
| 4 | `apps/compose/src/androidMain/AndroidManifest.xml`, `com.google.android.gms.ads.APPLICATION_ID` | Google's sample app id | The real AdMob Android app id | The SDK initialises against the sample app and serves nothing real. It is not in `AdUnits.kt` because the SDK reads it before any Kotlin runs. |
| 5 | `apps/ios/iosApp/Info.plist`, `GADApplicationIdentifier` | **absent** | The real AdMob iOS app id | `MobileAds` throws at startup without it. Absent is correct today because no ad SDK is linked; it becomes mandatory in the same change that links one. |
| 6 | `features/gate/src/commonMain/.../StoreListing.kt`, `AppStoreId` | `""` | App Store Connect's numeric "Apple ID" | The force-update wall's only button falls back to `https://apps.apple.com/search?term=Drop 2048`. That is a deliberate graceful fallback, not a bug, but it lands a walled-out player on a search results page instead of the app. **Note the fallback also hard-codes the name**, so it moves with the app-name decision. |
| 7 | `apps/ios/iosApp/iosApp.entitlements` | `com.apple.developer.applesignin` | **Game Center**, and drop the Apple Sign In key | Game Center authentication fails, every leaderboard submission is silently refused, and the app carries an entitlement for a sign-in it deleted in C0. App Review risk either way (`data-safety.md` §8.4). |
| 8 | `apps/ios/iosApp/PrivacyInfo.xcprivacy` | Written, **not in the target** | Added to Copy Bundle Resources | The manifest ships nowhere and the upload is rejected or warned. Only an owner can do this; it needs Xcode. |
| 9 | `apps/compose/src/androidMain/AndroidManifest.xml`, `android.permission.CAMERA` + `uses-feature` | Declared | **Deleted** | The Play listing shows a Camera permission for a falling-block puzzle. Nothing uses a camera (`data-safety.md` §8.3). |
| 10 | Same file, `android:allowBackup` | `true` | **An explicit decision** | Google Auto Backup restores `AppData` including the install id to a new device, which makes the Settings copy "There is no backup" false and weakens the privacy declarations. `data-safety.md` §7.3. |
| 11 | `pages/privacy.html` | The template's, and it denies serving ads | Rewritten from `data-safety.md` §2 | A live privacy policy that contradicts the shipped app. `data-safety.md` §8.5. |

A lint rule or a release-time assertion over rows 1, 2 and 4 would be worth having, and does not
exist. Flagged, not built.

---

## 5. Baseline Profile and R8: both run, and both pass

**C13 ran `generateBaselineProfile` for the first time in this project and it
failed. C13a fixed it and ran R8.** The original diagnosis is kept below §5.4
because the *reason* it failed is the reusable part.

### 5.1 Where it stands

```
./gradlew :apps:compose:generateBaselineProfile
```

`BUILD SUCCESSFUL in 5m 43s`, 3 tests, 0 failed, 0 skipped. Committed at
`apps/compose/src/androidRelease/generated/baselineProfiles/`:
**34,268 baseline rules and 29,573 startup rules**, with the engine
(`libraries/cascade`, 795), the game (`features/game`, 917), Room (680),
kotlinx-serialization (1,622) and each visited screen all present.

```
./gradlew :apps:baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest
```

`BUILD SUCCESSFUL`. **R8 has now run against this app and broke nothing.** The
result XML says `skipped="2"` — the two generators, which `BaselineProfileRule`
refuses to run against a minified variant, exactly as that class's KDoc warns —
and `theMinifiedAppReachesTheMenuAndNavigates` with `time="18.729"` and no
`<skipped/>`. **It RAN.** Check that every time: a skip reads like a pass.

```
apps/baselineprofile/build/outputs/androidTest-results/managedDevice/benchmarkrelease/pixel6Api34/
```

No keep rules were added. `apps/compose/proguard-rules.pro` is untouched.
`docs/decisions.md` has the entry on why that is a result rather than luck, and
on the parts of the app the one journey does not cover.

### 5.2 What the journey is now

`BenchmarkJourney` walks the tutorial — steer, hard-drop, acknowledge the card,
hard-drop — takes "Skip tutorial" when it appears at drop 3, pauses the run it
lands in, and visits Stats, Daily Challenge and Settings. The destination is the
**pause** overlay and not the start overlay, because `finishTutorial` starts a
live run rather than returning to a menu. Five emulator runs went into learning
that and the three anchoring rules that came out of them are in `decisions.md`.

Achievements is deliberately not on the journey: it is reachable only from the
fourth of eight Settings sections, and the scroll-then-tap proved unreliable
across two runs. It has no profile and no R8 coverage as a result.

### 5.3 Before you cut a release

Both commands above must pass, and the profile in
`apps/compose/src/androidRelease/generated/baselineProfiles/` must be the one
they produced. `.github/workflows/baseline-profile.yml` now **opens an issue when
it fails** rather than failing silently, and its sanity check no longer requires
coverage of `features/onboarding`, a module deleted in C5 — which would have
failed the job on a perfectly good profile.

### 5.4 The original failure, kept because the reason generalises

C13's run: `BUILD FAILED in 9m 8s`. The managed device worked, the AOSP system
image booted, the non-minified release APK built and installed, and the
instrumentation ran. All three tests failed with the same cause:

```
java.lang.IllegalStateException: Never reached Home. [foreground=com.dangerfield.drop2048]
  screen: SCORE | 0 | best 0 | LEVEL | 1 | II | 2 | ×2 | ◀ | ▼ | ▶ | Slide it over |
          Drag anywhere on the board. The block follows your finger.
  at BenchmarkJourney.reachHome(BenchmarkJourney.kt:72)
```

**The infrastructure was fine. The journey was the template's.** It tapped
through "Continue as guest" and waited for a Home screen containing "Send
Feedback". None of that is Drop 2048: the identity stack was deleted in C0 and
SPEC 13 drops first launch straight into the tutorial. The error message is the
tutorial's first coach mark, word for word from `strings.xml`.

Note what the failure output is doing: `describeScreen()` printed exactly what
was on screen, and that is the only reason this took one nine-minute run to
diagnose rather than four. Its KDoc says that is what it is for. It was right,
and it was right four more times in C13a — every one of that chunk's five runs
was diagnosed from the one line it printed.

## 6. Order of operations for the first release

1. Gate green, 0 skips (§0).
2. Owner items 1-8 from §1 resolved.
3. Flags flipped (§4), all eleven, in one commit so a half-migration is not possible.
4. `generateBaselineProfile` fixed and run; the generated profile committed (§5).
5. `pixel6Api34BenchmarkReleaseAndroidTest` run and **confirmed to have RUN, not skipped** (§5).
6. Licence report regenerated (`docs/store/licenses.md`) and the in-app `licenses_body` copy
   reconciled with it. **It currently says every dependency is Apache 2.0 or MIT and the generated
   report says 17 modules are on proprietary Google SDK terms.**
7. `data-safety.md` re-derived against the tree as it is at that moment. C8 landed at `5244bbe`
   and was already folded in (§2.3a); the next thing that moves it is a new SDK or a new destination
   for anything the player types.
8. Store screenshots produced (`screenshots.md` §2 for Play, §3 for why iOS is blocked).
9. Tag, and let `release.yml` do the rest. It signs the AAB, renders release notes, uploads to Play
   (routing to `internal` automatically on a first release, because Play will not accept an
   automated production upload without a manually-promoted draft), creates the Sentry release and
   uploads mappings.
10. **Manually promote the first Play release** from internal to production. The workflow logs a
    warning telling you to; nothing enforces it.
11. Install the signed release APK and play it. L56 is the argument: this project went eight chunks
    with a green gate, 1,000+ passing tests and an app that could not get past the tutorial.

---

## 7. Consolidated owner list

Everything from every document in `docs/store/`, in one place, because this is the artefact that is
actually useful. Items marked **decision** need an opinion; the rest need an account, a credential
or a file.

### Decisions, and each one blocks work rather than just paperwork

1. **The app name**, Play title, App Store name, `CFBundleName`, `StoreListing`'s search fallback,
   and the `Drop2048` / `Drop 2048` split between the two `strings.xml` files.
2. **Kids theming / age rating**, decides the ad SDK configuration, the consent flow, the Play
   target-audience answer and whether Apple permits third-party analytics at all. Carried on
   `OWNER-TODO.md` since before ads were wired. `age-rating.md` §2.
3. ~~**`android:allowBackup`**~~ **Settled in C13a: it is `false`.** The install id is the only
   identity this app has and Auto Backup would restore it onto a second device, so two phones would
   report as one install and share one config-targeting bucket; and it made the in-app copy "There
   is no backup" false. The cost — run history does not move phones — is what the Settings copy
   already promises. `data-safety.md` §7.3. Nothing is blocked on this any more.
4. **Whether to show the install id** somewhere copyable, so Play's "users can request deletion"
   answer is fully true rather than nearly. `data-safety.md` §7.1.
5. **The support email**, `pages/privacy.html` currently says `contact@nightjarlabs.llc`. Confirm
   or replace.
6. **Confirm `drop2048.app` is yours.** It is the share footer and the compiled default for
   `legal.termsUrl` and `legal.privacyUrl`.
7. **Whether Play Games is in v1**, SPEC 15 says no and Android binds an inert seam. Saying yes
   means a Play Games project, a second id per board, and re-answering the IARC "users interact"
   question (`age-rating.md` §3.1).

### Accounts and credentials

8. **Google Play Console app**, with the bundle id `com.dangerfield.drop2048`.
9. **App Store Connect app**, same bundle id, and its numeric Apple ID for `StoreListing.AppStoreId`.
10. **Android upload keystore** plus the four secrets in §3, in GitHub Actions. Back it up off this
    machine.
11. **`PLAY_SERVICE_ACCOUNT_JSON`** for the automated Play upload.
12. **Apple signing**, the Fastlane lanes exist; the certificates and profiles do not.
13. **AdMob account**, the Android app id (manifest row 4) and both Android unit ids (rows 1-2).
14. **The Pro IAP product**, `drop2048_pro`, non-consumable / managed, $2.99, created on both
    stores.
15. **Game Center leaderboard ids**, exactly as typed in `Leaderboard.kt`:
    `com.dangerfield.drop2048.leaderboard.score_alltime` (classic) and `…score_weekly`
    (recurring weekly). There is no Daily board; D24 cut it.
15b. **Game Center achievement ids**, all 24, one per `AchievementId` entry under the
    `com.dangerfield.drop2048.achievement.` prefix. The full list is in `OWNER-TODO.md`. Each is
    one-step and not hidden. An id changed after a player earns it orphans that badge on their
    profile, so treat these as permanent once the first build ships.
16. **Sentry DSN** per environment, and **Grafana OTLP endpoint, instance id and write token**.
    Without them both telemetry pipes are off in any build, and every analytics row in
    `data-safety.md` describes a build that does not exist yet.

### Files and artwork

17. **App icon**, and the Play feature graphic (1024x500). Longest lead time in the project, and
    worse than "not started": **every icon surface in the app is the template placeholder reading
    "YOUR APPS IMAGE HERE"**, the Android launcher at all five densities, the adaptive foreground,
    the 512x512 Play listing icon, the 1024x1024 iOS icon, and the icon on the hosted legal pages.
    App Store review rejects placeholder icons outright. Sizes and file locations are in
    `icons.md`; the art must also not read as a children's app, for the reason in `age-rating.md`
    §2.
18. **Badge art**, the 24 achievement glyphs are placeholder emoji, and two of them (🩸 and 🃏)
    are the ones a content-rating reviewer would look twice at (`age-rating.md` §1).
19. **Host the privacy policy and terms, and bump the legal version.** `pages/privacy.html` was
    rewritten in C13a from `data-safety.md` §2 and no longer denies the ad and analytics SDKs the
    app ships; what is left is an owner job, not a writing one. Confirm the support address
    (item 5), publish the page at the URL `legal.privacyUrl` compiles to (item 6), and **bump the
    legal version**, because the launch gate re-asks for acceptance on the version and not on the
    text. `pages/terms.html` was not touched and should be read once before it goes up.
20. **Store screenshots.** Play: the goldens are admissible but soft, see `screenshots.md` §2.
    iOS: blocked on item 21.

### Xcode-only, nobody else can do these

21. **`sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`**, `/var/db/xcode_select_link`
    does not exist, so no agent can attach to, launch or screenshot a simulator. Already
    `OWNER-TODO.md`'s first blocking item; it now also blocks every iOS store screenshot.
22. **Add `PrivacyInfo.xcprivacy` to the app target's Copy Bundle Resources phase.** The file is
    written and valid (`plutil -lint` passes); it is not in the target.
23. **Enable the Game Center capability on the App ID**, and confirm the provisioning profile
    carries it. The file half was done in C13a — `iosApp.entitlements` now holds
    `com.apple.developer.game-center` and nothing else — but an entitlements file is a claim about
    a profile, and nobody here can archive or sign to check it. Until this is done, every
    `GKLeaderboard.submitScore` fails silently, which is indistinguishable from a signed-out
    player.
24. **Confirm `sentry-cocoa` ships its own signed privacy manifest** on the resolved version, and
    the same for Google Mobile Ads whenever that package is added.

### Engineering follow-ups, not owner-blocked but release-blocking

25. **Fix `BenchmarkJourney`** so the profile generators and the R8 smoke test run (§5). Until then
    R8 is unvalidated on a build that ships with `isMinifyEnabled = true`.
26. ~~**Reconcile `licenses_body`**~~ **Done in C13a, structurally rather than by editing the
    sentence.** `LicensesScreen` reads a generated `files/licenses.txt` written by the same init
    script run that writes `licenses.md`, and the copy above it names no licence at all — naming
    one is the thing that goes stale. What survives is the step: **regenerate before filing**
    (§6, step 6), because nothing in the build does it for you.
27. ~~**Delete the camera permission**~~ **Done in C13a**, along with the `uses-feature` and the
    nine `:libraries:ui` files it existed for. `:libraries:ui`'s `iosMain` `NativeViewFactory`
    still declares the camera bridge and `IOSNativeViewFactory.swift` still implements it, with no
    Kotlin caller; that is dead code in the iOS binary rather than a store problem, and it was left
    because it cannot be build-verified here (`data-safety.md` §8.3).
28. **Re-derive `data-safety.md`** against the tree at submission time. C8 landed mid-chunk and is
    already folded in (§2.3a), so this is about whatever lands next.
