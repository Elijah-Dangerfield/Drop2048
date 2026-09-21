# Owner TODO

Things only a human with credentials, a password, a store account or an opinion can do. Agents add
to this list; they cannot clear it.

Ordered by when it starts blocking. Last pruned 2026-09-21 while filing the Play declarations.

---

## Blocking now

### File one directive from a real build, so the channel gets proved end to end

The DSN landed in `5558f70` and is committed in `telemetry.properties`, so the blocker is gone. The
channel is built and device-verified up to the send: the button drags and stays put across a
force-stop, the panel captures the frame underneath, a typed directive reaches
`captureUserFeedback` with `feedback_kind=owner_directive`. What has never been observed is a report
arriving in Sentry, because `docs/feedback-log.md` records that nothing has left a device yet.

What is left is one real directive filed from your own build. After that, `/feedback-triage` reads
your directives out of Sentry and files them into `docs/todos.md`, with `docs/feedback-log.md` as
the ledger that keeps it idempotent.

**Expect the first triage run to need you beside it.** Sodogku's found the feedback twin was not
reachable from the Sentry MCP and had to fall back to the carrier's extra and attachment — the
skill is written against that, and it should be corrected from whatever is actually true here.

### `.gitignore` changed, and it is the first time `.claude/` is committed

`.claude/` became `.claude/*` plus `!.claude/skills/`, so the triage skill is source and agent
worktrees and settings stay ignored. Worth a look since it changes what the repo carries.


### Xcode: create the `xcode_select_link`

`xcode-select -p` already returns the right path, **but `/var/db/xcode_select_link` does not
exist**, and that is what the iOS Simulator tooling actually checks. It refuses with "Xcode is
installed but not selected", so an agent cannot attach, launch, tap or screenshot.

C3 worked around it with `xcrun simctl` and by driving the Simulator window directly. It was slow
and had to target taps by accessibility index rather than coordinate.

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

Needs your password, so it is yours. Until then iOS is compile-verified only, and **nothing has
ever been rendered on iOS** — the screenshot harness is Robolectric, so all 26 goldens are Android.

### Play it, and rule on three feel questions

The game is playable, persisted, and now looks like the handoff. Nobody but an agent has played it.

1. **The 500ms opening.** Three candidate curves produced outcomes identical to the digit, so the
   change is measurably risk-free — and no human has felt it. With ▼ now a hard drop (D21), level 4
   arrives in about 33 seconds rather than 223 for a player who never touches it.
2. **Does drag steering read as locked to the finger?** It is absolute-from-grab-point per the
   handoff. The flick thresholds (30dp, 450ms) are unvalidated guesses.
3. **Is the re-paced cascade right?** C3a found it was far too fast to read — the chain callout
   held for three frames and the 2048 existed for 400ms before its own burst erased it. It is now
   paced per step kind. Measured as readable; not yet judged as *good*.

## Release blockers

Both store records now exist and every Play declaration except Data safety is filed, so these are
what stand between the current tree and a build anyone can install. Found 2026-09-21 while filling
the Play forms.

### AdMob: the apps and units exist now, and one flip remains

**Done 2026-09-21.** Two AdMob apps were created, `Doublestack (Android)`
(`ca-app-pub-7008637445039253~5728891206`) and `Doublestack (iOS)`
(`ca-app-pub-7008637445039253~9568808728`), each with a banner, an interstitial and a rewarded
unit. All six ids are filled into `AdUnits.AndroidLive` and `AdUnits.IosLive`. None of this is
secret: an app id and a unit id ship inside every binary and can be read out of any APK, so they
live in git rather than in a CI secret.

**What is left is one atomic change, and it is yours to time.** `AdUnits.useTestUnits` is still
`true` and `AndroidManifest.xml` still carries Google's sample app id. Those two flip **together**,
because a real app id paired with test units is exactly the half-migrated state the manifest
comment and the `AdUnits` KDoc both exist to prevent. Until then a release serves test ads and
earns nothing.

