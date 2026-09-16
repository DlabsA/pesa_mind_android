package cc.dlabs.pesamind.core.database.migration

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers [MIGRATION_9_10] — makes `channels.serverId` uniquely indexed, and repairs any device
 * already corrupted by the bug this closes: two distinct local channel rows silently sharing one
 * `serverId` (see [MIGRATION_9_10]'s own doc comment in `Migrations.kt` for the full mechanism —
 * a confirmed production bug where creating a second same-provider channel, e.g. a second Airtel
 * Money channel for a different SIM, could get stamped with the *existing* Airtel channel's
 * `serverId`, after which an unrelated channel's name/description/balance could be silently
 * overwritten on a later sync pull).
 */
@RunWith(AndroidJUnit4::class)
class Migration9To10Test {
    private val testDbName = "migration-9-10-test.db"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            PesaMindDatabase::class.java,
        )

    private fun insertV9Channel(
        db: SupportSQLiteDatabase,
        id: String,
        serverId: String?,
        userId: String,
        normalizedSenderKey: String,
        receivingNumber: String,
        dirty: Int = 0,
        syncStatus: String = "SYNCED",
    ) {
        db.execSQL(
            """
            INSERT INTO `channels`
                (`id`, `serverId`, `userId`, `name`, `channelType`, `description`, `status`,
                 `channelDesc`, `normalizedSenderKey`, `availableBalance`, `accountNumber`,
                 `receivingNumber`, `smsNotificationEnabled`, `syncStatus`, `dirty`, `createdAt`,
                 `updatedAt`, `deletedAt`)
            VALUES
                ('$id', ${serverId?.let { "'$it'" } ?: "NULL"}, '$userId', 'Airtel Money', 'MOBILE_MONEY',
                 'Mobile money channel', 1, 'Airtel Money', '$normalizedSenderKey', 0.0, NULL,
                 '$receivingNumber', 1, '$syncStatus', $dirty, 1000, 1000, NULL)
            """.trimIndent(),
        )
    }

    /**
     * Two rows sharing one non-null `serverId` (the corrupted state this migration repairs): one
     * must keep the `serverId` (the lexicographically smaller `id`), the other must be detached
     * — `serverId` cleared, marked dirty/pending — and end up with a fresh `CHANNEL`/`CREATE`
     * outbox entry so it gets its own real `serverId` on the next push.
     */
    @Test
    fun detachesTheLosingRowInAServerIdCollisionAndRequeuesIt() {
        val v9db = helper.createDatabase(testDbName, 9)
        insertV9Channel(
            v9db,
            id = "ch-a-main",
            serverId = "server-airtel-1",
            userId = "user-1",
            normalizedSenderKey = "airtel money",
            receivingNumber = "755175388",
        )
        insertV9Channel(
            v9db,
            id = "ch-b-test",
            serverId = "server-airtel-1",
            userId = "user-1",
            normalizedSenderKey = "airtel money",
            receivingNumber = "700175388",
        )
        v9db.close()

        val db = helper.runMigrationsAndValidate(testDbName, 10, true, MIGRATION_9_10)

        db.query("SELECT `id`, `serverId`, `dirty`, `syncStatus` FROM `channels` ORDER BY `id`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ch-a-main", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals("server-airtel-1", cursor.getString(cursor.getColumnIndexOrThrow("serverId")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("dirty")))

            assertTrue(cursor.moveToNext())
            assertEquals("ch-b-test", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertTrue(
                "the losing row's serverId must be cleared",
                cursor.isNull(cursor.getColumnIndexOrThrow("serverId")),
            )
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("dirty")))
            assertEquals("PENDING", cursor.getString(cursor.getColumnIndexOrThrow("syncStatus")))
        }

        db.query(
            "SELECT COUNT(*) FROM `outbox` WHERE `entityType` = 'CHANNEL' AND `entityId` = 'ch-b-test' " +
                "AND `operation` = 'CREATE'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("the detached row must be requeued for a fresh create", 1, cursor.getInt(0))
        }
        db.close()
    }

    /** A row that already has a legitimate pending create outbox entry must not get a duplicate
     * one inserted by the migration's requeue step. */
    @Test
    fun doesNotDuplicateAnExistingPendingOutboxEntry() {
        val v9db = helper.createDatabase(testDbName, 9)
        insertV9Channel(
            v9db,
            id = "ch-offline-created",
            serverId = null,
            userId = "user-1",
            normalizedSenderKey = "mtn mobile money",
            receivingNumber = "770000000",
            dirty = 1,
            syncStatus = "PENDING",
        )
        v9db.execSQL(
            """
            INSERT INTO `outbox`
                (`id`, `entityType`, `entityId`, `operation`, `status`, `attempts`, `lastError`, `createdAt`, `updatedAt`)
            VALUES
                ('outbox-1', 'CHANNEL', 'ch-offline-created', 'CREATE', 'PENDING', 0, NULL, 1000, 1000)
            """.trimIndent(),
        )
        v9db.close()

        val db = helper.runMigrationsAndValidate(testDbName, 10, true, MIGRATION_9_10)

        db.query("SELECT COUNT(*) FROM `outbox` WHERE `entityType` = 'CHANNEL' AND `entityId` = 'ch-offline-created'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        db.close()
    }

    /** Rows that never collided (distinct or null serverIds) must be left completely untouched. */
    @Test
    fun leavesNonCollidingRowsUntouched() {
        val v9db = helper.createDatabase(testDbName, 9)
        insertV9Channel(
            v9db,
            id = "ch-mtn",
            serverId = "server-mtn-1",
            userId = "user-1",
            normalizedSenderKey = "mtn mobile money",
            receivingNumber = "770000000",
        )
        insertV9Channel(
            v9db,
            id = "ch-airtel",
            serverId = "server-airtel-1",
            userId = "user-1",
            normalizedSenderKey = "airtel money",
            receivingNumber = "755175388",
        )
        v9db.close()

        val db = helper.runMigrationsAndValidate(testDbName, 10, true, MIGRATION_9_10)

        db.query("SELECT `id`, `serverId`, `dirty` FROM `channels` ORDER BY `id`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("server-airtel-1", cursor.getString(cursor.getColumnIndexOrThrow("serverId")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("dirty")))

            assertTrue(cursor.moveToNext())
            assertEquals("server-mtn-1", cursor.getString(cursor.getColumnIndexOrThrow("serverId")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("dirty")))
        }
        db.close()
    }

    /** The unique index must exist post-migration, and must actually reject a genuine duplicate
     * non-null `serverId` going forward — proving the corruption this migration fixes can't be
     * reintroduced. */
    @Test
    fun migrationCreatesAUniqueServerIdIndexThatRejectsFutureDuplicates() {
        helper.createDatabase(testDbName, 9).close()
        val db = helper.runMigrationsAndValidate(testDbName, 10, true, MIGRATION_9_10)

        assertTrue(
            "the unique serverId index must exist",
            db.indexExists("index_channels_serverId"),
        )

        insertV9Channel(
            db,
            id = "ch-1",
            serverId = "server-x",
            userId = "user-1",
            normalizedSenderKey = "mtn mobile money",
            receivingNumber = "770000000",
        )
        assertThrows(SQLiteConstraintException::class.java) {
            insertV9Channel(
                db,
                id = "ch-2",
                serverId = "server-x",
                userId = "user-1",
                normalizedSenderKey = "airtel money",
                receivingNumber = "755175388",
            )
        }
        db.close()
    }

    /** Multiple unsynced rows (`serverId IS NULL`) must remain insertable — SQLite treats every
     * `NULL` in a unique index as distinct, so the new constraint must not block ordinary,
     * legitimately-not-yet-synced channels. */
    @Test
    fun multipleNullServerIdsRemainAllowedPostMigration() {
        helper.createDatabase(testDbName, 9).close()
        val db = helper.runMigrationsAndValidate(testDbName, 10, true, MIGRATION_9_10)

        insertV9Channel(
            db,
            id = "ch-unsynced-1",
            serverId = null,
            userId = "user-1",
            normalizedSenderKey = "mtn mobile money",
            receivingNumber = "770000000",
        )
        insertV9Channel(
            db,
            id = "ch-unsynced-2",
            serverId = null,
            userId = "user-1",
            normalizedSenderKey = "airtel money",
            receivingNumber = "755175388",
        )

        db.query("SELECT COUNT(*) FROM `channels` WHERE `serverId` IS NULL").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        db.close()
    }

    /**
     * [MIGRATION_8_9] added `transactions.debtCreditId`/`savingGoalId` via plain
     * `ALTER TABLE ADD COLUMN`, which cannot attach a `FOREIGN KEY` constraint in SQLite — so a
     * database that actually ran that migration (unlike [MigrationTestHelper.createDatabase],
     * which synthesizes its v9 baseline straight from the schema file's already-correct
     * `CREATE TABLE`) is left missing 2 of `transactions`'s 3 declared FKs. [MIGRATION_9_10]
     * repairs this via a rename/create/copy/drop; this test is the only one in this file that
     * chains from a real v1 baseline, so it's the only one that can catch a regression here.
     */
    @Test
    fun transactionsTableHasAllThreeForeignKeysAfterFullMigrationChain() {
        helper.createDatabase(testDbName, 1).close()
        val db =
            helper.runMigrationsAndValidate(
                testDbName, 10, true,
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
            )

        val referencedTables = mutableSetOf<String>()
        db.query("PRAGMA foreign_key_list(`transactions`)").use { cursor ->
            val tableIndex = cursor.getColumnIndexOrThrow("table")
            while (cursor.moveToNext()) referencedTables.add(cursor.getString(tableIndex))
        }
        assertEquals(setOf("channels", "debt_credits", "saving_goals"), referencedTables)
        db.close()
    }

    private fun SupportSQLiteDatabase.indexExists(index: String): Boolean =
        query("SELECT name FROM sqlite_master WHERE type='index' AND name=?", arrayOf(index)).use {
            it.count == 1
        }
}
