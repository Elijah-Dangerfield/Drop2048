package com.dangerfield.drop2048.libraries.drop2048

/**
 * Who wrote a report and what it is for. Rides to Sentry as the `feedback_kind`
 * tag, which is the one thing the `feedback-triage` skill filters on.
 *
 * It is a tag rather than a message prefix because the carrier event's message
 * is also its issue title: titles are grouped, re-summarised by Sentry's own AI,
 * and edited by whoever next touches the copy. A tag is indexed, is queried with
 * `feedback_kind:owner_directive`, and is unaffected by grouping. Each report is
 * already fingerprinted into its own issue, and the tag rides every event in it.
 *
 * It replaced an `isBugReport: Boolean`, which could describe two channels and
 * this app has three. The third is not a third kind of player: it is the owner,
 * and [OwnerDirective] is the only value on which the privacy rules below
 * change.
 */
enum class FeedbackKind(val tag: String, val carrierMessage: String) {

    /** A player said something nice or had an idea. Read, not actioned. */
    Feedback(tag = "feedback", carrierMessage = "User feedback"),

    /** A player reported something broken. Actioned after diagnosis. */
    BugReport(tag = "bug_report", carrierMessage = "Bug report"),

    /**
     * The owner, from a build only the people making the app can run, asking for
     * a change. Treated as an instruction rather than a data point: triage files
     * it on the TODO list without asking whether it is worth doing.
     */
    OwnerDirective(tag = "owner_directive", carrierMessage = "Owner directive"),
    ;

    /**
     * Whether this report was written by the owner about their own install.
     *
     * The one thing in the app that decides what a report is allowed to carry,
     * and it is a property of the **kind** rather than a second boolean beside
     * it, because those two could disagree. C13a's finding (L74) was that the
     * session log and the breadcrumbs beneath it were leaking a player's scores
     * out from under the switch that was supposed to govern them; the fix was to
     * gate both on the player's opt-in. An owner directive wants that evidence
     * unconditionally, and the honest way to say so is that there is no player
     * here to keep a promise to — not to loosen the promise.
     *
     * See `Telemetry.captureUserFeedback` for what follows from this, and
     * `FeedbackKindPolicyTest` for the assertion that the player kinds are
     * unaffected.
     */
    val isOwnerChannel: Boolean get() = this == OwnerDirective
}