Do not flip it before you intend to ship. Requesting a live unit from a development build is what
gets an AdMob account suspended for invalid traffic, which `AdUnits.kt` warns about at the top.

**Both apps read "Requires review, limited ad serving" and will until a store listing is attached.**
That is normal for an app AdMob cannot find on a store yet; the Sodogku apps sit in the same state.
It resolves once Doublestack is actually published, not before, so it is not a thing to chase.

**iOS is wired now, as of 2026-09-21.** `GoogleMobileAds` 13.9.0 is an SPM dependency,
`IOSAdNetwork` is ported from Sodogku, and both plist keys are in. A simulator install boots and
`AdUnits.IosLive` is live code rather than a record. Three things that port did **not** bring with
it, all of which an iOS ad release needs:

### iOS has no banner, and this one has nothing to copy

Android draws banners through `AdMobBannerSurface`. iOS falls through to `NoBannerSurface`, so the
strip is simply absent there, which is a safe and coherent state rather than a bug. Sodogku has no
banners at all, so the UIKit-`AdView`-inside-Compose bridge is genuinely new work. Interstitials and
rewarded video are unaffected and work on both platforms.

### `SKAdNetworkItems` and the privacy manifest: done

**Done 2026-09-21.** `Info.plist` carries all 50 SKAdNetwork identifiers, scraped from
developers.google.com/admob/ios/privacy/strategies rather than transcribed. Google adds buyers over
time and the list does not update itself, so re-check that page before a release.
`PrivacyInfo.xcprivacy` has `NSPrivacyTracking` true, Device ID as tracking with third-party
advertising, and a new Advertising Data entry, which is `data-safety.md` §7.2 item 4 applied. §7.2
item 5 is closed too: `sentry-cocoa`, `GoogleMobileAds` and `UserMessagingPlatform` were each
confirmed to ship their own signed manifest at the resolved versions.

`NSPrivacyTrackingDomains` is deliberately still empty, and the file explains why at length: the
Google SDK declares none of its own, Apple aggregates manifests, and iOS **blocks** any domain
listed here when ATT is refused, which would break ads for every declining user rather than
degrading them.

**Still yours:** the file has never been added to the target's Copy Bundle Resources phase, so it
ships nowhere. Only Xcode can do that. It is item 8 on `release-checklist.md` and it is the
difference between a manifest and a file sitting in a folder.

### Advertising ID declaration, found and filed

Play flagged it on the App content page rather than the dashboard checklist, which is why it was
missed: "you will not be able to submit releases targeting Android 13 until you complete this
section". Filed 2026-09-21 as **yes, with Analytics and Advertising or marketing**, matching the
advertising-id row in `data-safety.md` §4. App content now reads "you're all caught up".

### `MaxAdContentRating` is never set, and the target audience now makes that matter

The Play target audience is filed as **13-15, 16-17, 18+**, which means Play's Families policy
applies whenever a child uses the app. AdMob is a Play-certified ad network, so that half is
already satisfied and needs nothing. What is missing is the content ceiling: nothing in the tree
calls `setMaxAdContentRating`, so AdMob is free to serve `MA`-rated creative to a 13-year-old.

`AdMobAdNetwork.kt:215` already builds a `RequestConfiguration`; this is one more line on that
builder, set to `MAX_AD_CONTENT_RATING_G`. It is an agent-sized change and it is listed here only
because it is a policy decision about your revenue, not a bug.

The related question is `tagForUnderAgeOfConsent`, deliberately unset at `AdMobAdNetwork.kt:63`
because setting it turns off personalised ads for everyone. That reasoning still holds for the
general-audience branch, but it does mean an under-16 player in the EEA may receive personalised
ads. Worth a ruling now that 13-15 is declared rather than after a complaint.

### The Grafana telemetry pipe is dark, and it is not a URL-scheme question

