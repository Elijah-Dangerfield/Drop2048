package com.dangerfield.drop2048.libraries.devfeedback

import androidx.compose.runtime.Composable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The seam through which a build may contain the owner's directive channel: a
 * small button floating over the whole app, and the form it opens.
 *
 * ### Why this is a seam and not a flag
 *
 * The channel is developer machinery. It records every frame of the app into a
 * graphics layer so a screenshot needs no platform API and no permission, it
 * draws a button on top of the game, and it uploads the session log
 * unconditionally because the only person who can reach it is the person who
 * wrote the app. None of that has any business being compiled into a player's
 * binary, and `if (isTesterBuild)` around it is a runtime answer to a question
 * the build can answer.
 *
 * So the shape is `HouseAds`', for the same reasons and with the same three
 * layers (L78). [NoDevFeedback] is the only binding that exists unless
 * `:libraries:devfeedback:tester` is on the compilation's classpath. On Android
 * that module is a `debugImplementation` of `:apps:compose`, so a release APK
 * contains no button, no panel, no layer recording and no JPEG encoder — there
 * is nothing to switch on. `verifyNoDevFeedbackInRelease` in
 * `apps/compose/build.gradle.kts` is what stops that being a comment.
 *
 * iOS has no per-build-type source set to hang it on, so the tester module is
 * linked into every iOS binary and the host additionally refuses to draw
 * anything unless `BuildInfo.isTesterBuild`. That is the weakest of the three
 * layers and it is also the one that makes TestFlight work at all, which is the
 * trade: a TestFlight build *is* a release binary, and holding the channel out
 * of every release binary would mean the owner could only file directives from a
 * cable.
 *
 * ### [Host] is a composable on the interface
 *
 * So that `App` can wrap the entire tree without naming the module that
 * implements it. An overlay in `apps/compose` keyed on a state flow would put
 * the drawing code back into a source set that compiles into the release
 * binary, which is exactly the property this design is buying. It is a wrapper
 * rather than a sibling because the panel has to cover dialogs and sheets too —
 * the screen a report is about is often one of those.
 */
interface DevFeedback {

    /**
     * Wraps the whole app. Draws [content] and, in a tester build, the floating
     * button and the panel over the top of it.
     */
    @Composable
    fun Host(content: @Composable () -> Unit)
}

/**
 * The answer in a build with no directive channel, which is every Android
 * release build.
 *
 * It draws the app and nothing else — not a `Box` around it, because a wrapper
 * layout node in a player's build would be a measurable cost paid for a button
 * that does not exist.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NoDevFeedback : DevFeedback {

    @Composable
    override fun Host(content: @Composable () -> Unit) = content()
}
