# Store listing

Draft copy for Google Play and the App Store. Written to be reacted to, not adopted unread. Every
factual claim is checked against `SPEC.md` 2 and against the code; the voice is a first pass and
yours to overwrite.

Rules applied: no em dashes, no "not just X but Y", no three-item rhythm for its own sake, no
"seamless", "elevate", "unleash", "dive in", no "endless hours of fun". **Nothing here promises a
feature that does not exist**, and §6 is the list of sentences that have to be re-checked if the
code moves.

## The name is one substitution, deliberately

`{APP_NAME}` appears everywhere the store name would. The decision has since been made and it is
**Doublestack** (`OWNER-TODO.md`, "App name and store identity"), but the placeholder is left in
place because substituting it is a copy pass over the whole file and that pass is yours. Substitute
once and the file is done.

Two things behind that decision, still worth knowing:

- **"2048" is heavily squatted on both stores.** That is a discovery problem, not a legal one, and
  it is the reason the Play title below carries a descriptive suffix rather than standing alone.
- **The two launcher labels agreed as of 2026-09-21, and getting iOS there took a build setting.**
  Both `strings.xml` files now say `Doublestack`, and on iOS the name comes from `PRODUCT_NAME` in
  `apps/ios/Configuration/Config.xcconfig`, not from `Info.plist`: the generated plist overwrites a
  `CFBundleName` written there. See `OWNER-TODO.md`, "App name and store identity", before editing
  either one.

---

## 1. Names and short copy