`gh secret list` shows exactly one repository secret, `SENTRY_AUTH_TOKEN`. Neither environment
(`github-pages`, `production`) holds any. So `GRAFANA_OTLP_BASE_URL`, `GRAFANA_OTLP_INSTANCE_ID`
and `GRAFANA_LOGS_WRITE_TOKEN` are all unset, `GrafanaAppEvents.kt:150` reads them as blank, and
every release built today ships with analytics switched off.

This is the same ask as "The Grafana credentials are now the single highest-value thing you can
supply" below; it is repeated here because it now also touches a store form. `data-safety.md` §7.5
worried that the OTLP URL might not be HTTPS. That worry is premature: there is no value to be
non-HTTPS yet. Grafana Cloud's OTLP gateway is HTTPS, so the "encrypted in transit" answer on the
Data safety form becomes true the moment you paste the real endpoint in. Sentry is already live and
settled by construction, since its DSN is an HTTPS URL committed in `telemetry.properties`.

### Run `scripts/setup_legal_sync.sh` once

Two secrets, one script, and it is the only owner step left on the website work.

It creates a Firebase service account and sets `FIREBASE_SERVICE_ACCOUNT` on the `nightjar` repo,
then prompts you for a fine-grained GitHub token and sets `NIGHTJAR_SITE_TOKEN` here. Until both
exist, `legal-sync.yml` cannot open its pull request and merging one cannot deploy the site.

```bash
./scripts/setup_legal_sync.sh
```

An agent cannot mint a GitHub personal access token, which is the only reason this is yours.

### ~~`doublestack.app` is not registered~~ — closed 2026-09-21, not bought

Resolved by not buying it. `share_footer` now prints `nightjarlabs.llc`, a domain that is already
owned, already live and already the app's home. The full path to the app page is too long to read
at share-image size, so the footer carries the bare studio domain.

### ~~Get off `github.io` and onto a real site~~ — closed 2026-09-21

Everything player-facing now points at `https://nightjarlabs.llc/doublestack/…`. The
`pages/` folder and `.github/workflows/pages.yml` are deleted.

**How it was solved, and why not the way this entry originally proposed.** The original plan was
Cards' shape: buy `doublestack.app`, put an Astro site in `website/`, publish it from this repo to
GitHub Pages. That is one domain, one site and one pipeline per app, forever.

Instead the legal text lives here as Markdown in `legal/`, and `.github/workflows/legal-sync.yml`
opens a pull request against `Elijah-Dangerfield/nightjar` whenever it changes. That repo renders it
at `nightjarlabs.llc/doublestack/privacy` and `/terms` and deploys itself from CI. Every future app
inherits the domain, the design and the pipeline for free.

The load-bearing places the old URL appeared, all updated:

- `LaunchGateConfigValues.kt:130` and `:141`, the compiled fallbacks the app opens when remote
  config is unreachable.
- Filed with Google as the **privacy policy URL**, the store listing **website**, and the
  **delete-data URL** on the Data safety form.
- Filed with Apple as the **Support URL**.

**Still yours:** re-file those four URLs in the two consoles. They were entered against the
`github.io` host and nothing updates them automatically. Nothing is submitted yet, so this is free
today and costs two review cycles once it is not.

**Note the ongoing obligation.** A sync pull request that sits unmerged means the published policy
is stale. `docs/store/release-checklist.md` makes merging it part of shipping a release.

---

## Decide before the chunk that needs it

### Kids theming / age rating: answered, with one piece left

**Answered 2026-09-21 as general audience, 13+.** The hosted privacy policy and terms both say 13
and over and not directed at children, Play's target audience is filed as 13-15 / 16-17 / 18+, and
the code already implements this branch with `TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE`. Designed for
Families is not enrolled and should not be.

What is left is the `MaxAdContentRating` ceiling and the `tagForUnderAgeOfConsent` ruling, both in
Release blockers above. Reopen this only if you want to move to 18+ only, which would mean editing
both legal pages and re-answering the Play form.

### The digest freeze — the window is closing

