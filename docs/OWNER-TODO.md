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
   change is measurably risk-free — and no human has felt it. With ▼ now a hard drop (D21), level 4
   arrives in about 33 seconds rather than 223 for a player who never touches it.
2. **Does drag steering read as locked to the finger?** It is absolute-from-grab-point per the
   handoff. The flick thresholds (30dp, 450ms) are unvalidated guesses.
3. **Is the re-paced cascade right?** C3a found it was far too fast to read — the chain callout
   held for three frames and the 2048 existed for 400ms before its own burst erased it. It is now
   paced per step kind. Measured as readable; not yet judged as *good*.

## Decide before the chunk that needs it

### Kids theming / age rating

Changes the ad SDK configuration, the consent flow and the store questionnaire. Getting it wrong
is a policy problem, not a bug. **Cheaper to answer now than at C10.**

### The digest freeze — the window is closing

`PINNED_DIGEST` has now moved **three** times (the merge-position ruling, cutting hard drop, and
reinstating it), and every one was free because **no Daily score has ever been recorded.** Daily
Challenge is built and shipped; the moment a real score exists, changing the digest silently
invalidates every posted score, because those players competed on a different block sequence.

Frozen at that same moment:
- **`EngineConfig.Default`** — D18 pins the Daily to it for comparability, so any release that moves
  a field of it splits that day's board between app versions.
- **`level.blocksPerLevel`** — remote-configurable and digest-moving. The console warns loudly and
  requires a typed confirmation in prod, but **nothing enforces it server-side.**
- **`dailySeedFor`'s stride and salt** — moving either re-rolls every past and future day.

Measured safe to keep tuning live: the speed curve, the spawn table.

**Decide the rule now.** The obvious one is that the digest is versioned alongside the leaderboard
and changing it retires the old board.

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
| **Game Center leaderboard IDs** | C9 | **Exact ids, already in code:** `com.dangerfield.drop2048.leaderboard.score_alltime`, `…score_weekly` (recurring weekly), `…daily` (recurring daily, rolling at **00:00 UTC** — SPEC 14's boundary). Until these exist every submission fails silently, exactly like a signed-out player's. Also enable the Game Center capability on the iOS target. |
| **Decide whether Play Games is in v1** | C9 | SPEC 15 names it; C9 shipped an inert Android seam. Yes means a Play Games Services project, a second id per board, and replacing `NoGameServices`. |
| **Badge art** | C9 | The 24 achievement glyphs are placeholder emoji, and the locked treatment (35% alpha) reads weakly on colour emoji. |
| **Confirm `drop2048.app`** | C9 | It is in the share footer and in the default privacy/terms URLs. If the domain is not yours, change `share_footer` and the `legal.*` config defaults. |
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
`AVAudioPlayer`), wired to the sound setting, with per-cascade-step pitch. Verified on an emulator,
which logged all sixteen samples missing by name.

**16 files, one `.ogg` per sound, mono, short, named by its key** (`merge.ogg`, `merge_big.ogg`,
`burst.ogg`, `spawn.ogg`, `move.ogg`, `nudge.ogg`, `lock.ogg`, `bomb.ogg`, `board_cleared.ogg`,
`danger_enter.ogg`, `danger_exit.ogg`, `stacked_out.ogg`, `level_up.ogg`, `ui_tap.ogg`,
`ui_back.ogg`, plus the remaining one listed in `SoundBank`'s KDoc).

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
