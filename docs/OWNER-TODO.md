# Owner TODO

Things only a human with credentials, a password, a store account or an opinion can do. Agents add
to this list; they cannot clear it.

Ordered by when it starts blocking. Last pruned 2026-09-09 after C7.

---

## Blocking now

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
   change is measurably risk-free — and no human has felt it. Worth knowing while you play:
   whether you use the ▼ nudge is worth more than every curve change combined (223s to level 4
   without it, 56s with).
2. **Does ▼ feel decisive?** It recovers 88% of the wall clock that hard drop gave. C1e's own
   caveat: 88% of the clock is not 88% of the *decisiveness*, and no harness can measure the
   difference.
3. **Does drag steering read as locked to the finger?** It is absolute-from-grab-point per the
   handoff. The flick thresholds (30dp, 450ms) are unvalidated guesses.

## Decide before the chunk that needs it

### Kids theming / age rating

Changes the ad SDK configuration, the consent flow and the store questionnaire. Getting it wrong
is a policy problem, not a bug. **Cheaper to answer now than at C10.**

### The digest freeze date

`PINNED_DIGEST` has moved twice, and both times were free because no scores exist. Once Daily
Challenge ships (C6), changing it silently invalidates every posted score.

Also frozen at that moment: **`level.blocksPerLevel`**, which is now remote-configurable and moves
the digest. The admin console warns loudly and requires a typed confirmation in prod, but **nothing
enforces it server-side.**

Safe to keep tuning live, measured: the speed curve, `nudgeRows`, the spawn table.

### `SAVE_FORMAT_VERSION` ownership

Must be bumped whenever `GameState`, `RunTally` or `SavedResolution` change shape. Nothing enforces
it. If forgotten, the failure is silent and appears on a real player's device with their run in it.
Decide whether it becomes a checklist item or a build assertion.

### App name and store identity

"Drop 2048" is the repo name. Is it the store name? "2048" is heavily squatted on both stores — a
search-results problem, not a legal one. The bundle ID is set much earlier and is painful to change.

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
| **Game Center + Play Games IDs** | C9 | 24 achievements, 3 leaderboards. |
| **AdMob account, app + unit IDs** | C10 | Test IDs work until then. |
| **Pro IAP product** | C10 | Non-consumable, $2.99 at current scope (SPEC 2 — thinner than the original $3.99 because coins, powerups and Zen are cut). |
| **Privacy policy + terms, hosted** | C11 | The gate does legal re-accept, so the version matters, not just the text. |
| **Revoke dead Supabase secrets** | Housekeeping | `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` on Fly and in GitHub Actions. **Keep `DATABASE_URL`** — the config server still uses that Postgres. |

## Telemetry: the one decision that matters most

**Instrument the player's decision time and tap rate** (time from spawn to first sideways input,
and inter-tap interval).

Every clocked balance number in this project rests on a modelled 250ms decision time that nobody
has measured, and it is worth **five levels of median** and a 3x swing in the 1024 rate — more than
the drop clock, the spawn table and every curve change put together. Until it is measured, every
tuning conversation is conditional on a guess.

**Needed by:** C8.

## Art and audio

The longest lead time in the project.

### Audio, and this is the important one

SPEC 21 says the cascade-step pitched merge sound is one of two things to keep if everything else
is cut. It cannot be faked with a generic pop.

- A merge sample that survives being pitched across ten semitones without sounding pitch-shifted.
- The row burst: the longest, most cinematic sample in the game.
- Spawn, move click, lock, heavy merge (256+), bomb, danger enter/exit, stacked out, level up, UI
  tap, UI back. Plus `BOOM!`, `WILD!` and `SWEPT!` now have callouts and no sounds.
- One music track, three intensity layers, plus a filtered danger variant.

**Blocks C3a**, which is the chunk where the game either feels good or does not. The seam exists
and defaults to silent; placeholders will not close it.

### Haptics need hands on hardware

Nothing about the haptic engine is tested or observed — both platform implementations compile and
that is the entire guarantee. The burst envelope and the stacked-out double are guesses.

### Art

App icon, launch screen, store screenshots, feature graphic. The tier block faces are the design
system's job, not an illustrator's.
