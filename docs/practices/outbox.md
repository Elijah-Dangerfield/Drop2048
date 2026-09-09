# The outbox pattern (offline writes that must not be lost)

The template shipped a worked example of this pattern — the profile edit queue
in `:libraries:identity:impl` — which went with the identity stack in C0. This
doc is what survives it, so the first outbox this app needs can be built
without reverse-engineering one.

## When you need it

A user takes an action while offline (or session-less) that MUST eventually
reach the server: an edit, a purchase acknowledgment, a consumable grant.
Dropping it silently is a bug; blocking the UI on connectivity is worse. The
answer is an outbox: persist the intent locally, apply it optimistically,
flush when a session + connectivity exist.

## The shape

1. **A persisted event store.** One table (or `Cache`) of pending events.
   Fields: a client-generated **idempotency key** (UUID minted when the event
   is created — the server dedupes on it), the payload, and created-at. For a
   Room table, implement `ClearableDao` so a "reset progress" wipes it.
2. **Optimistic local apply.** The UI reflects the change immediately; the
   caller gets a `Queued` outcome and treats it as success.
3. **A flusher driven by the sync triggers.** Don't invent your own timing:
   hang off `SyncTriggers` (`warmForeground`, `cameOnline`) so the flush fires
   on warm resume and on reconnect. The
   flush must be **idempotent and re-entrant**: take a mutex, read all
   pending events, attempt each in order.
4. **Per-event reconciliation.** For each event: send with the idempotency
   key → on success delete it; on a *rejection* (4xx that means "the server
   will never accept this") delete it, revert the optimistic state, and emit
   a user-visible rejection signal; on a *transient* failure (offline, 5xx) stop —
   leave the rest queued for the next trigger.
5. **Coalescing (optional).** If newer events supersede older ones (a second
   rename), collapse them at enqueue time so the flush sends only the latest.

## The loop, distilled

Observe the sync triggers, take the repo's mutex, read the pending events, send
each with its idempotency key, and map the outcome — success → clear, a 4xx the
server will never accept → clear + revert + rejection event, network error →
keep queued.

## What the production app did that we deliberately did NOT port

The origin app's high-value outbox (an economy ledger) added per-event
server-side dedup tables and reconciliation-on-read. That machinery is
worth building only when events carry money-like weight. Start with the
shape above; graduate when an event's loss or double-apply would be
user-visible harm.
