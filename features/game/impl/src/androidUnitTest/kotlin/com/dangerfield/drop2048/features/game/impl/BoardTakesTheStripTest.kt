package com.dangerfield.drop2048.features.game.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.features.game.impl.screenshot.ROBOLECTRIC_SDK
import com.dangerfield.drop2048.features.settings.ControlScheme
import com.dangerfield.drop2048.libraries.ads.BannerSurface
import com.dangerfield.drop2048.libraries.ads.LocalBannerSurface
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.FallingBlock
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.system.FocusRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The owner's two 2026-09-20 rulings about the space under the board, measured
 * rather than looked at.
 *
 * **"It should have a GONE behaviour, not INVISIBLE. Expand the board."** The
 * arrow row already disappeared under `ControlScheme.Drag` and the board already
 * grew into the height that freed, but the board was top-aligned under a flex
 * spacer, so everything it could not use became a band of backdrop at the
 * bottom. [theBoardIsCentredInTheSpaceItIsGiven] states the fix as an identity:
 * give the screen 80dp more height and a centred board moves down by 40; a
 * top-aligned one does not move at all.
 *
 * **"If the banner fails to load, the space goes back to the board."** That is
 * the test this file exists for, and the one most likely to rot, because every
 * future edit to the banner is a chance to reserve 50dp "so nothing moves". The
 * banner can be absent for six reasons and
 * [everyReasonTheBannerIsAbsentGivesTheBoardTheSameSpace] asserts they produce
 * the **same rectangle** as a build with no banner code in it at all. It is one
 * comparison rather than six expectations on purpose: a regression that reserved
 * the strip would move all six together, and six independent tests pinned to six
 * numbers would all still pass.
 *
 * Bounds come from [FocusRegistry], which the board already reports itself to
 * for the tutorial spotlight. Nothing was added to the screen for this: a layout
 * rule should be measurable from what the layout already publishes.
 *
 * Everything is measured through **one** composition that is re-driven between
 * readings, because `setContent` may be called once per rule. That is also
 * closer to the real failure: the banner arriving or failing is a recomposition
 * of a screen that is already up, not a fresh launch.
 *
 * Not covered here: what the banner looks like, which is the goldens', and
 * whether AdMob ever calls back, which is the SDK's.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = TallPhone)
class BoardTakesTheStripTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theBoardTakesTheArrowRowsSpaceWhenThereIsNoArrowRow() {
        val harness = compose.harness()

        val withArrows = harness.measure(dragOnly().copy(controlScheme = ControlScheme.Both))
        val withoutArrows = harness.measure(dragOnly())

        assertTrue(
            withoutArrows.bottom > withArrows.bottom,
            "the board should reach lower with no arrow row: " +
                "${withoutArrows.bottom} vs ${withArrows.bottom}",
        )
        assertTrue(
            withoutArrows.height >= withArrows.height,
            "and it should be no smaller: ${withoutArrows.height} vs ${withArrows.height}",
        )
    }

    /**
     * `bannerAllowed = false` stands for `ads.enabled` off, `ads.banner.enabled`
     * off and Pro, because the ViewModel resolves all three into that one flag.
     * [NoBannerDrawn] stands for iOS and every build with no ad SDK bound.
     * [FailingBannerSurface] stands for a no-fill and for a network error.
     */
    @Test
    fun everyReasonTheBannerIsAbsentGivesTheBoardTheSameSpace() {
        val harness = compose.harness()

        val noBannerAtAll = harness.measure(dragOnly())
        val refusedByPolicy = harness.measure(dragOnly().copy(bannerAllowed = false))
        val allowedButUnbound = harness.measure(dragOnly().copy(bannerAllowed = true))
        val failedToLoad = harness.measure(
            state = dragOnly().copy(bannerAllowed = true),
            surface = FailingBannerSurface,
        )

        assertEquals(noBannerAtAll, refusedByPolicy, "ads off, banner key off, or Pro")
        assertEquals(noBannerAtAll, allowedButUnbound, "no surface bound, which is iOS")
        assertEquals(noBannerAtAll, failedToLoad, "no fill, or no network")
    }

    /**
     * The other direction, and what stops the test above being satisfied by a
     * banner that can never draw at all. An ad that really arrives takes the
     * strip and the board gives it up.
     */
    @Test
    fun aBannerThatLoadsTakesTheStripBack() {
        val harness = compose.harness()

        val absent = harness.measure(dragOnly().copy(bannerAllowed = true))
        val filled = harness.measure(
            state = dragOnly().copy(bannerAllowed = true),
            surface = FilledBannerSurface,
        )

        assertTrue(
            filled.bottom < absent.bottom,
            "a filled banner pushes the board up: ${filled.bottom} vs ${absent.bottom}",
        )
    }

    /**
     * The arrow row is still the arrow row. A banner may only ever occupy the
     * strip the row is not using, so a player who switched the arrows on must
     * get the identical layout whatever the banner is doing.
     */
    @Test
    fun theBannerNeverAppearsWhenTheArrowsAreOn() {
        val harness = compose.harness()

        val arrows = dragOnly().copy(controlScheme = ControlScheme.Both, bannerAllowed = true)
        val withoutASurface = harness.measure(arrows)
        val withAFilledOne = harness.measure(state = arrows, surface = FilledBannerSurface)

        assertEquals(
            withoutASurface,
            withAFilledOne,
            "an ad that filled must not move a board that has an arrow row under it",
        )
    }

    /**
     * A 5x8 board is 0.625 wide per tall and a phone is nearer 0.45, so the
     * board is width-bound on every phone and there is always height left over.
     * The question was never whether to have leftover space, only where to put
     * it, and the answer is half above and half below.
     *
     * Asserted as a *difference* between two frame heights, so it says nothing
     * about the header or the root padding and cannot rot when either moves.
     */
    @Test
    fun theBoardIsCentredInTheSpaceItIsGiven() {
        val harness = compose.harness()

        val short = harness.measure(dragOnly(), height = ShortFrame)
        val tall = harness.measure(dragOnly(), height = ShortFrame + Extra)

        assertEquals(
            short.height,
            tall.height,
            "width-bound at both heights, so only the board's position may move",
        )

        val expected = with(compose.density) { (Extra / 2).toPx() }
        assertTrue(
            abs((tall.top - short.top) - expected) <= 1f,
            "the board should take half the new height above it: moved ${tall.top - short.top}, " +
                "expected $expected. Zero means it is top-aligned again.",
        )
    }

    private fun dragOnly() = GameUiState(
        controlScheme = ControlScheme.Drag,
        board = Board.empty(Cols, Rows).with(Cell(0, Rows - 1), NumberBlock(BlockValue.V32)),
        falling = FallingBlock(NumberBlock(BlockValue.V4), Cell(2, 2)),
        phase = GamePhase.Playing,
        score = 4_896,
        best = 130_450,
        level = 7,
        levelFraction = 0.4f,
    )
}

