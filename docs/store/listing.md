# Store listing

Draft copy for Google Play and the App Store. Written to be reacted to, not adopted unread. Every
factual claim is checked against `SPEC.md` 2 and against the code; the voice is a first pass and
yours to overwrite.

Rules applied: no em dashes, no "not just X but Y", no three-item rhythm for its own sake, no
"seamless", "elevate", "unleash", "dive in", no "endless hours of fun". **Nothing here promises a
feature that does not exist**, and §6 is the list of sentences that have to be re-checked if the
code moves.

## The name is one substitution, deliberately

`{APP_NAME}` appears everywhere the store name would. **"Drop 2048" is the repo name and the store
name is an open owner decision** (`OWNER-TODO.md`, "App name and store identity"), so nothing below
hard-codes it. Substitute once and the whole file is done.

Two things that bear on the decision and are worth knowing before you make it:

- **"2048" is heavily squatted on both stores.** That is a discovery problem, not a legal one, and
  it is the reason the Play title below carries a descriptive suffix rather than standing alone.
- **The Android launcher label and the iOS bundle name already disagree.**
  `apps/compose/src/androidMain/res/values/strings.xml` says `Drop2048` (no space);
  `libraries/resources/.../strings.xml` and `Info.plist`'s `CFBundleName` both say `Drop 2048`. The
  manifest points `android:label` at the *androidMain* one, so today the Android home screen reads
  "Drop2048" and everything else reads "Drop 2048". Fix that in the same pass as the name decision.

---

## 1. Names and short copy

| Field | Value | Limit | Used |
|---|---|---|---|
| Play title | `{APP_NAME}: Merge Block Drop` | 30 | depends on name |
| App Store name | `{APP_NAME}` | 30 | depends on name |
| App Store subtitle | `Tetris shape, 2048 brain` | 30 | 24 |
| Play short description | `Blocks fall. Land a 4 on a 4 and it becomes an 8. Chain it and watch it go.` | 80 | 74 |
| App Store promotional text | `A new Daily board at midnight UTC, the same seed for everyone. Endless underneath it.` | 170 | 84 |

Two alternates for the short description:

- `Steer the falling block. Matching numbers double. Reach 2048 and the row detonates.` (82, one
  over, cut "the" before "row")
- `A falling-block game where landing on your own number is the point.` (67)

**On the subtitle.** "Tetris shape, 2048 brain" is SPEC 1's own line and it is the best sentence
anyone has written about this game. It also names two other products. Apple does not reject that in
a subtitle the way it rejects competitor names in the keyword field, but if it makes you
uncomfortable the alternate is `Falling blocks that double`.

---

## 2. App Store keywords

100 characters, comma separated, no spaces after commas, no words already in the name or subtitle
(Apple indexes those separately, so repeating them wastes the field).

```
merge,number,falling,block,drop,puzzle,chain,combo,daily,offline,arcade,tile,stack,brain,relax
```

94 characters. Notes on what is in and out:

- `2048` is **out** if it is in the app name, and **in** (replacing `relax`) if it is not. Do not
  pay for it twice.
- `tetris` is out. It is a trademark and a competitor name, and Apple rejects those.
- `merge` and `chain` are in because they are what this game does that the genre leader does not.
- `offline` earns its place: see §6, it is a true claim and a real differentiator against the
  heavily-monetised end of this genre.

Play has no keyword field. Play indexes the title, short description and long description, so the
terms above need to appear naturally in the long description instead. They do.

---

## 3. Long description

**Two versions.** The only difference is leaderboards, and the difference is not cosmetic: Game
Center ships on iOS and **Play Games is explicitly not in v1** (SPEC 15; Android binds an inert
seam). A leaderboard line on the Play listing would be a claim about a feature that is not there.

4,000 character limit on each store. This is about 1,800.

> A block falls into a narrow grid. You steer it. Land it on a block showing the same number and
> the two combine into one that is twice as big.
>
> That is the whole rule, and everything else is what follows from it.
>
> **Chains are the point.** A merge that creates an 8 next to another 8 merges again, and that one
> can set off the next. Blocks above fall into the gaps, and sometimes a single well-placed drop
> keeps going for six or seven steps while you watch.
>
> **Build a 2048 and the row detonates.** It is the biggest thing in the game and it clears the
> line it was made on.
>
> **Tap down and mean it.** The drop button sends the block straight to the bottom, and it pays for
> the rows you skipped. There is no waiting for gravity if you already know where it goes.
>
> **Three blocks that are not numbers.** A Wildcard doubles a neighbour instead of matching it. A
> Bomb clears the four cells around it. A Stone is worth nothing, never merges, and starts turning
> up once you are good enough to deserve it.
>
> **It gets faster.** Every level shortens the drop interval, and the spawn table stops offering
> the small numbers you were relying on. Fill the grid and you are stacked out.
>
> **A Daily board.** One seed, the same for everyone in the world, rolling at midnight UTC. One
> attempt. A streak that only counts if you keep showing up.
>
> **Plays offline.** The whole game is on the phone. Nothing to download and nothing to sign in to.
>
> **No account, ever.** No sign-up, no email, no password. Your runs and your best score live on
> this phone.
>
> **Built to be read.** Every block carries its number, so colour never has to carry the meaning on
> its own. There are palettes designed for deuteranopia, protanopia and tritanopia, a high-contrast
> set, a larger-numbers toggle, and a reduce-motion setting that shortens the animation without
> changing a single thing about the game underneath it.
>
> **24 achievements**, and every one of them has been proved earnable by playing the actual engine.
>
> {APP_NAME} Pro is a one-off purchase. It removes the ads between runs, unlocks every palette,
> gives you two Daily attempts instead of one, and two continues per run instead of one.