| Field | Value | Limit | Used |
|---|---|---|---|
| Play title | `{APP_NAME}: Merge Block Drop` | 30 | depends on name |
| App Store name | `{APP_NAME}` | 30 | depends on name |
| App Store subtitle | `Tetris shape, 2048 brain` | 30 | 24 |
| Play short description | `Blocks fall. Land a 4 on a 4 and it becomes an 8. Chain it and watch it go.` | 80 | 74 |
| App Store promotional text | `A block falls. You steer it. Land it on its own number and they double, and the one you just made can set off the next.` | 170 | 119 |

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
merge,number,falling,block,drop,puzzle,chain,combo,swipe,offline,arcade,tile,stack,brain,relax
```

94 characters. Notes on what is in and out:

- `2048` is **out** if it is in the app name, and **in** (replacing `relax`) if it is not. Do not
  pay for it twice.
- `tetris` is out. It is a trademark and a competitor name, and Apple rejects those.
- `merge` and `chain` are in because they are what this game does that the genre leader does not.
- `offline` earns its place: see §6, it is a true claim and a real differentiator against the
  heavily-monetised end of this genre.
- `swipe` took the slot `daily` held until D27 removed the mode. It is the same five characters, so
  the field does not need re-balancing, and it is now a true description of the control: the game
  ships on drag, the block follows your thumb and the hard drop is a downward flick. It is also the
  verb people use when they search this genre, which `drag` is not.

Play has no keyword field. Play indexes the title, short description and long description, so the
terms above need to appear naturally in the long description instead. They do.

---

## 3. Long description

**Two versions.** The only difference is leaderboards, and the difference is not cosmetic: Game
Center ships on iOS and **Play Games is explicitly not in v1** (SPEC 15; Android binds an inert
seam). A leaderboard line on the Play listing would be a claim about a feature that is not there.

4,000 character limit on each store. This is about 2,100, and the iOS paragraph adds 130.

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
> **Flick down and mean it.** Drag anywhere on the board to line the block up, then flick down and
> it goes straight to the bottom, paying for every row it skipped. There is no waiting for gravity
> once you know where it goes. If you would rather tap, one switch puts arrow buttons back under
> the board.
>
> **Three blocks that are not numbers.** A Wildcard doubles a neighbour instead of matching it. A
> Bomb clears the four cells around it. A Stone is worth nothing, never merges, and starts turning
> up once you are good enough to deserve it.
>
> **It gets faster.** Every level shortens the drop interval, and the spawn table stops offering
> the small numbers you were relying on. Fill the grid and you are stacked out.
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
> **22 achievements**, and every one of them has been proved earnable by playing the actual engine.
>
> {APP_NAME} Pro is a one-off purchase. It removes the ads between runs, unlocks every palette, and
> gives you two continues per run instead of one.

**iOS only**, inserted before the "Plays offline" paragraph:

> **Game Center leaderboards.** All-time best, and a weekly board that resets so a newcomer is not
> ranked against a year of scores.

### Things deliberately not in it

- **No board sizes, no Zen mode, no powerups, no coins, no cosmetic themes.** All cut from v1
  (SPEC 2). Several of them are in the design handoff and none of them ships.
- **No Daily Challenge.** It was in this file until D27 removed the mode on 2026-09-20. There is no
  second mode, no shared seed and no streak, and a draft that reintroduces any of them is selling a
  screen that has been deleted.
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
| Privacy policy URL | `https://nightjarlabs.llc/doublestack/privacy`, the compiled default (`LaunchGateConfigValues.kt`). Written in `legal/privacy.md`, published by `legal-sync.yml`. See `data-safety.md` §8.5 | same |
| Support URL and email | `https://nightjarlabs.llc/contact`. The email is **not settled**: `legal/*.md` say `contact@nightjarlabs.llc` and the site's own footer says `hello@nightjarlabs.llc`. Pick one | same |
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
> Nothing in the app is locked behind progress. Stats and Settings are one tap from the menu over
> the board, and the achievements grid is in the Achievements row of Settings. The in-app purchase
> is a single non-consumable that removes the ads between runs; a sandbox account is enough to
> exercise it. Rewarded video is always player-initiated and never interrupts a live run.

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
| "Build a 2048 and the row detonates" | SPEC 5, the terminal tier and the row burst | `PINNED_DIGEST` and `EngineConfig.Default` are leaderboard-frozen from the first score posted to a live board (D18) |
| "Drag anywhere on the board", "flick down", "one switch puts arrow buttons back" | The owner ruling of 2026-09-20: `ControlScheme.Drag` is the default and Settings offers a single "Show arrow buttons" toggle (`settings_arrow_buttons`) | A change to the default scheme. The app shipped on buttons until that ruling and this copy described them |
| "pays for the rows you skipped" | D21 reinstated SPEC 7's `2 x rowsSkipped` bonus | Any future change to the drop control |
| "A Stone is worth nothing, never merges" | SPEC 5.2, SPEC 19.4 | A change here also un-earns an achievement (L54) |
| "Plays offline" | The engine is local and Room holds everything. The only network call a shipping client makes is `GET /v1/app-config`, which fails open to compiled defaults | A feature that needs the server |
| "No account, ever" | SPEC 20, and the identity stack was deleted rather than disabled in C0 | |
| "your best score lives on this phone" | Room, no sync, no server copy, and `android:allowBackup="false"` since C13a, so Android will not restore it to a second device either. See `data-safety.md` §7.3 | Turning Auto Backup back on. This claim was unsafe as written while it was `"true"`, and the fix was the manifest rather than the copy |
| "palettes designed for deuteranopia, protanopia and tritanopia" | SPEC 16, and each is pinned by a contrast/ΔE/luminance property test | |
| "22 achievements, every one proved earnable" | SPEC 15, `Achievements.catalog` and `AchievementReachabilityTest`. D27 deleted `SevenDays` and `ThirtyDays` with the Daily streak they folded over | |
| "removes the ads between runs" | SPEC 12: Pro removes **interstitials**. Rewarded video stays available to Pro holders by design | Saying "removes ads" without "between runs" would be a claim Pro does not deliver |
| "two continues per run instead of one" | SPEC 12, and `settings_pro_hint` says the same three things | Pro is three perks since D27 cut the Daily attempt. Any copy that still sells four is describing a build that does not exist |
| "$2.99" | SPEC 2, priced for the thinner v1 scope | If Zen or powerups land, this moves. D27 took a perk away and flagged the price for the owner rather than changing it |
| Game Center paragraph, **iOS only** | SPEC 15, `GameCenterServices.kt`. **Android has no leaderboard at all in v1** | Do not let this paragraph reach the Play listing |