/**
 * One composition of the game screen, re-driven between readings.
 *
 * The state, the banner surface and the frame height are all snapshot state, so
 * a measurement is a write plus a `waitForIdle`. Reading [FocusRegistry] after
 * that gives the board's rectangle in root coordinates.
 */
private class StripHarness(private val compose: ComposeContentTestRule) {
    private val registry = FocusRegistry()
    private var state by mutableStateOf(GameUiState())
    private var surface by mutableStateOf<BannerSurface>(NoBannerDrawn)
    private var height by mutableStateOf(TallFrame)

    fun start() {
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalBannerSurface provides surface,
            ) {
                PreviewContent {
                    Box(modifier = Modifier.width(FrameWidth).height(height)) {
                        GameScreen(state = state, onAction = {}, focusRegistry = registry)
                    }
                }
            }
        }
    }

    fun measure(
        state: GameUiState,
        surface: BannerSurface = NoBannerDrawn,
        height: Dp = TallFrame,
    ): Rect {
        compose.runOnUiThread {
            this.state = state
            this.surface = surface
            this.height = height
        }
        compose.waitForIdle()
        return requireNotNull(registry.boundsOf(BoardFocusKey)) {
            "the board never reported a position; the focus target is what this measures"
        }
    }
}

private fun ComposeContentTestRule.harness(): StripHarness =
    StripHarness(this).also { it.start() }

/** iOS, and every build with no ad SDK: nothing is bound, so nothing is drawn. */
private object NoBannerDrawn : BannerSurface {
    @Composable
    override fun Banner(onFilled: (Boolean) -> Unit, modifier: Modifier) = Unit
}

/** A no-fill or a network error: the SDK answers, and the answer is no. */
private object FailingBannerSurface : BannerSurface {
    @Composable
    override fun Banner(onFilled: (Boolean) -> Unit, modifier: Modifier) {
        LaunchedEffect(Unit) { onFilled(false) }
    }
}

/** An ad really arrived, at the height an adaptive AdMob banner asks for. */
private object FilledBannerSurface : BannerSurface {
    @Composable
    override fun Banner(onFilled: (Boolean) -> Unit, modifier: Modifier) {
        LaunchedEffect(Unit) { onFilled(true) }
        Box(modifier = modifier.fillMaxWidth().height(BannerHeight))
    }
}

private val BannerHeight = 50.dp
private val FrameWidth = 412.dp
private val TallFrame = 860.dp
private val ShortFrame = 740.dp
private val Extra = 80.dp

private const val Cols = 5
private const val Rows = 8

/**
 * A tall phone, which is the frame the owner is describing and the one this
 * module's goldens deliberately are not: they capture a 360x640 short phone,
 * where the leftover height under the board is 20dp rather than 130.
 */
internal const val TallPhone = "w412dp-h915dp-xhdpi"