`PINNED_DIGEST` has now moved **four** times: the merge-position ruling, cutting hard drop,
reinstating it, and the 2026-09-20 difficulty ruling (random spawn column plus `blocksPerLevel`
20 → 15, shipped together so the pin moved once). Every one was free because **nothing has ever been
banked**: no store accounts, no live leaderboard, no shipped build.

**The Daily Challenge was removed on 2026-09-20 (D27), which changes what this section is about.**
The old argument was seed comparability: everyone played one shared board, so moving the digest
split that board between app versions. That reason is gone with the feature. What remains is the
weaker but still real one, SPEC 3's: a score is only comparable to another score played under the
same rules. The all-time and weekly boards survive, so the moment a real score is posted to either,
a digest move silently makes the table a mix of two different games.

Frozen at that moment:
- **`EngineConfig.Default`** — any release that moves a field of it changes what a given score was
  worth. D18's Daily-specific pinning is moot; the comparability argument is not.
- **`level.blocksPerLevel`** — remote-configurable and digest-moving, and it *just moved* (20 → 15).
  The console warns loudly and requires a typed confirmation in prod, but **nothing enforces it
  server-side.**
- **`dailySeedFor`'s stride and salt** — no longer relevant, removed with the feature.

Measured safe to keep tuning live: the speed curve, the spawn table.

**`special.stone.firstLevel` is the difficulty knob you have left, and it is remote.** The harness
priced it on 2026-09-20: moving the Stone from level 12 to 10 roughly halves the 2048 rate (1.79% →
0.84%) and pushes runs ending at 256 from 20% to 36%, while buying zero seconds off time to level 4.
It was not shipped because it is aimed at the end of a run rather than the start, which is not what
"does not get hard quick enough" describes. If the build still feels slow after playing it, that is
a console push, not a release, and not a permanent digest cost.

**Decide the rule now.** The obvious one is that the digest is versioned alongside the leaderboard
and changing it retires the old board.

### `SAVE_FORMAT_VERSION` ownership

Must be bumped whenever `GameState`, `RunTally` or `SavedResolution` change shape. Nothing enforces
it. If forgotten, the failure is silent and appears on a real player's device with their run in it.
Decide whether it becomes a checklist item or a build assertion.

### App name and store identity — settled

**Settled 2026-09-20: the app is Doublestack.** "Drop 2048" turned out to be unavailable on the App
Store, which the New App dialog is the only way to discover, since Apple holds reserved-but-unpublished
names that appear in no search anywhere.

Both stores carry `Doublestack: Merge Block Drop` as the title; the launcher and in-app name are
just `Doublestack`. The identifiers keep the old spelling on purpose: `com.dangerfield.drop2048` is
permanent on Play, `com.dangerfield.drop2048.Drop2048` is what the iOS target builds, and no player
sees either. Same for `Drop2048Application`, `Theme.Drop2048` and the repo name.

**One exception to that, found and fixed 2026-09-21: iOS `PRODUCT_NAME` could not stay `Drop2048`.**
A build at 9d055db still read "Drop 2048" on the home screen, from two places. The target carried a
leftover `INFOPLIST_KEY_CFBundleDisplayName = "Drop 2048"`, and CFBundleDisplayName beats
CFBundleName on the home screen. Underneath it, `GENERATE_INFOPLIST_FILE = YES` derives
`CFBundleName` from `PRODUCT_NAME` and overwrites whatever `iosApp/Info.plist` says, with no
warning, so the `Doublestack` sitting in that file had never once reached a build. Setting
`INFOPLIST_KEY_CFBundleName` does not rescue it either: Xcode accepts the setting and then ignores
it, which was verified against the built plist rather than assumed. `PRODUCT_NAME` is the only lever,
so it is now `Doublestack`, and `Doublestack.app`, the executable and the Swift module renamed with
it. The bundle id is a literal in the pbxproj and did not move.

That made it a submission blocker rather than a cosmetic one: CFBundleName is the name App Store
delivery checks for ITMS-90129, and "Drop 2048" is the name that is already taken.

## Look at these when convenient

### The five block palettes, rendered

