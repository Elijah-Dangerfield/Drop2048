package com.dangerfield.drop2048.libraries.drop2048

/**
 * The app's crash-reporting and feedback seam.
 *
 * **There is deliberately no `setUser`, no `email` and no `screenshots` here.**
 * All three existed as template scaffolding with no caller, which is a worse
 * state than it sounds: a one-line call is the natural thing to write the day
 * someone adds a contact field, and nothing would fail. §1 of
 * `docs/store/data-safety.md` claims no identity of a person reaches Sentry;
 * the absence of these three is what makes that a property rather than a habit,
 * and `NoIdentitySeamsTest` fails the build if any of them comes back.
 */
interface Telemetry {
    fun initialize()

    /**
     * Records the user's current navigation route on the crash-reporting
     * scope as both a searchable tag and a visible extra (`route`). Set it
     * eagerly on every navigation, NOT at event time.
     *
     * Why on the scope rather than in a `beforeSend` hook: the SDK persists
     * the scope to disk the moment it changes, and native crashes are turned
     * into events on the *next launch* using that persisted scope. So a route
     * written here is frozen at the instant the user was on it — a crash on
     * this route carries this route, even though it's transmitted later. A
     * `beforeSend` callback, by contrast, runs at next-launch for crashes and
     * would read a stale/empty route.
     *
     * The value sticks until the next navigation overwrites it, and any single
     * event may override it by setting its own `route` tag (a per-event local
     * scope wins over this global one). Best-effort: a no-op when crash
     * reporting is disabled.
     */
    fun setCurrentRoute(route: String)

    /**
     * Records the current app session's correlation id on the crash-
     * reporting scope as a searchable `session_id` tag. Set it whenever the
     * session rolls (cold boot, background-rollover) so every subsequent
     * event — including user feedback — carries the id. The same value is
     * sent to the backend via `X-Session-Id`, so one id pulls a session's
     * frontend events and backend traces/logs together. Best-effort: a
     * no-op when crash reporting is disabled.
     */
    fun setSession(sessionId: String)

    /**
     * Records the install id on the crash-reporting scope as an `install_id`
     * tag — stable across sessions, useful for "all of this tester's
     * sessions." Best-effort; no-op when crash reporting is disabled.
     */
    fun setInstallId(installId: String)

    /**
     * Records an app-specific context value as a searchable [key] tag on the
     * crash-reporting scope, or clears it when [value] is null/blank. Use for
     * domain state worth pivoting on at triage time (the active document id,
     * the current lobby code, an experiment bucket) — the value sticks until
     * overwritten or cleared, so set it on entry and clear it on exit. If the
     * same key exists on backend spans/logs, use identical naming so one
     * query string works across Sentry, Tempo, and Loki. Best-effort; no-op
     * when crash reporting is disabled.
     */
    fun setContext(key: String, value: String?)

    /**
     * Sends what the player typed, and only what the player agreed to send with
     * it.
     *
     * [attachSessionLog] is `PlayerSettings.diagnosticsOptIn`, off by default.
     * `settings_diagnostics_hint` is the copy the player read before deciding:
     * *"Attaches your device model and build number. Never your board, your
     * scores or anything you typed elsewhere."* Two things follow, and both are
     * the implementation's job rather than the caller's:
     *
     * 1. The session log rides only when this is `true`. It used to ride on
     *    every report, gated by nothing.
     * 2. The carrier event carries no breadcrumbs. Breadcrumbs are Info-and-above
     *    in release, `logEvent` is Info, and a breadcrumb carries the event's
     *    attributes — so `run.end` would have put `score`, `level` and
     *    `highest_tier` on every feedback report ever filed. That is "your
     *    scores" in the plainest sense, and it was arriving whether or not the
     *    switch was on.
     *
     * The session log itself holds log **messages**, never event attributes;
     * `SentryLogTreeTest` pins that, because it is the other half of the same
     * promise.
     *
     * Defaulting to `false` is the point: a caller that forgets the parameter
     * sends less, not more.
     *
     * ### The owner's own channel is the one exception, and it is decided by kind
     *
     * [FeedbackKind.OwnerDirective] attaches the session log and keeps its
     * breadcrumbs whatever [attachSessionLog] says. That is not a hole in the
     * two rules above — it is the recognition that they are promises made to a
     * *player*, and a directive is the owner writing about their own install
     * from a build no player can run. Deciding it by [kind] rather than by a
     * second flag beside it means there is no combination of arguments that
     * sends a player's board to Sentry: a caller cannot ask for the owner's
     * treatment, only to be the owner. See [FeedbackKind.isOwnerChannel].
     *
     * [screenshots] are frames the reporter attached deliberately. Only the
     * directive panel supplies any; the player-facing screens pass none, and the
     * capture code that would produce one is not in a release binary at all.
     */
    fun captureUserFeedback(
        message: String,
        kind: FeedbackKind,
        eventId: String?,
        errorCode: Int?,
        attachSessionLog: Boolean = false,
        screenshots: List<ByteArray> = emptyList(),
    )
}
