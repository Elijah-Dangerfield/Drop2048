# Owner TODO

Things only a human with credentials, a password, a store account or an opinion can do. Agents
add to this list; they cannot clear it.

Ordered roughly by when it starts blocking. Nothing here blocks C0 through C3, which is
deliberate: the loop gets built and proven before anyone has to open an account.

---

## Blocking soon

### Xcode toolchain

Sodogku hit this and could not run anything on an iOS simulator: `xcode-select` pointed somewhere
that was not Xcode. Needs your password, so it cannot be automated.

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

Verify with `xcode-select -p`. Until this is right, iOS is compile-verified only and no agent can
confirm anything actually runs on a phone.

**Blocks:** any "does it feel right on iOS" judgement, so effectively C3.

### Docker running, and one command to run once it is

C0 rewrote the integration harness onto the remote-config endpoint and **it has never actually
run**. Docker was down, so all 5 of the suite's skipped tests self-skipped, and a skipped test
reads exactly like a passing one in the summary.

Start Docker Desktop, then:

```bash
./gradlew :apps:integration:testDebugUnitTest :apps:server:test
```

That covers `HarnessSmokeTest` plus the four Testcontainers Postgres tests
(`PostgresAppConfigSourceTest`, `PostgresAppConfigAdminRepositoryTest`,
`PostgresAppConfigManifestRepositoryTest`, `DatabaseSchemaTest`).

**Blocks:** trusting C7's config path. Worth doing before C7 rather than during it.

### Revoke the now-dead Supabase credentials

C0 deleted the entire Supabase auth stack. If a Supabase project exists for this app, these are
now dead and should be revoked rather than left live:

- `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` as Fly secrets
- The same two as GitHub Actions secrets

**Keep `DATABASE_URL`.** The Postgres itself is still in use by the config server.

### Play both control schemes and pick (C3a)

Not a credential, an opinion, and it is yours. Buttons ship first because they are testable. Once
Drag exists, play both for a day and say which is the default.

**Blocks:** C3a closing.

### Settle 5x7 vs 5x8 on a real phone (C3)

Same deal. 8 rows buys a row of reaction time in the danger state, 7 draws bigger blocks. An
agent cannot hold a phone.

**Blocks:** C3 closing. Everything downstream works either way, so this is not urgent, but it
gets more expensive to change after art is final.

---

## Needed before the relevant chunk

### Sentry DSN

The template wires Sentry (`SentryLogTree`). It needs a real DSN per environment.

**Needed by:** C8.

### Grafana / OTel credentials

`:libraries:telemetry:impl` ships the Grafana log tree. Needs an endpoint and a token.

**Needed by:** C8. The two dashboards that matter are median level reached and highest-tier
distribution at run end.

### Fly.io app for the server

Remote config is server-driven and the admin console is served from the same app. Needs a Fly app
and a Postgres.

**Needed by:** C7.

### App Store Connect and Play Console apps

Bundle IDs, an app record on each store, and a signing setup.

**Needed by:** C9 (Game Center and Play Games leaderboards are configured store-side before any
code can submit to them), C10 (the IAP product has to exist before billing can query it).

### Game Center leaderboard + achievement IDs

Created in App Store Connect, mirrored in Play Games. The 24 achievements in SPEC 15 and the
three leaderboards in SPEC 15 each need an ID.

**Needed by:** C9.

### AdMob account, app IDs and ad unit IDs

One app ID and one ad unit per placement per platform. Test IDs work until then, which is why
this is not earlier.

**Needed by:** C10.

### Pro IAP product

