# Store screenshots

What the Roborazzi goldens can and cannot do as store assets, measured rather than assumed, plus
the shot list they support.

Derived 2026-09-10 for C13, recounted 2026-09-20 after D27. **No store screenshots have been
produced.** This is the investigation the chunk was asked for, and its headline is that the goldens
are *nearly* usable on Play and are not usable at all on the App Store, for two different reasons.

---

## 1. What exists

86 committed PNGs across ten `screenshots/` directories:

| Module | Frames | Notable |
|---|---|---|
| `features/game/impl` | 27 | The game screen in every state: playing, danger, paused, stacked out, the three specials' ghosts, the continue offer, the upsell, four tutorial frames, both control schemes, and seven accessibility variants |
| `libraries/ui` | 19 | Design-system pieces: the five tile ramps, board states, toasts, coach marks, wordmark |
| `features/settings/impl` | 7 | Including the accessible-settings frame |
| `features/debug/impl` | 7 | |
| `features/achievements/impl` | 6 | Grid, detail, unlock toast |
| `features/gate/impl` | 6 | |
| `features/paywall/impl` | 4 | |
| `libraries/devfeedback/tester` | 4 | |
| `features/stats/impl` | 3 | Empty, first run, populated |
| `libraries/ads/fake` | 3 | The house-ad placeholders |

`features/daily/impl` held five frames and is gone with the module (D27), and the `stats-daily-only`
frame went with it.

**57 of them are full-screen 720x1280 frames**, rendered from the real composables with real state.
There is no status bar, no gesture bar and no emulator chrome, because Robolectric draws the
composable and nothing else. That is a genuine advantage over the sibling project's approach, which
captured an emulator and then cropped 150 rows off the top to remove the system bars.

---

## 2. Google Play: admissible, and too small to want to use

Play's phone screenshot constraints are min 320px per side, max 3840px per side, and no side more
than twice the other. **720x1280 is 9:16 and sits inside all three**, so the existing goldens could
be uploaded today and would be accepted.

They should not be. 720p renders on a listing that is mostly viewed on 1080p-and-up phones, next to
competitors uploading at 1080x1920 or higher, and the first thing a prospective player sees is a
soft image. The frames themselves are good; the resolution is the problem.

**Verify the current numbers against Play Console Help, "Add preview assets to showcase your app",
at the time you upload.** Play has tightened screenshot rules more than once, including a minimum
count and aspect-ratio requirements for specific placements.

### Re-rendering bigger is a one-constant change, and this is measured

Every screenshot test is annotated `@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)`
where the qualifier string is `w360dp-h640dp-xhdpi`. The output size is the dp box times the
density multiplier, and the tree already contains the control that proves it: `TALL_QUALIFIERS` is
`w360dp-h2400dp-xhdpi` and produces **720x4800**. Same width, same 2x, 2400 x 2 = 4800. So the
arithmetic is exact and not a guess.

| Qualifier | Output | Fits Play? |
|---|---|---|
| `w360dp-h640dp-xhdpi` (today) | 720x1280 | Yes, but soft |
| `w360dp-h640dp-xxhdpi` | 1080x1920 | Yes, and this is the one to want |
| `w360dp-h640dp-xxxhdpi` | 1440x2560 | Yes |

**But changing `ROBOLECTRIC_QUALIFIERS` invalidates every golden**, all 86 of which then have to be
re-recorded, which throws away the byte-for-byte comparison that has caught real bugs three times
in this project (L39a). The goldens' job is regression detection and the store's job is marketing,
and they want different densities for good reasons. **Do not raise the golden density to get store
assets.**

### The shape that actually works

A separate capture task that writes into `docs/store/screenshots/` rather than into the modules'
`screenshots/` directories, at `xxhdpi`, and which is **excluded from `testDebugUnitTest`**. That
last part is the whole difficulty and the reason this is not built here: these modules set
`roborazzi.test.verify = true` on every `Test` task unless the invoked task name contains
`recordRoborazzi`, so a new capture test added naively runs under the standard gate, finds no
golden to compare against, and fails. Making it work means either its own source set with its own
test task, or a task-name filter alongside the existing `recordingGoldens` check.

That is a build-configuration change to three or more modules, on a gate two other agents are
sharing. It is written down rather than done. **Estimated cost: one focused session.**

---

## 3. App Store: not usable, and it is not a resolution problem

App Store Connect requires **exact pixel dimensions** per display class. For 6.9" it is 1320x2868
or 2868x1320; for 6.5", 1242x2688 or 1284x2778. There is no "close enough". **Check the current
required set against App Store Connect Help, "Screenshot specifications", when you upload**, Apple
adds a display class with every new phone size and retires the old required ones.

