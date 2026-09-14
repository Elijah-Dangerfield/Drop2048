---
name: feedback-triage
description: Turn in-app feedback into TODO items. Scans Sentry for reports filed from the app's directive panel and the player-facing feedback and bug-report screens, reads their log and screenshot attachments, and either files an item in docs/todos.md or resolves the report as no-action. Use when asked to "triage feedback", "process feedback", "check what I filed", or on a schedule.
---

# Feedback triage

Drop 2048 has three ways a report reaches Sentry: the owner's floating directive
button (tester builds only), the player's feedback page in Settings, and the bug
reporter behind a crash. This routine reads them and turns the actionable ones
into `docs/todos.md` items a worker session can pick up later.

## Fixed coordinates

| Thing | Value |
| --- | --- |
| Sentry search tag | `feedback_kind` |
| Owner directives | `feedback_kind:owner_directive` |
| Player bug reports | `feedback_kind:bug_report` |
| Player feedback | `feedback_kind:feedback` |
| TODO queue | `docs/todos.md` |
| Ledger | `docs/feedback-log.md` |
| Enum that defines the tag values | `libraries/drop2048/src/commonMain/kotlin/com/dangerfield/drop2048/libraries/FeedbackKind.kt` |

Use the Sentry MCP (`search_issues`, `search_events`, `get_sentry_resource`). No
auth token or `curl` needed.

## How a report is shaped in Sentry

Submitting feedback produces **two linked records**, and you usually need both:

1. **The carrier event.** A `captureMessage` whose title is `Owner directive`,
   `Bug report` or `User feedback`, at `INFO` level so it does not sort with the
   crashes. It carries the tags (`feedback_kind`, `diagnostics_opt_in`,
   `session_id`, `install_id`, `route`, `commit_sha`, `commit_branch`), the
   `feedback_message` extra, and the attachments: `feedback.txt`, sometimes
   `session-log.txt`, and up to three `screenshot-N.jpg`.
2. **The feedback twin.** Sentry's own user-feedback record, linked by
   `associated_event_id`. Same words, plus the build line.

The message is deliberately on both. The legacy User Feedback API is the only one
this SDK has, and where its comments render depends on the org's feedback
settings; the `feedback_message` extra and `feedback.txt` are shown on the issue
page unconditionally. **Read the carrier first** — if the twin is not reachable
from the MCP, nothing is lost.

Every carrier gets a unique fingerprint (`["feedback", <uuid>]`), so **one report
is one issue**.

## What each kind carries, and why they differ

This matters when you are reading a report and wondering where the evidence is.

- **`owner_directive`** always has `session-log.txt` and keeps its breadcrumbs.
  The owner is writing about their own install from a build no player can run, so
  there is no promise to keep.
- **`feedback` and `bug_report`** have a session log **only if
  `diagnostics_opt_in:true`**, and never have breadcrumbs. C13a made that so
  after finding that breadcrumbs were carrying `score`, `level` and
  `highest_tier` onto every report whatever the switch said (L74). A player's
  report with no log is not a broken report; it is the switch working.

So: do not ask a player to "turn on diagnostics and file it again" as a first
move. Read what is there. `docs/store/data-safety.md` is what the app promises
and this routine does not get to renegotiate it.

## Procedure

### 1. List unhandled reports

Query each kind separately, because Sentry's issue search has no boolean `OR`:

- `feedback_kind:owner_directive`
- `feedback_kind:bug_report`
- `feedback_kind:feedback`

Then **check every event id against `docs/feedback-log.md` and skip the ones
already there.** This is the step that keeps the routine idempotent: a fixed TODO
is *deleted* from the queue rather than ticked off, so a handled report leaves no
other trace. Without the ledger, the run after a fix lands re-files the same
report.

### 2. Read the report

Pull the carrier for the text and the context, the twin if you can get it.
Extract: the message, `feedback_kind`, `session_id`, `route`, `environment`,
`release`, `commit_sha`, the timestamp, and the attachments.

### 3. Classify before investigating

**`owner_directive` is an instruction, not a data point.** The owner filed it
from a tester build, deliberately, about their own app. Do not investigate
whether it is worth doing and do not weigh it against other priorities. Write the
TODO and move on. The whole point of the channel is that the round trip from
"this bothers me while playing" to "it is on the list" is one swipe.

**`bug_report` and `feedback` come from players.** Those get the full treatment
below. A player's product ask you decide not to file still gets a line in the
ledger saying so, with the reason. Never drop a player's ask silently.

### 4. Investigate (player reports only)

- **The attached log** is the highest-value artifact when it is there: the
  Debug-and-below detail never shipped as breadcrumbs, from the minutes before
  they hit send. Read it before anything else.
- **The screenshot** shows the board state they were describing, which on this
  game is usually a faster diagnosis than the words.
- **Correlate by `session_id`.** The same value is a Sentry tag and an OTLP
  attribute on client logs in Grafana Loki. In Loki, `session_id` is structured
  metadata, not a stream label: match it with `| session_id="<id>"`, never with a
  line filter `|= "<id>"`, which silently returns nothing.
- **Check `debug_session`.** `GrafanaLogTree` stamps it on every exported record
  from a process that opened the debug menu (L63). A report from such a session
  may describe a board that was forced into existence.
- **Absence of client events is weak evidence.** Telemetry buffers to disk when
  offline and can arrive days late.
- **Check `commit_sha` against `main` before filing.** Sodogku's first triage run
  found four of six directives had already been fixed before they were read. The
  carrier carries the commit for exactly this.

### 5. File it

Append a `### <title>` section under `## Now` in `docs/todos.md`, in the shape
the rest of that file uses: a sentence saying what is wrong, then what is known.
Drop 2048's queue has no ids — items are identified by their heading and
**deleted when they ship**.

Keep the reporter's own words where the wording carries intent. Put the Sentry
URL, the session id and the date on the last line so a worker can go back to the
source without you.

If there is nothing to do, say so in the ledger with a reason.

### 6. Close the loop

Append one line to `docs/feedback-log.md` for every report handled, whichever way
it went.

Then, in Sentry:

- **Filed a TODO?** Comment on the issue with the TODO's heading and leave it
  unresolved. It is not fixed yet. Resolving at triage time is how a dropped TODO
  disappears with no trace anywhere.
- **No action?** Resolve it.

### 7. Report

Say how many reports were seen, how many became TODOs, how many were no-action,
and anything that needs a human.

## Guardrails

- **Idempotent.** The ledger check in step 1 is not optional.
- **This routine writes two files and nothing else.** `docs/todos.md`,
  `docs/feedback-log.md`, plus Sentry comments and statuses. It does not change
  code.
- **It is the one exception to `ORCHESTRATION.md` rule 13.** That rule says a
  subagent must not edit `todos.md`, because the orchestrator owns it and edits
  it concurrently. This routine *is* the orchestrator doing that, so run it from
  the orchestrating session and not from a worker chunk, and do not run it
  alongside another session that is editing the queue.
- **One report, at most one TODO.** If a directive contains three asks, file the
  one it leads with and note the others in the body. Three items from one report
  become three half-remembered items.
- **Do not invent telemetry.** If the answer needs an event that is not emitted,
  say so and file adding the event as the TODO.
