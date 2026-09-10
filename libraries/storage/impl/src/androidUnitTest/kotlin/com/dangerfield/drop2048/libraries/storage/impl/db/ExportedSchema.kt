package com.dangerfield.drop2048.libraries.storage.impl.db

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Builds a database file at an **old** schema version, from the JSON Room
 * exported when that version shipped.
 *
 * This is the only honest way to test a migration: an old database has to be a
 * real file laid out the way the release that wrote it laid it out, not a
 * fresh one with `user_version` poked backwards. Room's own
 * `MigrationTestHelper` does the same thing from the same files; it is not used
 * here because its driver-carrying constructor wants an `Instrumentation`, and
 * the point of this test is that it runs in the standard host-JVM gate rather
 * than needing a device (L46).
 *
 * The `room_master_table` identity hash is what makes the file convincing. Room
 * compares it on open and treats a mismatch as a corrupt database, so writing
 * the recorded hash is what turns "a file with the right tables" into "the
 * database version 6 actually shipped".
 */
object ExportedSchema {

    private val json = Json { ignoreUnknownKeys = true }

    private val directory: File
        get() = File(
            requireNotNull(System.getProperty("drop2048.schemaDir")) {
                "drop2048.schemaDir was not set; see this module's build.gradle.kts"
            },
            AppDatabase::class.qualifiedName!!,
        )

    fun createDatabaseAt(version: Int, path: File, driver: SQLiteDriver) {
        val schema = json
            .parseToJsonElement(File(directory, "$version.json").readText())
            .jsonObject
            .getValue("database")
            .jsonObject

        val statements = buildList {
            schema["entities"]?.jsonArray.orEmptyList().forEach { entity ->
                val table = entity.jsonObject
                add(
                    table.getValue("createSql").jsonPrimitive.content
                        .replace("\${TABLE_NAME}", table.getValue("tableName").jsonPrimitive.content)
                )
                table["indices"]?.jsonArray.orEmptyList().forEach { index ->
                    add(
                        index.jsonObject.getValue("createSql").jsonPrimitive.content
                            .replace(
                                "\${TABLE_NAME}",
                                table.getValue("tableName").jsonPrimitive.content,
                            )
                    )
                }
            }
            schema["setupQueries"]?.jsonArray.orEmptyList().forEach {
                add(it.jsonPrimitive.content)
            }
            add("PRAGMA user_version = $version")
        }

        path.parentFile?.mkdirs()
        driver.open(path.absolutePath).use { connection ->
            statements.forEach { connection.execSQL(it) }
        }
    }

    /**
     * A database claiming a [version] this project never exported a schema for,
     * carrying one recognisable table.
     *
     * Versions 1 to 4 are template history from before the game existed, and a
     * version above 8 is a player who installed a newer build and rolled back.
     * Neither has a schema on disk to build from and neither needs one — what
     * matters is the version number Room reads and whether the rows survive
     * being opened.
     */
    fun createUnknownDatabaseAt(version: Int, path: File, driver: SQLiteDriver) {
        path.parentFile?.mkdirs()
        driver.open(path.absolutePath).use { connection ->
            connection.execSQL("CREATE TABLE legacy_thing (`key` TEXT PRIMARY KEY NOT NULL)")
            connection.execSQL("INSERT INTO legacy_thing (`key`) VALUES ('kept')")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY,identity_hash TEXT)"
            )
            connection.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                    "VALUES(42, 'notarealidentityhash000000000000')"
            )
            connection.execSQL("PRAGMA user_version = $version")
        }
    }

    fun tableExists(path: File, table: String, driver: SQLiteDriver): Boolean =
        driver.open(path.absolutePath).use { connection ->
            connection.prepare(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?"
            ).use { statement ->
                statement.bindText(1, table)
                statement.step()
                statement.getLong(0) > 0
            }
        }
}

private fun JsonArray?.orEmptyList(): List<JsonElement> = this ?: emptyList()