The arithmetic above does reach one of them: `w440dp-h956dp-xxhdpi` is 440x3 = 1320 by 956x3 =
2868, which is exactly the 6.9" portrait size. **Do not do this.** The output would be an Android
Robolectric render submitted as an iPhone screenshot. Sodogku's store doc says the same thing in
one line and it is worth repeating: Android renders must not be submitted as iPhone screenshots.

It matters more here than it would in a normal project, because **nothing in this app has ever been
rendered on iOS.** The screenshot harness is Robolectric, so all 86 goldens are Android, and
`OWNER-TODO.md`'s first blocking item is the missing `/var/db/xcode_select_link` that stops any
agent from launching a simulator at all. So the iOS screenshots are not a task anyone can do today,
and the honest status is **blocked on an owner task**, not "not done yet".

Compose Multiplatform renders the same composables on both platforms, so the two will look very
similar. "Very similar" is not the claim a store screenshot makes.

---

## 4. The shot list, for whenever the capture path exists

Order matters more than count. Play shows a scrollable row and most people never scroll past the
third. This order answers, in sequence: what is this, why would I care, is there enough of it.

| # | Source golden | Shows | What it has to prove |
|---|---|---|---|
| 1 | `features/game/impl/screenshots/game-playing.png` | Mid-run, level 7, a 1024 on the board, a falling 4 and its ghost outline | The mechanic in one glance. The ghost says "you steer this", the 1024 says "these get big", and the score says there is a game here. The single best frame in the set. |
| 2 | `game-callout.png` | A chain callout mid-cascade | The payoff. Chains are what the game is actually about and they are impossible to describe in a sentence. |
| 3 | `game-danger.png` | The board near the top with the danger ring armed | Tension, and the fail state, without spending a frame on losing. |
| 4 | `features/game/impl/screenshots/tutorial-spotlight.png` | The opening beat of the guided run: the block at the far edge, the cell it has to reach outlined, and the card asking for it | That you will be shown how. This slot used to hold a Daily card and D27 deleted the mode; the frame that took it over is the coach-mark shot "Still missing" below spent a paragraph asking for. |
| 5 | `game-ghost-wildcard.png` or `game-ghost-bomb` | A special block with its landing ghost | Depth. It says the game has rules you have not met yet. |
| 6 | `a11y-high-contrast.png` or `a11y-tritanopia.png` | The board on an accessibility palette | Earns the accessibility line in the description, and is a genuinely distinctive image next to every other puzzle listing. |
| 7 | `achievements-full.png` | The badge grid, mostly locked | Something to come back for. Weakest of the set, **hold it until the placeholder emoji are replaced** (`OWNER-TODO.md`, "Badge art"). |
| 8 | `stats-populated.png` | Lifetime stats | Optional. Drop it first. |

**Not in the list, deliberately:** the paywall (`features/paywall/impl`), the stacked-out upsell
(`game-stacked-out-upsell.png`) and the continue offer (`game-continue-offer.png`). All three are
good frames and all three lead with monetisation, which is a poor second impression and on Play
also invites a policy read of the listing that nobody needs.

### Still missing, and no golden covers them

- ~~A tutorial / coach-mark frame.~~ **Now covered.** The control-scheme round added
  `tutorial-spotlight.png`, `tutorial-coach.png` and `tutorial-coach-arrows.png` to
  `features/game/impl`, all of them the real screen rather than `:libraries:ui`'s isolated
  `coach-mark.png`. `tutorial-spotlight.png` is the one in the shot list; the other two are the same
  beat under each control scheme and exist to catch a scrim bug, so they are regression frames and
  not marketing.
- **A Play feature graphic** (1024x500). Wants the icon artwork first, and the icon does not exist.
- **An App Store preview video.** Same dependency, plus a simulator.
- **Anything at all on iOS.** §3.

---

## 5. Summary for the report

- The goldens **are** usable on Play as-is, technically. They are 720x1280, 9:16, inside every
  constraint, with no system chrome to crop.
- They **should not** be used as-is: 720p is visibly soft on a modern listing.
- Raising the density is a one-constant change with exact, measured arithmetic, but it **must not**
  be done to the goldens themselves, because that destroys the regression baseline.
- The right answer is a separate store-capture path, and the only thing standing in its way is the
  `roborazzi.test.verify` wiring, which is a build change on a shared gate.
- On iOS the goldens are **not usable at any resolution**, and iOS screenshots are blocked behind
  the `xcode-select` owner task rather than behind any work an agent could do.