`BlockTierPreview` in `libraries/ui/.../catalog/DesignSystemPreview.kt`, laid out as a matrix so a
collision shows as two adjacent cells. **The protanopia ramp is five yellow-greens and one blue
family** — it clears every floor and may still read as drab.

The default ramp is the handoff's and its three failed floors are accepted per your ruling (D14).

### The three special block colours

`BlockSpecialPreview`. Electric violet Wildcard, near-black plum Bomb, neutral grey Stone. These
are the first three colours in the game chosen for **where they aren't** rather than for what they
look like, and the Bomb is nearly black.

### Whether "high contrast" should look loud

That palette is pale faces with dark numerals, which maximises measured contrast (6.31:1, highest
of the five) and is the opposite of what most people picture. Defensible, and possibly still wrong
for what players expect from the setting name.

### Glyphs versus a platform icon set

`◀ ▶ ▼ II` are text characters. The handoff explicitly leaves this open and says to swap for the
platform icon set if it reads better natively.

## Credentials, by chunk

| Needed | For | Notes |
|---|---|---|
| **Fly app + Postgres + `ADMIN_API_TOKEN`** | C7's last mile | Everything else in C7 is built and green. Nothing has run against a deployed server. |
| **Sentry DSN** | C8 | Per environment. |
| **Grafana / OTel endpoint + token** | C8 | The two dashboards that matter: median level reached, and highest-tier-reached distribution. |
| **App Store Connect + Play Console apps** | C9, C10 | Bundle IDs and signing. Leaderboards are configured store-side before any code can submit. |
| **Game Center leaderboard IDs** | C9 | **Exact ids, already in code:** `com.dangerfield.drop2048.leaderboard.score_alltime` (classic, all-time) and `com.dangerfield.drop2048.leaderboard.score_weekly` (**recurring, weekly**). The Daily board was cut (D24), so do not create a third. Until these exist every submission fails silently, exactly like a signed-out player's. The Game Center capability is already enabled on the iOS target (`iosApp.entitlements`). |
| **Game Center achievement IDs** | C9 | All 22, derived from `AchievementId` and typed by hand into App Store Connect. Each is one-step (100% in a single report) and hidden = no. **Changing an id after a player has earned it orphans that badge on their profile**, which is why `PlatformAchievementIdTest` pins the format. The full list: `com.dangerfield.drop2048.achievement.FirstMerge`<br>`com.dangerfield.drop2048.achievement.SixtyFour`<br>`com.dangerfield.drop2048.achievement.FirstBurst`<br>`com.dangerfield.drop2048.achievement.ChainOfFive`<br>`com.dangerfield.drop2048.achievement.ChainOfTen`<br>`com.dangerfield.drop2048.achievement.DoubleBurst`<br>`com.dangerfield.drop2048.achievement.LevelTwenty`<br>`com.dangerfield.drop2048.achievement.FiveHundredBlocks`<br>`com.dangerfield.drop2048.achievement.OnTheBrink`<br>`com.dangerfield.drop2048.achievement.CleanSweep`<br>`com.dangerfield.drop2048.achievement.StoneCold`<br>`com.dangerfield.drop2048.achievement.WildFinish`<br>`com.dangerfield.drop2048.achievement.FirstFigures`<br>`com.dangerfield.drop2048.achievement.SolidRun`<br>`com.dangerfield.drop2048.achievement.SharpRun`<br>`com.dangerfield.drop2048.achievement.BigRun`<br>`com.dangerfield.drop2048.achievement.MonsterRun`<br>`com.dangerfield.drop2048.achievement.OneHour`<br>`com.dangerfield.drop2048.achievement.FiveHours`<br>`com.dangerfield.drop2048.achievement.TenHours`<br>`com.dangerfield.drop2048.achievement.TwentyFiveHours`<br>`com.dangerfield.drop2048.achievement.FiftyHours` |
| **Decide whether Play Games is in v1** | C9 | SPEC 15 names it; C9 shipped an inert Android seam. Yes means a Play Games Services project, a second id per board, and replacing `NoGameServices`. |
| **Badge art** | C9 | The 24 achievement glyphs are placeholder emoji, and the locked treatment (35% alpha) reads weakly on colour emoji. |
| **Confirm `drop2048.app`** | C9 | Now only in the share footer (`share_footer`). The `legal.*` defaults moved to the live Pages URLs on 2026-09-16. If the domain is not yours, change `share_footer`; if it is, pointing the legal keys at it is a config push, not a release. |
| **AdMob account, app + unit IDs** | C10 | Test IDs work until then. |
| **Pro IAP product** | C10 | Non-consumable, $2.99 at current scope (SPEC 2 — thinner than the original $3.99 because coins, powerups and Zen are cut). |
| **Privacy policy + terms, hosted** | C11 | The gate does legal re-accept, so the version matters, not just the text. |
| **Revoke dead Supabase secrets** | Housekeeping | `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` on Fly and in GitHub Actions. **Keep `DATABASE_URL`** — the config server still uses that Postgres. |

