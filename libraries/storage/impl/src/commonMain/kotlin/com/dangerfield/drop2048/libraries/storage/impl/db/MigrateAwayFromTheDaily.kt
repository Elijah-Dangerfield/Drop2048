package com.dangerfield.drop2048.libraries.storage.impl.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Schema 8 to 9: the Daily Challenge leaves the database (D27).
 *
 * Hand-written rather than an `AutoMigration`, and the reason is the first
 * statement. Room can generate a `@DeleteTable` and a `@DeleteColumn` on its
 * own, but it cannot express *delete the rows that column identified, before it
 * goes*. Dropping `run_record.mode` without that step would leave every Daily
 * run behind as an ordinary one, silently folded into lifetime totals and into
 * `MAX(score)` — and a Daily score was set on a shared seed and a pinned
 * `EngineConfig` (the old D18), so it is not the same quantity the best score
 * measures. The column is the only witness to which rows those are, so the
 * deletion has to happen while it still exists.
 *
 * The three tables, in the order they are dealt with:
 *
 * - **`daily_result`** is dropped outright. It held the ledger of days played,
 *   attempts spent and rewarded retries, and every question it could answer has
 *   been deleted along with it.
 * - **`run_record`** loses `mode` and its `'DAILY'` rows. The remaining rows keep
 *   their `id`, because `RunOutcome` and the stats page both read it.
 * - **`achievement_fact`** loses `mode` and `dailyStreakDays` and its `'DAILY'`
 *   rows, for the same reason: the fold replays this table, so a Daily run left
 *   in it would keep paying into `TotalMerges` and `MinutesPlayed` forever.
 *   Its `key` is rewritten too — the identity used to be `"${'$'}mode:${'$'}endedAt"`
 *   and is now the timestamp alone, and a table holding both spellings would
 *   have two shapes of the same unique constraint.
 *
 * SQLite cannot drop a column in place at the version Room targets, so both
 * tables are rebuilt the way Room's own generated migrations rebuild them: new
 * table, copy, drop, rename. The `CREATE TABLE` statements are the exported
 * schema 9's `createSql` verbatim. **They have to stay verbatim** — Room
 * validates the shape it finds against the schema it expects on the first open
 * after a migration, and a stray `NOT NULL` here fails on a player's device
 * rather than in the build.
 *
 * `AppDatabaseMigrationTest` walks a real version 8 file through this.
 */
internal val MIGRATE_AWAY_FROM_THE_DAILY = object : Migration(8, 9) {

    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DELETE FROM `run_record` WHERE `mode` = 'DAILY'")
        connection.execSQL("DELETE FROM `achievement_fact` WHERE `mode` = 'DAILY'")

        connection.execSQL("DROP TABLE IF EXISTS `daily_result`")

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_run_record` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `endedAt` INTEGER NOT NULL, " +
                "`score` INTEGER NOT NULL, `level` INTEGER NOT NULL, " +
                "`blocksPlaced` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                "`highestTier` INTEGER NOT NULL, `cause` TEXT NOT NULL, " +
                "`longestCascade` INTEGER NOT NULL, `bursts` INTEGER NOT NULL, " +
                "`merges` INTEGER NOT NULL, `seed` INTEGER NOT NULL)"
        )
        connection.execSQL(
            "INSERT INTO `_new_run_record` (`id`, `endedAt`, `score`, `level`, `blocksPlaced`, " +
                "`durationMs`, `highestTier`, `cause`, `longestCascade`, `bursts`, `merges`, " +
                "`seed`) SELECT `id`, `endedAt`, `score`, `level`, `blocksPlaced`, `durationMs`, " +
                "`highestTier`, `cause`, `longestCascade`, `bursts`, `merges`, `seed` " +
                "FROM `run_record`"
        )
        connection.execSQL("DROP TABLE `run_record`")
        connection.execSQL("ALTER TABLE `_new_run_record` RENAME TO `run_record`")

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_achievement_fact` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `key` TEXT NOT NULL, " +
                "`score` INTEGER NOT NULL, `level` INTEGER NOT NULL, " +
                "`blocksPlaced` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                "`highestTier` INTEGER NOT NULL, `longestCascade` INTEGER NOT NULL, " +
                "`bursts` INTEGER NOT NULL, `merges` INTEGER NOT NULL, " +
                "`boardsCleared` INTEGER NOT NULL, `stoneBursts` INTEGER NOT NULL, " +
                "`wildcardBursts` INTEGER NOT NULL, `longestDangerRun` INTEGER NOT NULL, " +
                "`endedAt` INTEGER NOT NULL)"
        )
        connection.execSQL(
            "INSERT INTO `_new_achievement_fact` (`id`, `key`, `score`, `level`, `blocksPlaced`, " +
                "`durationMs`, `highestTier`, `longestCascade`, `bursts`, `merges`, " +
                "`boardsCleared`, `stoneBursts`, `wildcardBursts`, `longestDangerRun`, " +
                "`endedAt`) SELECT `id`, replace(`key`, 'ENDLESS:', ''), `score`, `level`, " +
                "`blocksPlaced`, `durationMs`, `highestTier`, `longestCascade`, `bursts`, " +
                "`merges`, `boardsCleared`, `stoneBursts`, `wildcardBursts`, `longestDangerRun`, " +
                "`endedAt` FROM `achievement_fact`"
        )
        connection.execSQL("DROP TABLE `achievement_fact`")
        connection.execSQL("ALTER TABLE `_new_achievement_fact` RENAME TO `achievement_fact`")
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_achievement_fact_key` " +
                "ON `achievement_fact` (`key`)"
        )
    }
}
