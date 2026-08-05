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
 * Covers [MIGRATION_5_6] — the fix for a real, confirmed production bug: no local Room table
 * was ever scoped by `userId`, so a device reused across accounts (without a full app
 * reinstall) could have one account's local data collide with or block another account's.
 * Confirmed by pulling a real device's `pesamind.db`: `channels` had a table-wide `UNIQUE`
 * index on `normalizedSenderKey` alone, so a *previous* test account's soft-deleted "MTN
 * Mobile Money"/"Airtel Money" channels silently blocked a *new* account's onboarding from
 * ever creating channels under those same provider names (`ChannelDao.
 * findByNormalizedSenderKey` matched the old account's row regardless of which account it
 * belonged to). The same table-wide-unique-index shape existed on `transactions.smsSourceKey`
 * and `processed_messages.dedupeKey`; budget tables had no `userId` column to disambiguate by
 * at all. See [MIGRATION_5_6]'s own doc comment in `Migrations.kt` for the full picture.
 *
 * ## Why these tests don't call `helper.createDatabase(name, 5)` directly
 *
 * `app/schemas/.../5.json` — as checked in on this branch at the time these tests were
 * written — is corrupted: it is byte-for-byte identical (including `identityHash`) to
 * `6.json` apart from the `"version"` field, i.e. it already describes the *post*-migration
 * composite-index/`userId` shape under the `version: 5` label. `4.json`, by contrast, is
 * correct (it matches [MIGRATION_4_5]'s well-established, already-tested output). This is a
 * schema-export artifact bug — most likely `PesaMindDatabase.version` was exported once while
 * still `5` but after the entity classes had already been updated to their [MIGRATION_5_6]
 * target shape — not a bug in [MIGRATION_5_6] itself, and not something fixable from
 * `androidTest` (regenerating it correctly needs a real Gradle schema-export build against the
 * entity definitions as they stood before this feature's changes).
 *
 * Using `helper.createDatabase(testDbName, 5)` as-is would build a database that *already* has
 * every column [MIGRATION_5_6] unconditionally `ALTER TABLE ... ADD COLUMN`s in (unlike
 * [MIGRATION_4_5], [MIGRATION_5_6] has no `hasColumn` guard, matching the assumption that real
 * v5 devices never have these columns) — every test below would fail immediately with a
 * `SQLiteException: duplicate column name`, for a reason entirely unrelated to whether
 * [MIGRATION_5_6] is correct.
 *
 * Instead, every test below builds a *genuine* v5 state the same way a real v4 device would
 * have gotten there: `helper.createDatabase(testDbName, 4)` against the correct, trusted
 * `4.json`, followed by literally invoking `MIGRATION_4_5.migrate(db)` (already covered in
 * isolation by [Migration4To5Test]) before stamping `db.version = 5`. This mirrors
 * [Migration4To5Test]'s own established pattern of hand-building pre-migration states that
 * were never themselves checked in as schema JSON.
 */
@RunWith(AndroidJUnit4::class)
class Migration5To6Test {
    private val testDbName = "migration-5-6-test.db"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            PesaMindDatabase::class.java,
        )

    /** Builds a genuine (uncorrupted) pre-[MIGRATION_5_6] v5 database — see the class doc. */
    private fun createRealV5Database(): SupportSQLiteDatabase {
        val db = helper.createDatabase(testDbName, 4)
        MIGRATION_4_5.migrate(db)
        db.version = 5
        return db
    }

    private fun insertChannel(
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

    private fun insertTransaction(
        db: SupportSQLiteDatabase,
        id: String,
        userId: String,
        smsSourceKey: String,
    ) {
        db.execSQL(
            """
            INSERT INTO `transactions`
                (`id`, `serverId`, `userId`, `channelId`, `channelDetailsName`, `amount`, `type`,
                 `note`, `username`, `smsSourceKey`, `providerTransactionId`, `syncStatus`, `dirty`,
                 `createdAt`, `updatedAt`, `deletedAt`)
            VALUES
                ('$id', NULL, '$userId', NULL, 'MTN Mobile Money', 100.0, 'EXPENSE',
                 'note', 'jane', '$smsSourceKey', NULL, 'SYNCED', 0,
                 1000, 1000, NULL)
            """.trimIndent(),
        )
    }

    private fun insertProcessedMessage(
        db: SupportSQLiteDatabase,
        id: String,
        userId: String,
        dedupeKey: String,
    ) {
        db.execSQL(
            """
            INSERT INTO `processed_messages`
                (`id`, `serverId`, `userId`, `senderId`, `content`, `timestamp`, `simInfo`,
                 `receivingSimNumber`, `dedupeKey`, `syncStatus`, `createdAt`, `updatedAt`)
            VALUES
                ('$id', NULL, '$userId', 'MPESA', 'You have received Ksh100', 1000, 0,
                 '+254700000000', '$dedupeKey', 'SYNCED', 1000, 1000)
            """.trimIndent(),
        )
    }

    /**
     * Reproduces the actual reported bug and proves [MIGRATION_5_6] fixes it: a channel row
     * for one account must no longer block a *different* account from creating a channel with
     * the same `normalizedSenderKey`. Under the real (uncorrupted) v5 schema this table had a
     * table-wide `UNIQUE INDEX index_channels_normalizedSenderKey (normalizedSenderKey)` — the
     * second insert below, for a different `userId`, would have thrown
     * `SQLiteConstraintException` pre-migration. Per-account uniqueness must still hold too:
     * inserting the *same* account's key twice must still fail.
     */
    @Test
    fun migratedChannelsAllowsDifferentAccountsToShareNormalizedSenderKey() {
        val v5db = createRealV5Database()
        insertChannel(v5db, id = "ch-old-1", userId = "old-user", normalizedSenderKey = "mtn mobile money")
        v5db.close()

        val db = helper.runMigrationsAndValidate(testDbName, 6, true, MIGRATION_5_6)

        // Would have thrown SQLiteConstraintException against the old table-wide unique index —
        // this is the exact production bug: a different account's channel row for the same
        // provider key must now be insertable.
        insertChannel(db, id = "ch-new-1", userId = "new-user", normalizedSenderKey = "mtn mobile money")

        assertThrows(SQLiteConstraintException::class.java) {
            // Same account, same key, again — per-account uniqueness must still be enforced,
            // i.e. this isn't a regression to "no uniqueness at all".
            insertChannel(db, id = "ch-old-2", userId = "old-user", normalizedSenderKey = "mtn mobile money")
        }
        db.close()
    }

    /**
     * `transactions.smsSourceKey` uniqueness becomes per-account: two different accounts can
     * share an `smsSourceKey` (e.g. both processed an SMS with a coincidentally-identical
     * dedup key), but the same account can't create two transactions for the same key.
     */
    @Test
    fun migratedTransactionsEnforceSmsSourceKeyUniquenessPerAccountOnly() {
        createRealV5Database().close()
        val db = helper.runMigrationsAndValidate(testDbName, 6, true, MIGRATION_5_6)

        insertTransaction(db, id = "tx-a", userId = "user-a", smsSourceKey = "sms-key-1")
        // Different account, same smsSourceKey — must succeed (this is the whole point of the fix).
        insertTransaction(db, id = "tx-b", userId = "user-b", smsSourceKey = "sms-key-1")

        assertThrows(SQLiteConstraintException::class.java) {
            // Same account (user-a), same smsSourceKey again — per-account uniqueness retained.
            insertTransaction(db, id = "tx-c", userId = "user-a", smsSourceKey = "sms-key-1")
        }
        db.close()
    }

    /**
     * `processed_messages.dedupeKey` uniqueness becomes per-account, mirroring the
     * `transactions.smsSourceKey` case above.
     */
    @Test
    fun migratedProcessedMessagesEnforceDedupeKeyUniquenessPerAccountOnly() {
        createRealV5Database().close()
        val db = helper.runMigrationsAndValidate(testDbName, 6, true, MIGRATION_5_6)

        insertProcessedMessage(db, id = "pm-a", userId = "user-a", dedupeKey = "dedupe-1")
        // Different account, same dedupeKey — must succeed.
        insertProcessedMessage(db, id = "pm-b", userId = "user-b", dedupeKey = "dedupe-1")

        assertThrows(SQLiteConstraintException::class.java) {
            // Same account (user-a), same dedupeKey again — per-account uniqueness retained.
            insertProcessedMessage(db, id = "pm-c", userId = "user-a", dedupeKey = "dedupe-1")
        }
        db.close()
    }

    /**
     * Budget tables gain a `userId` column and a `userId`-scoped lookup index. Unlike the
     * three tables above, these indices were never unique (a household/shared-device scenario
     * can legitimately have multiple budget rows for the same month/year), so there's no
     * collision behavior to prove — just that the schema itself changed as intended, and that
     * the old, unscoped indices are gone.
     */
    @Test
    fun migratedBudgetTablesGainUserIdColumnAndScopedIndices() {
        createRealV5Database().close()
        val db = helper.runMigrationsAndValidate(testDbName, 6, true, MIGRATION_5_6)

        assertTrue("monthly_budgets must gain a userId column", db.columnNames("monthly_budgets").contains("userId"))
        assertTrue("yearly_budgets must gain a userId column", db.columnNames("yearly_budgets").contains("userId"))

        assertTrue(
            "index_monthly_budgets_userId_month_year must exist",
            db.indexExists("index_monthly_budgets_userId_month_year"),
        )
        assertTrue(
            "index_yearly_budgets_userId_year must exist",
            db.indexExists("index_yearly_budgets_userId_year"),
        )

        assertFalse(
            "the old unscoped index_monthly_budgets_month_year must be dropped",
            db.indexExists("index_monthly_budgets_month_year"),
        )
        assertFalse(
            "the old unscoped index_yearly_budgets_year must be dropped",
            db.indexExists("index_yearly_budgets_year"),
        )
        db.close()
    }

    /**
     * A genuine, row-free v5 -> v6 upgrade, verified against the checked-in `6.json` via
     * `runMigrationsAndValidate`'s schema-hash check (the `validateDroppedTables = true`
     * argument) — proves [MIGRATION_5_6] converges a real v5 state to exactly the schema the
     * current entity definitions describe, not merely "no exception thrown".
     */
    @Test
    fun migrate5To6ConvergesRealV5StateToSchemaMatchingCheckedInV6Json() {
        createRealV5Database().close()

        val db = helper.runMigrationsAndValidate(testDbName, 6, true, MIGRATION_5_6)

        assertEquals(1, db.columnOccurrences("transactions", "userId"))
        assertEquals(1, db.columnOccurrences("monthly_budgets", "userId"))
        assertEquals(1, db.columnOccurrences("yearly_budgets", "userId"))
        assertEquals(1, db.columnOccurrences("processed_messages", "userId"))

        assertTrue(db.indexExists("index_channels_userId_normalizedSenderKey"))
        assertTrue(db.indexExists("index_transactions_userId_smsSourceKey"))
        assertFalse(db.indexExists("index_channels_normalizedSenderKey"))
        assertFalse(db.indexExists("index_transactions_smsSourceKey"))
        assertFalse(db.indexExists("index_processed_messages_dedupeKey"))
        db.close()
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
