package com.dangerfield.drop2048.server.data

import com.dangerfield.drop2048.server.db.Database
import com.dangerfield.drop2048.server.db.PlayerReportsTable
import com.dangerfield.drop2048.server.db.toJavaInstant
import com.dangerfield.drop2048.server.di.ServerScope
import com.dangerfield.drop2048.server.domain.PlayerReportRepository
import com.dangerfield.drop2048.server.domain.UserId
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.sql.insert
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Exposed-backed [PlayerReportRepository]. Append-only inserts into
 * `player_reports`; every read is deferred to the future moderation-review
 * surface (see [PlayerReportsTable]).
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
@OptIn(ExperimentalTime::class)
class PostgresPlayerReportRepository(
    private val database: Database,
    private val clock: Clock,
) : PlayerReportRepository {

    override suspend fun record(
        reporter: UserId,
        reported: UserId,
        context: String?,
        reason: String?,
        categories: List<String>,
    ) {
        val now = clock.now().toJavaInstant()
        database.transaction {
            PlayerReportsTable.insert {
                it[reporterUserId] = reporter.value
                it[reportedUserId] = reported.value
                it[PlayerReportsTable.context] = context
                it[PlayerReportsTable.reason] = reason
                it[reasonCategories] = categories.joinToString(",").takeIf { s -> s.isNotEmpty() }
                it[createdAt] = now
            }
        }
    }
}
