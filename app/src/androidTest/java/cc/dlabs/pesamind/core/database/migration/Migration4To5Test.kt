package cc.dlabs.pesamind.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers [MIGRATION_4_5] — the fix for a real production crash. `2cf9604` (added
 * `processed_messages`) and `be8695f` (added `channels.accountNumber`) each independently
 * bumped `PesaMindDatabase.version` 3 -> 4 on sibling branches before being merged.
 * `MIGRATION_3_4` ended up with the union of both changes, but any device that had already
 * installed a build from just ONE of those two commits is stamped `version = 4` locally with
 * only half that schema. Room only runs a migration on a version *transition*, so those
 * devices hit `4 == 4` on the merged app and failed Room's identity-hash check instead of
 * migrating ("Room cannot verify the data integrity..."). `MIGRATION_4_5` is intentionally
 * idempotent so it safely converges every possible incoming v4 state to the same v5 schema.
 *
 * The two "half schema" v4 states exercised in [migrate4To5ConvergesAccountNumberOnlyStateToFullSchema]
 * and [migrate4To5ConvergesProcessedMessagesOnlyStateToFullSchema] were never checked in as
 * exported schema JSON — `app/schemas` only has the union `4.json`, matching the merged app,
 * never the divergent single-commit states real abandoned devices actually had on disk. So
 * they're built by hand here: start from the real, checked-in v3 schema, hand-apply only ONE
 * side of what `MIGRATION_3_4` does, then stamp the result as version 4 via
 * [SupportSQLiteDatabase.setVersion] — mirroring exactly what a real device that only ever
 * received one of the two sibling commits would have locally.
 */
@RunWith(AndroidJUnit4::class)
class Migration4To5Test {
    private val testDbName = "migration-4-5-test.db"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            PesaMindDatabase::class.java,
        )

    /**
     * Simulates a device that only ever got `be8695f` (channels.accountNumber) and never
     * `2cf9604` (processed_messages) — e.g. it installed a build cut from that commit before
     * the merge. Locally stamped `version = 4` but `processed_messages` doesn't exist at all.
     */
    @Test
    fun migrate4To5ConvergesAccountNumberOnlyStateToFullSchema() {
        var db = helper.createDatabase(testDbName, 3)
        // Hand-apply only the accountNumber half of what MIGRATION_3_4 does today — mirrors
        // be8695f in isolation, deliberately never applying 2cf9604's processed_messages table.
        db.execSQL("ALTER TABLE `channels` ADD COLUMN `accountNumber` TEXT DEFAULT NULL")
        db.version = 4
        db.close()

        db = helper.runMigrationsAndValidate(testDbName, 5, true, MIGRATION_4_5)

        assertTrue("processed_messages must exist after MIGRATION_4_5", db.tableExists("processed_messages"))
        assertEquals(
            setOf(
                "id", "serverId", "senderId", "content", "timestamp", "simInfo",
                "receivingSimNumber", "dedupeKey", "syncStatus", "createdAt", "updatedAt",
            ),
            db.columnNames("processed_messages"),
        )
        assertTrue(
            "index_processed_messages_dedupeKey must exist",
            db.indexExists("index_processed_messages_dedupeKey"),
        )
        assertTrue(
            "index_processed_messages_syncStatus must exist",
            db.indexExists("index_processed_messages_syncStatus"),
        )

        // accountNumber must survive untouched and, critically, not be duplicated: a second
        // `ALTER TABLE ... ADD COLUMN accountNumber` on top of the one this test already ran
        // would throw "duplicate column name" and fail the migration outright, so reaching this
        // assertion without an exception already proves the hasColumn() guard did its job — this
        // is a belt-and-braces check that there's exactly one such column, not zero or two.
        assertEquals(1, db.columnOccurrences("channels", "accountNumber"))
        db.close()
    }

    /**
     * Simulates a device that only ever got `2cf9604` (processed_messages) and never
     * `be8695f` (channels.accountNumber). Locally stamped `version = 4` but
     * `channels.accountNumber` doesn't exist at all.
     */
    @Test
    fun migrate4To5ConvergesProcessedMessagesOnlyStateToFullSchema() {
        var db = helper.createDatabase(testDbName, 3)
        // Hand-apply only the processed_messages half of what MIGRATION_3_4 does today —
        // mirrors 2cf9604 in isolation, deliberately never applying be8695f's accountNumber
        // column.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `processed_messages` (
                `id` TEXT NOT NULL, `serverId` TEXT, `senderId` TEXT NOT NULL,
                `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL,
                `simInfo` INTEGER NOT NULL, `receivingSimNumber` TEXT NOT NULL,
                `dedupeKey` TEXT NOT NULL, `syncStatus` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_processed_messages_dedupeKey` " +
                "ON `processed_messages` (`dedupeKey`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_processed_messages_syncStatus` " +
                "ON `processed_messages` (`syncStatus`)",
        )
        db.version = 4
        db.close()

        db = helper.runMigrationsAndValidate(testDbName, 5, true, MIGRATION_4_5)

        assertEquals(
            "accountNumber must be added exactly once, not left missing or duplicated",
            1,
            db.columnOccurrences("channels", "accountNumber"),
        )
        assertTrue("processed_messages must still exist, untouched", db.tableExists("processed_messages"))
        assertEquals(
            setOf(
                "id", "serverId", "senderId", "content", "timestamp", "simInfo",
                "receivingSimNumber", "dedupeKey", "syncStatus", "createdAt", "updatedAt",
            ),
            db.columnNames("processed_messages"),
        )
        assertTrue(
            "index_processed_messages_dedupeKey must still exist",
            db.indexExists("index_processed_messages_dedupeKey"),
        )
        assertTrue(
            "index_processed_messages_syncStatus must still exist",
            db.indexExists("index_processed_messages_syncStatus"),
        )
        db.close()
    }

    /**
     * A genuine v3 -> v4 upgrade through the current (post-merge, union) `MIGRATION_3_4`,
     * immediately followed by `MIGRATION_4_5` — proves the idempotent guards in `MIGRATION_4_5`
     * (`CREATE TABLE`/`INDEX IF NOT EXISTS`, the `hasColumn` check) are true no-ops when nothing
     * was actually missing, not just "happen not to have been exercised yet" in cases A and B.
     */
    @Test
    fun migrate3To4Then4To5BackToBackDoesNotThrowOnAnAlreadyCompleteSchema() {
        helper.createDatabase(testDbName, 3).close()

        val db =
            helper.runMigrationsAndValidate(
                testDbName,
                5,
                true,
                MIGRATION_3_4,
                MIGRATION_4_5,
            )

        assertEquals(1, db.columnOccurrences("channels", "accountNumber"))
        assertTrue(db.tableExists("processed_messages"))
        assertTrue(db.indexExists("index_processed_messages_dedupeKey"))
        assertTrue(db.indexExists("index_processed_messages_syncStatus"))
        db.close()
    }

    private fun SupportSQLiteDatabase.tableExists(table: String): Boolean =
        query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use {
            it.count == 1
        }

    private fun SupportSQLiteDatabase.indexExists(index: String): Boolean =
        query("SELECT name FROM sqlite_master WHERE type='index' AND name=?", arrayOf(index)).use {
            it.count == 1
        }

    private fun SupportSQLiteDatabase.columnNames(table: String): Set<String> =
        query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val names = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                names.add(cursor.getString(nameIndex))
            }
            names
        }

    private fun SupportSQLiteDatabase.columnOccurrences(
        table: String,
        column: String,
    ): Int =
        query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            var count = 0
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) count++
            }
            count
        }
}
