# Architecture decisions

Append-only log. Add an entry whenever you make a non-trivial architectural call
(new module boundary, library choice, scope cut, schema shape). Each entry: date,
the decision, alternatives considered, and *why*. Newest first.

---

## 2026-09-20 · D29 — Five settings removed, and the behaviour each one guarded is fixed in place

**Owner ruling.** Five rows leave the settings screen. Four of them were real
preferences and one was never a preference at all. Open source licences were
asked about in the same pass and explicitly **stay**: the app ships Apache-2.0,
MIT, BSD, MPL and EPL dependencies, and MIT and BSD both require the notice to
travel with the binary, so that row is an obligation rather than a courtesy.

### The removal that is a behaviour change, not a deletion

"Ask before quitting" defaulted **on**, and `GameViewModel.quit()` read it:
`if (!confirmBeforeQuit) leaveRun()`. Deleting a field a branch reads has two
outcomes and only one of them is the ruling — the branch body is `leaveRun()`,
so the careless removal is the one where Quit silently ends a run with no
question. The surviving behaviour is that quitting **always** confirms, and
`QuitConfirmTest` is what makes that deliberate rather than whichever half of an
`if` the delete happened to leave standing. This is the general shape of the
whole chunk: a removed setting leaves a default behind, and the default that
survives has to be chosen rather than inherited.

### Reduce motion becomes the phone's setting, not the game's

The in-app toggle is gone; `isOsReduceMotionEnabled` stays and is now the whole
of what `LocalReduceMotion` carries. The two were ORed, which is a defensible
design and a worse one than this: a player who has told their phone once should
not have to tell the game again, and two sources for one boolean is two things
to keep in step. The `reduceMotion` parameter survives on `AppThemeProvider` and
`PreviewContent` for previews and screenshot goldens, which have no OS setting
to read — it is documented there as a rendering override rather than a
preference, and production passes it nowhere. `AccessibilitySettingsReachTheUiTest`
drives the Android global the actual reads, in both directions.

### The music switch was a switch that did nothing

`PlayerSettings.musicEnabled` was persisted, rendered, and read by no audio code
anywhere, because there is no music system (SPEC 9 says the track is not built).
That is worse than an absent feature: a player who turns it off believes they
have changed something. Removed rather than disabled, and SPEC 9 now says the
switch comes back with the track and not before.

### Diagnostics moves rather than disappearing

"Send diagnostics with feedback" was offered twice, in Settings and on the
feedback form, over one stored field. The settings row went and the feedback
one stayed, because the form is the screen a player is on when the question is
live. **The C13a distinction underneath it is untouched**: a player's report
attaches a session log only on `diagnosticsOptIn` and has its breadcrumbs
cleared (L74), and `FeedbackKind.OwnerDirective` takes both unconditionally
because it is decided by the kind and not by an argument. Flattening those into
one rule while removing a duplicate switch was the available mistake;
`TelemetryOwnerDirectiveReporterTest` pins the negative half — the owner's
reporter never passes `attachSessionLog` at all.

### The left-handed mirror goes down to the design system

It was `PlayerSettings.leftHanded`, `AppData.leftHandedControls`, a row that
appeared only while the arrow switch was on, a field on `GameUiState`, and a
`mirrored` flag on `GameControlRow` that swapped the two arrows and their
handlers. All of it is gone, including the flag: a parameter with no caller is
a second layout waiting to drift from the first.

### Stats: a rename and one new fold

The owner asked for "their high score and highest level reached". The high score
was already the page's headline under the label "Best endless run" — wording
that existed to distinguish it from a Daily score, which D27 removed — so it was
relabelled rather than duplicated as a second stat. `RunStats.highestLevel` is
new, folded as `max(level)` over `run_record` beside the existing `max(score)`,
and it sits in **Bests** next to the highest tier deliberately: levels advance on
blocks dropped, so a level is a survival record and a tier is an achievement
one, and a run sets either without setting the other. Nothing is stored — the
rule in `RunStats`' KDoc holds, and a fold stays retroactively fixable.

### Goldens

`settings-default`, `settings-full`, `settings-accessible` and the three settings
dialog frames all move, and `stats-populated` and `stats-first-run` with them.
`game-left-handed.png` is deleted with the test that drew it. `stats-full` is
new, on the tall inventory frame `SettingsScreenshotTest` already uses: the
stats page is longer than the 360x640 frame it is judged on, so before it the
whole of Bests and Lifetime was never in a golden at all — a row could be added
below the fold and move no pixels.

---

## 2026-09-20 · D28 — A banner under the board, and the principle it cost

**Owner ruling, and it overturns the hardest rule in SPEC 12.** That section read
"Banners: none", and `AdFormat` said in prose that there is no `Banner` and there
will not be one. Both are now wrong on purpose. This entry exists so the next
person to read the new rule can see what was traded for it rather than assuming
nobody had thought about it.

### What the old rule was protecting, and what is left of it

Three arguments, and they are not equally good.

**"The board is tall and narrow and vertical space is what makes the danger row
readable."** This one was measured and it is the reason the banner is *conditional*.
A 5x8 board is 0.625 wide per tall; a phone is nearer 0.45. The board is width-bound
on every phone, so vertical space is exactly what the board does **not** convert into
cells: on a 412x915 frame it used 598dp of the 738dp it was given. The banner takes
the strip the arrow row uses, which is height the board has never been able to spend.
Measured on that frame: the board is 380dp wide with or without a banner, and the cell
pitch is 72.8dp in both. **No player loses a pixel of board to the banner that they
were not already losing to the arrow row.**

**"A banner that shifts layout mid-run reads as the game cheating."** Survives
intact, and is why `bannerAllowed` is resolved at a run boundary rather than on every
publish. Nothing about the strip changes while a block is falling.

**"If one is ever added, its space is reserved from app launch so nothing moves."**
Explicitly reversed, and this is the ruling's sharpest part. Reserving the space is
what makes a missing ad permanent: on a no-fill the player gets a 50dp band of
backdrop instead of an ad, forever, and the board never gets it back. The rule is now
the opposite and it is enforced by shape rather than by a branch. `BannerSurface`
emits **nothing at all** until an ad is on screen, the board above it has
`weight(1f)`, so every reason the banner can be absent (no fill, network error, either
remote key off, Pro, a platform with no SDK) produces the identical layout without any
of them being enumerated anywhere. `BoardTakesTheStripTest` asserts they produce the
same rectangle rather than six expected numbers, because a regression that reserved
the strip would move all six together.

### The principle that was spent

SPEC 12's governing principle is *the player never sees an ad they did not choose
while a run is alive*, and it says everything else is negotiable and that is not. A
banner under a live board is the first thing in this app that has to be argued against
it rather than derived from it.

