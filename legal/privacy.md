---
app: Doublestack
title: Privacy Policy
updated: 2026-09-25
contact: contact@nightjarlabs.llc
---

Doublestack is made by Nightjar Labs LLC. It is a single-player puzzle game with no sign-in, no account and no profile. This page says what the app stores, what it sends and who it sends it to, and it is written to match what the code actually does.

## There is no name attached to any of this

The app never asks for your name, your email address or a password, and we hold no record of you. The only identifier is an **install ID**: a random number generated on your device the first time the app runs. It is not your advertising ID, not a hardware ID, and not derived from anything about you or your phone. It identifies the installation, not the person.

Settings → Delete local data erases everything the app has stored and issues a new install ID, which breaks the link between this device and anything sent before. It cannot reach records that already left the device; for those, [nightjarlabs.llc/delete-data](https://nightjarlabs.llc/delete-data) asks for the install ID, which Settings shows directly under that button.

## What stays on your device

Your runs, scores, stats, achievements, settings and whether you own Pro are stored on the device and nowhere else. There is no sync and no backup. Delete the app or delete your local data and it is gone for good.

## What the app sends

### Our configuration server

On launch the app asks our server which settings to use. That request carries the install ID, your device's language and country, and the app version. The language and country come from your device's own locale setting, not from GPS and not from your IP address. The app makes no other request to a server of ours.

### Analytics, via Grafana Cloud

The app records how the game is being played so we can tell whether it is too hard, too slow or broken. These records carry the install ID and a session ID, and cover things like when a run starts and ends, its score and level, how many blocks were dropped and how it ended; which tutorial steps you reached; whether an ad or a purchase screen appeared and what happened next; and reliability events such as dropped frames or the app being unable to reach our server.

It is gameplay measurement. It holds nothing you have typed, nothing about your other apps, and nothing about where you are. [Grafana Cloud](https://grafana.com/legal/privacy-policy/) stores it on our behalf and does nothing else with it.

### Crash reporting, via Sentry

When the app crashes or hits an error, a report goes to [Sentry](https://sentry.io/privacy/) containing the stack trace, your device model and OS version, the app version and the install ID. The app is configured not to send your IP address or user agent.

### Ads

Doublestack shows ads through Google AdMob on both Android and iOS. The Google Mobile Ads SDK reads your device's **advertising ID** and sends it to Google; the app never reads it directly.

On Android you can reset or turn off that ID under Privacy → Ads in your device settings. On iOS, the app asks for permission before it requests its first ad; decline and the SDK serves ads without your advertising identifier. You can change that answer later under Settings → Privacy & Security → Tracking.

In the EEA, the UK and Switzerland a consent form appears before the first ad request, and your answer controls whether ads are personalised. Google's use of this data is covered by the [Google Privacy Policy](https://policies.google.com/technologies/partner-sites).

Buying Pro removes the ads between runs. Rewarded videos you choose to watch stay available and go through the same SDK.

### Purchases

Pro is a one-time purchase sold through the app store you installed from. That store handles the payment and the app never sees your card, your billing address or your order details. All it learns is whether you own Pro, which it records on the device as a yes or no. We record that a purchase succeeded or failed, and nothing about the purchase itself.

### Game Center (iOS)

On iOS, scores are submitted to Apple Game Center leaderboards under your Game Center identity. Only the score goes. We do not receive your Game Center ID, alias or nickname and we store nothing from it. Apple's handling is covered by the [Apple Privacy Policy](https://www.apple.com/legal/privacy/). Android has no leaderboards.

### Feedback you send us

If you use Send feedback or Report a bug, we receive what you typed plus the app version and build it came from. That text goes to Sentry with the crash reports.

Settings has a **Send diagnostics with feedback** switch, off unless you turn it on. With it on, a report also carries your device model, the build number, and a log of what the app printed during the session: the names of things that happened, not their contents. Your board, your scores and anything you typed elsewhere are not in it.

The free-text box is yours to write in. If you put your email address or your name in it, we will have it, because you sent it.

## Location, and what we actually mean by that

The app asks for no location permission on either platform and reads no GPS. Nothing in our code knows where you are. The country and language we send to our own server come from the language setting on your device, not from your position.

One thing is worth being precise about rather than glossing. Every internet request carries an IP address, and Google's ad network uses yours to estimate a rough area, usually about the size of a city, so it can pick which ad to show. We never see that estimate and never store it. It is still approximate location data being collected, so we say so here and declare it on both app stores rather than hiding behind the fact that it is not our code doing it.

## What the app never collects

No precise location, ever. No contacts, calendar, photos, files, microphone or camera. No name, email address, phone number or postal address. No browsing or search history. No list of your other apps. No payment details. None of these has a read path in the app.

## Permissions

- **Vibrate** (Android): the haptic feedback when a block lands or merges. Turn it off in Settings.
- **Notifications** (iOS, only if you allow it): occasional updates from the app.
- **Tracking** (iOS, only if you allow it): lets the ad SDK use your advertising identifier. Decline and ads still work, they are just not personalised.

That is the whole list. The app declares no camera, microphone, location, contacts or storage permission.

## Your choices

- **Delete local data** (Settings): erases everything on the device and issues a new install ID.
- **Reset progress** (Settings): clears runs, scores and stats, keeps your settings.
- **Advertising ID**: reset or opt out in your device settings.
- **Uninstall**: removes everything the app stored.
- **Delete records already sent** at [nightjarlabs.llc/delete-data](https://nightjarlabs.llc/delete-data). Deleting local data cannot reach analytics and crash records that already left the device; the form can, because those records are keyed by your install ID. Settings shows it under Delete local data, and tapping it copies it.

## Children

Doublestack is for a general audience aged 13 and over. It is not directed at children and is not enrolled in Google Play's Designed for Families programme or Apple's Kids category. We do not knowingly collect anything from a child under 13. If you believe we have, email us and we will delete it.

## Changes to this policy

If this policy changes, the new version is posted here and the "Last updated" date is revised. Material changes are also shown in the app, which asks you to accept them before you carry on playing.
