package com.dangerfield.drop2048.server.db

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.selectAll
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Schema smoke test: proves Flyway created the table *and* that the Exposed
 * [AppConfigValuesTable] projection matches the real columns (the `selectAll()`
 * references every declared column, so a drifted column name fails here). Add a
 * line per new table.
 *
 * It asserts on V4's seeded rows rather than on emptiness. The original version
 * asserted the table was empty, which V4's own seed had already made false; it
 * never failed because Docker was down for the whole project and every
 * Testcontainers test self-skipped. C7 was the first run with a daemon.
 */
class DatabaseSchemaTest : DatabaseTest() {

    @Test
    fun migrationsCreateAppConfigTable() = runTest {
        val paths = database.transaction {
            AppConfigValuesTable.selectAll().map { it[AppConfigValuesTable.path] }
        }

        assertTrue("upgrade.maintenanceMode" in paths, "V4's seeded kill-switch rows are present")
    }
}
