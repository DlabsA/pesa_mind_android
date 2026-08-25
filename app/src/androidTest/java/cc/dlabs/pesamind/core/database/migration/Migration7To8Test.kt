package cc.dlabs.pesamind.core.database.migration

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers [MIGRATION_7_8] — adds `channels.receivingNumber` and widens the provider-uniqueness
 * index from `(userId, normalizedSenderKey)` to `(userId, normalizedSenderKey, receivingNumber)`
 * so a user can have more than one channel for the same provider (e.g. two MTN MoMo lines),
 * disambiguated by which phone number actually receives each one's SMS. See [MIGRATION_7_8]'s own
 * doc comment in `Migrations.kt` for the full design, and
 * [cc.dlabs.pesamind.core.database.entity.ChannelEntity]'s doc comment for why the new column
 * defaults to the `UNSPECIFIED` sentinel rather than `NULL`.
 *
 * Unlike [Migration5To6Test], the checked-in `app/schemas/.../7.json` this migration starts from
 * is trustworthy (confirmed: no `receivingNumber` column, matches [MIGRATION_6_7]'s known-good
 * output), so `helper.createDatabase(testDbName, 7)` is used directly rather than hand-building
 * the pre-migration state from an earlier, trusted schema version.
 */
@RunWith(AndroidJUnit4::class)
class Migration7To8Test {
    private val testDbName = "migration-7-8-test.db"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            PesaMindDatabase::class.java,
        )

    private fun insertV7Channel(
        db: SupportSQLiteDatabase,
        id: String,
        userId: String,
        normalizedSenderKey: String,
    ) {
        db.execSQL(
            """
            INSERT INTO `channels`
                (`id`, `serverId`, `userId`, `name`, `channelType`, `description`, `status`,
                 `channelDesc`, `normalizedSenderKey`, `availableBalance`, `accountNumber`,
                 `smsNotificationEnabled`, `syncStatus`, `dirty`, `createdAt`, `updatedAt`, `deletedAt`)
            VALUES
                ('$id', NULL, '$userId', 'MTN Mobile Money', 'MOBILE_MONEY', 'Mobile money channel', 1,
                 'MTN Mobile Money', '$normalizedSenderKey', 0.0, NULL,
                 1, 'SYNCED', 0, 1000, 1000, NULL)
            """.trimIndent(),
        )
    }

    private fun insertV8ChannelWithReceivingNumber(
        db: SupportSQLiteDatabase,
        id: String,
        userId: String,
        normalizedSenderKey: String,
        receivingNumber: String,
    ) {
        db.execSQL(
            """
            INSERT INTO `channels`
                (`id`, `serverId`, `userId`, `name`, `channelType`, `description`, `status`,
                 `channelDesc`, `normalizedSenderKey`, `availableBalance`, `accountNumber`,
                 `receivingNumber`, `smsNotificationEnabled`, `syncStatus`, `dirty`, `createdAt`,
                 `updatedAt`, `deletedAt`)
            VALUES
                ('$id', NULL, '$userId', 'MTN Mobile Money', 'MOBILE_MONEY', 'Mobile money channel', 1,
                 'MTN Mobile Money', '$normalizedSenderKey', 0.0, NULL,
                 '$receivingNumber', 1, 'SYNCED', 0, 1000, 1000, NULL)
            """.trimIndent(),
        )
    }

    /**
     * A pre-migration row (inserted against the real v7 schema, no `receivingNumber` column at
     * all) must survive the migration with the new column backfilled to the `UNSPECIFIED`
     * sentinel — see [MIGRATION_7_8]'s doc comment for why that default is what keeps every
     * pre-existing row's provider-uniqueness guarantee intact without a real backfill.
     */
    @Test
    fun preMigrationChannelRowSurvivesWithUnspecifiedReceivingNumber() {
        val v7db = helper.createDatabase(testDbName, 7)
        insertV7Channel(v7db, id = "ch-1", userId = "user-1", normalizedSenderKey = "mtn mobile money")
        v7db.close()

        val db = helper.runMigrationsAndValidate(testDbName, 8, true, MIGRATION_7_8)

        db.query("SELECT `receivingNumber` FROM `channels` WHERE `id` = 'ch-1'").use { cursor ->
            assertTrue("the migrated row must still exist", cursor.moveToFirst())
            assertEquals("UNSPECIFIED", cursor.getString(cursor.getColumnIndexOrThrow("receivingNumber")))
        }
        db.close()
    }

    /** The new composite unique index must exist post-migration, and the old, narrower one must
     * be gone. */
    @Test
    fun migrationCreatesTheWidenedIndexAndDropsTheOldOne() {
        helper.createDatabase(testDbName, 7).close()

        val db = helper.runMigrationsAndValidate(testDbName, 8, true, MIGRATION_7_8)

        assertTrue(
            "the new (userId, normalizedSenderKey, receivingNumber) unique index must exist",
            db.indexExists("index_channels_userId_normalizedSenderKey_receivingNumber"),
        )
        assertFalse(
            "the old (userId, normalizedSenderKey) index must be dropped",
            db.indexExists("index_channels_userId_normalizedSenderKey"),
        )
        db.close()
    }

    /**
     * Two rows sharing `(userId, normalizedSenderKey)` but with different `receivingNumber`
     * values must both insert successfully post-migration — this is the entire point of the
     * fix: a user can now have two channels for the same provider (e.g. two MTN MoMo lines).
     */
    @Test
    fun migratedIndexAllowsTwoChannelsForTheSameProviderWithDifferentReceivingNumbers() {
        helper.createDatabase(testDbName, 7).close()
        val db = helper.runMigrationsAndValidate(testDbName, 8, true, MIGRATION_7_8)

        insertV8ChannelWithReceivingNumber(
            db,
            id = "ch-line-1",
            userId = "user-1",
            normalizedSenderKey = "mtn mobile money",
            receivingNumber = "770123456",
        )
        insertV8ChannelWithReceivingNumber(
            db,
            id = "ch-line-2",
            userId = "user-1",
            normalizedSenderKey = "mtn mobile money",
            receivingNumber = "780123456",
        )

        db.query("SELECT COUNT(*) FROM `channels` WHERE `userId` = 'user-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        db.close()
    }

    /**
     * A genuine duplicate triple — same `userId`, same `normalizedSenderKey`, same
     * `receivingNumber` — must still be rejected by the widened unique index; the fix loosens
     * uniqueness to include `receivingNumber`, it doesn't remove uniqueness altogether.
     */
    @Test
    fun migratedIndexStillRejectsAGenuineDuplicateTriple() {
        helper.createDatabase(testDbName, 7).close()
        val db = helper.runMigrationsAndValidate(testDbName, 8, true, MIGRATION_7_8)

        insertV8ChannelWithReceivingNumber(
            db,
            id = "ch-line-1",
            userId = "user-1",
            normalizedSenderKey = "mtn mobile money",
            receivingNumber = "770123456",
        )

        assertThrows(SQLiteConstraintException::class.java) {
            insertV8ChannelWithReceivingNumber(
                db,
                id = "ch-line-1-dup",
                userId = "user-1",
                normalizedSenderKey = "mtn mobile money",
                receivingNumber = "770123456",
            )
        }
        db.close()
    }

    private fun SupportSQLiteDatabase.indexExists(index: String): Boolean =
        query("SELECT name FROM sqlite_master WHERE type='index' AND name=?", arrayOf(index)).use {
            it.count == 1
        }
}
