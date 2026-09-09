package com.dangerfield.drop2048.libraries.cascade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SPEC 5.6's eight-deep ring. Undo is a full snapshot restore, so the only
 * things worth testing are the bound, the ordering, and the two refusals.
 */
class UndoRingTest {

    @Test
    fun theRingHoldsEightStatesAndDropsTheOldest() {
        var ring = UndoRing()
        val states = (1..12).map { stateOf(Board.empty(5, 8), score = it.toLong()) }
        states.forEach { ring = ring.recording(it) }

        assertEquals(UndoRing.DEFAULT_CAPACITY, ring.depth)
        assertEquals(12L, (ring.undo(states.first()) as UndoResult.Restored).state.score)
        assertTrue(ring.states.none { it.score <= 4 }, "the first four fell off the back")
    }

    @Test
    fun undoingWalksBackwardsOneDropAtATime() {
        var ring = UndoRing()
        val states = (1..3).map { stateOf(Board.empty(5, 8), score = it.toLong()) }
        states.forEach { ring = ring.recording(it) }

        val scores = mutableListOf<Long>()
        var current = stateOf(Board.empty(5, 8), score = 99)
        repeat(3) {
            val result = ring.undo(current) as UndoResult.Restored
            scores += result.state.score
            current = result.state
            ring = result.ring
        }

        assertEquals(listOf(3L, 2L, 1L), scores)
        assertEquals(UndoResult.Refused(UndoRefusal.EMPTY), ring.undo(current))
    }

    @Test
    fun anEmptyRingRefusesRatherThanThrowing() {
        assertEquals(
            UndoResult.Refused(UndoRefusal.EMPTY),
            UndoRing().undo(stateOf(Board.empty(5, 8))),
        )
    }
}
