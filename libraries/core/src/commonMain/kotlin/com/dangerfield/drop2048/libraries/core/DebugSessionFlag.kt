package com.dangerfield.drop2048.libraries.core

/**
 * Whether this process has had the debug menu opened in it (SPEC 19, L63).
 *
 * The latch itself belongs to `DebugController` in `:features:debug`, and
 * telemetry cannot see a feature. This is the library-side seam it is read
 * through, and it exists so the flag can be stamped **once**, on every record
 * `GrafanaLogTree` exports, rather than remembered at every `logEvent` call
 * site. A guard that has to be reapplied at forty call sites is a guard that
 * will be missing from the forty-first, and the failure is silent: QA data
 * arriving on a dashboard looks exactly like real data.
 *
 * Session-wide rather than per-run on purpose — a tester who has had their
 * hands on a seed switch is not a source of real data afterwards either. The
 * run-level gate that decides whether `run_record` is written is a separate,
 * narrower thing (`StartedRun.debug`), and `run.end` reports the two
 * independently so a missing row can be told apart from a lost one.
 */
interface DebugSessionFlag {
    val isDebugSession: Boolean
}

/**
 * The answer for every build and every test that has no debug menu in it.
 * Same move as `AlwaysReadyAuthGate` beside `AuthGate`: ship the inert answer
 * next to the seam so nothing hand-rolls a fake whose whole content is "no".
 */
object NotADebugSession : DebugSessionFlag {
    override val isDebugSession: Boolean = false
}
