package com.dangerfield.drop2048.libraries.achievements

import com.dangerfield.drop2048.libraries.cascade.Special
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The four facts that come out of the transcript rather than out of
 * `run_record`, read off transitions the shipped engine actually produced.
 *
 * Nothing here builds a `Transcript` by hand. A hand-built one would let the
 * fold agree with a transcript shape the resolver has never emitted, which is
 * how a badge comes to be counted against a step that does not exist.
 */
class RunFactsTest {

    @Test
    fun aDropThatChangesNothingRareLeavesEveryFactAlone() {
        val quiet = EngineWitness.dropInto(
            state = EngineWitness.stateOf(EngineWitness.boardOf(". . 2 . .")),
            block = EngineWitness.value(4),
            col = 0,
        )

        assertEquals(RunFacts.Empty, RunFacts.Empty.fold(quiet))
    }

    @Test
    fun aWildcardThatMakesA2048CountsOnceAndTheBurstItCausesIsNotAStoneBurst() {
        val facts = RunFacts.Empty.fold(
            EngineWitness.dropInto(
                state = EngineWitness.stateOf(EngineWitness.boardOf(". . 1024 . .")),
                block = EngineWitness.special(Special.WILDCARD),
                col = 2,
            )
        )

        assertEquals(1, facts.wildcardBursts)
        assertEquals(0, facts.stoneBursts)
        assertEquals(1, facts.boardsCleared)
    }

    /**
     * SPEC 15 asks for ten drops survived **in a row**, so the streak has to
     * reset the moment the board comes out of the red — otherwise a player who
     * touched row 1 on ten separate occasions across a long run would earn a
     * badge for something they never did.
     */
    @Test
    fun theDangerStreakCountsConsecutiveDropsAndResetsWhenTheBoardClears() {
        val danger = EngineWitness.stateOf(
            EngineWitness.boardOf(
                """
                2 . . . .
                4 . . . .
                2 . . . .
                4 . . . .
                2 . . . .
                4 . . . .
                2 . . . .
                """
            )
        )

        var facts = RunFacts.Empty
        var state = danger
        repeat(DangerDrops) {
            val col = 1 + it % SpareColumns
            val transition = EngineWitness.dropInto(state, EngineWitness.special(Special.STONE), col)
            facts = facts.fold(transition)
            state = transition.state
        }

        assertEquals(DangerDrops, facts.dangerDrops)
        assertEquals(DangerDrops, facts.longestDangerRun)

        val calm = EngineWitness.dropInto(
            state = EngineWitness.stateOf(EngineWitness.boardOf(". . 2 . .")),
            block = EngineWitness.value(4),
            col = 0,
        )
        val after = facts.fold(calm)

        assertEquals(0, after.dangerDrops, "the streak survived a drop out of danger")
        assertEquals(DangerDrops, after.longestDangerRun, "the high-water mark was lowered")
    }

    private companion object {
        /** SPEC 15's badge asks for ten; eleven proves it is not an off-by-one. */
        const val DangerDrops = 11

        /**
         * Columns 1 to 3, round-robin. Stones never merge, so eleven of them
         * into one column would fill it and end the run on the ninth drop —
         * which would be a true reading of a different situation.
         */
        const val SpareColumns = 3
    }
}