A non-consumable, `$2.99` at current v1 scope (see SPEC 2, the price is thinner than the original
spec's $3.99 because coins, powerups and Zen are cut). Created in both stores.

**Needed by:** C10.

---

### Confirm the three special block colours

Electric violet Wildcard, near-black plum Bomb, neutral grey Stone. They clear every measured
floor, and Stone's worst case anywhere is ΔE 26.1 against the tritanopia ramp's 2.

Worth your eye because these are the first three colours in the game chosen for **where they
aren't** rather than for what they look like. The Bomb in particular is nearly black.

`BlockSpecialPreview` in `DesignSystemPreview.kt`.

### Look at the five block palettes rendered

They are numerically sound and **nobody has seen them.** C2 authored them by hill-climbing against
a constraint set rather than by eye, and every one clears its contrast, ΔE and luminance floors.
That guarantees they are distinguishable. It does not guarantee they are nice.

Open `BlockTierPreview` in `libraries/ui/.../catalog/DesignSystemPreview.kt`. It lays the ramp out
as a matrix, one row per tier and one column per palette, so a collision shows up as two adjacent
cells rather than needing five previews compared from memory.

Specifically worth your eye: **the Protanopia ramp is five yellow-greens and one blue family.** It
passes every floor and may still read as drab.

### Decide whether "high contrast" should look loud

That palette is all pale faces with dark numerals, which is what maximises measured contrast
(6.31:1, the highest of the five) and is the opposite of what most people picture when they read
"high contrast". The rationale is in its KDoc. It is defensible and it may still be wrong for what
players expect from the setting name.

### Feel the haptics on a real iPhone and a real Android

Nothing about the haptic engine is tested or observed — both platform implementations compile and
that is the entire guarantee. The Android `VibrationEffect` waveform envelopes, the
amplitude-control fallback, the pre-Oreo path, and the whole iOS Core Haptics path are unexercised.

**The burst envelope and the stacked-out double are guesses.** SPEC 21 puts the burst at half of
what makes this game feel good, so these need hands on hardware, not a code review.

### Write the policy for changing `PINNED_DIGEST`, before C6 not after

The engine pins a determinism digest that proves a seed replays identically on every platform. It
was re-pinned once already, when the merge-position ruling changed outcomes, and that was free
**because no scores exist yet**.

The moment Daily Challenge ships (C6), it stops being free: changing the digest silently
invalidates every posted score, because players were competing on a different sequence. There
should be a written rule before that, and the obvious one is *the digest is versioned alongside
the leaderboard, and changing it retires the old board.*

Cheap to decide now. Expensive to decide after the first player complains.

## Decisions I need from you

### Kids theming / age rating

Sodogku flagged this as needing resolution *before* ads are wired, and it is the same here.
Whether the app is directed at children changes the ad SDK configuration, the consent flow, and
the store questionnaire. Getting it wrong is a policy problem, not a bug.

**Needed by:** C10, and it is genuinely cheaper to answer now.

### App name and store identity

"Drop 2048" is the repo name. Is it the store name? "2048" is heavily squatted on both stores and
a search-result problem, not a legal one.

**Needed by:** C13, but the bundle ID is set much earlier and is painful to change.

### Privacy policy and terms

Hosted somewhere with a stable URL. The template's gate feature does legal re-accept, so the
version matters, not just the text.

**Needed by:** C11.

---

## Art and audio

The biggest external dependency in the project and the one with the longest lead time.

### Audio, and this is the important one

SPEC 21 says the cascade-step pitched merge sound is one of two things to keep if everything else
is cut. It cannot be faked with a generic pop.

- A merge sample that survives being pitched across ten semitones without sounding like a
  pitch-shifted sample.
- The row burst: the longest, most cinematic sample in the game.
- Spawn, move click, hard drop thud, lock, heavy merge (256+), bomb, danger enter/exit, stacked
  out, level up, UI tap, UI back.
- One music track with three intensity layers plus a filtered danger variant.

**Needed by:** C3a. Placeholder audio is fine for C3, but C3a is where the game either feels good
or does not, and it cannot close on placeholders.

### Two font choices, and one of them is load-bearing

Drop 2048 has `Brand`, `SansSerif` and `Serif`. Sodogku added a fourth, a rounded family (Fredoka),
and that single choice is most of why it reads as a game rather than a utility. Drop 2048 needs an
equivalent, and it is a taste call.

The second one is not taste and it is now **blocking two visible things**: block faces jitter as
values change, and the score visibly wobbles while it counts. Both need a face with **tabular
(monospaced) digits**. No sibling repo has one.

Every number in the game already routes through one token, so **the swap is a single declaration**
in `libraries/ui/.../system/typography/FontFamily.kt`. Pick a family with a real tabular figure
set and check the licence covers app embedding. Open-licence candidates: Roboto Mono, JetBrains
Mono, Inter (has `tnum`), Nunito Sans.

**Needed by:** C3, where it becomes visible for the first time.

### Art

- App icon.
- The three special block faces: wildcard star, bomb fuse, cracked stone.
- Store screenshots and feature graphic.

The tier block faces are the design system's job, not an illustrator's: they are a color ramp plus
a number, per SPEC 5.1.

**Needed by:** C13, except the special faces which C3 needs in some form.