## The Grafana credentials are now the single highest-value thing you can supply

The instrument is **built**. `steer_ms_p50` and `tap_gap_ms_p50` ship on every `run.end`, and the
per-drop values ride the 10th-drop sample so they arrive next to `level` — which answers "do players
slow down as the board speeds up", a question the offline sweep structurally could not.

**One week of real data retires an assumption that every clocked number in this project rests on.**
The modelled 250ms decision time is worth five levels of median and a 3x swing in the 1024 rate,
which is more than the drop clock, the spawn table and every speed-curve change put together.

Once the endpoint and token land, create the two dashboards from
`docs/practices/observability.md` — they are defined query-for-query. Expect `unwrap level` to need
a tweak on first contact with real ingest; that is the most likely thing to not work first time.

## Art and audio

The longest lead time in the project.

### Author the sample bank — this is now the only thing between you and a game with sound

The playback path is **built and live on both platforms** (Android `SoundPool`, iOS pooled
`AVAudioPlayer`), wired to the sound setting, with per-cascade-step pitch.

This list was wrong until 2026-09-20 and is now derived from the `Sound` enum rather than from
memory. It said sixteen files, listed a `nudge.ogg` that D21 retired, and omitted `hard_drop.ogg`.
Anyone who had followed it would have authored the wrong set.

**15 files, one `.ogg` per sound, mono, short, named by its key:** `spawn.ogg`, `move.ogg`,
`hard_drop.ogg`, `lock.ogg`, `merge.ogg`, `merge_big.ogg`, `burst.ogg`, `bomb.ogg`,
`board_cleared.ogg`, `danger_enter.ogg`, `danger_exit.ogg`, `level_up.ogg`, `stacked_out.ogg`,
`ui_tap.ogg`, `ui_back.ogg`.

Two of those fifteen are declared but **never fired by any code**: `spawn` and `ui_back`. So the
set that actually makes noise is thirteen. Author them last, or not at all until something plays
them.

Drop them in `libraries/ui/src/androidMain/assets/audio/` and the Xcode project's resources. **No
code changes.** A missing sample logs its name and leaves that one effect silent.

**The merge sample is the one that matters.** It gets resampled up to a full octave, so it has to
survive being played at 2x speed without sounding like a pitch-shifted sample. That single sound is
half of what SPEC 21 says makes this game feel good.

Music is not built — that is streaming, not a sample bank.

### Confirm the iOS audio session category

Currently `Ambient`: the game honours the ring/silent switch and does not stop the player's music.
The alternative is `Playback`, which overrides both. `Ambient` is the right default for a puzzle
game; say if you disagree.

### Haptics: Android confirmed working, iOS still unfelt

Android fired for the first time and the Off/Light/Strong setting was confirmed scaling end to end
in `dumpsys`. **iOS Core Haptics compiles and links and has never executed** — the burst envelope
and the stacked-out double are still guesses there.

### Art

App icon, launch screen, store screenshots, feature graphic. The tier block faces are the design
system's job, not an illustrator's.
