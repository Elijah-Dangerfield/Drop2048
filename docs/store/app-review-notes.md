# App Review notes

Answers to Apple's Guideline 2.1 "Information Needed" request for the first submission (November
2026). Apple asked for these both as a reply in App Store Connect and in **App Review Information
→ Notes**, so they are kept here as the source of truth for future submissions. Re-check §4 and the
Pro perks against the code before each resubmission.

---

## Reply to paste into App Store Connect (and into the Notes field)

> Hello, and thank you for the review. Answers to each point below.
>
> **1. Screen recording**
>
> Attached is a recording captured on a physical iPhone running the latest iOS. It begins with
> launching the app and shows: the first-run tutorial, a full run (steering, hard drop, merges and
> a chain), the game-over screen with the optional rewarded-video continue, the free experience with
> ads, opening Doublestack Pro from Settings, purchasing Pro with a sandbox account, the app after
> purchase (no interstitial or banner ads, free continue), and Restore Purchase.
>
> The app has no account registration, no login and no account deletion, because there are no
> accounts of any kind. It has no user-generated content and no social features, so no reporting
> or blocking mechanism applies.
>
> **2. Purpose and target audience**
>
> Doublestack is a single-player falling-block puzzle game for a general audience aged 13 and up.
> A numbered block falls into a narrow grid; the player steers it and drops it. When it lands on a
> block with the same number, the two merge into one block worth double, and that new block can
> trigger further merges in a chain. Making a 2048 clears the row. The game speeds up with each
> level and ends when the grid fills.
>
> The value is a quick, skill-based puzzle game that plays entirely offline, needs no sign-in, and
> is built to be readable by everyone: every block shows its number, with colour-blind palettes, a
> high-contrast palette, larger numbers and a reduce-motion setting.
>
> **3. Setup and access to main features**
>
> No login, credentials or sample files are needed. Launch the app and the tutorial starts; after
> it, tap Play to start a run.
>
> - Controls: drag on the board to move the block, flick down to hard drop. Settings has an option
>   to show arrow buttons instead.
> - Settings: sound and vibration, controls, colour palettes and accessibility options, replay the
>   tutorial, Doublestack Pro (Go Pro and Restore purchases), privacy and ad preferences, and
>   Reset progress / Delete local data.
> - Doublestack Pro is a single non-consumable in-app purchase (product ID `drop2048_pro`). Open it
>   from Settings, or from the continue screen after a game over. It removes interstitial and
>   banner ads, and lets the player take the one continue per run without watching an ad.
>   Rewarded videos remain available to everyone as an option. Purchases can be restored with
>   Restore purchase on the Pro screen or Restore purchases in Settings.
> - Game Center leaderboards are used if the player is signed in to Game Center; nothing in the
>   app requires it.
>
> **4. External services**
>
> - Apple StoreKit: the Doublestack Pro in-app purchase and restore.
> - Apple Game Center: leaderboards (score only).
> - Google AdMob (Google Mobile Ads SDK), with Google's User Messaging Platform for consent:
>   interstitial, banner and optional rewarded ads for players without Pro. App Tracking
>   Transparency permission is requested before the first ad request.
> - Sentry: crash and error reporting, and the in-app feedback form.
> - Grafana Cloud: anonymous gameplay analytics keyed to a random install ID.
> - Our own configuration server (hosted on Fly.io): returns game and feature settings on launch.
>
> The app uses no AI services, no authentication provider and no third-party data provider. The
> gameplay itself runs entirely on the device.
>
> **5. Regional differences**
>
> The app's features and content are the same in every region. The only differences are those
> required by law or set by the App Store: in the EEA, UK and Switzerland a Google consent form is
> shown before the first ad request, and the Pro price is the App Store's local price for each
> storefront. The app is currently in English only.
>
> **6. Regulated industry or protected material**
>
> Not applicable. The app does not operate in a regulated industry and contains no third-party
> protected material. The game, its art and its code are original work by Nightjar Labs LLC.
>
> Thank you.

---

## Recording the demo when the device already owns Pro

Pro is a non-consumable, so a real or TestFlight purchase cannot be undone on that Apple ID. A
**sandbox** purchase can, and the app is already built to follow it: `RealEntitlements` re-asks
StoreKit on launch and on every foreground, and a `NotOwned` answer clears the cached flag
(`libraries/billing/impl/.../RealEntitlements.kt`, `refreshFromStore`). Nothing in the code needs
to change.

1. **Get a sandbox account with no purchases.** App Store Connect → Users and Access → Sandbox →
   Test Accounts. Either create a new tester (any unused email, it does not need to be real), or
   open the existing one and use **Clear Purchase History**. Clearing can take a few minutes to
   take effect; a new tester is instant.
2. **Install a build that uses the sandbox.** Run the app from Xcode onto the iPhone (a Debug or
   Release configuration, not a `.storekit` configuration file). TestFlight builds bill your real
   Apple ID's TestFlight purchases, which cannot be cleared, so do not record from TestFlight if
   that build already bought Pro.
3. **Sign the device in to the sandbox tester.** iOS Settings → Developer → Sandbox Apple Account
   (on older iOS: Settings → App Store → Sandbox Account). Sign out of any other sandbox account
   there first. Your real Apple ID stays signed in as normal.
4. **Delete the app and reinstall it** from Xcode. That clears the on-device Pro cache and the
   tutorial so the recording starts from a true first launch. (Settings → Delete local data does
   the same for the cache, but a reinstall is the cleaner demo.)
5. **Hide the debug menu.** Do not open it during the recording; it has a Grant Pro switch that
   would confuse the reviewer.
6. **Record**: start Control Centre screen recording, then launch the app from the home screen.
   Suggested order: tutorial, one run with a chain, game over and the ad continue, an interstitial
   between runs, Settings → Doublestack Pro → buy (the sandbox sheet is fine to show), back to a
   run showing no ads and the free continue, then Restore Purchase.
7. **To redo the take**, clear the tester's purchase history again (or make another tester) and
   repeat from step 4.

If ads do not fill on the device during recording, keep going: Apple only needs to see where they
appear and that Pro removes them.

## Metadata to fix before resubmitting

`listing.md` §3 says Pro "removes the ads between runs, unlocks every palette, and gives you two
continues per run instead of one". The shipping paywall (`strings.xml`, `paywall_perk_*`) and code
say otherwise: every palette is free, and Pro makes the one continue ad-free rather than adding a
second. If that sentence is in the App Store description, change it to match before resubmitting,
since the reviewer will compare it against the recording (Guideline 2.3.1).
