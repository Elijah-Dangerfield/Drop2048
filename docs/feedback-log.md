# Feedback ledger

One line per report the `feedback-triage` skill has handled, newest at the
bottom. Append only.

This file exists for exactly one reason: **triage must be idempotent.**
`docs/todos.md` is not a record of what has been seen, because items are deleted
when they ship rather than ticked off. Without a ledger, the run after a fix
lands re-reads the same Sentry issue, finds no matching TODO, and files it again.

Line format:

```
- <date> · <sentry event id> · <feedback_kind> · <disposition> · <sentry url>
```

`<disposition>` is either `todo: <the heading filed in docs/todos.md>` or
`no-action: <one-line reason>`.

Run notes for passes that found nothing go in an HTML comment, so the file stays
a list of reports while still recording what the routine learned about the
tooling.

## Entries

<!--
Nothing yet. The channel landed in C16 and the app has no Sentry DSN configured,
so no report has left a device.

C16 filed one real directive from a debug build on the emulator to prove the path
end to end. `Telemetry.captureUserFeedback` logged
`Sentry disabled, feedback dropped` and returned, which is the correct behaviour
with no DSN and is as far as it can get here. See the chunk's report and
`docs/OWNER-TODO.md` — the DSN is an owner item.
-->
