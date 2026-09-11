# Reading the design handoff

The handoff lives at `/Users/elijahdangerfield/Documents/design_handoff_drop2048/`.
`Drop 2048 Mobile.dc.html` is its canonical file; `README.md` is its written spec.

**Read it with this page open.** Its README opens by claiming it "reflects all the latest gameplay
decisions". It does not. It is a high-fidelity *visual and interaction* handoff whose gameplay
sections describe an earlier, simpler prototype, and it will keep telling every future reader
things this project has deliberately rejected.

Owner ruling 2026-09-09, recorded as D10 in `ORCHESTRATION.md`: **the handoff wins on look and
feel, `SPEC.md` wins on rules.**

---

## Where the handoff is authoritative

Take these exactly, not approximately. The numbers in its README are final.

- Colour: the background gradient, board well and ring, danger ring, slot and slot-active, the ink
  ramp, control and control-quiet with their shadow pairs, accent yellow and violet, overlay
  scrims.
- The **tile ramp**: hue per value, `L/C` of `0.78/0.15` below 2048 and `0.85/0.17` at 2048+, ink
  at `oklch(0.26 0.07 H)`.
- Type: **Fredoka** for numerals, wordmark and buttons; **Nunito** for labels and body. Bundled,
  not loaded at runtime.
- The **hard-offset shadow system** with no blur, an inset top highlight, and pressed states that
  move the element down by the shadow delta. Only the falling tile and overlays use soft shadows.
- Radii, spacing, and the em-based board scaling where every tile dimension derives from one
  font-size.
- Every motion timing, and the pop curve `scale(.55) → 1.14 at 60% → 1` on
  `cubic-bezier(.2,1.5,.4,1)`.
- Layout: the header, the flex spacer that pushes controls into thumb reach, the control row.
- The overlays: start, paused, game over.
- **The control scheme** (D11), *except* for what ▼ does: drag-anywhere as primary, arrows
  secondary, no preview, no hold.

## Where the handoff is stale, and what wins instead

| Handoff says | We do | Why |
|---|---|---|
| Hard drop "was tried and cut. Don't reintroduce it." ▼ is a two-tick accelerator | **▼ is a hard drop** (SPEC 6, D21) | Owner ruling from playing it on device, 2026-09-10. The expectation at that control is "send this tile to the bottom". The handoff governs interaction and it is overridden here anyway, which is worth stating plainly rather than leaving the packet to keep telling the next reader otherwise. Soft drop went with it: it was a *hold* of ▼, and the hold is what stranded a speed mode for the rest of a run. |
| N-way merges: 4 neighbours at once, `value × 2^N`, "three-way touch goes straight to ×4" | **Priority-order, first match only** (SPEC 4.3) | Owner ruling. The original design doc explicitly wanted "one 8 and one 4, no three-way merge". Changing it would redo C1, C1b and the whole balance pass. |
| No special blocks | **Wildcard, Bomb and Stone** (SPEC 5.2) | In v1 scope. The desktop file's bombs were cut from the prototype, not from the game. |
| `level = floor(merges / 10) + 1` | **Level advances every 20 blocks dropped** (SPEC 5.5) | Blocks dropped is a clock that skill does not accelerate. A merge-driven clock punishes good players with runaway speed. |
| `max(190, 850 - (level-1) * 65)` ms per row | **The SPEC 5.5 curve**, 500ms opening, floor 90 | Measured in C1c and D9. Three candidate curves gave identical outcomes; the opening is a pacing dial, not a difficulty one. |
| Row burst adds each tile's face value | **`5000 + 250 x blocksCleared`** (SPEC 7) | The burst is the game's release valve and has to dwarf flat play. |
| 5x7 board, "the intended shipping default" | **5x8** (SPEC 3) | Settled on a device in C3, measured. At five columns the cell size is set by the phone's *width*, so 7 rows drew a cell 3% larger and turned the rest into gutter, while costing a full row of danger-state warning (L25). |
| Game over when the spawn cell is occupied | **Row 0 occupied after a resolution completes** (SPEC 3.1) | Checking mid-resolution is the most common way this genre gets it wrong. |
| "No backend, no accounts, no network. Everything is local and synchronous." Best score only. | **Room stats, Daily Challenge, achievements, leaderboards, remote config, ads, Pro IAP** | Owner ruling. That line scopes the prototype, not the product. |
| Score `+= newValue × chainDepth` | SPEC 7's table | Broadly agrees on merges; the handoff omits burst, bomb, level-up and survival. |

## Things the handoff explicitly leaves open

Its own "Not designed yet" list: haptics, sound design, app icon, launch screen, settings, and any
leaderboard or share flow. **Ask before inventing** in those areas — that is the handoff's
instruction and it is a good one.

Note that haptics and sound are exactly where SPEC 9 and 21 place the game's most important feel
decision (the cascade-step pitched merge). The handoff not covering it does not make it optional;
it makes it ours.

## If the handoff is ever updated

Re-read this page against the new version. The most likely place for a genuine change to hide is
the gameplay sections, precisely because they are the ones currently being ignored — a real
gameplay decision arriving in an updated handoff would look identical to the stale text we are
already discounting.