**iOS only**, inserted after the Daily board paragraph:

> **Game Center leaderboards.** All-time best, a weekly board that resets so a newcomer is not
> ranked against a year of scores, and one for today's Daily.

### Things deliberately not in it

- **No board sizes, no Zen mode, no powerups, no coins, no cosmetic themes.** All cut from v1
  (SPEC 2). Several of them are in the design handoff and none of them ships.
- **No "train your brain"**, no claim about IQ, focus or cognition. It is a falling-block game.
- **No stats-screen tour.** It is a good screen and it is not a reason to install.
- **No rewarded-ad pitch.** The continue exists and the player finds it at the moment it matters.
  Advertising it in the description makes the game sound like it is built around the ad, and SPEC
  12's governing principle is that it is not.

---

## 4. Categorisation and the rest of the form

| Field | Play | App Store |
|---|---|---|
| Category | Games → Puzzle | Games → Puzzle (secondary: Games → Arcade) |
| Contains ads | Yes | N/A, declared via the label |
| In-app purchases | Yes, one managed product `drop2048_pro`, $2.99 | **Not yet.** iOS billing is unwired (`NoStoreBilling`), so there is no iOS IAP to declare until StoreKit is implemented |
| Content rating | See `age-rating.md` | See `age-rating.md` |
| Target audience | **Blocked on the kids decision** (`OWNER-TODO.md`) | same |
| Privacy policy URL | `https://drop2048.app/privacy` is already the compiled default (`LaunchGateConfigValues.kt`). **The page there is the template's and is factually wrong**. See `data-safety.md` §8.5 | same |
| Support URL and email | **Not decided.** `pages/privacy.html` currently points at `contact@nightjarlabs.llc`; confirm or replace | same |
| App name | **Not decided** | **Not decided** |

Data safety and the privacy nutrition label are in [`data-safety.md`](./data-safety.md), not here.

---

## 5. Review notes, for a game with no account

Both stores ask how a reviewer signs in. The answer is that there is nothing to sign in to, and
saying so plainly saves a round trip. Suggested text:

> There is no account, no login and no server-side player data. Everything a reviewer needs is
> available on first launch: the app opens directly into a short scripted tutorial, and the game is
> playable immediately afterwards.
>
> The Daily Challenge and the achievements are reachable from the home screen with no unlock. The
> in-app purchase is a single non-consumable that removes the ads between runs; a sandbox account
> is enough to exercise it. Rewarded video is always player-initiated and never interrupts a live
> run.

**Two things to add to the iOS notes when the relevant piece exists**, and not before: that Game
Center is used for leaderboards (once the capability is on the target), and that the app shows an
ATT prompt before serving personalised ads (once the ad SDK is linked). Claiming either today would
be describing a build that does not exist.

---

## 6. Copy that has to match the app

If any of these change in code, this file is wrong.

| Claim in §3 | What makes it true | Where it breaks |
|---|---|---|
| "Land it on a block showing the same number" | SPEC 4.3, and D6's ruling that the merged block appears in the partner's cell | A merge-rule change, which SPEC 4.3 says cannot happen after ship |
| "Build a 2048 and the row detonates" | SPEC 5, the terminal tier and the row burst | `PINNED_DIGEST` and `EngineConfig.Default` are leaderboard-frozen once Daily scores exist (D18) |
| "pays for the rows you skipped" | D21 reinstated SPEC 7's `2 x rowsSkipped` bonus | Any future change to the drop control |
| "A Stone is worth nothing, never merges" | SPEC 5.2, SPEC 19.4 | A change here also un-earns an achievement (L54) |
| "One seed, the same for everyone, rolling at midnight UTC" | SPEC 14 and D18 | `dailySeedFor`'s stride and salt are frozen from the first recorded score |
| "One attempt" | SPEC 14, and the attempt is spent at run start | |
| "Plays offline" | The engine is local, the Daily seed is computed on device, and Room holds everything. The only network call a shipping client makes is `GET /v1/app-config`, which fails open to compiled defaults | A feature that needs the server |
| "No account, ever" | SPEC 20, and the identity stack was deleted rather than disabled in C0 | |
| "your best score lives on this phone" | Room, no sync, no server copy, **but `android:allowBackup="true"` means Android may restore it to a new device.** See `data-safety.md` §7.3 | **This line is arguably already wrong on Android.** It is the one claim in §3 that is not fully safe as written |
| "palettes designed for deuteranopia, protanopia and tritanopia" | SPEC 16, and each is pinned by a contrast/ΔE/luminance property test | |
| "24 achievements, every one proved earnable" | SPEC 15 and `AchievementReachabilityTest` | |
| "removes the ads between runs" | SPEC 12: Pro removes **interstitials**. Rewarded video stays available to Pro holders by design | Saying "removes ads" without "between runs" would be a claim Pro does not deliver |
| "two Daily attempts, two continues" | SPEC 12 | |
| "$2.99" | SPEC 2, priced for the thinner v1 scope | If Zen or powerups land, this moves |
| Game Center paragraph, **iOS only** | SPEC 15, `GameCenterServices.kt`. **Android has no leaderboard at all in v1** | Do not let this paragraph reach the Play listing |
