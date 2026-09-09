package com.dangerfield.drop2048.libraries.cascade

/**
 * A cell allowed to initiate a merge this step, and the direction the player
 * last pushed it if it is the block that just landed.
 */
internal data class Seed(val cell: Cell, val lastDirection: Direction?)

internal data class Resolution(
    val board: Board,
    val steps: List<ResolutionStep>,
    val fault: Fault?,
    val stepsTaken: Int,
)

/**
 * SPEC 4.3's resolution loop.
 *
 * Per cascade step: merge phase, then burst phase, then gravity. The next
 * step's queue is the newly merged blocks plus everything gravity moved.
 * Terminate when the queue is empty.
 *
 * **Only a block that just moved or was just created by a merge may initiate.**
 * That is the rule that keeps the board legible — the player is the cause of
 * every change — and it is enforced by the queue existing at all. A block that
 * is not in the queue is never even looked at. There is exactly one exception,
 * and SPEC 5.2 writes it out: a Wildcard resting inert is re-evaluated whenever
 * an adjacent cell changes.
 */
internal object Resolver {

    fun resolve(board: Board, seeds: List<Seed>, config: EngineConfig): Resolution {
        var current = board
        var queue = seeds.distinctBy { it.cell }
        val steps = mutableListOf<ResolutionStep>()
        var step = 1
        var fault: Fault? = null

        while (queue.isNotEmpty()) {
            if (step > config.cascadeStepCap) {
                fault = Fault.CASCADE_STEP_CAP_EXCEEDED
                break
            }
            val phase = runStep(current, queue, step, config)
            current = phase.board
            steps += phase.steps
            queue = phase.next
            step++
        }

        return Resolution(current, steps, fault, stepsTaken = step - 1)
    }

    private class Phase(
        val board: Board,
        val steps: List<ResolutionStep>,
        val next: List<Seed>,
    )

    private fun runStep(board: Board, queue: List<Seed>, step: Int, config: EngineConfig): Phase {
        val multiplier = if (step < config.cascadeMultiplierCap) step else config.cascadeMultiplierCap
        val steps = mutableListOf<ResolutionStep>()
        val changed = mutableSetOf<Cell>()
        val created = mutableListOf<Cell>()
        var current = board

        val ordered = queue.sortedWith(
            compareByDescending<Seed> { it.cell.row }.thenBy { it.cell.col }
        )

        for (seed in ordered) {
            val block = current[seed.cell] ?: continue
            if (block.specialKind == Special.BOMB) {
                val detonation = detonate(current, seed.cell, step, config)
                current = detonation.board
                steps += detonation.step
                changed += detonation.step.destroyed
                changed += seed.cell
                continue
            }
            val merge = merge(current, seed, step, multiplier) ?: continue
            current = merge.board
            steps += merge.step
            changed += merge.step.initiator
            changed += merge.step.partner
            changed += merge.step.into
            created += merge.step.into
        }

        val burst = burst(current, step, config)
        current = burst.board
        steps += burst.steps
        changed += burst.cleared

        val settled = settle(current)
        val moveMap = settled.moves.associate { it.from to it.to }
        val survivingCreated = created.filter { current[it] != null }.map { moveMap[it] ?: it }
        current = settled.board
        if (settled.moves.isNotEmpty()) {
            steps += ResolutionStep.Gravity(step, settled.moves)
            settled.moves.forEach {
                changed += it.from
                changed += it.to
            }
        }

        val next = if (changed.isEmpty()) {
            emptyList()
        } else {
            nextQueue(current, survivingCreated, settled.moves, changed)
        }
        return Phase(current, steps, next)
    }

    private fun nextQueue(
        board: Board,
        created: List<Cell>,
        moves: List<BlockMove>,
        changed: Set<Cell>,
    ): List<Seed> {
        val movers = moves.map { it.to }
        val reactiveWildcards = board.occupiedCells.filter { cell ->
            board[cell]?.specialKind == Special.WILDCARD &&
                board.neighborsOf(cell).any { it in changed }
        }
        return (created + movers + reactiveWildcards)
            .filter { board[it] != null }
            .distinct()
            .map { Seed(it, lastDirection = null) }
    }

    private class MergeOutcome(val board: Board, val step: ResolutionStep.Merge)

    /**
     * SPEC 4.3's priority order, first match only, never up: down, then the last
     * input direction this drop, then left, then right. Blocks that gravity moved
     * have no last input direction, so their order collapses to down, left, right.
     *
     * **The merged block lands in the partner's cell, in every orientation.** A
     * vertical merge's "lower cell" *is* the partner's, because the initiator
     * falls onto the partner from above, so one rule covers both and there is one
     * code path rather than a branch on direction. Stating it that way is not
     * cosmetic: it makes a horizontal merge pull the result toward the match,
     * which is the thing that lines the new block up over what is underneath the
     * partner and lets a chain continue. SPEC 21 says chains are what the game is
     * for, and the spec's own worked example (a 4 landing between two 4s with an 8
     * below the left one, cascading to a 16) only reaches its 16 under this rule.
     */
    private fun merge(board: Board, seed: Seed, step: Int, multiplier: Int): MergeOutcome? {
        val initiator = board[seed.cell] ?: return null
        if (initiator.numberValue == null && initiator.specialKind != Special.WILDCARD) return null

        val order = buildList {
            add(Direction.DOWN)
            seed.lastDirection?.let { add(it) }
            add(Direction.LEFT)
            add(Direction.RIGHT)
        }.distinct()

        for (direction in order) {
            val target = seed.cell + direction
            val partner = board[target] ?: continue
            val outcome = combine(initiator, partner) ?: continue
            val next = board
                .with(seed.cell, null)
                .with(target, NumberBlock(outcome.first))
            return MergeOutcome(
                board = next,
                step = ResolutionStep.Merge(
                    step = step,
                    initiator = seed.cell,
                    partner = target,
                    into = target,
                    result = outcome.first,
                    kind = outcome.second,
                    points = outcome.first.points * multiplier,
                ),
            )
        }
        return null
    }

