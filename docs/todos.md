# Agent TODO

The queue the orchestrating session works from: things found mid-chunk that do not belong in the
chunk that found them. Ideas, deferred fixes, cleanups, things to add or remove.

Not a changelog. **Delete entries as they land** rather than ticking them off, so this stays a
queue.

Human-only items go in `OWNER-TODO.md` instead.

---

## Now

*(nothing yet)*

## Soon

### Port the design system pieces Sodogku already proved

`ORCHESTRATION.md` has the table. C2 takes the color, motion and haptics foundations; the rest
gets pulled as the chunk that needs it arrives. Do not port the whole thing up front: an unported
component costs nothing, a ported-and-unused one costs maintenance forever.

### Decide whether `:features:onboarding` keeps its name

C0 guts it down to the route. C5 refills it with the tutorial. If nothing auth-shaped ever comes
back, `:features:tutorial` is the honest name and the rename is cheapest before C5 fills it.

### Check `:libraries:review` is still wanted

The template ships an in-app-review library. SPEC 15 (original spec 15) wants the rate prompt
surfaced only after a run that set a personal best. That is a real trigger and worth keeping, but
confirm the library is wired rather than assuming.

### `docs/PORT-CANDIDATES.md` writeback

`AGENTS.md` says anything learned here that a brand-new app would want goes back to the template
repo's `docs/PORT-CANDIDATES.md`, which lives in `../KMPTemplate` and not here. Collect candidates
as they appear; do not write them back one at a time.

## Later

### Zen mode is nearly free

SPEC 2 cuts it. It is a one-line engine change (never tick) plus a Pro gate. Worth revisiting the
moment Pro exists in C10, because it makes Pro's value proposition much less thin.

### The powerup economy re-entry

All five powerups in the original spec are designed against board state the engine already
exposes, and Undo already ships internally (SPEC 5.6). When the economy comes back, it is
additive. Keep it that way: no engine change should make a powerup harder to add later.

---

## Watch list

Not tasks. Things that have bitten this template before and should be checked when the relevant
code lands.

- **A clean detekt run does not prove a custom rule ran.** The Gradle daemon caches detekt's
  worker classloader, so an edited rule keeps running its previous jar until `./gradlew --stop`.
  Prove dispatch by making a rule report unconditionally, confirm the flood, revert.
- **Skipped tests read as passing.** Docker-dependent tests self-skip. Count them.
- **An event with no production call site looks like coverage.** Sodogku shipped
  `Leaderboards.submit` with zero callers and telemetry events emitted by nothing. Assert call
  sites, not just definitions.
- **`UIApplication.canOpenURL` needs its scheme in `LSApplicationQueriesSchemes`**, or every
  outbound link silently opens nothing with no error.
- **Infinite animations hang preview and screenshot capture.** Anything looping forever returns a
  fixed value under `LocalInspectionMode`.