The argument, stated plainly so it can be disagreed with later: the banner is chosen
in the only sense the principle was protecting. It stands in the space the player told
the game they were not using, it is absent for anybody who turned the arrow row on, it
cannot appear or vanish during a drop, and it never covers, interrupts or delays
anything. What the principle was built to forbid is an ad that takes the game away
from somebody in the middle of it, and every mechanism that could do that (the
interstitial's one call site, `RunActivity.isRunAlive`, `InterstitialPolicy`) is
untouched.

That is a real cost and it should be read as one. The honest version is that the
principle is now "the player never sees an ad they did not choose **take the game
away from them** while a run is alive", and the second clause is doing work it did not
have to do before.

### `PaywallTrigger.Continue`, wired at last

Declared in C10 with a written rationale and **zero call sites anywhere, including
tests**, which is exactly the failure `feature.leaderboards` was (D25): a documented
surface that did not exist. It is now a quiet "Or see Pro" under "No thanks" on the
continue offer, which is where its own rationale always said it belonged: the player
is already deciding whether an advertisement is worth their time.

`PaywallCoordinator` grew `mayOffer(trigger)` for it. The coordinator refuses every
trigger but `Direct` when `pro.upsell.enabled` is off, and all of them for a Pro
player, and a caller cannot know those two rules without copying them. Asking first is
how the control is *absent* rather than dead.

### "Tired of the ads?" is no longer allowed to be a lie

The card is claimed once per session on the stacked-out screen. SPEC 12.3 suppresses
interstitials for three days after install and until the fourth run of a session, so
the first time a new player saw that card, they had reliably been shown nothing at
all. It was selling the removal of an experience they had not had.

`claimStackedOutCard` is now gated on `AdImpressions.anyAdShown()` and is suspending,
because the answer is the same on-disk `AdState` the frequency gates read. Three
consequences worth naming:

- **The gate is checked before the session cap is spent.** Claiming and discarding
  would burn the session's one card on a sheet that drew nothing, so the first
  stacked-out screen *after* the first interstitial would have none left.
- **A filled banner counts.** A banner under the board for a whole run is the
  most-seen advertising the app has, and Pro removes it, so it is squarely what the
  card is about.
- **A rewarded ad does not count.** The player asked for it, watched it on purpose and
  was paid for it, and SPEC 12 keeps rewarded video available to Pro holders. Counting
  it would let the card promise to remove an ad the purchase keeps.
- **iOS draws no card at all**, because `NoAdImpressions` is the binding that survives
  where no ad network is wired. That is correct rather than a gap: iOS serves no ads,
  so there is no honest version of this card there. Pro is still sold from Settings and
  from the continue offer, both of which are controls the player reached for.

### Two defects on the stacked-out screen, fixed in the same pass

**The red corners were a leak, not a design.** `BoardWell` draws the danger ring and
its glow *outside* its own bounds so the warning reaches peripheral vision, and every
overlay is clipped to the board's 24dp corner. The blur clips to the board's
**rectangle** while the ring follows a **rounded** one, so the only part of the alarm
that survived was where the corner arc bulges inside the rectangle: four red corners
around a dimmed, blurred board on the one screen where there is nothing left to warn
anybody about. The danger treatment now stops at `GamePhase.StackedOut`. It stays on
`ContinueOffer`, which is the distinction rather than an exception: the run is alive,
the board is deliberately unblurred (SPEC 12.2) because it is the argument for taking
the offer, and how close to the top the stack is *is* the argument.

Nobody had seen it because `game-stacked-out` did not set `inDanger`, and a run cannot
end without the stack reaching the danger row. The golden was the only stacked-out
screen in existence that was not in danger. It sets it now.

**"Watch and continue" meant nothing on a results sheet.** On the offer it is
surrounded by a countdown, a headline and the terms; on the sheet it sat under a final
score and read as an advertisement for an advertisement. The sheet's option now says
`Not too late: watch an ad, keep going`, which is the fact the screen was failing to
carry: the run is not actually over. The terms are deliberately not repeated there,
because the option can only be drawn on a sheet the player reached through the offer,
which has just spent four lines on them.

**`continue_unavailable` is deleted.** "No ad to show right now, so this one is on us"
was written and never shown, because `continueAccept` grants on every outcome except a
deliberate dismissal. Shown, it would advertise the failure and teach that the reward
is free, which is the lesson `UnservedAdGate` and `NotWiredAdNetwork` are both written
to avoid. The grant is silent by design, so the string was documenting a behaviour the
app does not have.

### The board reclaims the space, and the 370dp number is gone

The owner's words were *"it should have a GONE behaviour, not INVISIBLE"*, and the
measurement says that is exactly right. On a 412x915 frame, before this change, hiding
the arrow row moved the board by **zero pixels**: `BoardMaxWidth` was the handoff's
`max-width: 370px`, a CSS number for a 5x7 board, and it bound at 370dp while the
usable width was 380dp and 100dp of height sat unused underneath. Every pixel the row
freed became a `Spacer` under a top-aligned board.

Two changes, one rule. The cap goes to 480dp, which is wider than the usable width of
any phone, so on a phone the board is bounded by the screen and by its own height and
never by a number from a stylesheet; what the cap is still for is a tablet, where both
of those bounds are enormous. And the board is **centred** in whatever space is left,
because leftover height is structural (0.625 against 0.45) and the only question was
ever where to put it.

Measured, same 412x915 frame, `ControlScheme.Drag`:

| | before | after |
|---|---|---|
| board width | 370dp | 380dp |
| cell pitch | 70.8dp | 72.8dp |
| board bottom edge | 675.5dp | 770dp |
| identical to the arrows-on layout | **yes** | no |

The last row is the ruling. `theBoardIsCentredInTheSpaceItIsGiven` states the fix as
an identity rather than a number: give the frame 80dp more height and a centred board
moves down by 40, a top-aligned one does not move at all.

---

## 2026-09-20 · D27 — The Daily Challenge is removed

**Owner ruling.** "remove the daly challenge thing thats kinda dumb." SPEC 14 is
struck, `:features:daily` and `:features:daily:impl` are deleted, and every seam
the mode reached into goes with it. This is a scope cut, not a deprecation:
nothing is left behind a flag and nothing is left dark.

**Why it is worth writing down anyway.** The Daily was the most cross-cutting
feature in the app for its size. It owned a screen, a table, a run mode, a
rewarded ad placement, two remote config keys, two achievements, a stats
section, a paywall perk and a save slot — and the mode discriminator it
introduced, `GameMode`, had reached twenty-seven files. A feature that costs one
module to build and eleven to remove is worth noticing, and the note for next
time is that the discriminator is what spreads: `mode` travelled into
`run_record`, into `achievement_fact`, into the saved-run blob, into a route
argument and into every telemetry event, because each of those places had a
reason to care which kind of run it was looking at.

### `GameMode` goes too, rather than shrinking to one value

The narrow change was to delete `GameMode.DAILY` and leave the enum with
`ENDLESS`. It was rejected. A discriminator with one value cannot be read wrong
and cannot be read right either: `bestScore()`'s `WHERE mode = 'ENDLESS'`, the
exhaustive `when` in `postToLeaderboards` that D24 argued for, the two save
slots, the `newBest` carve-out — all of those are rules *about the difference*
between two modes, and with one mode they are ceremony that reads like a rule.
The alternative cost is that a second mode, if one ever arrives, reintroduces
the column. That is the right way round: the column is cheap to add back and the
dead ceremony is expensive to keep, because it keeps being maintained.

### Schema 9 deletes rows, which is why it is hand-written

`daily_result` is dropped, and `run_record.mode`, `achievement_fact.mode` and
`achievement_fact.dailyStreakDays` go with it. Room can generate `@DeleteTable`
and `@DeleteColumn` on its own; what it cannot express is *delete the rows that
column identified, before the column goes*. Dropping `mode` first would leave
every Daily run behind as an ordinary one, silently folded into lifetime totals
and into `MAX(score)` — and a Daily score was set on a shared seed and a pinned
`EngineConfig`, so it is not the quantity a best score measures. The column is
the only witness to which rows those are, so the deletion has to happen while it
still exists. `MIGRATE_AWAY_FROM_THE_DAILY` is therefore a hand-written
`Migration(8, 9)` added to the builder, and `AppDatabase`'s `autoMigrations`
list carries a paragraph saying so, because a migration that is not on that list
is one nobody will look for.

`AppDatabaseMigrationTest` walks a real version 8 file through it, seeded with an
Endless row and a Daily row in each of the two tables that carried a mode. It
asserts the Endless rows survive and are still readable through the DAOs, the
Daily rows are gone rather than relabelled, `daily_result` is not on disk, and
the achievement `key` has been rewritten from `ENDLESS:2000` to `2000`. The
positive control the file's own KDoc asks for is already there: a version 4
database is still emptied and a version 10 one still refuses to open.

**Alternative considered:** a destructive fallback from 8. Rejected on the
standing rule `docs/todos.md`'s Watch list states — progress is device-local,
there is no server copy, and an unrecoverable wipe to save a dozen lines of SQL
is not a trade this app gets to make.

### Two achievements become unearnable, and are deleted rather than left

`SevenDays` and `ThirtyDays` were `Stat.BestDailyStreak` at 7 and 30. With the
Daily gone nothing in the game can move that counter, so both would have shipped
as tiles with a progress bar frozen at zero — which is exactly the failure
`AchievementReachabilityTest` exists to catch, and it would not have caught it:
`BestDailyStreak` was `Reach.OffTheBoard`, the arm that says "a calendar decides
this, the engine has no opinion". A badge nobody can earn looks exactly like a
working one.

So `AchievementId` loses two entries, `AchievementGroup.Daily` goes with them,
and the catalog is twenty-two. Deleting an entry contradicts that enum's own
KDoc — ids are persisted by name, so removing one throws away everybody who
earned it — and it is safe here for a reason that will not be true again: there
are no store accounts, no release and no player who has earned either. The two
Game Center ids are listed in `OWNER-TODO.md` as still to be typed into App
Store Connect, so the cost is that the list is now two lines too long.

**Alternative considered:** repurposing them as a play-day streak — "open the
game on seven separate days" — folded from `run_record.endedAt`. It keeps the
two ids, keeps the catalog at twenty-four and is a reasonable retention badge on
its own merits. Rejected as scope: it is a new feature with a new date-aware
branch in `AchievementCounters.fold`, invented in the same change that was asked
to remove one. It is worth offering to the owner as its own decision rather than
smuggling it in as a migration.

### The Pro offer is three things now, and every string that sold four changed

Pro was "no ads, every palette, two Daily attempts, two continues". The third
perk no longer exists. `paywall_perk_daily` and `paywall_perk_daily_detail` are
deleted along with the `Perk` row that drew them, and `settings_pro_hint` now
reads "No ads, every palette, two continues instead of one." SPEC 2's
monetization paragraph and SPEC 12's Pro table say the same three things. The
price is not revisited here — $2.99 was already argued from a thinner Pro than
the original spec's, and a perk fewer is a reason to look at it, not a number to
change in passing. Flagged for the owner rather than decided.

### Both config keys are deleted on both sides, in the same change

`feature.dailyChallenge` and `ads.rewarded.dailyRetriesPerDay` are gone from
`MonetizationConfigValues`, from the server's `ConfigCatalog`, and from the two
tests that pin the catalog's contents. That order matters because of D25:
`ConfigValuesHaveReadersTest` fails the build on a key with no production reader,
so removing the readers without removing the keys would have broken the build —
which is the guard working. Removing the key without the catalog entry would
have left the admin console offering a switch over nothing, which is the failure
D25 is a post-mortem of, pointing the other way.

### What was kept, and why

- **`StreakTrack` in `:libraries:ui`.** The milestone track the Daily's stats
  section drew. It has no consumer now, and it is a design-system primitive with
  no Daily in its name or its API — a generic "N stops, some reached" row. The
  standing rule is that a primitive belongs in `:libraries:ui` even when one
  screen uses it; nothing says it has to be deleted when that screen goes.
  Flagged rather than removed.
- **`RunRecordDaoTest`.** It existed for `bestScore()`'s mode filter, which is
  gone. It stays because the rest of the SQL it covers — the ordering, the
  generated key, the wipe — has no other coverage anywhere.
- **The two historical save blobs in `SavedRunStoreTest`.** They still carry
  `"mode":"ENDLESS"` in their bytes, because they are bytes a shipped build
  actually wrote and the test is about refusing them.

### The save format is version 8

`SavedRun` loses `mode` and `dailyDate`, and `AppData` loses `savedDailyRun` —
one run can be in flight, so one slot holds it. A version 7 blob would decode
(both fields are gone rather than retyped, and `ignoreUnknownKeys` would drop
them), and it is refused anyway. Here the strict rule earns its keep rather than
merely being checkable: a version 7 blob *can be a Daily run*, and resuming one
would put the player on a board whose mode no longer exists, on a seed nobody
chose, ending in a write to a table that has been dropped.

---

## 2026-09-20 — Drag is the default, and the tutorial that taught the button now teaches the gesture

**Owner ruling, two parts, one job.** `PlayerSettings.controlScheme` defaults to
`ControlScheme.Drag`. The settings screen offers one switch, "Show arrow
buttons", rather than a three-way choice between "Buttons", "Drag the board" and
"Both".

**Why a switch and not a fourth state.** The enum already said everything the
ruling asks for: `Drag` is no arrows, `Both` is the arrows back. What a player is
deciding is whether a row of buttons is on their screen, which is a yes-or-no
question, and answering it with three nouns — two of which are "drag the board"
and "both" — asks them to learn the vocabulary first. `Buttons` (drag switched
off as well) is now unreachable from the UI and still honoured when stored: the
switch reads *on* for it and leaves it alone. Taking a scheme away from the
handful of players who chose it deliberately is a worse trade than leaving a
value nothing writes. The left-handed mirror moved under the same switch — it
mirrors the arrow row and nothing else, so it is offered only when there is a row
to mirror.

**The tutorial adapts to the scheme rather than being rewritten for drag.** The
script is one script: the same six drops, the same boards, the same order,
because what SPEC 13 teaches is the habit of putting the block down yourself.
Three beats name a control and those three have two versions. Writing it once
for drag was the tempting answer and it is wrong under `Buttons`, where the board
refuses drags outright — "drag anywhere on the board" there is an instruction to
do something the game ignores, on the one beat that cannot be finished any other
way.

**`TutorialFocus` names a role, not a widget, and that was a live bug.**
`TutorialFocus.Drop` resolved to the ▼ button's focus key unconditionally, and
that key is registered by `GameControlRow` alone. Under `Drag` there is no
control row: nothing registered the key, `FocusScrim` found no rectangle to punch
a hole for, and `CoachMark` was handed `Rect.Zero`. Three beats of the guided run
pointed at the top-left corner of a uniformly dimmed screen. Nothing failed,
because the flick still works and the run still completes, which is how it
survived from C5 — and it was reachable the whole time by switching the scheme
and replaying the tutorial. The focus now resolves against the controls the
player has, and `TutorialFocusKeysTest` renders the real screen under every
scheme and asserts every key the script asks for is one the screen registered.
That is the general form: any future focus that resolves to a key some scheme
never draws fails it.

**The opening beat is pinned to one cell, and that is a deliberate exception to
`allowedColumns`.** "Slide it over" was said to a block that spawned in the
column it was being asked to reach, on a drop where every allowed column
resolved identically — so the first instruction the game ever gives could be
satisfied by doing nothing, and what the player learned on beat one was that the
card can be ignored. Drop 1 now spawns at the far edge, the board outlines the
cell the block has to reach, and the hard drop is refused until the block is
there. The restriction is a property of the drop rather than of the beat: one
that lapsed when the steer beat ended would be undone by the very next input.
Every other drop is unchanged — they still resolve the same way from any allowed
column, and they still finish for a player who only ever drops.

Considered and rejected: leaving the beat as a suggestion and letting a hurried
player skip it (that is the status quo, and it is what produced a first beat
nobody reads); and ending the beat on *any* movement (under the arrows that is
one tap of three, so the card would have said "now drop it" while the drop was
still being refused — a worse dead end than the one it replaced).

**The flick thresholds were kept and their origin was moved.** 30dp in 450ms is
a reasonable description of a flick and there is nothing yet to correct it with.
Measuring them from the moment the finger *landed* was not reasonable: it made
the flick conditional on everything the finger did first, so steering for a
second closed the window and steering three columns made `dy > abs(dx)`
arithmetically unreachable. The one gesture the game is built on — slide it
over, then send it down, without lifting — could not be performed at all, and the
tutorial's first drop is exactly that gesture. The origin is now the highest
point the finger has reached. A straight downward flick is unaffected.

---

## 2026-09-16 — The R8 smoke test now walks the other eight surfaces, and the keep rules turn out to be inert

**What happened:** `MinifiedReleaseSurfacesTest` joins `MinifiedReleaseSmokeTest`
in `:apps:baselineprofile`. Four tests, all green on the minified
`benchmarkRelease` variant, zero skipped, 3m47s on the `pixel6Api34` managed
device. It covers the paywall sheet and a real Play Billing round trip, the
achievements grid, the ad gate readout, the QA config overrides screen, and the
launch gates.

**The assertion worth copying** is the third one. It types a string that exists
nowhere in the APK into `upgrade.maintenanceMessage`, force stops the app,
launches it cold, and asserts that string is on the first screen. Passing it
requires a write through `ConfigCacheSnapshot`'s generated serializer, a process
boundary, a read back through the same serializer, a decode through
`ConfigJsonConverter`, a resolve through a `StringConfigValue`, a trip through
`resolveLaunchGates` and a draw by the gate host. A screen opening proves a route
survived; a value the binary has never seen appearing on one proves the
serializer did.

**Three keep-rule removals were tried and none of them broke anything.** The
whole kotlinx-serialization block deleted: green. The navigation route keeps
deleted with it: green. The Room `@ProvidedTypeConverter` keeps, which this very
file documents as a *previously observed* crash in `Application.onCreate`:
green. C13a's "zero keep rules added" is stronger than it read. On this app's
shapes the hand-written rules in `apps/compose/proguard-rules.pro` are inert,
because every serializer here is resolved at compile time and the libraries ship
consumer rules that cover the rest. **Do not read that as permission to delete
the file.** A rule that does nothing today is the one that matters the day
somebody writes `Json.decodeFromString<T>(raw)` against a type parameter.

**So the suite was proved against the failure it exists to catch, not against a
rule.** Wiping the app's persisted state between the write and the cold launch
is what a serializer that cannot read its own output produces, and inserting one
`pm clear` there turned the test red on exactly the assertion it should, with
the other two still passing. That is the evidence that the green is real.

**What is still uncovered, and why.** Buying anything needs a licensed tester
account and a published product. A filled ad needs AdMob to have inventory for
an emulator, and a release build has no house network by design. Leaderboards do
not draw their row at all without a signed-in Play Games account, so
`AchievementPlatformSync` has no coverage. Sharing launches the system chooser,
and `:libraries:sharing` carries no `@Serializable` model, so the hazard does not
apply to it in the first place.

**Four UiAutomator findings came out of five emulator runs**, all of them the
same symptom C13a described as "the tap reported success and the screen never
changed":

1. A row scrolled under the translucent `TopBar` is still `hasObject`, and the
   click lands on the bar.
2. `waitForIdle` returns while a Compose list is still flinging, because a fling
   is an animation and animations emit no accessibility events. Polling the
   target's rectangle until it stops moving is the fix.
3. Seven `UiObject2.click()` calls silently drop one if the node goes stale
   between two of them. Tap a coordinate, and count the taps by their effect.
4. The snackbar that says **"Debug menu unlocked."** is a prefix of the **"Debug
   menu"** row it is announcing, and it covers the bottom of the list where that
   row is drawn. L75 again, in a new place.

## 2026-09-16 — The 15 metadata warnings were 93, they were all lambdas, and R8 is pinned two patches forward

**Decision:** `settings.gradle.kts` pins `com.android.tools:r8:8.13.19` on the
`pluginManagement` buildscript classpath.

AGP 8.13.1 shades **R8 8.13.17** into `com.android.tools.build:builder`, and that
build links a kotlin-metadata-jvm that reads metadata up to **2.2.0**. Kotlin
2.3.21 emits **2.3.0**. Android's own table puts the floor for Kotlin 2.3 at R8
**8.13.19**, two patch releases above what ships. `./gradlew
:apps:compose:assembleRelease` printed **93** of the warning, not the 15 that had
been quoted; the count grew with the codebase and nobody had recounted.

**Which classes**, which needed `--info` to find out, because the `WARNING` line
names nothing and the `INFO` line beside it names everything:

```
INFO: R8: Class com.dangerfield.drop2048.features.paywall.impl.PaywallNavigator$1$1
has malformed kotlin.Metadata: Provided Metadata instance has version 2.3.0,
while maximum supported version is 2.2.0.
```

94 classes, and **every single one is synthetic**: 84 in this app plus 10 in Ktor
and kotlinx-serialization, all of them lambdas, inlined flow operators,
`$$inlined$compareByDescending$1` comparators or SAM conversions. Reading the
`kotlin.Metadata` annotation off all 84 compiled class files gives `mv=[2,3,0]`
and `k=3` for all 84, and `k=3` is `SyntheticClass` — metadata that carries a
lambda's signature and nothing else. Not one named class, `@Serializable` model,
enum or interface is in the list.

**So it was cosmetic, and here is the argument rather than the assertion.**
Synthetic-class metadata is read by `kotlin-reflect`, which is not a dependency
anywhere in this repo, and by the Kotlin compiler reading a library, which an APK
is not. kotlinx-serialization resolves through generated `$serializer` classes and
`Companion.serializer()`, never through `kotlin.Metadata`. Coroutines read
`@DebugMetadata`, a different annotation. The only consequence of R8 failing to
parse is that R8 cannot rewrite that class's metadata after renaming, and nothing
at runtime reads it.

It is fixed anyway, because the fix is two patch versions and it makes the next
release log readable: **93 warnings to 0**, `mapping.txt` reports
`compiler_version: 8.13.19`, and the four R8 smoke tests pass against a binary
built with it.

## 2026-09-16 · D25 — A config key with no reader fails the build, and `pro.price.tier` is deleted

**What happened:** D24 found that `feature.leaderboards` had been declared,
documented as a kill switch, covered by its own unit test, listed in the admin
console and shown in the QA menu, while being read by nothing at all. Flipping it
switched nothing off. This is the audit of every other key.

**Result: one more.** `ProPriceTier` (`pro.price.tier`) had no production
consumer either. The other thirty-six all do, and every one of them is genuinely
*read* rather than merely accepted as a constructor parameter, which was checked
separately because an injected-and-ignored value fails in exactly the same silent
way.

### `pro.price.tier` is deleted, not wired

Its `description` said "Store product id the paywall requests". Three things were
wrong with that, and none of them could be seen from the key:

1. **The paywall does not request it.** `PaywallViewModel` and `RealEntitlements`
   both use `ProductIds.pro`, whose own KDoc says in as many words that a product
   id is a store operation and is deliberately not in remote config, because an
   outage that emptied it would take Pro down. Two committed positions in direct
   contradiction, with the code implementing the other one.
2. **Its default was wrong.** `pro_299`, against a real product id of
   `drop2048_pro`. Wiring it as documented would have asked the store for a
   product that does not exist, and Pro would have become unbuyable. A default
   nobody reads is a default nobody checks.
3. **It could have revoked a paid entitlement.** `RealEntitlements.restore` and
   `refreshFromStore` key ownership on the same id, and `StoreOwnership.NotOwned`
   writes `setPurchased(false)`. A remote product-id change would therefore have
   read every existing purchase as absent and cleared it. That is not a price
   experiment, it is a remote kill switch for things people paid for.

The price itself already comes from the store through `priceLabel`, which is the
correct source and was already wired. A real price experiment is a store-side
price change on one product, not a client config that swaps product ids.

**Alternative considered:** wiring it with the correct default and keying
entitlement checks on a separate, non-remote id, so only the *purchase* path used
the configured one. Rejected: two ids for one product is a state where a player
can buy something the app will not then recognise, and the feature being bought
is a price experiment nobody has asked for.

### The guard is a source scan, and the reasoning is about what a runtime cannot see

`ConfigValuesHaveReadersTest` walks the repo, finds every concrete
`ConfiguredValue` declaration, and fails when one has no constructor-injection or
construction site in non-test, non-QA production code.

**Reflection was the first idea and it cannot work.** The question is "does
anything inject this", and a DI graph only knows what it can *provide*. By the
time a test can enumerate the `Set<QaConfigValue>` multibinding, an unread key and
a read one are the same object with the same bindings. The injection site exists
only in source, so the test reads source.

That makes it a grep with a reason, and it is worth saying plainly: a consumer
written in a shape the pattern does not recognise would be a false alarm, and the
fix then is to widen the pattern rather than to add an exemption. What it cannot
be defeated by is forgetting, which is the failure that actually happened twice.

Two guards against it passing vacuously, both of which earn their keep: a floor on
the number of Kotlin files found, because a wrong repo root would otherwise make
it green by finding nothing, and a floor on the number of declarations matched,
because a declaration pattern that stopped matching looks exactly like a codebase
with no config in it.

**It was proved by failing.** A throwaway unread key was added, the test named it,
and the key was removed again — the same method AGENTS.md prescribes for detekt
rules, and for the same reason: a structural check that never fires and a
structural check that is not running are identical from the build output. The
first real run also earned its place immediately by reporting `LevelLadderConfig`,
a class that exists only inside a KDoc worked example, which is why the scan
strips comments before matching. The mirror case is the one that would have been
silent: `OfflineFirstAppConfigRepository` names `ConfigRefreshThrottleMs` twice in
prose, and a key counted as read on the strength of a sentence describing it would
have reintroduced the whole bug.

**Alternative considered:** a detekt rule. Rejected because detekt rules run
per-file and this is a whole-project question, and because the repo has already
been bitten by custom rules that silently fail to dispatch (AGENTS.md). A plain
unit test in CI has neither problem.

**The doctrine that caused this is retired.** `MonetizationConfigValues` used to
open by saying its keys had no consumer yet and that this was the point, which was
true and reasonable at C7, when `:libraries:ads`, `:libraries:billing`, the Daily
Challenge and the leaderboards were all still ahead. Every one of those has landed.
Declaring a key ahead of its reader is now how a key ends up never having one, so
the rule is: add the reader in the same change, or do not add the key.

---

## 2026-09-16 · D24 — There is no Daily leaderboard, and the boards that remain got four fixes

**Decision:** `Leaderboard.DailyScore` is deleted. SPEC 15 asked for three boards
and two ship: all-time high score, which is the one this feature exists for, and
the weekly recurring window on the same value. A finished Daily run now posts to
no board at all.

**Why.** A board ranks players on a quantity. The Daily gives everybody one seed
and a capped number of attempts, so the ceiling of a Daily score is a property of
that day's board rather than of the player, and the top of the table is whoever
got the kindest drops rather than whoever plays best. D19 had already ruled that
a Daily score cannot be compared to an Endless one; the part that was wrong was
assuming it was therefore comparable to another Daily score. Two players who
both played the seed perfectly do not tie, they differ by spawn luck, and one
attempt each means there is no second sample to average it out.

The Daily keeps its streak badges, which reward turning up rather than rolling
well, and SPEC 14 is otherwise untouched.

**Alternative considered:** keeping it and accepting the noise, on the grounds
that a shared seed is the fairest comparison any daily puzzle game offers.
Rejected because the fairness argument is about the *seed* and the noise is in
the *spawns*, and because a third board splits a small player base three ways.
A leaderboard nobody is on is worse than no leaderboard.

`GameViewModel.postToLeaderboards` keeps its exhaustive `when` with an empty
`DAILY` arm rather than dropping to an `else`. An `else` would silently route the
next mode anyone adds onto the all-time board, which is the exact failure D19 is
about.

### Four things found in the same pass, all of them silent

**A recurring board cannot be deduplicated on this side.** `RealLeaderboards`
remembers the best value the platform has accepted and declines to resend
anything no better. Correct for an all-time board, wrong for the weekly one: the
platform resets that window on its own clock, this process cannot see the reset,
so an app alive across the boundary drops its first score of the new week for
failing to beat last week's, and the player is absent from the new board
entirely. `Leaderboard.recurring` now exempts it. Modelling the window here
instead was rejected: it means agreeing with a recurrence start the owner typed
into App Store Connect, and a disagreement is the same silent skip somewhere
harder to find. The cost of the fix is one network call per run.

**`feature.leaderboards` had no reader.** The kill switch was defined in
`:libraries:gameconfig`, documented as "off hides the entry point", covered by
its own unit test, and consulted by nothing anywhere in the app. Switching it off
switched nothing off. It is now read in `RealLeaderboards`, and it stops
submissions and reports as well as the entry point: the reason to reach for this
switch is a board that has gone wrong, and hiding the row while still posting to
it is not what anybody flipping it expects. This is worth a second look at the
other SPEC 10 flags, since nothing in the build can tell a wired kill switch from
an unwired one.

**Twenty-four achievements existed and none of them reached Game Center.**
`GameServices` had no achievement surface at all, so every badge was local and
invisible on the player's profile, which is most of the reason a player cares
about one. `GKAchievement.reportAchievements` now backs a new seam, and
`AchievementPlatformSync` observes the stored unlock set rather than hooking
`endRun`. Observing is what makes the signed-out case work: the set is re-folded
from the fact log on every launch, so a player who earned five badges before
signing in gets all five when they do, and a report that dies mid-flight is
retried on the next launch rather than lost. The ids are derived from
`AchievementId.name` for the reason `ScoreLadder` derives its rungs, and
`PlatformAchievementIdTest` is what holds the two catalogs together.

**The sign-in entry point could go dead.** `GameCenterServices.present` cleared
the held sign-in view controller as it presented it, assuming GameKit would call
the authentication handler again to say what happened. A player who swipes the
sheet away without finishing may produce no such call: the status stays
`SignInRequired`, so the row stays drawn, and with the screen already discarded
every later tap did nothing at all. The screen is now dropped only by
`onAuthenticationChanged`, once the platform has actually changed its mind.

**Unrelated, found while reading the project file:** the iOS target's Release
configuration had `IPHONEOS_DEPLOYMENT_TARGET = 26.1` against Debug's `18.2`, so
a store build would have been installable on almost nothing. Release now matches
Debug.

---

## 2026-09-16 — The Sentry DSN is committed, and the `SENTRY_DSN` secret stays as an escape hatch

**What happened:** the DSN lived only in the gitignored `local.properties`, so a
fresh clone reported nothing and said nothing about it — blank is a supported
value meaning "off". Ported from the template (`e8492f3`, `0f44f9f`, `e02a438`,
`77b7c42`): a committed `telemetry.properties` at the repo root, a
`scripts/setup_sentry.main.kts` that fills it from the Sentry API, and a fourth
resolution tier in `loadTelemetryMetadata`.

**Why committing it is safe:** a DSN is a write-only ingest endpoint. It ships
inside every store binary on both platforms, so anyone can read it out of a
published build in minutes. Treating it as a secret buys nothing and costs the
thing that matters. The Grafana `glc_` token is the opposite — a real write
credential, auto-revoked by Grafana when it appears in a public repo — and stays
in CI secrets or `local.properties`.

**The precedence, which is the part that will bite somebody.** Resolution is
env → `local.properties` → `telemetry.properties` → blank. So a set `SENTRY_DSN`
secret **silently wins over the committed file for every build in the repo**,
with no log line and no failed step. Nothing about that is discoverable from the
workflow file alone, which is why it is written here.

**The decision: keep the `SENTRY_DSN` env line** in `beta.yml` and `release.yml`
rather than deleting it, mirroring the template. It is the only way to point one
workflow at a different Sentry project without a commit, and it is what makes the
release guard able to accept either source. It is expected to be **unset**.

Two things make the override visible rather than silent, which is what was
actually wrong with keeping it:

- The release preflight logs a **warning** when both a secret and a committed
  value are present and they differ.
- `SETUP.md` now says there is no `SENTRY_DSN` secret to set, and says why.

**Rejected: deleting the env lines.** It would make the committed file the only
source, which reads cleaner, but it diverges from the template for no gain here
and removes the one hatch that does not require a commit to a tagged release.
Currently academic either way — this repo has no GitHub remote and no secrets
are set — which is exactly why it is cheap to settle now rather than after
somebody sets one.

**Also landed:** the release job `build-number` becomes `Resolve build number +
preflight` and fails the release if neither source has a DSN. Both `android` and
`ios` already depend on that job, so one check covers the whole ship. It was
landed after `telemetry.properties` existed, not before, or the next release
would have failed on a file that was not there yet.

## 2026-09-14 — Ads are visible in a debug build, and structurally absent from a release one

**What happened:** nobody had ever seen this app's interstitial, including the
agent that wrote it. SPEC 12.3 stacks a three-day install suppression, a
fourth-run minimum and a 180-second cooldown in front of it; `date` is refused on
a non-userdebug emulator; and `ConfigOverrideRepository` had existed since the
template with **no writer anywhere in the app**, so there was no way to relax a
gate on a device. The rewarded path was only better by luck: it depended on
AdMob's test units filling, which makes it a test of Play Services and a network
rather than of this app.

**The decision:** a **house ad network** — `HouseAdNetwork` in a new
`:libraries:ads:fake` — that always fills because it draws the ad itself. A
full-screen placeholder that says NOT A REAL AD in the largest type on screen,
names the placement it is standing in for, counts down, and can be closed early.

### The safety is in the build, because it cannot be in the type

L66's rule for a stand-in is that it must be *incapable* of the reward:
`NotWiredAdNetwork` is safe because its type cannot express a grant. A house
network that could not grant would be useless, so that defence is unavailable and
the guarantee moves to the build, in three layers:

1. **`:libraries:ads:fake` is a `debugImplementation` of `:apps:compose`.** A
   release Android artifact does not contain the class, the anvil binding, or the
   drawing code. Verified against the generated graph:
   `InjectKotlinInjectAndroidAppComponent` provides `HouseAdNetwork` on debug and
   `NoHouseAds` on release.
2. **`:apps:compose:verifyNoHouseAdsInRelease`**, wired into `check`, resolves the
   release runtime classpath and fails if the module is on it. A variant-scoped
   dependency is only structural for as long as nobody changes one word in a file
   nobody reads twice.
3. **`HouseAdNetwork.select` ands with `BuildInfo.isDebug`.** This is the only
   layer iOS has: Kotlin/Native has no build-type source sets for layer 1 to hang
   on, so the fake is linked into every iOS binary and the guard is
   `Platform.isDebugBinary`, a property of the Xcode configuration rather than of
   anything the app can set.

`NoHouseAds` — the binding a release build gets — holds a null network, a
`StateFlow` that is always false, and no way to acquire either.

### The seam carries a composable

`HouseAds.Surface()` is `@Composable` on the interface, so `App` draws the
placeholder without naming the module that implements it. The alternative — an
overlay in `apps/compose` keyed on a state flow — would have put the drawing code
in a source set that compiles into the release binary, which is the property the
whole design is buying. It cost `:libraries:ads` the compose plugin for exactly
one declaration.

### Config overrides, at last with a writer

`ConfigOverridesScreen` is over the `Set<QaConfigValue>` multibinding rather than
a named list of keys, for the reason `Set<ClearableDao>` is: a key added in a
later chunk is editable without anyone coming back to the file.

**Every value is written at the type its compiled default has.** L48 is why: the
resolver runs booleans through `toBooleanStrictOrNull` and numbers through
`toDoubleOrNull`, and anything those return null for falls back to the default
with nothing on screen to say the override did not take. So a number that does
not parse is refused with a message rather than persisted, and a flag is written
as a `Boolean` and never as `"true"`. Removing an override is
`removeOverride(path)` rather than writing the default back, because an override
equal to the default still shadows the console for the life of the install.

### The gate is asked, not bypassed

"Force show: interstitial" calls `InterstitialGate.showIfReady()` — the one method
that can show one, with the one caller it has always had — so every rule in SPEC
12.3 still runs and a refusal is the interesting outcome. `AdDiagnostics` reports
the gate's own inputs and `InterstitialPolicy`'s verdict as the same named reason
`ads.interstitial_blocked` carries, computed from the same gathered conditions the
decision uses, so the readout cannot disagree with the gate it describes.

On device: two config overrides, four taps, and the interstitial appeared for the
first time in the project's life.

---

## 2026-09-11 — R8 has now run, and it broke nothing

**What happened:** `MinifiedReleaseSmokeTest` ran against the minified
`benchmarkRelease` variant for the first time in the project's life, walked the
tutorial, a cascade, three routes and a Room-backed screen, and passed in 18.7
seconds. **Zero keep rules were added.** `apps/compose/proguard-rules.pro` is
unchanged.

That is a result and not a non-result, because the app has all three of the KMP
shapes R8 is known to break by name, and every one of them was exercised:

- **kotlinx-serialization.** The tutorial writes a `GameState` through its
  generated serializer on the first landing, and the run is read back on the next
  launch. `@Serializable`'s companion-based serializer lookup survived.
- **kotlin-inject / anvil.** The graph is built before the first frame and it
  built. The generated component is ordinary Kotlin with no reflection, which is
  exactly why it is fine — the family R8 breaks is *reflective* DI, and this is
  not that, however much it looks like it.
- **`@Serializable` navigation routes.** Stats, Daily and Settings were each
  pushed and popped by type. A renamed route fails with an argument error rather
  than a missing class, which is the failure mode hardest to attribute in the
  wild, and it did not happen.
- **Room** was read on the way into Stats and the Daily.

**Why nothing broke, stated so the next person does not read this as luck.**
Every one of these ships consumer ProGuard rules in its own artefact, and AGP
applies them automatically. The risk was never that nobody had written rules; it
was that nobody had ever checked whether the ones that arrive by default cover
this app's shape. Now someone has.

**What this does not prove.** The smoke test walks one journey. It does not touch
the ad or billing SDKs, the paywall, sharing, leaderboards, achievements, the
launch gates or remote config, all of which contain `@Serializable` models that
R8 could rename without anything failing until a player hits them. The 15 "R8: An
error occurred when parsing kotlin metadata" warnings in the build log are also
unexplained and unexplored; R8 is older than this Kotlin version, and the
warnings are consistent with that rather than with a problem, but nobody has
checked which classes they refer to.

## 2026-09-11 — The benchmark journey's destination is the pause overlay, not a home screen

**Decision:** `BenchmarkJourney.reachMenu` walks to any overlay carrying
`MenuOptions`, and the predicate is "Stats and Settings are both on screen"
rather than any single overlay's headline.

**Why not the start overlay**, which is the obvious answer and was tried twice:
`finishTutorial` calls `resetToRun`, which sets the phase to `Playing` and starts
the clock. Skipping the tutorial drops the player into a live run, not a menu.
Every iteration after the first has a saved run and resumes into `Playing` for
the same reason. The start overlay belongs to a launch with no saved run and no
tutorial, which a generation run is almost never in.

**Why a predicate and not a headline:** the same `MenuOptions` composable is
drawn by the start, paused and stacked-out overlays, and which one an iteration
lands on is not something the journey gets to choose. A later iteration resumed a
board that was already near the top, hard-dropped twice and stacked out — and the
loop sat tapping a Pause button that is disabled on a finished run, with Stats
and Settings plainly on screen the whole time.

**Three anchoring rules came out of five runs**, and they generalise past this
file:

1. **Anchor on a string that identifies the screen, not one that appears on it.**
   The tutorial's last card and the start overlay's primary button both read
   "Play". Tapping the button alone started a real run and hard-dropped through
   it; all three tests failed holding a board at level 2. A coach mark is now
   identified by its *title*.
2. **Arrival checks wait; only action checks ask.** `hasObject` with no timeout
   asks whether something is on screen this instant, and an overlay fading in
   over the board is not.
3. **Do not put a scroll in the path if there is a route that does not need
   one.** Achievements is only reachable from the fourth of eight Settings
   sections. Two runs were spent on a swipe that worked, a row that was found,
   and a tap that reported success while the screen never changed. The Daily
   Challenge is one tap from the same menu and buys the same route coverage.

**Cost, named:** the achievements grid now has no baseline-profile coverage and
no R8 coverage. `docs/todos.md` should carry it.

## 2026-09-11 — Splitting the profile generators is not enough; the startup one must stop at the first frame

`StartupProfileGenerator` and `JourneyProfileGenerator` were already separate
classes with `includeInStartupProfile` set on only one, which is the fix the
class KDoc describes. They still shared `reachMenu`, which on this app means
playing the scripted tutorial — so the generated `startup-prof.txt` came out at
**31,233 rules against the journey's 34,673**. Ninety percent of the whole app,
in the file Android's guidance says must not be the whole app, from two
correctly separated generators.

`StartupProfileGenerator` now walks `awaitFirstFrame` and stops, because that is
what "launch" means. It dropped to 29,573 of 34,268.

**The interesting part is that it only dropped 5%.** Drop 2048 has no home
screen: launch builds the whole DI graph and renders the game, so a *correct*
startup profile really is 86% of the journey profile here. A size-ratio check in
CI was written, measured against both numbers, and thrown away — the correct
value is 86% and the broken one was 91%, and a threshold in that gap is a coin
toss that fails monthly in the dark, which is the exact thing this chunk was
fixing. The guard that shipped instead asserts what actually differs: a launch
cannot reach Settings, so `startup-prof` must carry fewer `features/settings`
rules than `baseline-prof` does. Sharing the journey makes the two equal.

## 2026-09-11 — The diagnostics toggle now gates what it says it gates, and the code moved rather than the copy

`settings_diagnostics_hint` promises "Attaches your device model and build
number. Never your board, your scores or anything you typed elsewhere." Neither
half was true. **The copy is what the player consented to when they turned an
opt-in switch on, so the code is what moves.**

Four changes, and the third is the one nobody had spotted:

1. `session-log.txt` rides only when `diagnosticsOptIn` is on. `attachSessionLog`
   defaults to `false`, so a caller that forgets it sends *less*.
2. `BugReportViewModel` reads the preference at all, which it never did.
3. **The feedback carrier event clears its breadcrumbs.** Breadcrumbs are
   Info-and-above in release, `logEvent` is Info, and `SentryLogTree` copies an
   entry's extras onto the breadcrumb — so every feedback report was carrying
   `extra.score`, `extra.level` and `extra.highest_tier` from the last `run.end`,
   switch or no switch. `data-safety.md` §8.2 found the attachment and missed
   this, which was the larger leak.
4. The buffered line format is `internal` and pinned by a test, because the
   attachment's safety rests entirely on it writing the level, tag and **message**
   and never the context. An app event's message is its *name*, so a `run.end`
   buffers as "run.end".

And `DeviceInfo` in `:libraries:core` exists for one caller, because the other
half of the promise — "your device model" — was never implemented either.

**`Telemetry.setUser` is deleted**, along with `captureUserFeedback`'s dead
`email` and `screenshots` parameters. `NoIdentitySeamsTest` uses JVM reflection
rather than a compile-time shape, because a re-added parameter *with a default
value* breaks no caller and is exactly how this comes back.

## 2026-09-11 — Android Auto Backup is off

`android:allowBackup="false"`. `AppData` holds `installId`, which is the only
identity this app has: the bucketing key our config server targets rollouts on,
the `install_id` on every OTLP record, and a Sentry scope tag. Auto Backup
restoring it onto a second device means two phones reporting as one install and
sharing one targeting bucket — a device-scoped identifier silently becoming
person-scoped, which is the reasoning the whole of `data-safety.md` §5 rests on.

It also makes the Settings copy true. "There is no backup and no way to undo
this" is what a player reads before erasing their data, and Auto Backup hands it
back.

**The cost is real and accepted:** a player who changes phones loses their run
history. With no account and no server-side copy (SPEC 20), that is what the
Settings copy already promises them. `data-safety.md` §7.3 carried this as an
undetermined owner decision; it is decided.

## 2026-09-11 — The licences screen reads a generated file; the copy names no licence

`LicensesScreen` reads `files/licenses.txt`, written by the same
`scripts/licenses/license-report.init.gradle` run that writes
`docs/store/licenses.md`, from the same POMs, so the screen and the document
cannot disagree. 363 entries: 361 classpath modules plus the two fonts, which a
classpath scan structurally cannot see and whose OFL attribution clause is the
one obligation a generator would silently drop.

**Curated was considered and rejected on its own history.** C11's sentence was
true when written and stopped being true two chunks later when C10 added the ad
and billing stack, and nothing failed in between (L73). Correcting the sentence
would buy exactly as long as the next dependency. The copy above the list now
names no licence at all, because naming one is the part with a shelf life.

**A licence plugin on the shared build was still rejected**, on C13's argument
rather than a new one: a report is a release artefact, not a build input, so a
plugin would put a configuration-time dependency on every detekt run and every
screenshot test for a file regenerated a few times a year. The cost is named:
regeneration is a release-checklist step and adding a dependency fails nothing.
That is strictly better than a sentence, which could not be refreshed at all.

Grouped by licence **name**, not by name-and-URL, which was the first attempt:
modules under the same licence cite it at different addresses, and pairing the
two split "The Apache Software License, Version 2.0" into one heading of 243 and
an identical one of 157.


## 2026-09-10 — There is one `ProEntitlement`, it lives in `:libraries:billing`, and the debug grant folds in above the store

**Decision:** both `ProEntitlement` types are deleted. `Entitlements` in the new
leaf library `:libraries:billing` is the single answer to "does this device own
Pro", read by `SettingsViewModel`, by `DailyRepositoryImpl`, by
`RealInterstitialGate` and by the paywall. QA's "grant Pro" is `ProGrant` in the
same library, and `RealEntitlements` ORs it over the store's answer, so every
consumer present and future gets it without knowing it exists.

**The bug this closes was silent and shipped.** C11 put a `StateFlow`-shaped
`ProEntitlement` in `:features:settings` and folded the debug grant into its
binding. C6 had already put a synchronous `fun interface` of the same name in
`:libraries:progress`, which is what decides SPEC 12's second Daily attempt. A
library impl may not read a feature's api, so the two could never be the same
object: granting Pro from the debug menu worked on the settings row, worked on
everything the settings row gated, and silently did nothing for the Daily — the
one perk a tester is least likely to check, because checking it costs a day.
C12 wrote the gap down in `DebugEntitlements`' KDoc rather than fixing it, and
`todos.md` carried it as a C10 prerequisite.

**Alternatives.** Keeping two types and binding them from one source was
rejected: two types for one fact means the question "is this player Pro" has two
answers that agree by convention, and conventions are what the first version
already was. Putting the shared type in `:libraries:progress` was rejected
because the Daily's library has no business owning the store. Moving
`DebugEntitlements` into `:libraries:billing` wholesale was rejected in favour of
moving only the *flag*: the debug menu's screen state stays in the feature, and
what crosses the boundary is one boolean.

**The synchronous read survives.** C6's `fun interface` was deliberately not a
`Flow`, because the day's allowance is a fact about the moment the attempt was
requested and an entitlement that changed mid-run must not retroactively change
it. `DailyRepositoryImpl` now reads `entitlements.isPro.value` at exactly that
instant, which is the same answer; what the flow buys is a settings row that
updates without polling.

**The debug grant is never persisted.** It lives in `ProGrant`'s memory and dies
with the process. A granted entitlement that survived a relaunch is
indistinguishable from a billing bug, and the person most likely to hit it is the
tester who granted it and forgot.

---

## 2026-09-10 — The interstitial is its own type with one caller, and the rules are a pure function

**Decision:** interstitials are `InterstitialGate`, not a third `AdPlacement` on
`AdGate`. It has one method that can show anything, one caller, and that caller
is the code that has just watched the player dismiss a results sheet. Every rule
in SPEC 12.3 is evaluated in `InterstitialPolicy.decide`, a pure function over an
`InterstitialConditions` value with no clock, config, storage or DI in it.

**Why the type split.** SPEC 12's governing principle — *the player never sees an
ad they did not choose while a run is alive* — is the only rule the spec refuses
to negotiate, and the defence that survives a year of edits is one where the
unchosen format has no expressible call site inside a live run. Sodogku is the
control: it shipped a `LevelComplete` interstitial as one entry in its rewarded
placement enum, gave it three remote keys and a triple gate, and never called it.
The one ad nobody asked for was a single call site away from shipping by
accident, and deleting it was free precisely because nothing had ever used it.

**Why a pure function.** Eight rules that all have to hold is eight ways to be
wrong, and every wrong version is silent: nobody notices an interstitial that is
ten percent too frequent, and nobody notices a gate that stopped being evaluated.
`AdPolicyTest` holds one rule at a time against a positive baseline that *does*
show — without the baseline a policy that refused everything would pass all eight
negative cases — and asserts *which* block fired rather than that one did, so a
deleted cooldown cannot be covered for by the install suppression happening to be
true of the same player.

**"Either direction" of the 45-second rule needed a second mechanism.** Backwards
is arithmetic against a stored timestamp. Forwards is not, because the future is
not a number you can subtract: the only moment the app knows a rewarded ad is
about to happen is while one is on screen. So `RewardedClock` is held across the
whole rewarded show and the policy refuses while it is raised. Without it the
8-second continue countdown and the results dismissal are one mistimed tap away
from racing each other.

**`RunActivity` is asked, not passed.** The alternative was a `runAlive` argument
on `showIfReady`, which is one type smaller and makes the governing principle the
*caller's* rule — true as long as every present and future call site passes the
right value, and silently false the first time one does not. Asked of the app, it
is the ad layer's rule, and a call site that gets the moment wrong is refused
rather than obeyed.

---

## 2026-09-10 — A Daily run is never offered a continue

**Decision:** SPEC 12's rewarded continue is refused in `GameMode.DAILY`. The
offer is not made, the sheet carries no continue option, and `GameViewModel`
refuses the action as well as hiding the control.

**Why.** This is the one place SPEC 12 and SPEC 14 have to be reconciled rather
than both applied. The Daily's whole claim is that everybody played the same
board — D18 goes as far as pinning `EngineConfig.Default` against remote config,
and accepts that `EngineConfig.Default` can never move again, in order to keep
two players' runs comparable. A continue clears the top three rows and drops a
level, which is a larger change to the board than any config key could make and
one that only some players would have. Watching an advert is not allowed to buy a
better score on a leaderboard everyone shares.

**Alternative considered:** allowing it and recording the continue count on
`daily_result`, so a leaderboard could segregate. Rejected as a worse version of
the same problem: two Daily leaderboards for one seed is two answers to "who won
today", and the second one is the paid answer.

**The rewarded Daily *retry* is untouched**, and the distinction is the one that
matters: a retry is a fresh attempt at the same board from the beginning, which
is a second sample of the same skill. A continue is a different board.

---

## 2026-09-10 — The second continue is reached for, not offered

**Decision:** SPEC 12 asks for "1 free per run, a 2nd at higher friction, hard cap
2". The first continue is offered automatically when the run ends, with the
8-second auto-decline. The second is not offered at all: the stacked-out sheet
carries a quiet option below "Drop again", and taking it goes straight to the ad
with no countdown.

**Why this is the friction.** The obvious reading of "higher friction" is a
second offer with a longer countdown or an extra confirmation. Both are more
friction to *sit through* and less to *accept*, which is backwards — the thing
worth making harder is the decision, not the wait. A control the player has to go
looking for costs nothing to ignore and requires an actual intent to use.

It also solves a real case for free: a player who declined the first offer and
changed their mind three seconds later can still take it. The eight seconds exist
so a dead board does not sit waiting forever, not to punish someone for reading
slowly. The cap is the cap either way.

**No countdown on the second one**, because a sheet is not something that
expires. The countdown belongs to an offer that appeared without being asked for.

---

## 2026-09-10 — The 8-second countdown is a ViewModel rule and the ring is not animated

**Decision:** `GameViewModel` publishes `continueSecondsLeft` as an integer,
decremented once a second by a coroutine, and declines the offer at zero.
`CountdownRing` draws the arc straight off that integer. Nothing animates.

**Why.** The countdown ends a run, so it is a rule, and a rule belongs where it
can be tested without a frame clock. Drawing off the published integer also means
the ring can never disagree with the thing that is actually counting — an
animated ring on its own spec can sit at 1.2 seconds after the decline has
already fired, leaving the player looking at a control that no longer does
anything.

It removes two hazards that meet exactly here. Reading an animated value during
composition fails the repo's own detekt rule, and an `animateFloatAsState` left
running under `LocalInspectionMode` **hangs** screenshot capture rather than
failing it — so `game-continue-offer.png` would simply never finish recording.
The golden existing at all is the proof.

The cost is that the sweep steps eight times instead of gliding. On a control
counting down to a decision, a step per second is arguably the more honest
reading.

---

## 2026-09-10 — The continue offer keeps the board sharp and puts the copy on a plate

**Decision:** `OverlayKind.Continue` is the lightest scrim in the app (0.42
against the game-over sheet's 0.90), the board underneath is **not** blurred, and
the offer's copy sits on its own translucent panel inside the overlay.

**Why the panel exists.** SPEC 12.2's requirement is that the player sees exactly
what they are saving, which pulls against the other requirement — that the offer
itself is readable. A scrim dark enough for white text over a 1024 tile takes the
board away; a scrim light enough to show the board puts "Keep going?" on top of a
tile face, which the first recorded golden showed plainly. The scrim stays light
and the text gets a plate, so the stack reads all around it.

**The blur is applied conditionally, not at radius zero.** L43: `Modifier.blur`
clips to its bounds at any radius including zero, and passing zero as a no-op was
already caught once shaving the board well's ring off three sides. `GameScreen`
therefore branches on the phase rather than on the radius.

---

## 2026-09-10 — A malformed boolean falls back to its compiled default

**Decision:** `getValueRecursive` parses booleans with `toBooleanStrictOrNull`
instead of `toBoolean`.

**Why now.** L48 recorded the behaviour during C7 and noted it mattered "before
C10 leans on these keys". `toBoolean` answers `false` for anything that is not
the literal `"true"`, so a corrupted remote value did not fall back — it silently
became `false`. SPEC 10 asks for the opposite in as many words: *a malformed
remote value falls back to the compiled default instead of crashing.*

For `ads.enabled` the old behaviour was arguably safe, since garbage turned
advertising off. For `feature.dailyChallenge` and `feature.leaderboards` it
removed a whole feature with no error anywhere and no way to tell it from a
deliberate kill switch — and both default **on** precisely so that an unreachable
server leaves the game exactly as the binary ships it. A one-character typo in the
admin console defeated that.

---

## 2026-09-10 — A debug session sees every ad surface, and still writes nothing

**Decision:** neither the continue nor the interstitial is gated on
`StartedRun.debug`. A debug run may take a rewarded continue, counts towards "not
before the 4th run of a session", and can be shown an interstitial. Pro can be
granted freely. What is gated is unchanged: `endRun` still refuses `run_record`,
`daily_result`, the leaderboard submission and the achievement fact for a debug
session, exactly as L63 left it.

**This was decided the other way first, and reversed by trying to use it.** The
first version refused the continue on the argument that it changes a run's
outcome and a tester with a seed switch should not be able to rewind a board.
Installing the build to play it (L56) showed what that costs: the only practical
way to reach a stacked-out board on purpose is the debug menu's preset boards and
forced-block queue, so refusing the continue for a debug session made the new
screen **unreachable by hand** — on the chunk whose whole risk is a screen nobody
has looked at.

The argument was also weaker than it read. L63's list is four *writes*, and a
continue is not one of them; `endRun` already refuses all four for a debug run
whether it was continued or not, so a continued debug run reaches the economy
exactly as much as an uncontinued one, which is not at all. The second guard was
saying the same thing as the first one and charging QA for it.

**The general shape:** a debug gate that blocks a *write* is worth having, and a
debug gate that blocks a *screen* is usually the tester paying for a rule that is
already enforced somewhere else. Ask which of the two it is before adding one.

---

## 2026-09-10 — ▼ is a hard drop, soft drop is deleted, and the bonus comes back with it

**Decision:** D21. The ▼ control and the downward flick send the falling block to
the bottom of its column and lock it, in one press. `Input.Nudge`,
`EngineConfig.nudgeRows`, `SpeedCurve.softDropMsPerRow`, `GameAction.Nudge`,
`GameAction.SoftDropStart` / `SoftDropEnd`, `Modifier.softDropOnHold` and the
`softDropping` field are gone, along with the two remote keys that fronted them.
SPEC 7's `2 x rowsSkipped` bonus is reinstated as `ResolutionStep.HardDropBonus`.

**Why delete soft drop rather than fix it.** The reported bug was that ▼
"triggers like a fast fall mode that's permanent". `softDropOnHold` was a
`pointerInput` keyed on `enabled = live`; holding ▼ through a landing flipped
`live` false, re-keyed the gesture and tore it down mid-press, so
`waitForUpOrCancellation()` was cancelled, `onEnd()` never ran, and the flag
stayed set with no path out but a new run.

That is fixable — reset the flag on the phase change as well as at the callback.
It was not fixed, because the *shape* is the defect: a held mode reachable by a
recogniser that something else can re-key will strand its "on" state again the
next time anyone builds one. A plain click has no on state to strand. The
generalisable form is recorded as part of D21 in `ORCHESTRATION.md`: **a
`pointerInput` keyed on a value that changes during the gesture will drop the
release callback.**

**Why the bonus returns.** D13 struck it on the argument that it paid for
commitment and a two-tick nudge was not a commitment. That argument was correct
and it is now an argument *for* paying: a hard drop gives up the rest of the fall
irrevocably and can be pressed once per block, so a per-row payout rewards the
decision rather than the tap. The ceiling on a 5x8 board is seven rows, or
fourteen points against merges worth hundreds — a nudge toward confident play,
not a strategy. What survives from D13 is that no *other* input scores, pinned by
`noTouchOfTheBoardScoresAnything`.

**Rejected: keeping soft drop as a separate control.** It would need its own
button or its own gesture, and the handoff's control row has three slots. A
second acceleration also reopens the question D21 exists to close: one input, one
meaning.

**Rejected: animating the travel by moving the engine's block first.** This was
tried, shipped to the emulator, and caught by playing it — publishing the block at
its landing cell *in `engine`* before `Input.Lock` makes `rowsSkipped` zero on
every drop, so the board looks perfect and the bonus silently never fires. The
travel is now published to the UI state only; the engine stays where the player
pressed. `hardDrop_paysTheBonusForTheRowsThePlayerSkipped` pins it.

**Accepted costs.** The determinism digest moves a third time (`592` → `772`, same
22 drops, same level, the whole difference being the bonus) and
`SAVE_FORMAT_VERSION` goes 4 → 5. Both are free exactly once more: no Daily score
has ever been recorded, and D18 freezes `EngineConfig.Default` from the first one.

---

## 2026-09-10 — The sample bank is one folder per platform, and a missing sample is not an error

**Decision:** `SoundPlayer` now has a real engine behind it on both platforms —
Android `SoundPool`, iOS pooled `AVAudioPlayer` — and both pitch a cascade step
by **playback rate**, `2^(n/12)`. The samples themselves do not exist yet and are
the owner's; each platform looks for them in a folder called `audio`, one file
per `Sound` named after its `key` (`merge_big.ogg`). Android reads
`libraries/ui/src/androidMain/assets/audio/`, iOS reads the app bundle, checking
a subdirectory and then the root so that either kind of Xcode drag works.

A sample that is not there is logged once, by name, and leaves that one `Sound`
silent. Nothing throws and nothing else stops playing.

**Why rate rather than a pitch shifter.** Resampling makes a merge at step six
both higher *and* shorter, which is what an arcade cascade has sounded like for
forty years and what keeps a deep chain from dragging — every note the same
length is a run that outstays its welcome. It is also the one method both
platforms implement natively and identically, so the two cannot disagree about
what step four sounds like. It happens to justify `Cue.MaxPitchSteps = 12`, which
C2 chose by ear and nobody had checked: both engines cap playback rate at 2.0x,
which *is* twelve semitones, so a thirteenth step would have been clamped by
whichever engine received it rather than by a decision.

**Why two folders rather than `composeResources`.** One bank in
`:libraries:resources` would be one place for the owner to look, and it is how the
fonts ship. But `Res.readBytes` is suspend and hands back a `ByteArray`, and
`SoundPool` wants a file descriptor — so Android would have to spool every sample
through the cache directory at launch to feed an API that reads assets natively.
Two documented folders beat a copy step that can fail on a full disk.

**Alternatives rejected.** *`MediaPlayer`*: decodes on demand, so the first merge
of a cascade is late, and plays one thing at a time, so the second merge cuts off
the first. *`AVAudioEngine` with a varispeed node per voice on iOS*: correct, and
a great deal of interop for a result `AVAudioPlayer.rate` already gives. *Shipping
placeholder beeps so the path could be heard*: a placeholder that ships is a
placeholder that stays.

**What is not built:** the music track and its three intensity layers (SPEC 9).
That is a streaming concern rather than a sample bank and it has no assets either.

---

## 2026-09-10 — The cascade is paced per step kind, and the 2048 gets its own beat

**Decision:** transcript playback no longer holds every step for the same
`Motion.CascadeStepMillis`. A step-one merge gets the handoff's `MergeHoldMillis`,
a **chained** merge gets `CascadeStepMillis` (raised 195 → 300), a merge producing
a **2048** gets a new `TerminalMergeMillis` of 520, a burst gets `RowBurstMillis`
(raised 240 → 420), and gravity gets the handoff's `GravitySettleMillis`.

**Why, and it is a measurement rather than taste.** C3a recorded the tutorial's
scripted cascade and its 2048 burst off a device at 20fps and counted frames.

- Three merges, `CHAIN x2` and `CHAIN x3` all landed inside **500ms**. A later
  callout replaces the one before it, so consecutive chain steps — not
  `ToastMillis` — are the real ceiling on how long a toast is readable, and at
  195ms `CHAIN x2` was gone before it could be read. The number the player is
  being congratulated on was never legible, and the run arrived as one flash
  rather than as three things in order. SPEC 21 says this run is half of what the
  game is for.
- The **2048 was on screen for 400ms** between the merge that made it and the
  burst that took it away. It is terminal (SPEC 5.1) — it destroys its own row —
  so it is the one tile in the game that can never be looked at, and it is the
  tile the game is named after.
- `ROW BUST!` and `SWEPT!` were both announced **over an empty board**, because
  the row cleared between two frames.

**Why not one bigger number.** A step-one merge happens on most drops and has
nothing to announce; making the common case slower to fix the rare one is the
trade the old constant was already making in the wrong direction. Separating them
costs the ordinary drop nothing and buys the chain the room it needs.

`CascadeStepMillis`'s KDoc also corrected a claim: it called 195 "the sum-ish
middle" of the handoff's `MergeHoldMillis` 210 and `GravitySettleMillis` 180. It
is the arithmetic mean of the two, and the handoff describes them as a hold *then*
a settle — two phases of one step, not two candidate values for it.

**Reduce motion is unaffected in kind** (D4): `reduced()` scales all of these by
0.4 and none of them to zero, so a player who asked for less motion still sees
which merges happened and in what order.

---

## 2026-09-10 — Back does nothing on the board, and the pause overlay is the exception

**Decision:** while the phase is `Playing` or `Resolving`, the system back gesture
is swallowed. While `Paused`, it resumes. `Ready` and `StackedOut` are left to the
system.

**Why:** the game screen is the launch destination, so back popped an empty stack
and closed the app mid-drop, with the run saved only as far as its last lock.
Owner ruling: a game does not close from its play surface. It deliberately does
not *pause* either — a gesture made by accident should not also stop the clock and
put a menu in front of the player.

`Ready` and `StackedOut` are not the board. The start overlay is the app's whole
menu (C5 deleted the home screen) and swallowing back there would leave the screen
the app opens on with no way out; the stacked-out sheet sits over a run that is
finished and already in `run_record`, so leaving from it costs nothing. The quit
confirmation needs nothing: `BasicDialog` registers its own handler and a nested
one composed later wins.

---

## 2026-09-10 — Move and nudge are rate-limited in `Cues`, and nowhere else

**Decision:** `Cues.play` drops a `Cue` whose sound is `Move` or `Nudge` if one
has already played in the last 50ms. Every other cue is untouched. The `TimeSource`
is a constructor parameter so the gate is testable.

**Why:** `Cue.Move` fires once per engine column step and a drag steps a column
every frame or two, so ten of them can land inside a fifth of a second. Ten clicks
in 200ms is not steering feedback, it is a fault noise — and `Cue.Move`'s own KDoc
already asks for "a texture, not a rhythm". It is a **throttle rather than a
debounce**: the first click of a drag always plays, because that is the one that
tells the player the board took the gesture; only its followers wait.

It lives in `Cues` rather than in `GameViewModel` because `Cues` is the one place
that sees every cue in the app, and because the ViewModel would need a second
clock beside the one it already has for `run_record`. 50ms is above any human tap
rate and below a drag's, so a deliberate press is never swallowed.

The nudge also stopped borrowing `Sound.Move`. SPEC 9 lists them as two effects,
and a player who cannot tell them apart cannot hear that a nudge registered when
the block was already resting.

---

## 2026-09-10 — `GameUiState.best` is the pre-run record, not a running maximum

**Decision:** `published()` no longer folds the live score into `best`. It is the
value the run store last returned — read on entry, and again in `endRun` after the
run has been recorded.

**Why:** as `maxOf(best, score)` the header read "BEST 736" during a run sitting
at 736, which is the game congratulating a player on a record they are in the
middle of setting and then disagreeing with itself on the next launch. L44 already
recorded the other half: a value derived to always include the current one cannot
be the baseline you compare the current one against, which is why C3b had to add
`bestBeforeRun` beside it. The moment the number is genuinely beaten is now the
moment the stacked-out sheet says so, which is where "new best!" already lives.

Cheaper than the `todos.md` entry feared: nothing animates `best`. The header
draws it as plain text and only the live score has a `ScoreCounter` on it.

---

## 2026-09-10 — Score achievements are priced off the scoring coefficients, never typed

**Decision:** the five score badges take their targets from `ScoreLadder`, which
computes the score a run is **guaranteed** to have banked by the time it reaches
level 5, 10, 15, 20 or 25 — survival plus level-up bonuses, nothing else — and
rounds each down to two significant figures. No number of points is written down
anywhere in `:libraries:achievements`.

**Why:** points are the one quantity in the game with no natural scale. SPEC 7's
table is six coefficients in `Scoring`, and halving `survivalPerLevel` would
halve most of every score there will ever be. Sodogku shipped the typed version
twice and stranded three badges both times — a 10x rescale of its coefficients
left targets nobody could reach, while they still rendered as ordinary tiles with
a progress bar stuck near zero. A badge nobody can earn is worse than no badge
precisely because it looks like a working one.

Pricing off a *level* rather than off a measured distribution is what makes the
rung mean something a player can aim at ("get to level twenty") rather than name
a number, and the floor is a guarantee: merges, bursts and detonations are all
additive on top, so anybody who reaches the level has the rung.

**Alternatives rejected.** *Typed literals*: the bug above. *A fraction of a
measured p90*: it moves with the balance harness rather than with the formula, so
a spawn-table change nobody thought was a scoring change would silently retune
the badges. *A remote-config key*: SPEC 10 marks the scoring formulas never-remote
because moving them invalidates high scores; a remote target would be a way to
invalidate badges instead.

**What it does not cover:** the catalog is compiled in and reads the *shipped*
coefficients, while an Endless run scores on whatever remote config says. SPEC
10's never-remote rule is what closes that gap, and it is a rule rather than
code.

---

## 2026-09-10 — Achievement facts are folded out of the transcript, and the burst step counts its own Stones

**Decision:** `RunFacts` — board clears, three-Stone bursts, Wildcard 2048s and
the danger-drop streak — is folded from `Transition` one locked drop at a time,
alongside the tally `run_record` is written from. `ResolutionStep.Burst` gained a
`stones: Int` field so the fold can read it.

**Why:** SPEC 4.2 makes the transcript the single channel for everything a
transition did, and scoring already flows down it — the invariant
`next.score == previous.score + transcript.points` exists because a second
scoring channel would have let the floating numbers drift from the score. A badge
counted in the ViewModel by watching events would be exactly that second channel,
and the first thing to disagree would be a board-clear badge against the
board-cleared bonus that paid out beside it.

The `stones` count is on the engine's step rather than derived downstream because
it cannot be derived downstream. A burst three cascade steps in sits on a board no
caller holds: the transcript returns the final board, and the intermediate ones
are re-derived by replaying the steps. Counting Stones outside the resolver would
mean a second copy of the resolution loop, which is a much worse trade than one
integer. It is not scored — the burst bonus is per block regardless of what the
block was — and the determinism digest is over `GameState`, so it does not move.

**Cost:** `SAVE_FORMAT_VERSION` goes to 4. `RunTally` grew a field and the saved
transcript embeds the new one, and the rule is "bump on every shape change"
rather than "bump when it is not backwards compatible", for the reason version 3
gave.

---

## 2026-09-10 — Achievements record while the player is not looking, and pay nothing

**Decision:** there is no settings toggle for achievements and no currency
attached to them. A badge unlocks, posts to the platform, and stops.

**Why:** SPEC 2 cut coins along with the powerups they existed to buy, so the
payouts the original spec attached to badges have nothing behind them. Adding a
currency field "for later" would be a ledger with no sink, which is the exact
argument that cut coins.

Sodogku's display toggle is not ported either. It exists there because that game
has seventy-three badges and nine of them are surprises; twenty-four goals a
player is working toward is a screen somebody opens on purpose, and a switch that
hides it earns less than the branch it costs on every read.

## 2026-09-09 — The Daily Challenge pins the compiled-in `EngineConfig` and ignores remote config

**Decision:** `RunFactory.dailyRun()` builds its `GameState` with
`EngineConfig.Default` and never calls `RemoteEngineConfig.current()`. Endless is
unchanged and still samples the fetched config at the start of every run.

**Why:** SPEC 14's whole promise is that everyone played the same game. D5 puts
`EngineConfig` inside `GameState` and C7 ruled that a fetched config is sampled
once at the start of a run and never mid-run. That is enough to keep one run
coherent and it is not enough here. Two players opening the same day's seed ten
minutes apart, one before a config push and one after, would each get an
internally coherent run and a different board. The seed would match, the scores
would not be comparable, and nothing anywhere would say so — the expensive kind
of wrong, the kind that looks right.

**Alternatives rejected.** *Use whatever config the player has*: rejected above.
*A separately frozen `EngineConfig.Daily` with its own literals*: it decouples the
Daily from the balance work, so the mode would permanently play a worse-tuned
game than Endless and the two constants would drift under maintenance. *A config
version stamped into the seed*: everyone on a given fetch would agree, but the
population would fragment into cohorts nobody can see, which is the same failure
with more machinery.

**The cost, accepted:** the Daily plays on whatever balance the binary shipped
with, so a live spawn-table fix does not reach it until the next release. That is
the right boundary. A remote push is invisible, instant and can be
segment-targeted; a release is versioned, staged and something the owner decides
to do.

**What this makes immovable.** From the first recorded Daily score,
`EngineConfig.Default` is a leaderboard-visible constant: moving any field of it
in a release splits that day's board between app versions, exactly as a remote
push would have. `PINNED_DIGEST` in `DeterminismTest` and `level.blocksPerLevel`
(D9) are in the same position and were already flagged as such — the difference
is that the flag is now real rather than theoretical.

**Enforced by** `DailyConfigPinningTest`, which plays two runs on the same seed
under configs that differ in every gameplay key and asserts the block sequences
are identical, with a positive control (L35) proving the same config really does
change an Endless run.

---

## 2026-09-09 — The Daily day is UTC everywhere; the player's zone renders the countdown only

**Decision:** `daily_result` is keyed on the UTC date, the seed is derived from
the UTC date, the attempt allowance is per UTC day and the streak folds over UTC
days. `DeviceTimeZone` is used for one thing: rendering "new board in 4h 12m"
against the player's own clock.

**Why:** SPEC 14 rotates the seed at 00:00 UTC and SPEC 11 keys the row on a UTC
date. Keying the streak on the player's *local* day over a ledger keyed on UTC
days is two clocks: a player in UTC-5 who plays at 19:00 and again at 20:00 would
spend two boards in one of their evenings and none in another, so a local day
would sometimes hold two rows and sometimes none, and every count over that table
would have to decide which of the two days it meant. One clock is the only
version a fold can be right about.

**The fairness problem is real and is answered in the UI rather than the schema.**
A player whose habit sits near their local equivalent of midnight UTC can lose a
day they feel they played. The answer is that the screen always says when the
next board arrives, in their time, so the boundary is a visible fact rather than
something discovered by losing a streak.

**Alternatives rejected.** *A local-day ledger*: two clocks, above. *A grace
window either side of the boundary*: it moves the surprise rather than removing
it, and it makes "did I play today" unanswerable from the rows.

---

## 2026-09-09 — A Daily attempt is spent when it starts, and Restart is refused

**Decision:** `DailyRepository.startAttempt()` increments `attemptsUsed` before a
block has fallen. `GameViewModel` refuses `Restart` during a Daily run and the
pause menu hides the control.

**Why:** spending the attempt on *completion* would make force-quitting a bad run
a free reroll, and Restart would be the same reroll with a button. The run is
resumable from the saved run store, so a player who leaves mid-attempt comes back
to the same attempt rather than losing it — the reservation is what makes that
safe.

**The cost, accepted and worth naming:** the saved run store holds exactly one
run. A Daily attempt that is abandoned and then overwritten by starting an
Endless run is gone, and the day is spent. Recorded in the report as an owner
item rather than fixed here, because the fix is a second save slot and that is a
`SavedRunStore` change with its own format version.

---

## 2026-09-09 — `daily_result` carries one column SPEC 11 does not list

**Decision:** the table is
`(date PK, seed, score, attemptsUsed, completed, retriesUsed)`.

SPEC 11 lists the first five. `retriesUsed` is added because SPEC 12 caps
rewarded Daily retries per day, and a cap that is not counted on disk is a cap a
force-quit resets. Attempts allowed is `(Pro ? 2 : 1) + retriesUsed`, so it is
also the only reason the allowance survives a restart.

Unlike `run_record`, this table is **updated in place**. A `run_record` row is a
finished fact; a `daily_result` row is the running state of one day and changes
twice per attempt. Two rows per attempt would make "attempts used" a `COUNT` and
"the day's score" a `MAX` over a table that also has to answer "has today been
completed", and each of those questions would then have its own way of being
wrong.

`score` is the **best** of the day's attempts. SPEC 14 says the mode is scored on
final score, meaning the score a run ends on rather than some other in-run
measure; it does not say which of two attempts counts, and taking the better one
is the only reading under which paying for a retry is worth anything.

---

## 2026-09-09 — The tutorial's frozen clock is how the nudge gets taught

**Decision:** during the guided run (SPEC 13) `GameViewModel` starts no drop
ticker at all. Gravity never moves a block, so ▼ is the only input that brings
one down, and a player finishes six drops having pressed it on every one of
them.

**Why:** SPEC 13 already froze the timer, and the reason given was that a
scripted run should not be a race. That reason is fine and it is not the
valuable one. C1c and C1e measured that whether a player reaches for the drop
control is worth 223 seconds against 56 to reach level 4 (L29) — a bigger lever
on the opening than the drop clock, the spawn table and every speed-curve change
put together. The original script treated ▼ as one of three things a middle step
mentions.

A card that says "tap ▼" teaches a fact, and a fact is forgotten by drop seven.
The frozen clock makes the control the only way to make progress, so the player
leaves with it in their hands rather than in their notes. It also costs one line
(`if (tutorial.isRunning) return` in `restartTicker`) rather than a lesson, a
gate and a nag.

**Alternatives rejected.** A blocking scrim that only lets ▼ through: it forces
the same behaviour but it forces it by taking the controls away, and the first
lesson asks the player to *drag the board*, which a tap-reporting scrim cannot
express. A "you did not use ▼" nag after each drop: it teaches the player that
the game will tell them off, which is a different lesson.

**Consequence to know about:** soft drop is a hold on ▼ and soft drop is the
ticker running faster, so soft drop does nothing during the tutorial. That is
acceptable — SPEC 13 never taught it — but it is why the tutorial's ▼ is a tap
and only a tap.

## 2026-09-09 — A tutorial drop carries the columns it will accept

**Decision:** each of the six scripted drops declares an `allowedColumns` range,
and `GameViewModel` clamps steering to it while the script is running. Every
column in the range was checked against SPEC 4.3's merge rules to resolve to the
*same* board, and `TutorialTest` pins the resulting boards drop by drop.

**Why:** a forced `GameState` fixes what is on the board and what is falling. It
does not fix where the player puts it. Drop 5 is the three-step cascade the whole
tutorial builds toward, and dropping its 4 into column 1 instead of 2 lands it
inert on top of an 8 — no cascade, no spectacle, and the beat that exists to be
watched is simply skipped. Drop 6 is worse: the 2048 burst is the moment SPEC 13
is written around, and it is one mis-steer from not happening on a player's sixth
ever drop.

The clamp is a range rather than a single column on purpose. A single column is a
rail, and a rail teaches nothing about steering; a range the player can be wrong
inside teaches that placement is theirs while guaranteeing the payoff.

**Rejected:** re-seeding the board when the player lands badly. It works, and it
means the tutorial silently undoes a placement the player just made, which is the
one thing a first lesson must never do.

## 2026-09-09 — There is no onboarding screen; first launch is the game

**Decision:** `:features:onboarding` and `:features:onboarding:impl` are deleted.
`AppViewModel` returns `GameRoute()` for everybody, and `GameViewModel` reads
`AppData.hasUserOnboarded` to decide whether the drop clock runs. Replay is
`GameRoute(replayTutorial = true)`.

**Why:** C3b noticed the app had two Play buttons — one on the game screen's
start overlay, one on the onboarding screen — and SPEC 13 says first launch has
no menus at all. Once the tutorial is "the game screen with the timer frozen",
an onboarding module holds a welcome screen whose only job is to be dismissed
before the real first screen appears.

`docs/todos.md` asks whether the module should be renamed `:features:tutorial`.
It should not: the tutorial has no screen of its own to put in it. Deleting is
cheaper than renaming and it removes a module rather than moving one.

The **flag keeps its name.** `hasUserOnboarded` is a persisted field in
`AppData`; renaming it to `hasCompletedTutorial` would be a cache migration for
a word, and every reader of it now means the same thing.

## 2026-09-09 — A fetched config takes effect at the start of the next run, never during one

**Decision:** `RunFactory.newRun()` is the only reader of remote gameplay config.
`EndlessRunFactory` calls `RemoteEngineConfig.current()` once per run, writes the
result into `GameState.config`, and everything the ViewModel needs while the run
is alive — the drop interval, the level bar's denominator, the nudge distance,
the board's shape — comes off `state.config` rather than back to the config map.

**Why it needed deciding at all.** `OfflineFirstAppConfigRepository` refreshes on
every app foreground past its throttle and publishes a live merged map, so a
`ConfiguredValue` read twice in one process can give two answers. Nothing about
that is wrong; what would be wrong is the game noticing mid-drop. A board that
gains a row, a level bar whose denominator moves and a drop timer that changes
interval, all without the player doing anything, is a bug with no description.

**Why the fix was already half-built.** D5 put `EngineConfig` inside `GameState`
so a seed reproduces a run byte for byte. Rules changing under a live run is the
same failure at one scale smaller, and pinning the config at the run boundary
covers both. A run resumed from C4's saved blob comes back on the numbers it was
played under for the same reason.

**Rejected:** reading config per drop and only applying "safe" keys live. It
splits one value into two lifetimes, and the definition of safe is exactly the
argument this chunk is trying to make explicit rather than incidental.

`RemoteConfigRunBoundaryTest` moves the map underneath a live run and asserts
nothing budges until Restart.

## 2026-09-09 — Every SPEC 10 key is remote, including `level.blocksPerLevel`, and the console is where the hazard lives

**Decision:** wire all of SPEC 10, `level.blocksPerLevel` included, and put its
digest warning at the admin surface rather than excluding the key.

D9 measured the distinction and it is not stylistic. The speed curve is a pacing
dial with outcome columns identical to the digit across three candidates (L28).
`speed.nudgeRows` cannot change where a block lands, so it never reaches the
engine's recorded state (L40). The spawn table sets the tier ceiling and moves the
median level by at most one (L19). All three are safe to turn live.

`level.blocksPerLevel` feeds level advancement, so it **moves the pinned
determinism digest**: every Daily Challenge score and every seed-attached bug
report recorded under the old value replays as a different run, and it still
replays, which is the expensive kind of wrong. D9 keeps it in reserve as the knob
to reach for if live data says runs feel long, so excluding it would contradict a
decision already made.

The console carries it instead, in three places: the flag's description, a red
banner in the detail row on open, and `dangerousWarning` on every write path —
which on prod also forces the operator to type the environment name before
Confirm arms. Reverting the key warns too, because going back moves the digest a
second time. `DangerousWarningTest` pins both halves: the clock warns, and the
ten keys measured safe do not, because a console that warns about everything is a
console nobody reads.

## 2026-09-09 — SPEC 10's keys live in `:libraries:gameconfig`, between config and the engine

**Decision:** a new leaf module holds one `ConfiguredValue` per SPEC 10 key plus
`RemoteEngineConfig`, the only thing that assembles them into an `EngineConfig`.

`:libraries:cascade` cannot hold them: SPEC 4.1 gives the engine zero project
dependencies and everything downstream assumes it stays that way.
`:libraries:config` cannot hold them either without depending on the engine,
which inverts the template's generic config stack onto one game. Putting them in
`:features:game:impl` would work today and be wrong by C6, when Daily Challenge
needs the same values from a different feature.

Every default is read off `EngineConfig`'s own companion rather than retyped, so
the compiled-in fallback and the number the engine ships with cannot drift apart.

**Range checks are per-key, not per-config.** A bad value falls back to that
value's own default and leaves its neighbours remote, so a typo in one row of the
admin console cannot discard the other fourteen. `EngineConfig`'s and
`SpeedCurve`'s `require` blocks are the backstop: the assembly runs inside
`Catching` and a throw resolves to `EngineConfig.Default`.

**The never-remote list is guarded from outside.** `Scoring`, the cascade caps,
`spawnCapFloor`, `continueRowsCleared` and `cols` have no `ConfiguredValue` and
must never gain one. `NeverRemoteTest` feeds the assembler a map naming every
plausible path for them and asserts the result is unchanged — because a key
pointed at scoring would compile silently and invalidate every score on the
board.

## 2026-09-09 — The server ships a config catalog, used only until CI uploads a manifest

**Decision:** `ConfigCatalog` in `:apps:server` lists SPEC 10's keys with their
types, compiled-in defaults and descriptions, and every manifest read in
`ConfigAdminRoutes` goes through `orCatalog()`. An uploaded manifest replaces it
outright.

The admin console discovers flags from the uploaded per-version manifest, and
nothing uploads one yet — CI manifest capture is not built. On a fresh deploy that
makes the flag table empty: every value editable in principle, none discoverable
in practice, and `ConfigSchema` with nothing to type-check against, so
`board.rows = "eight"` would be accepted and silently ignored by the client.

**Rejected: seeding `app_config_values` in a migration**, the way V4 seeded the
kill-switch trio. A seeded row is a served override, so the day a later chunk
retunes a compiled default the stale row would silently win. The catalog is a
fallback, not a value.

`ifEmpty` rather than a per-path merge, so "what did v1.0.1 ship with" stays an
honest question once a real manifest exists.

**Known cost:** the catalog is a hand-maintained mirror of the client's
`ConfiguredValue` registry and can drift. `:apps:server` cannot import
`:libraries:cascade` to check — the server-only Docker build excludes every
client module by design. `ConfigCatalogTest` pins the path set, the types and the
absence of never-remote keys; the durable fix is wiring the manifest upload into
CI.

## 2026-09-09 — C3b's five rulings on the parts of the game screen the handoff never drew

The design handoff is a 5x7 board with no special blocks, so it has nothing to say
about four of the states this game can actually be in. C2c found them while
building the components and left them open. Ruled here, with the argument, because
each one is a promise the screen makes to the player.

**1. The danger ring keeps its "row 1" threshold at 5x8, unchanged.** L25 bought a
row of warning by going from 7 rows to 8, and the tempting move was to spend it by
arming the ring a row lower. It is not spent, and the reason is that the threshold
is not a distance measurement. Row 0 occupied after a resolution *is* the end of
the run (SPEC 3.1), so anything resting in row 1 means the next block that lands
there without merging ends the game. That is the same sentence on a 7-row board
and on a 12-row one — the threshold is defined by the death rule, not by the board
height. The extra row is spent where it was earned: the player now gets one more
drop's worth of reaction time *after* the ring turns red, which is the thing that
was in short supply. It also costs nothing to leave alone: the engine already
computes `inDanger` as `isRowOccupied(1)` and the screen only reads it.

**2. A Stone's landing preview is always plain.** A Stone has no value and never
merges. A bright ghost on a Stone would be a lie the first time a player saw one,
and the first time is the time they are learning what a Stone is.

**3. A Wildcard's bright cell is the neighbour, not the landing cell.** SPEC 5.2
has the Wildcard take a neighbour's value doubled, so the cell that *changes* is
the neighbour's, and the question the player is asking is which of up to four
neighbours it will pick. Marking the landing cell answers a question nobody asked.
The label stays `×2` because the neighbour doubles. The landing cell keeps a plain
outline so the block's own destination is still visible.

**4. A Bomb outlines all five cells and labels none of them.** The footprint is the
message. A `×5` in the middle would read as a multiplier, which is what every
other mark on this board means, and a bomb multiplies nothing. Only *occupied*
neighbours are outlined: an empty cell does not visibly clear and scores nothing
(SPEC 18.4), so outlining it promises a bang that does not happen.

**5. Three callouts are added to the handoff's three.** It draws `CHAIN ×N`,
`ROW BUST!` and `LEVEL N`. SPEC 7 also pays for bomb detonations, Wildcard
resolutions and a cleared board, and a payout with nothing on screen to explain it
is a payout the player does not connect to what they did. They are `BOOM!`,
`WILD!` and `SWEPT!`. The board-cleared bonus is the strongest case of the three:
it is the rarest frame in the game and its only other evidence is the score having
moved more than expected.

Precedence between callouts is structural rather than a rule anybody has to
remember: one per playback frame, and a later frame's callout replaces the one
before it. A burst is a later frame than the merge that caused it, so `ROW BUST!`
beats `CHAIN ×N` without an `if`.

---

## 2026-09-09 — The board is bounded by height as well as by width

The handoff hardcodes `max-width: 370px` on a board with a 5/7 aspect ratio. This
game is 5x8 (SPEC 3, measured on a device in C3), which is a whole row taller for
the same width, and the handoff's `flex: 1; min-height: 8px` spacer is the only
thing absorbing the difference. On a 360x640 frame it collapses to its minimum and
then keeps going, taking the control row off the bottom of the screen.

**Decision:** the board takes the smallest of the available width, 370dp, and the
width whose 5x8 height fits the space it was given. The flex spacer is whatever is
left, which is the handoff's *intent* — push the controls into thumb reach —
rather than its arithmetic.

**Alternatives:** cap the board's height and letterbox it (leaves a gutter that
looks like a layout bug), or scroll the screen (a game screen that scrolls under a
drag gesture is unusable). Neither is better and both are more code.

This is now pinned by a golden captured at 360x640 rather than at a generous
device size, deliberately: a screenshot taken on a tall phone agrees with the
design and disagrees with the player.

---

## 2026-09-09 — The cheating policy stays, and the cheat moves onto the instrument

**Decision:** `Policy.Lookahead1` keeps reading the next block it cannot see, and
gains a `cheats` flag that `Report` prints on every line it renders, so its output
reads `policy=lookahead1 (ceiling, reads a block the player never sees)`.

Since D11 the next block is drawn at spawn time and never shown, so this policy
plans around a value that does not exist yet. Measured, that is worth two levels
of median and four times the 2048 rate over `Greedy`.

**Alternatives:** demote it to a curiosity, or rewrite it to search the
distribution of next draws instead of the actual one. The first leaves the project
with no upper bound at all — `Greedy` is a competent player, not a bound, and
SPEC 4.4 asks for a ceiling. The second is a different and far more expensive
policy answering a question nobody asked.

**Why a flag rather than a KDoc paragraph:** the failure mode is somebody quoting
a lookahead median as a prediction of play, and that happens when they read the
output, not when they read the source.

**One landmine on the way in.** Declaring `cheats` on the sealed interface with a
`get() = false` default turned it into a JVM default method, which makes
initialising `Policy.Random` initialise `Policy`, which builds the companion and
evaluates `Policy.All` while the object it is reading is still half-built. `All`
then held a null and two tests died on a non-null parameter check, with no
compiler warning. It is abstract now and each object states its own answer. Same
shape as L22: a declaration order nobody can see, and correct-looking code.

---

## 2026-09-09 — `nudgeRows` does not move the determinism digest, measured

**Decision:** treat `EngineConfig.nudgeRows` as a freely tunable remote key, unlike
`blocksPerLevel`.

D11 and `EngineConfig`'s own KDoc imply the opposite, on the reasonable grounds
that it travels inside `GameState`. Checked rather than reasoned: the default was
set to 3 and all five `DeterminismTest` cases passed on the JVM.

**Two reasons, both independent.** `kotlinx.serialization` omits a value equal to
its declared default, so moving the default moves nothing in the bytes. And
`Input.Nudge` is behaviourally inert in the pinned script, because every drop ends
in `Input.Lock` and `Lock` places at the block's *landing* cell — the engine cannot
tell how far down a nudge already moved it.

That second reason is the general one and it is worth stating on its own: **the
vertical position of a falling block is not an input to anything the engine
records.** It is why the drop control is a pure pacing dial, why `patient`,
`softie` and a nudging player produce identical outcome distributions over 10,000
runs, and why `nudgeRows` can be tuned live without invalidating a score.

---

## 2026-09-09 — The screenshot harness is Roborazzi, and it had to be forced to actually compare

**Decision:** `:libraries:ui` gets a Roborazzi + Robolectric screenshot harness.
Fifteen goldens live in `libraries/ui/screenshots/`, committed.
`./gradlew :libraries:ui:recordRoborazziDebug` re-records them; every other test
run compares them.

**Why Roborazzi and not the alternatives.** Paparazzi renders through layoutlib
and does not read Compose Multiplatform's `composeResources`, so the bundled
Fredoka and Nunito would not load and every golden would be of a design nobody
ships. The JetBrains Compose Multiplatform screenshot tooling is still tied to
the Android Gradle plugin's own `screenshotTest` source set and cannot see a
`commonMain` composable in a KMP module. Roborazzi runs inside the Android
target's ordinary unit tests, reads the module's real assets, and needed no
build-logic changes beyond one plugin and a `testOptions` flag. Neither
`../Sodogku` nor `../Cards` has solved this, so there was nothing to port.

**The trap, and it is the part worth remembering.** Out of the box
`captureRoboImage` is a **no-op** unless a Roborazzi flag is set. A plain
`testDebugUnitTest` runs every screenshot test, passes every one of them, and
never looks at a pixel. This was measured rather than assumed: a golden was
replaced with a completely different image and the task stayed green.

The module therefore sets `roborazzi.test.verify=true` on its test tasks by
default and stands it down only for the record task. The reason it is done this
way rather than by hanging `verifyRoborazziDebug` off `check` is that the
project's standard verification command is fixed at four tasks, and a check that
is not in the command people run is a check that does not run.

**What it does not cover.** Robolectric implements the *Android* framework, so
this is the Android target only. There is no equivalent for iOS, and a green run
means "the composition and its colours are unchanged", not "it looks right
everywhere".

---

## 2026-09-09 — Fredoka ships as the numeral face, and its figures are proportional

**Decision:** Fredoka 500/600/700 and Nunito 600/700/800 are bundled as static
instances cut from the variable originals. `DigitFontFamily` points at Fredoka,
as the handoff specifies. **The score counter's wobble is not fixed and must not
be quietly fixed by swapping the face.**

**The measurement.** The shipped Fredoka carries no `tnum` feature at all, and
its ten digits have eight distinct advance widths at every weight the design
uses. At 700 the `1` is 379 units against the `2`'s 566 — a 49% spread. Nunito,
by contrast, has all ten digits at exactly 600 units: tabular in effect without
needing the feature.

**Why ship it anyway.** On a *tile* the wobble does not exist — a tile draws one
value, centred, and never animates between two — and tiles are where almost every
numeral in the game is. The wobble is confined to the score counter and the level
number.

**The three fixes, for the owner to choose between:** lay the score out digit by
digit in fixed-width slots (keeps Fredoka, costs one composable, recommended);
draw the score in Nunito ExtraBold (one line, costs the most prominent number on
the screen its Fredoka look); or ship a `tnum`-patched cut of Fredoka (correct,
and a font pipeline nobody wants to own for one number).

---

## 2026-09-09 — The design's tile ramp replaces the hill-climbed one, and three floors go with it

**Decision:** `BlockPalettes.Default` is now the handoff's ramp — one hue per
tier at `L/C = 0.78/0.15`, and `0.85/0.17` at 2048 — rather than C2's
hill-climbed set. All five palettes are now authored as `L C H` triples and get
their ink and their hard shadow from one derivation.

**What it costs, measured on the shipped Kotlin:**

| floor | holds at | design ramp | worst pair |
|---|---|---|---|
| neighbouring tiers | ΔE 24 | **22.6** | 16 / 32 |
| any pair | ΔE 17 | **14.6** | 2 / 2048 |
| luminance span | 0.45 | **0.187** | — |
| numeral contrast | 4.5:1 | 7.16:1 | — |

The luminance span is not a mistake, it is the ramp's defining property: `L` is
constant so the board reads as one set of objects lit the same way. It also means
lightness carries nothing, which is the axis that survives every colour vision
deficiency. Under the Viénot simulation the default ramp's closest pair collapses
to **ΔE 0.67** (128 and 256, under deuteranopia). C2's ramp was not much better
there — 6.15 under protanopia — so this is a deepening rather than a new problem.

**The floors were not loosened.** The four accessibility palettes still hold all
of them. The default ramp's numbers are *pinned* instead, two-sided, in
`BlockPaletteTest.theDesignRampIsWhereTheHandoffPutIt` and
`theDesignRampCollapsesUnderEachDeficiency`, so a retune in either direction
fails loudly and has to be argued for. **Whether ΔE 14.6 between the 2 and the
2048 is acceptable is an owner call and has not been made.** The mitigation is
real: every tile carries its numeral, no setting can turn it off, and a 2048
bursts its row immediately so the pair is rarely co-present.

---

## 2026-09-09 — The tile's ink is the design's hue-matched one, with a fallback the design never needed

**Decision:** `inkFor` prefers the handoff's `oklch(0.26 0.07 H)` — the tier's
own hue taken almost to black — and falls back to the higher-contrast of the two
flat inks only when that ink cannot clear 4.5:1 on the face.

**Why prefer rather than take the best of three.** The flat near-black beats the
hue-matched ink on contrast on every one of the eleven default tiers. A
best-of-three would therefore reject the handoff's ink everywhere, silently, and
the design would lose a tie-break it never entered. Preferring it means the
shipped ramp gets exactly the ink that was drawn.

The fallback fires on twelve tiers, every one of them on the dark half of one of
the three colour-vision ramps, and never on the default or high-contrast ones.
The handoff only ever draws light tiles, so its ink rule has never met a face
dark enough to swallow it — which is precisely why the four accessibility
palettes cannot inherit the rule unmodified.

---

## 2026-09-09 — The saved run carries a format version, and the version is what invalidates it

**Decision:** `SavedRun` gains `version: Int` with **no default**, and
`SavedRunStore.load()` returns null unless it equals `SAVE_FORMAT_VERSION`. Bump
the constant whenever `GameState`, `RunTally` or `SavedResolution` change shape.

**Context:** D11 removed `preview`, `hold` and `holdUsedThisDrop` from
`GameState`, which is the first engine change since C4 that could invalidate a
blob sitting on a real device.

**Alternative, and the reason it was rejected:** rely on `ignoreUnknownKeys`,
which is already set. It would have worked here — a pre-D11 blob decodes cleanly
into the new shape, losing only the held block — and that is exactly the problem.
It only works while every change is subtractive, it decides silently, and the
first change it cannot absorb is a field that changes *meaning* rather than
disappearing. That one would restore a run into the wrong rules with nothing on
screen to say so, and a run restored wrong is worse than a run lost.

Having no default is the load-bearing half. A default would make a pre-D11 blob
decode as "version 2" and be accepted, which is the silent path again.

The cost is bounded and was already the accepted cost of storing this as a
string: the player loses the run in flight and nothing else. The install id, the
onboarding flag and the whole `run_record` history live outside the blob.

The test that guards it loads a hand-written pre-D11 blob twice, once as-is and
once with nothing changed but a `version` spliced in. The first must be refused
and the second must resume. Without the second half the test would pass on any
malformed field and prove nothing.

---

## 2026-09-09 — The in-progress run is a string in `AppData`, not a typed field

**Decision:** `AppData.savedRun` is a `String?` holding JSON. `SavedRun`, the type
inside it, lives in `:features:game:impl` and is the only thing that knows the
format.

**Alternative:** a typed `GameState` field on `AppData`, which is what SPEC 11
reads like. Rejected for two reasons that point the same way.

The dependency is wrong: `AppData` is the app-wide cache, and typing this field
would put the engine on its classpath and make the app-wide cache depend on a
feature's private save format. It also creates a cycle — `:libraries:progress`
holds `GameMode` and depends on `:libraries:drop2048:storage`, which depends on
`:libraries:drop2048`, which is where `AppData` is.

The blast radius is worse. The engine's serial shape moves with every chunk. A
nested field that fails to decode takes **the whole of `AppData`** with it
through `VersionedCacheJsonSerializer`, so a change to `EngineConfig` costs the
player their install id and their onboarding flag over an abandoned run. A string
decodes on its own, and a failure is "no saved run".

## 2026-09-09 — Resume rides the pause seam, and the snapshot is the frame index

**Decision:** the app dying is treated as a pause nobody got to handle. A
`SavedRun` is written at every lock and at every pause; when a cascade is on
screen it carries the board and score the resolution started from, the
transcript, and the playback frame index. Restoring re-derives the frames with
`framesFor` and relaunches the driver from that index.

**Why:** C3 already made the frame index the mid-cascade snapshot for pause, and
SPEC 18.9 asks for the same behaviour for a backgrounding. Building a second
mechanism would mean two things that can disagree about what "mid-cascade" means.
The frames are re-derived rather than stored because they are a pure function of
the transcript — storing them would be a second copy of the same truth, and the
copy is the one that goes stale.

**Written at locks and pauses, not on every tick.** The value only changes
meaningfully per drop. The cost is that a force-quit loses the sideways nudges of
the drop in progress; a disk write twice a second for the whole of every run is
the alternative.

## 2026-09-09 — A run's duration is time played, not wall time

**Decision:** `run_record.durationMs` accumulates only while the phase is
`Playing` or `Resolving`. Every pause closes the stretch, and every save banks
the stretch underway so a force-quit does not lose it.

**Why:** SPEC 15 shows the sum of these as "total playtime", and SPEC 8.4
auto-pauses on backgrounding — which is the pause players actually use. End minus
start would report a run left on the lock screen overnight as eight hours, and
one such run would dominate the lifetime total forever.

The clock is the injected `kotlin.time.Clock` the app component already provides.
The engine has none and does not get one (SPEC 4.1); the ViewModel owns time, as
it already does for the drop timer.

## 2026-09-09 — The board is 5x8, settled on device, because the board is width-bound

**Decision:** `EngineConfig.DEFAULT_ROWS` stays at 8. SPEC 3's seven-versus-eight
question is closed.

**How it was settled:** both were built and run on an iPhone 16e simulator, which
is the smallest device this Mac has a runtime for. The mockups' case for 7 was
that it "draws bigger blocks and reads better on a small phone". It does not.

At five columns on a 6.1" phone the cell size is set by the **width**, not the
height: 8 rows drew a ~64pt cell and 7 rows drew a ~66pt cell. The extra row's
worth of height did not become a bigger block, it became gutter above and below
the stack. So 7 costs a full row of reaction time in the danger state and buys
about 3% of block size.

This holds for any phone narrower than roughly 4:3 against its board area, which
is every phone. If a tablet layout ever ships, it is the first place the
constraint could flip, and `board.rows` is a remote key either way (SPEC 10).

## 2026-09-09 — Transcript playback is a driver coroutine feeding the action channel

**Decision:** `GameViewModel` keeps the resolved `GameState` unpublished, rebuilds
the boards *between* transcript steps with a pure `framesFor`, and hands the
frames to a coroutine that feeds them back in as `GameAction.ShowFrame`. Nothing
animates the engine; the engine is finished before the first frame is drawn.

**Alternatives considered.** Playing the transcript *inside* `handleAction` would
have blocked the action channel for the length of the cascade, which queues player
input rather than ignoring it — the opposite of SPEC 6. Publishing the resolved
board immediately and overlaying effects on top would have meant the board on
screen and the board in the engine disagreeing about where blocks are, which is
the class of bug this whole architecture exists to avoid.

**Why it matters beyond C3.** Because everything still arrives on one channel,
there is exactly one writer of the engine state and no lock anywhere; SPEC 6's
"input during resolution is ignored" is one guard; and SPEC 18.9's pause-mid-
cascade is the frame index that already exists. C5's tutorial and C12's replay
both drive the same seam.

`framesFor` is a pure function in its own file with its own test, and the
assertion that matters is that its **last frame equals the board the engine
returned**. Every other way the replay could drift is a specific bug; that one
catches the ones nobody thought of.

## 2026-09-09 — A sideways move made during a cascade is replayed, not discarded

**Decision:** SPEC 6's "input during resolution is ignored" is amended. Input
still never reaches the engine mid-cascade. But the **last sideways move** is held
and applied to the block that spawns afterwards.

**Why:** playing the first build on device, every move tapped in the beat after a
hard drop vanished, and the block that had just spawned went straight down the
middle of the board. Three separate columns' worth of intended play ended up in
one. The player did the right thing and the game did nothing, which reads as the
game dropping inputs rather than as a rule.

**What is deliberately not buffered.** Hard drop, because replaying one would end
a run the player has not looked at yet. Hold, for the same reason. And it is one
move rather than a queue: a queue would let a burst of panicked taps march the new
block across the board on its own.

---

## 2026-09-09 — The SPEC 5.3 spawn table is kept, and the merge ruling is what made it fit

**Decision:** C1a measured the spawn table in SPEC 5.3 with `tools/balance` and
changed nothing. Three alternatives were measured beside it and all three are
worse against the criterion `BUILD-PLAN.md` set for C1a. The numbers are in that
file's C1a outcome; the alternatives live on in `Tables.kt` so the comparison can
be re-run rather than re-argued.

**Why it needed measuring at all:** every number in that table was written before
the merge-position ruling, so it was a guess made under different physics.

**What the measurement found.** The ruling made the game materially *harder*, and
not by changing how often chains happen. Greedy's cascade-depth histogram is
almost identical either side of it — mean depth 0.88 before, 0.87 after; share of
drops reaching depth 2 or more, 21.8% before, 20.8% after. What moved was where
the merged block ends up. A horizontal merge now migrates the result into the
partner's column instead of leaving it in the column the player just dropped
into, and the partner's column is by construction the one that already had a
matching block in it. The board therefore gets *less* level with every horizontal
merge rather than more, and an uneven board on five columns is what ends a run.
Greedy's 2048 rate fell from 36.7% to 3.8% and Lookahead-1's from 65.1% to 15.7%
on the same table and the same seeds.

**Consequence for the ruling itself:** it stands, and it is now the reason the
table fits. Under the old position rule that same table put a burst in the
majority of Lookahead-1 runs, which is SPEC 17's definition of too easy. Anyone
revisiting the merge position needs to retune the spawn table in the same change.

**Consequence for SPEC 5.3B.** The board-aware cap is evaluated two drops early
and can therefore only ever be too *loose*. The harness counts the drops where
the block that landed exceeds the cap the board would impose at landing time:
0.02% of Greedy's drops and 0.04% of Lookahead-1's. The staleness is real and it
is not worth engineering around. Under the pre-ruling engine it was 0.09% and
0.16% — still nothing, which is the answer to the worry that faster chains would
make the cap compound.

**Not measured:** the harness has no clock. Every policy hard-drops into the
column it wants, so these are ceilings for an unhurried player and say nothing
about SPEC 5.5's speed curve. Real medians will be below them.

## 2026-09-09 — Ruled: the merged block lands in the partner's cell, both orientations

**Supersedes the entry below it.** The owner ruled on the contradiction C1 found,
and ruled the other way: SPEC 4.3 now says **the partner's cell**, in every
orientation, and the vertical "lower cell" wording is gone because a vertical
merge's lower cell already *is* the partner's cell.

**Why the ruling goes this way.** One rule instead of two. It matches both worked
examples rather than only one. And a horizontal merge that pulls the result
*toward* the match is what lines the new block up over whatever sits under the
partner, which is how a chain gets a second step. SPEC 21 says chains are what
the game is for, so a rule that quietly suppresses them is the wrong rule even
where it is the more literal reading. The legibility worry in the superseded
entry stands but is smaller than it looked: the result still lands on a cell
adjacent to where the player put the block, and it lands on the block the player
was aiming at.

**In the code:** the two orientations collapsed into one code path.
`Resolver.merge` no longer branches on direction; the result is written to the
partner's cell and the initiator's cell is cleared, which is now also one board
write fewer.

**Tests:** `PriorityOrderTest` is inverted. The "8 below the left 4" arrangement
is the canonical worked example and must cascade to a 16; the "8 below the
landing cell" arrangement is pinned as the one that must *stop* after one merge.
Either direction of future change fails loudly and names this decision.

**Determinism:** the pinned digest in `DeterminismTest` moved, because merge
outcomes moved. `PINNED_SCORE` 942 → 862, `PINNED_BLOCKS_DROPPED` 27 → 25,
`PINNED_DIGEST` re-derived. Level unchanged at 2. No Daily Challenge scores exist
yet, so this was free; it will not be free again.

**Also confirmed, unchanged:** Wildcard merge symmetry stays, for the reason in
the entry two below. SPEC 5.2 now states it explicitly rather than leaving it as
an implementation divergence.

## 2026-09-09 — The horizontal merge position contradicts the priority worked example

**SUPERSEDED 2026-09-09 by the owner ruling above. Kept for the reasoning.**

**Decision:** SPEC 4.3 wins. A horizontal merge puts the new block in the
**initiating** block's cell, as 4.3 says, and the `PriorityOrderTest` worked
example is written against a board where that produces the outcome the build
plan describes.

**The conflict.** `BUILD-PLAN.md` C1 asks for "the *4 lands between two 4s with
an 8 below the left one* case produces an 8 and an untouched 4, then cascades to
a 16 in the lower cell". That chain is only reachable if the 8 the merge creates
lands where the **left 4** was, i.e. in the *partner's* cell. Under SPEC 4.3 it
lands where the falling 4 did, and an 8 sitting under the left 4 is then
diagonal to it and unreachable. The two statements cannot both hold.

**Why 4.3 wins:** `SPEC.md` says it is the source of truth and that where it and
the original spec disagree, it wins; 4.3 additionally marks the merge rules "not
negotiable once shipped". Moving the result to the partner's cell would also mean
a falling block can trigger a merge two cells away from where the player put it,
which is the legibility the rest of 4.3 is built to protect.

**Cost accepted:** both arrangements are pinned as tests. The "8 below the left
4" variant asserts the chain *stops*, so anyone who later changes the position
rule gets a failure that names the decision rather than a silent behaviour swap.

## 2026-09-09 — A Wildcard merge is symmetric

**Decision:** a value block landing beside a resting Wildcard merges with it, not
only the other way round. SPEC 5.2 describes only the Wildcard-initiated
direction ("on landing, merges with the first neighbor found via the priority
order").

**Why:** the Wildcard's whole job is to be the tile that fits. A Wildcard that
can only ever be consumed on the drop it arrives on becomes dead weight the
moment it rests inert — which SPEC 5.2 explicitly allows it to do — and the
board acquires a permanent obstacle that reads like a bug. "A Wildcard next to a
1024 makes a 2048" is a property of the pair as a player sees it, not of which of
the two moved last. Eligibility is otherwise unchanged: value blocks only, so
SPEC 18.2 (Wildcard beside Wildcard) and 18.3 (Wildcard beside Stone) still hold.

**Consequence:** SPEC 5.2's "re-evaluated whenever an adjacent cell changes"
becomes a rarely-observed safety net rather than the main path, because the
newly-adjacent block usually initiates first. It is implemented and tested
anyway (`SpecialsTest.anInertWildcardIsReEvaluatedWhenAnAdjacentCellChanges`),
since it is the only thing that reaches a Wildcard the arriving block ignored.

## 2026-09-09 — `EngineConfig` lives inside `GameState`, and the undo ring does not

**Decision:** every tunable SPEC 10 marks remote (spawn table, cap divisor, speed
curve, special rates, `board.rows`, `blocksPerLevel`) is carried as an
`EngineConfig` field *on* `GameState`. The eight-deep undo ring is a separate
`UndoRing` value held alongside a state, not a field on it. SPEC 4.1's field list
matches neither exactly.

**Why config goes in:** a seed only reproduces a run if the numbers it was played
under travel with it. Daily Challenge, replay and "attach a seed to the bug
report" all break silently the day a remote value moves, and they break in the
worst way — the run replays, it just replays *differently*.

**Why the ring stays out:** the ring holds whole `GameState`s. Nesting it would
make serialising one state carry eight boards, and SPEC 11 puts the in-progress
run in `AppData`, which is rewritten on every drop.

## 2026-09-09 — The level is stored, not derived from `blocksDropped`

**Decision:** `GameState.level` is incremented when a drop crosses a
`blocksPerLevel` boundary, rather than computed as `blocksDropped / 20 + 1`.

**Why:** SPEC 18.10's rewarded continue drops the level by one. A derived level
snaps straight back on the very next drop, so the reward the player paid an ad
for lasts less than a second. SPEC 4.1 also lists level as something the state
*holds*.

## 2026-09-09 — No accounts: the identity stack is removed, not disabled

**Decision:** `:libraries:identity` (+ impl), the Supabase auth screens in
`:features:onboarding:impl`, the session-expired recovery route, the user-scoped
sync/reset machinery, and the server's `/v1/me` + player-report + moderation
surface are all deleted rather than left dormant. `AuthGate` keeps its seam in
`:libraries:core` with a new `AlwaysReadyAuthGate` default binding in
`:libraries:networking`, next to `NoOpAuthTokenProvider` and for the same
boundary reason.

**Alternatives:** leave identity in place and never call it. **Why delete:**
dormant auth still runs at boot — `GuestAccountCreator` and `GuestSessionHealer`
fire on the launch path and would make doomed Supabase calls on every cold start,
muddying logs and telemetry for the whole project. Every screen built on top
would also have to decide whether to consult a session that can never exist. And
`SPEC.md` 20 calls dead auth code a liability at App Store review. Drop 2048's
only backend surface is public remote config.

**Cost accepted:** progress is device-local and cannot survive a reinstall.

## 2026-09-09 — The user-scoped machinery goes; `ClearableDao` stays

**Decision:** `UserScopedClearer` / `UserScopedWorkStopper` / `UserScopedDataReset`,
`UserScopedDaoCleaner`, `UserScopedSyncer` / `UserScopedSyncCoordinator` /
`UserScopedWorkRegistry`, `TelemetryUserBinder`, `AppData.resetAccountScoped()`
and `AppEvent.UserChanged` are all deleted. `ClearableDao` and the
`ProvideExampleUserDataDao` multibinding pattern stay.

**Why:** every one of those types is defined in terms of a *departing user id*,
which cannot exist here — keeping them would mean an API that lies about what the
app is. `ClearableDao` is the exception because the mechanism is genuinely
useful without the concept: Settings' "reset progress" (C11) injects
`Set<ClearableDao>` and gets every table for free. Renaming the rest into
account-free equivalents was rejected as speculative work for a chunk that
doesn't exist yet.

**Consequence:** nothing consumes `Set<ClearableDao>` today. That is recorded in
its KDoc so C11 finds it.

## 2026-09-09 — Rollout bucketing keys on install id, not user id

**Decision:** `AppConfigSource.read` drops its `UserId?` parameter and the
targeting engine buckets rollouts (and evaluates allow/deny lists) on
`ClientContext.installId`.

**Why:** the engine already fell back to `installId` when no user was resolved,
so this deletes a branch rather than adding one. Staged rollouts and A/B tests on
spawn rates and ad frequency — the main reason the config server exists (`SPEC.md`
10) — work fine keyed on a stable per-install id, and there is no user id to key
on.

**Cost:** a reinstall re-buckets that device. Acceptable for tuning; it would not
be acceptable for a billing experiment.

## 2026-09-09 — User-facing copy goes through `:libraries:resources` from day one

**Decision:** the `VerifyStrings` detekt rule gets honored rather than baselined
for new screens. `OnboardingScreen` is the worked example: strings live in
`libraries/resources/src/commonMain/composeResources/values/strings.xml` and
resolve through `stringResource(Res.string.…)`.

**Why:** the template ships the rule active but baselines every screen that
predates it, so nothing actually followed it. Drop 2048 will have a lot of copy
(HUD, tutorial coach marks, achievements, paywall, settings, legal), and
retrofitting string extraction across twenty screens costs far more than writing
the first one correctly. The baseline keeps the template's leftover screens
(28 entries, down from 53); each gets converted as real game UI replaces it.

## 2026-06-21 — Server mirrors client conventions

**Decision:** `:apps:server` reuses the client's stack — kotlin-inject + anvil DI
(`ServerScope`/`ServerComponent`), the `domain/` interface + `data/` impl split,
conventional commits, the version catalog. It's a plain JVM `application` module
(no convention plugin; those are KMP-only).

**Why:** one mental model across client and server. An agent (or human) moving
between them doesn't re-learn DI, error handling, or module layout. The cost —
the server can't use the KMP `:libraries:core` (`Catching`, logging) because that
module has no JVM target — was accepted; the server keeps a couple of small local
equivalents rather than forcing a `jvm()` target onto every client library.

## 2026-06-21 — Graceful degradation over required config

**Decision:** `DATABASE_URL`, `SENTRY_DSN`, and the OTLP endpoint are all
optional. With none set, the server boots and serves `/_health` + `/v1/example`;
DB-backed routes simply aren't mounted, Sentry no-ops, and OpenTelemetry exports
to stdout.

**Alternatives:** require `DATABASE_URL` like the Cards origin
(fail-fast). **Why optional:** this is a template — "clone and run, see it boot"
beats a fail-fast error on first run. The fail-fast discipline still applies per
field via `Env.require` when a future field genuinely can't be defaulted.

## 2026-06-21 — Auth is JWKS verification, never a shared secret

**Superseded 2026-09-09** — the server has no auth at all now. Kept for the
reasoning, which still applies if auth ever returns.

**Decision:** the server verifies Supabase JWTs against the project's public keys
(JWKS / ES256). The `JwtVerification` sealed seam has `Jwks` (prod) and `Static`
(tests mint HS256 tokens against a known verifier).

**Why:** no Supabase secret ever lives on the server, and auth — the highest-risk
surface — is fully testable offline (route tests + `FullStackMeTest` run the real
validate/challenge path with no network).

## 2026-06-21 — `NoOpAuthTokenProvider` lives in the `:networking` api module

**Decision:** the default no-op `AuthTokenProvider` binding sits in
`:libraries:networking` (api), not `:impl`.

**Why:** the module-boundary rule forbids one `:impl` depending on another, but
an auth library's `:impl` must reference `NoOpAuthTokenProvider` to override it
with `@ContributesBinding(replaces = [NoOpAuthTokenProvider::class])`. Putting the
default binding next to the interface it defaults keeps the replacement
boundary-clean. (See also the `enforceModuleBoundaries` self-edge fix in
`build-logic`.)

## 2026-06-21 — `serverOnly` build slimming

**Decision:** `-Ddrop2048.serverOnly=true` makes `settings.gradle.kts` include
only `:apps:server`, so a Docker image build needs no Android/iOS toolchain.

**Why:** this is a KMP monorepo; without slimming, a server image build would
configure every client module and need the Android SDK + Kotlin/Native. The
server has no client-library deps today, so the gate is a pure settings change;
if it gains one, add an always-included `include(...)` + a Dockerfile `COPY`.

## 2026-06-21 — Flyway SQL is the schema source of truth

**Decision:** migrations under `resources/db/migration` define the schema; the
Exposed `Tables.kt` objects are read-side projections kept honest by
`DatabaseSchemaTest`. Repositories treat a unique-violation (SQLSTATE `23505`) as
the arbiter rather than pre-checking for races.

**Why:** one procedure for schema change (add the next `V##__name.sql`, never edit
an applied one), and idempotency that's correct under concurrency.

## 2026-09-09 — Ports considered and rejected

**Decision:** three things a downstream app offered back are deliberately not in
this template. Recorded so they don't get re-proposed each time someone reads
that app's setup and notices the gap.

- **Macrobenchmark `FrameTimingMetric` in CI.** The right tool for catching jank
  regressions, and it needs a real device. Emulator frame timing on a shared CI
  runner varies more run-to-run than the regressions worth catching, so any
  threshold produces flaky red and gets disabled within a month. Only worth it
  for a project with a device farm. Real-user frame timing (`app.jank`) covers
  the same question continuously and is in the template instead.
- **The Grafana dashboards themselves.** The queries encode one app's event
  names. The *conventions* are portable and already carried; the boards are not.
- **The observability routine and its skills.** Genuinely useful, and shaped
  entirely around one project's dashboards, alert ids and inbox. Revisit only if
  a second app wants the same thing — that is the point at which the generic
  shape becomes visible.

**Why here rather than the port queue:** the queue is work waiting to happen, and
these are closed questions. Keeping them there made an empty queue impossible.

## 2026-09-09 — The three specials are one colour set, not five

**Decision:** `Wildcard`, `Bomb` and `Stone` (SPEC 5.2) get one `BlockStyle` each,
shared by all five palettes, rather than a per-palette entry like the eleven
numeric tiers. Each is held to the *neighbour* floor (ΔE 24) against every tier
face in every palette, which is stricter than the floor tiers hold against each
other at distance, and each is marked on the face by drawn geometry rather than
by a glyph.

**Why:** a special is not a rung on the ramp. A ramp asks the player to order
eleven colours, which is a job colour has to do alone; a special asks only "is
this one of them", and the mark answers that before the colour is consulted.
Authoring five Stones would be fifteen more hexes held against fifteen more
floors to express a difference no player can act on.

The mark is geometry because a star or a bomb typed as a character is a bet that
every font on every platform has that codepoint, and losing that bet puts a tofu
box in the middle of the board with no error anywhere.

`Stone` draws **nothing at all** and is the only achromatic block in the game.
Both follow from the same thing: a Stone is defined by the absence of a number,
so giving it a mark would make it look like a special that does something.

**Measured, on the shipped Kotlin:** the closest any of the fifty-five tier faces
comes is Stone at ΔE 26.1 (tritanopia's 2), then Wildcard at 36.0 (default's 128)
and Bomb at 41.8 (deuteranopia's 2). Mark contrast is 4.85:1, 15.79:1 and 4.99:1.
Under the Viénot simulation the tightest pair is Wildcard against deuteranopia's
8 at 21.5, which is under the 24 the ramps hold and above the 20 floor the test
sets for specials — see `BlockPaletteTest.theSpecialsSurviveEachDeficiency`.

## C11 · Settings, legal, launch gates, accessibility

### The player's settings are one value read at the root, not a setting per feature

`PlayerSettings` + `PlayerSettingsStore` live in `:features:settings`, are bound to
`AppData` in `:features:settings:impl`, and are read in exactly one place:
`PlayerThemeProvider`, which `App` calls instead of `AppThemeProvider`.

The alternative — each feature reading `AppCache` for the settings it cares about —
is what the app already did for `leftHandedControls` and `ghostEnabled`, and it is
how C2's five palettes stayed unreachable for six chunks: `AppThemeProvider` was
widened to take a palette, a reduce-motion flag, a large-numbers flag and a haptics
setting, and nothing ever passed one. A single store means a new accessibility
setting is a field, a row and a line in the provider, rather than a hunt for every
screen that ought to care.

`PlayerThemeProvider` lives in `:features:settings:impl` rather than in `App.kt` so
`AccessibilitySettingsReachTheUiTest` composes the production wire rather than a
copy of it. Reverting its body to `AppThemeProvider(content = content)` turns four
of its five assertions red, which is the exact bug the last six chunks shipped.

### Settings enums are persisted by name, decoded leniently

`AppData` stores the palette, the haptic strength and the control scheme as
nullable `String`s and decodes them with a fallback rather than `valueOf`.

Same reasoning as `savedRun` being a string: `AppData` is one serialized blob, and a
field that fails to decode takes the install id, the onboarding flag and the legal
record with it. A palette removed between releases must cost the player a colour
scheme, not their whole record. It also keeps `:libraries:drop2048` free of a
dependency on the design system, which it has never had.

### `SettingsRoute` carries no arguments at all

Type-safe nav turns every serialized constructor `val` into a nav argument, and on
Kotlin/Native an enum argument needs an explicit `NavType` in the destination's
typeMap or graph-build throws — naming a *different* argument than the one that was
forgotten. A settings screen is where enum arguments are most tempting. Every
choice on this screen is persisted rather than routed, so an argument would buy
nothing and cost a landmine.

### Reset progress and delete local data are not the same control

Both empty every `ClearableDao`. "Reset progress" additionally drops the run in
flight and keeps everything else — the install id, the legal record and the
accessibility choices are not progress, and re-accepting the terms is not what the
player asked for. "Delete local data" replaces `AppData` with a fresh one carrying
only the accessibility and control settings across, because resetting a palette out
from under somebody mid-tap on "Delete" reads as a bug rather than as a deletion.

Neither is `fallbackToDestructiveMigration`. L33 narrowed that so a *failed
migration* crashes rather than silently wiping a player's history. This is the
opposite thing wearing the same shape: a deliberate, double-confirmed request. The
second confirmation asks for a typed word, because this app has no account and no
server copy, so a reset is final in a way it is not in an app that can restore.

### The launch gates render instead of the nav host

Ported from `Sodogku/features/gate` with its domain stripped. `LaunchGateHost`
wraps `AppNavigation` inside `App`; a blocking gate returns early and the nav host
is never composed, so there is no back stack entry to pop and no deep link that can
land behind the wall. A notice is a sibling *after* the content in a `Box`, never a
`Column` above it — an inserted sibling before the content changes its slot and
throws away the nav host every time a banner appears.

The gate's config keys live in `:libraries:gameconfig` beside SPEC 10's other keys.
Every default blocks nobody: zero is below no version, `off` is not `blocking`, and
anything `resolveLaunchGates` does not recognise resolves to off. These are the only
keys in the project that can brick every install at once.

### The settings screen's visual language is the design system's list, derived

The design handoff lists settings under "not designed yet — ask before inventing".
Rather than invent a look, the screen is `ListSection` groups in the order a player
looks for things, with the two destructive rows last and alone. The palette rows
carry a live `BlockPaletteStrip`, because "Tritanopia" tells a player nothing about
what their board is about to look like.

One consequence worth an owner ruling: the meta screens (settings, stats, the
dialogs, the gates) are the design system's light theme, while the board draws its
own dark backdrop. That is consistent with the Stats screen as it already ships,
and it is jarring — see the `game-quit-confirm` golden, a white dialog over a dark
board.

---

## C12 · Debug menu, and the first Room-backed tests

### The debug *session* is the unit, not the debug run

Opening the debug menu latches a process-wide flag. From that moment no run
writes `run_record`, banks a `daily_result` or posts a leaderboard score, and
every analytics event carries `debug_session: true` for C8 to consume. It cannot
be switched back off from inside the app; relaunching is the reset.

The tempting design is to taint individual runs — mark the one that was forced,
leave the rest alone — and it is wrong in the only direction that matters. A
tester who loads a preset, plays it, quits and then plays a "normal" run has
still had their hands on a menu that can set a seed and grant Pro, so treating
that second run as real data trusts exactly the person most likely to be
producing junk. Per-run tainting also has to be *remembered* at four call sites,
which is four places to forget it. The flag is read once, in `RunFactory`, and
travels on `StartedRun` so that opening the menu mid-run cannot retroactively
delete a legitimate run's record.

The cost is real: a tester who opens the menu only to read the current seed loses
that run's record. That is the right trade, because the alternative failure is
silent.

### There is no debug `EngineConfig`, which is why one cannot leak into a Daily

D18 pins the Daily to `EngineConfig.Default` so everyone who plays a day plays the
same game, and D5 puts the config *inside* `GameState` so a replayed seed
reproduces the run it was recorded under. A debug override shaped like an
`EngineConfig` would therefore travel inside a saved run, inside a `run_record`
replay and — the moment anybody wired it up — inside a shared seed.

So `DebugOverrides` contains no config. Everything it holds is either an edit to a
*starting state* (board, level, seed, the forced queue) or a number the
ViewModel's own clock reads (tick interval, freeze). The tick override is the
clearest case: SPEC 5.5's speed curve is a remote key and this is deliberately not
it, and because vertical position is not an input to the engine (L40) a changed
interval cannot change where a block lands. `dailyRun` ignores the overrides
outright as a second, independent guard, and `DebugRunFactoryTest` pins both
halves — that the overrides visibly change an Endless run, and that the same
overrides move nothing about a Daily.

### Forcing and invincibility are applied after the engine, never inside it

The engine stays a pure function of state and a seed (SPEC 4.1). A forced block is
overwritten on the state the engine just returned, and invincibility rewrites a
`STACKED_OUT` status back to `PLAYING`. Teaching the engine about a debug queue
would mean a `GameState` no longer determines the next one, which is the property
Daily Challenge, undo, resume, the balance harness and replay are all built on.
The run is played honestly and then the answer is rewritten, which is also exactly
why a run that has been through there writes nothing.

### The autoplay soak reuses `tools/balance`'s policies

`Policy` moved from `tools/balance` into `:libraries:cascade` (`autoplay/`), with
the one drop helper it needs. A second scripted player in the debug menu would
report a level that looks like the harness's and is not comparable to it, which is
worse than no number. `tools/balance` now delegates to the same code, so the
policies that measured SPEC 5.3's spawn table are the policies the soak runs.

### Debug copy is plain constants, not `composeResources`

The menu is unreachable without seven taps and, on a release build, a passphrase.
Nobody outside the project will see it, so there is nothing for a translator to
translate and every string added would be a real string in every locale file for
the life of the app. The repo's string baseline stays at nine; `VerifyStrings`
fails on a literal passed directly to `Text(...)`, and these are named values.

### The Room harness runs on the framework driver, not the bundled one

`sqlite-bundled`'s **android** variant loads device `.so` files through
`System.loadLibrary`, so on a host JVM it dies with `no sqliteJni in
java.library.path` before a statement runs. The tests use `AndroidSQLiteDriver`
under Robolectric 4.16, which is a real SQLite build — so what they exercise is
real SQL. What they do not pin is the exact SQLite version the app links on
device.

That is also why `setDriver` moved out of `RealAppDatabaseProvider` and into the
two platform builder factories: a provider that hardcoded the bundled driver could
only ever be constructed on a device, which is why L33's migration promise went
untested for eight chunks.

### What the narrowed destructive fallback actually guards

Room prefers a migration to a drop, so *every* version with a path migrates
whether or not it is on the `fallbackToDestructiveMigrationFrom` list — which
means "the version 6 row survived" says nothing on its own about the narrowing.
What the list decides is the versions with **no** path: 1 to 4 are emptied and
rebuilt, and anything else (a downgrade from a rolled-back release, or a future
bump somebody forgets to write a migration for) refuses to open and leaves every
row where it was. Both halves are asserted, and both were proven to bite by
mutating the production configuration and watching exactly one of them go red.

### The decision-time instrument measures column steps, not gestures

`DropClock` charges the modelled player `tapMillis` for every `Input.MoveLeft` /
`MoveRight` it issues, so a target three columns away costs three taps. The live
instrument therefore counts **accepted engine column steps**, which means a drag
across three columns counts as three.

The alternative — one count per gesture — was rejected because it measures a
different quantity from the one the number exists to be compared against. Drag
steering (D11) is the control most players use, and counting it as one input
would make the live tap rate read roughly three times slower than `tapMillis`
while looking entirely plausible. The whole value of the measurement is that it
can be substituted straight into a harness sweep.

### An unsteered drop is censored, not zero, and not dropped

Live, a block that spawns in a column the player is happy with takes no input at
all. Offline that case does not exist, because every policy reaches its target.

Recording it as a 0ms decision would make the population read about three times
faster than it is. Discarding it would throw out every easy board, which biases
the other way and by an unknown amount. So the drop is counted in
`drops_unsteered`, no time is recorded, and both counts ship on `run.end` — the
analyst gets the steered-only median *and* the censoring rate that qualifies it,
which is the only honest pair.

### `debug_session` is stamped by the export tree, and `run.end` carries a second flag

L63's latch could have been added to each `logEvent` call. Forty call sites is
thirty-nine chances to forget it, and the forgotten one is invisible: QA data on
a dashboard looks exactly like real data. So `GrafanaLogTree` stamps it on every
record it exports — one place to be wrong, and one that no new event can miss.

That flag answers a wider question than the dashboards need, though: it is true
for the whole life of a process in which the menu was opened, including runs
that started before it. The narrow question — did the four writes that claim a
player did something actually happen — is `StartedRun.debug`, and it rides on
`run.end` as `recorded`. Keeping both is what lets "no `run_record` row was
written" be told apart from "the row was written and never reached us", which
are otherwise the same absence.

### Endless `run.end` carries its seed; a Daily one carries only its date

SPEC 17 asks for the seed. SPEC 14 says the Daily seed is the board everybody in
the world plays that day, and a `run.end` ships the moment the attempt ends —
which for an attempt started at 00:05 UTC is nineteen hours before the day is
over. An event stream is not a place to publish a board early.

Endless seeds have no such problem and make a reported run replayable, so they
ride. Daily runs carry `daily_date` instead, which is the only part of a Daily
attempt that was already public.

### Two reporting rates for the decision times, and neither is per drop

A per-drop event would be two thousand records for a long run, which is the
firehose `app-events.md` forbids. A run-level average alone would lose the
distribution and, worse, the correlation with level — and "do players get slower
as the board speeds up" is the question L41's sweep could not answer from the
outside.

So the per-drop timings ride on the every-10th-drop sample that already fires at
the right rate, where they arrive next to `level` for free, and the run-level
quantiles ride on `run.end` so a four-drop run still contributes a number. Two
hundred drops produce twenty samples and one summary.

### The decision histogram is fixed buckets, not a list of samples

Keeping every measurement would be simplest, and the debug menu's invincibility
can run a board for thousands of drops — an unbounded list inside a ViewModel
that also holds the board is how a telemetry counter becomes an OOM. Sixty 50ms
buckets is 240 bytes whatever the run does, and 50ms is well inside the
resolution the question needs: L41 swept `decisionMillis` in 100ms steps and what
it wants to know is whether the real number is nearer 150 or nearer 500.

### The meta screens get the game's vocabulary, not just its palette

C3c re-pointed the design system's role ramp at the dark palette, which fixed the
colour clash between the board and the stats page and did nothing about the
*form*. Both screens stayed list rows and flat cards while the game is chunky
pressable surfaces, Fredoka numerals and hard offset shadows, so the owner still
read them as a different product.

The handoff does not draw either screen — its own "not designed yet" list covers
settings and anything leaderboard-shaped — so these are derived from what it does
specify rather than copied. Three primitives now live in `:libraries:ui`:
`GamePanel` (the hard-shadow plate at the handoff's 20dp panel radius),
`StatPanel` (the HUD's quiet uppercase label over a Fredoka numeral in
`FixedWidthDigits` slots) and `StatReadout` (the same pairing as a row). They are
in the design system rather than in the feature because the next meta screen —
achievements, a leaderboard — needs exactly these and would otherwise grow its
own.

`SectionCard` and `SummaryRow` are untouched and still correct for a form. The
difference is that one states a preference and the other states a score.

### Highest tier is drawn as a tile

It is the one number on the stats page that names an object the player has held
on the board. Printed as `1024` it is a digit string; drawn as a `Tile` from the
live `BlockPalette` it is the block they remember making, in their own palette if
they have changed it. It is the cheapest single thing on the page that ties it to
the game, and it costs a `CompositionLocalProvider` for a smaller `BoardScale`.

### The recent-runs chart loses its axis and its guides

A tick ladder and a grid are what a chart wears when the reader has to take a
value off it. Nothing on this page asks that — the exact best and the exact
average are printed above it in a larger face — so what is left is the shape of
the last five runs, and that reads better as five chunky blocks than as a plotted
series. The best of the five takes the accent violet so the tallest bar is also
the coloured one.

### The paywall is a bottom sheet

Owner instruction, and the backstack makes the reason concrete: both things that
open it (the Settings row, the card on the stacked-out sheet) are places the
player expects to come straight back to. A sheet stays one entry deep, leaves the
screen underneath visible under the scrim, and dismisses on a drag, a back press
or a tap outside without any of those being a navigation to reason about. It also
deletes the last thing on that surface that read as a settings page: a title bar
with a back chevron pointing at nothing.

The two halves of leaving are separate on purpose. `PaywallEvent.Leave` asks the
sheet to close; `onDismissRequest` pops the entry once it has finished sliding
out. Doing both in one place cuts the exit animation in half.

`PaywallRoute` lost its `SlideUp`/`SlideDown` overrides with the move. A
floating-window destination is animated by the sheet, not by the nav host, so
those args were a promise the transition no longer keeps.

### The paywall's buy button is the game's primary button

`GamePrimaryButton` — accent yellow on its hard shadow, Fredoka, pressing four
pixels down. Buying Pro is the same *act* as `Drop again`, and drawing the one
paid action in the template's flat button was the loudest thing on the old screen
saying "different app". It gained an `enabled` flag for the moment a purchase is
in flight; it is deliberately not dimmed, because greying the only saturated
thing on the sheet for the length of one store modal reads as broken rather than
as busy.

The perk marks are geometry rather than a check glyph, for the reason
`SpecialTile` gives: a check character is a bet that every font on every target
carries that codepoint, and losing it puts a tofu box down the side of the one
screen that takes money.

### The directive channel is a build seam, not a runtime flag

`DevFeedback` is one composable on an interface, exactly the shape C15 gave
`HouseAds.Surface()`, and it was chosen because the alternative had already been
built and rejected once. The tester surface records every frame of the app into a
graphics layer so it can screenshot itself, draws a draggable button on top of a
live board, and uploads the session log without asking. `if (isTesterBuild)`
around all of that is a runtime answer to a question the build can answer, so the
build answers it: `:libraries:devfeedback:tester` is a `debugImplementation` of
`:apps:compose`, and an Android release APK contains no button, no panel, no
layer recording and no JPEG encoder. `verifyNoDevFeedbackInRelease` resolves the
release runtime classpath and fails if it ever does, because a variant scope is
structural only until somebody changes one word.

The api module keeps the persisted position and the `hidden` flag, which every
build carries. That is deliberate: the QA switch writes them, and the QA switch
has to work on a build the tester module is not linked into. Two floats and a
boolean nothing reads is not a risk; a button with no off switch is.

iOS gets one layer instead of three, and here that gap is load-bearing rather
than merely tolerated. A TestFlight build *is* a release binary, so nothing
structural can tell it apart from the App Store; holding the channel out of every
release iOS binary would mean the owner could only file a directive from a cable.
`BuildInfo.isTestFlight` reads the receipt at runtime, which is the only thing
that can, and `isTesterBuild` is `isDebug || isTestFlight`. Notably it is **not**
`releaseChannel`: that string says which artifact this is, not who is holding the
phone, and a channel named "internal" that somebody promotes would hand every
player a directive button.

### An owner directive carries the session log; a player's report still does not

C13a gated the session-log attachment on `diagnosticsOptIn` and cleared the
carrier's breadcrumbs, because a breadcrumb carries the logged event's attributes
and `run.end` was putting `score`, `level` and `highest_tier` on every report
filed, whatever the switch said (L74). The directive channel wants both
unconditionally.

That is decided by **kind** rather than by a second argument beside the opt-in.
`FeedbackKind.isOwnerChannel` is true of exactly one of three values, and
`captureUserFeedback` reads it; there is no combination of arguments a caller can
pass that sends a player's board to Sentry, because a caller cannot ask for the
owner's treatment, only to *be* the owner. The player's own seam,
`FeedbackRepository`, still takes `isBugReport: Boolean` and cannot name a
`FeedbackKind` at all, which `PlayerFeedbackSeamTest` pins — a shape that cannot
express the mistake, the same trade C12 made with `DebugOverrides`.

`diagnostics_opt_in` stays a separate tag from `feedback_kind` and still records
what the *player* chose, so a directive can read `false` there and carry a log.
Collapsing the two would lose the answer to the first question.

### The QA menu is its own screen, not a section of the debug menu

The debug menu would have been the obvious home, and two things rule it out, both
of them properties C12 put there on purpose. `DebugViewModel`'s `init` calls
`markDebugSession()`, so **opening** the menu silences run history, the Daily
result and leaderboard submissions for the life of the process (L63); and
navigating to it clears the back stack, so a tester reaching it loses the screen
they were on. Hiding a floating button is two seconds of housekeeping. Charging
it a whole process of silenced telemetry and the screen the button was in the way
of would make the switch cost more than the button.

`QaToolsViewModel` therefore holds no reference to `DebugController` at all,
rather than holding one and remembering not to call it.

The split follows Sodogku's, expressed as two routes instead of one `isDebug`
branch: the QA row shows on any tester build with no seven taps and no
passphrase, because a TestFlight tester must be able to get the button out of the
way; everything destructive stays behind the taps. What had to be preserved was
that the switch is reachable **without** the button it switches off, and it is.

### The floating button stores fractions, and reads them outside composition

Its position is a fraction of its *travel* — the container less the button — so
`0f` is flush left or top and `1f` flush right or bottom. Pixels would be wrong
on rotation, in split screen and on a different device; a stored offset that was
against the right edge in portrait is in the middle in landscape. The fractions
are clamped on the way in *and* coerced on the way out, with `NaN` taking the
default rather than a clamp, because they are the only thing between a bad write
and a button parked where it cannot be tapped.

Neither the placement nor the travel is read during composition. The `offset`
lambda reads them in the layout phase and the drag callback reads them off the
pointer coroutine, so a drag relayouts one node per frame and recomposes nothing
— the same rule as the ban on reading an animated value in a composable body, and
for the same reason. The host's state read is in a child composable for the
sharper version of that: the host's content lambda is the entire app, so a read
there would re-run the whole tree's composition to move a button.

`clickable` and `detectDragGestures` coexist on the button rather than one
replacing the other. `clickable` consumes the down; `detectDragGestures` arms
with `requireUnconsumed = false` so both are live; the drag then consumes each
move past touch slop and `waitForUpOrCancellation` re-checks consumption on the
`Final` pass. Drop the `clickable` and the button loses its semantics role and
its ripple; take the ordering for granted and a button that was only moved also
files a directive. Verified on a device: a 600ms drag across the board moved it
and opened nothing.

## 2026-09-16 · D30 — One vibrator, so the cues are ranked rather than queued

Numbered D25 when it was written, which collided with the config-key ruling above. Renumbered on
2026-09-20. Nothing outside this file ever cited it, so the collision cost nothing beyond the audit
that found it.

Filed 2026-09-16, and it reverses the diagnosis that prompted it.

`dumpsys vibrator_manager` caught a `Cue.Move` coming back `cancelled_superseded` a millisecond
after the `Cue.Merge` behind it. That was read as the bug. It is not: the merge is the board
answering the steer, one millisecond into a 12ms tap is not something a hand feels, and giving the
move up is the outcome you would choose if you were choosing. The move is still given up, and
`aMoveIsGivenUpToTheMergeBehindIt` says so by name so nobody re-files it.

The defect is the same collision resolved the same way in the other direction. Mapping every seam
that issues two cues within a few milliseconds, under reduce motion where the holds are scaled to
0.4, turned up two that cost something. A steer replayed out of the input buffer could cut a
`LEVEL UP` at 24ms of its 40. Worse, the merge after a row burst could cut the burst's decaying
390ms roll at 168ms, and SPEC 21 names that burst as one of the two effects worth keeping when
everything else goes.

**Ranked, not queued.** A haptic is the felt half of a drawn frame, so one played late is one played
over the wrong frame. A cascade is a run of steps, so a queue falls further behind on each of them
and ends up describing a cascade that has already finished; waiting out a burst is 390ms, which is a
different drop. `HapticBus` refuses a request of lower or equal rank while the incumbent is still
being felt, and hands the vibrator over to a higher one. Equal rank leaves the incumbent alone,
because cancelling to re-fire the same weight is strictly worse than letting the first finish.

The rank is written out rather than read off `ordinal`, so reordering the enum for readability
cannot silently reorder the game's priorities.

**Only the haptic half is arbitrated.** Two samples mix and two vibrations do not, so every merge of
a cascade is still heard even where its buzz is refused. The known cost is a `LEVEL UP` inside a row
burst's window losing its buzz while keeping its sound and its callout. That is the trade, taken
deliberately: the alternative is halving the burst.

`feltFor` is a floor, not a measurement. Neither platform reports what its vibrator is doing, so the
numbers come from `HapticEngine.android.kt`, the longer of the two, with the error one-sided: too
long costs a haptic that could have been felt, too short puts the cancellation back.

What CI can prove is that `Cues` never *asks* for the overlap, and `noHapticIsEverCutShortByALesserOne`
replays a whole reduce-motion drop to prove it. Whether a real vibrator still reports
`cancelled_superseded` needs a device, and if it does, it means a cue pair nobody mapped.

## D26 · The spawn column is random, level 1 is shorter, and the specials stay where they are

Filed 2026-09-20, from the owner's ruling that "the game doesnt get hard quick enough" and three
candidate levers: spawn the block from random places, shorten level 1, introduce the harder blocks
sooner. The first was a decision. The other two were questions, and `tools/balance` answered them.

Two of the three are adopted and they ship together, because both move `PINNED_DIGEST` and the pin
should move once.

### Lever 1 · A block enters in a uniform random column

`EngineConfig.spawnColumn` was `cols / 2` and every block in the game's history entered there. The
column is now a uniform draw taken in `Spawn.draw` off the run's own RNG, appended after the value
roll so a stream diff reads as one extra roll per draw rather than a re-alignment.

It has to come off the state's RNG and not from anywhere else: SPEC 4.1's whole architecture is that
a seed plus a list of inputs reproduces a run byte for byte, and Daily Challenge, undo, save/resume,
replay and every bug report are downstream of that one property.

**Nothing constrains the draw against the board, and nothing needs to.** A run ends when row 0 is
occupied after a resolution completes, so by the time anything spawns, row 0 is empty in every
column. `spawnNext`'s `SPAWN_BLOCKED` fault is kept as the guard on that reasoning. Re-drawing
against the board was the tempting alternative and was rejected for making the fault unreachable by
construction, which throws away the only thing that would tell us the stacked-out check had broken.

Measured, 10,000 clocked runs per policy at the `average` profile:

| | greedy before | greedy after | lookahead1 before | lookahead1 after |
|---|---|---|---|---|
| median level | 19 | 18 | 21 | 20 |
| drops (median) | 372 | 355 | 419 | 391 |
| 1024 or better | 24.2% | 20.1% | 43.4% | 37.9% |
| 2048 | 2.31% | 1.79% | 9.68% | 6.71% |
| off preferred column | 2.36% | **4.49%** | 2.69% | **4.72%** |

The last row is the one that matters. SPEC 4.4 calls it the only one of the clocked numbers that
means pressure rather than theatre, and it nearly doubled. SPEC 5.5 used to justify the speed curve
with "on five columns with a centre spawn, a block is at most two columns from anywhere"; that
sentence is now false by design — the worst case is four columns, and at 120ms a tap plus 250ms to
decide that is 730ms against a fall budget that reaches 720ms at the speed floor. Time becomes a
real pressure late, which is what it was never allowed to be before.

### Lever 2 · `blocksPerLevel` 20 → 15, and the speed curve left alone

The two halves of "should level 1 be shorter" came apart, and only one of them does anything.

**The speed curve is inert, again.** `--curve fast400` against the shipped curve: every outcome
column identical to the digit, on both a hard-dropping player and a patient one — median level 18,
p90 22, 355 drops, 512 at 58.4%, 1024 at 19.7%, 2048 at 2.02%. All it moves is the wall clock of a
player who never presses ▼, from 223s to 186s to reach level 4. C1c and C1e each measured this and
each left the curve alone; this is the third time, and the answer has not changed. What is left of
the opening's dead time belongs to a player the tutorial's frozen clock (L49) is designed to stop
existing.

**`blocksPerLevel` is the lever, exactly as C1c said it was.** Swept clocked, greedy:

| `blocksPerLevel` | time to level 4 | drops | 1024 or better | 2048 | random policy median |
|---|---|---|---|---|---|
| 20 | 36.1s | 355 | 20.1% | 1.79% | 4 |
| **15** | **27.1s** | **317** | **19.0%** | **1.29%** | **5** |
| 12 | 21.6s | 292 | 16.9% | 1.02% | 6 |

15 rather than 12 because 12 takes the Random policy's median to level 6, and SPEC 4.4 reads
"Random reaches level 6" as the game being too easy. It would be reading an inflated number — a
cheaper level is not a lower difficulty, and this is the trap in every row of that table — but a
tripwire that has to be explained away every time it fires stops being read. 15 leaves Random where
20 had it.

C1c measured this same change and declined it, and the reason it is taken now is not that the
numbers moved. They reproduced to the digit. What changed is that the owner has ruled the game too
easy, which is the judgement C1c declined to make on its own authority, and that lever 1 is moving
the digest anyway — so the cost C1c was weighing against is already being paid.

**The five achievement score rungs move with it and that is by construction.** `ScoreLadder` derives
each from `floorScore(level)`, which reads `blocksPerLevel`, so a 15-block level makes every rung
25% cheaper on its survival term while also making every level 25% cheaper to reach. C9 built it
that way precisely so this change needs no edit; `AchievementReachabilityTest` plays the shipped
engine and still passes. Sodogku stranded three badges behind exactly this and it is the reason the
numbers are not written down.

### Lever 3 · Rejected. The specials stay at 5 / 8 / 12

The question was whether Wildcard, Bomb and Stone arrive too late against runs that reach level
18-20. Swept clocked, greedy, and it decomposes cleanly: **the Stone is the entire effect and the
other two are not a difficulty lever at all.**

| first levels (W/B/S) | drops | 512 | 1024 | 2048 |
|---|---|---|---|---|
| 5 / 8 / 12 (shipped) | 355 | 57.8% | 20.1% | 1.79% |
| 3 / 5 / 12 | 359 | 56.7% | 18.7% | 1.51% |
| 5 / 8 / 10 | 327 | 50.9% | 11.2% | 0.84% |
| 5 / 8 / 9 | 313 | 44.2% | 8.1% | 0.63% |
| 3 / 5 / 8 | 303 | 34.3% | 5.6% | 0.30% |

Moving the Wildcard and the Bomb earlier changes nothing outside noise, which is the right answer
for two blocks that are gifts: a Wildcard beside a 1024 is the screenshot SPEC 5.2 is written
around, and a Bomb pays 50 a block.

Moving the Stone earlier is a very sharp instrument pointed at the wrong target. Two levels of it
halves the 2048 rate and pushes the share of runs that end at 256 from 20% to 36%, and it buys
**zero** seconds off time to level 4 — it makes the late game harder, and the complaint was that the
early game is not. SPEC 21 names 2048-inside-one-run as one of the two things to keep if everything
else is cut.

**And it does not need a release.** `special.stone.firstLevel` is already a live SPEC 10 remote key
with a compiled-in fallback, so this is a console push whenever the owner wants to try it, priced at
roughly half the 2048 rate per two levels. Moving the compiled default instead would move
`EngineConfig.Default`, which D18 pins the Daily to, and the digest with it — a permanent cost for a
dial that already turns.

### What this costs, and why now is the only time it is cheap

- **`PINNED_DIGEST` moves a fourth time**, to `7004126634716158444`, with `772 / 22 drops / level 2`
  becoming `7166 / 87 drops / level 6`. Re-derived by running the engine, with the arithmetic closed
  on all four scoring channels; the reasoning is in `DeterminismTest`'s KDoc.
- **`EngineConfig.Default` moves**, so D18's Daily pin moves with it. No Daily result has ever been
  recorded — no store accounts, no live boards, no shipped build (SPEC 0) — which is the only reason
  any of this is affordable. `OWNER-TODO.md` has been saying the window is closing since D21. It is
  still open and this is the fourth and last time that sentence gets written.
- **`SAVE_FORMAT_VERSION` goes to 7**, and it is the first bump where nothing about the shape
  changed. A version 6 blob decodes perfectly; what it decodes into is a run that would resume under
  a spawn rule the half already played never saw. That is the failure version 5 exists to refuse, so
  it is refused the same way. The rule in `SavedRun`'s KDoc is widened to cover it.
- **An in-flight Daily run is lost with its attempt.** SPEC 14 spends the attempt when the run
  starts, so a player who updates mid-Daily pays for a board they do not get to finish. Accepted
  rather than worked around: resuming would put two spawn rules inside one Daily board, which breaks
  the comparability D18 pins the whole mode on, and migrating is impossible because the spawns
  already made are in the board and not recoverable from the seed.
- **Nothing on the leaderboards moves**, because D24 cut the Daily board and a Daily score now posts
  nowhere. The all-time and weekly boards are Endless, which has never been comparable across a
  config change and has no recorded scores either.

### The tutorial does not inherit any of this

`TutorialDrop.spawnColumn` was nullable and meant "null spawns wherever the engine would have". It
is now required and every one of the six drops states its own: drop 1 at the far edge as before,
drops 2-6 in the middle beside their partner. Left as it was, a random entry column would have
handed drops 2-6 a block one steer away from a partner the player has not been taught to reach —
a scripted merge that silently does not happen, on a beat with no card in front of it, which is the
exact thing the "every partner sits beside the spawn column" note was written to prevent. Stating it
per drop also means the next change to the engine's spawn rule cannot reach the tutorial without
editing the script.