    /**
     * What two adjacent blocks produce, or null if they do not combine.
     *
     * A Wildcard takes its partner's value doubled and is eligible against value
     * blocks only, so it skips Stones (SPEC 18.3) and other Wildcards
     * (SPEC 18.2).
     *
     * **The relationship is symmetric** — a value block landing beside a resting
     * Wildcard merges with it too — and SPEC 5.2 says so. The reason it has to:
     * SPEC 5.2 explicitly lets a Wildcard rest inert when it lands with no
     * eligible neighbour. Without symmetry that inert Wildcard can never be
     * consumed again, so it becomes a permanent obstacle that a player reads as a
     * bug, because the obvious move — drop a value block next to it — does
     * nothing. "A Wildcard next to a 1024 makes a 2048" is a property of the pair
     * as a player sees it, not of which of the two moved last.
     */
    private fun combine(initiator: Block, partner: Block): Pair<BlockValue, MergeKind>? {
        val initiatorValue = initiator.numberValue
        val partnerValue = partner.numberValue
        return when {
            initiatorValue != null && partnerValue != null ->
                if (initiatorValue == partnerValue) {
                    initiatorValue.doubled?.let { it to MergeKind.VALUE }
                } else {
                    null
                }

            initiatorValue != null && partner.specialKind == Special.WILDCARD ->
                initiatorValue.doubled?.let { it to MergeKind.WILDCARD }

            initiator.specialKind == Special.WILDCARD && partnerValue != null ->
                partnerValue.doubled?.let { it to MergeKind.WILDCARD }

            else -> null
        }
    }

    private class DetonationOutcome(val board: Board, val step: ResolutionStep.Detonation)

    /**
     * SPEC 5.2: a Bomb destroys itself and its four orthogonal neighbours, then
     * resolution continues normally. The bomb's own cell is not in [destroyed],
     * which is what makes SPEC 18.4's "no neighbours, no score" fall out rather
     * than needing a special case.
     */
    private fun detonate(board: Board, cell: Cell, step: Int, config: EngineConfig): DetonationOutcome {
        val destroyed = board.neighborsOf(cell).filter { board[it] != null }
        val cleared = (destroyed + cell).associateWith { null }
        return DetonationOutcome(
            board = board.withAll(cleared),
            step = ResolutionStep.Detonation(
                step = step,
                bomb = cell,
                destroyed = destroyed,
                points = config.scoring.bombPerBlock * destroyed.size,
            ),
        )
    }

    private class BurstOutcome(
        val board: Board,
        val steps: List<ResolutionStep.Burst>,
        val cleared: List<Cell>,
    )

    /**
     * 2048 is terminal and cannot exist at rest, so any row holding one bursts
     * (SPEC 5.1). Two 2048s in different rows burst both rows and award both
     * bonuses; two in the same row burst it once (SPEC 18.6). Stones go with it
     * (SPEC 18.5) — without that, a Stone-heavy late game is unwinnable.
     *
     * The burst bonus is flat, not multiplied by the cascade step. The step
     * multiplier itself does not reset across a burst (SPEC 7).
     */
    private fun burst(board: Board, step: Int, config: EngineConfig): BurstOutcome {
        val rows = (0 until board.rows).filter { row ->
            board.rowCells(row).any { board[it]?.numberValue?.isTerminal == true }
        }
        if (rows.isEmpty()) return BurstOutcome(board, emptyList(), emptyList())

        var current = board
        val steps = mutableListOf<ResolutionStep.Burst>()
        val allCleared = mutableListOf<Cell>()
        rows.forEach { row ->
            val cleared = current.rowCells(row).filter { current[it] != null }
            current = current.withAll(cleared.associateWith { null })
            allCleared += cleared
            steps += ResolutionStep.Burst(
                step = step,
                row = row,
                cleared = cleared,
                points = config.scoring.burstBase + config.scoring.burstPerBlock * cleared.size,
            )
        }
        return BurstOutcome(current, steps, allCleared)
    }

    private class SettleOutcome(val board: Board, val moves: List<BlockMove>)

    /** Every block falls to the lowest empty cell in its column. */
    private fun settle(board: Board): SettleOutcome {
        val moves = mutableListOf<BlockMove>()
        val changes = mutableMapOf<Cell, Block?>()

        for (col in 0 until board.cols) {
            var write = board.rows - 1
            for (row in board.rows - 1 downTo 0) {
                val block = board[col, row] ?: continue
                if (row != write) {
                    val from = Cell(col, row)
                    val to = Cell(col, write)
                    moves += BlockMove(from, to)
                    changes[from] = null
                    changes[to] = block
                }
                write--
            }
        }
        if (moves.isEmpty()) return SettleOutcome(board, emptyList())
        return SettleOutcome(board.withAll(changes), moves)
    }
}
