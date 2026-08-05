package cc.dlabs.pesamind.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers [MIGRATION_6_7] — adds `lastSyncedTransactionsJson` to `monthly_budgets`/
 * `yearly_budgets`, the fix for a real, confirmed data-loss bug (see [MIGRATION_6_7]'s own doc
 * comment in `Migrations.kt` for the full picture: the legacy full-replace push field had no
 * ids, so the backend's diff-and-delete update logic could wipe server-side transactions the
 * client's local cache didn't know about).
 *
 * Unlike [Migration5To6Test], `app/schemas/.../6.json` is trustworthy (only `5.json` was
 * confirmed corrupted), so these tests build the pre-migration state directly via
 * `helper.createDatabase(testDbName, 6)` rather than replaying earlier migrations by hand.
 */
@RunWith(AndroidJUnit4::class)
class Migration6To7Test {
    private val testDbName = "migration-6-7-test.db"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            PesaMindDatabase::class.java,
        )

    private fun insertMonthlyBudget(
        db: SupportSQLiteDatabase,
        id: String,
    ) {
        db.execSQL(
            """
            INSERT INTO `monthly_budgets`
                (`id`, `serverId`, `userId`, `yearlyBudgetId`, `month`, `year`,
                 `totalExpenditures`, `totalIncome`, `totalSavings`, `totalTransactions`,
                 `transactionsJson`, `syncStatus`, `dirty`, `createdAt`, `updatedAt`, `deletedAt`)
            VALUES
                ('$id', NULL, 'user-a', NULL, 8, 2026,
                 0, 0, 0, 0,
                 '[]', 'SYNCED', 0, 1000, 1000, NULL)
            """.trimIndent(),
        )
    }

    private fun insertYearlyBudget(
        db: SupportSQLiteDatabase,
        id: String,
    ) {
        db.execSQL(
            """
            INSERT INTO `yearly_budgets`
                (`id`, `serverId`, `userId`, `year`,
                 `totalExpenditures`, `totalIncome`, `totalSavings`, `totalTransactions`,
                 `transactionsJson`, `syncStatus`, `dirty`, `createdAt`, `updatedAt`, `deletedAt`)
            VALUES
                ('$id', NULL, 'user-a', 2026,
                 0, 0, 0, 0,
                 '[]', 'SYNCED', 0, 1000, 1000, NULL)
            """.trimIndent(),
        )
    }

    /**
     * Existing rows must survive the migration with `lastSyncedTransactionsJson` defaulting to
     * `NULL` — per the migration's own doc comment, `NULL` is the correct "never synced under
     * the new scheme" baseline, not a stand-in for "delete everything".
     */
    @Test
    fun migratedBudgetTablesGainNullableLastSyncedColumnDefaultingToNull() {
        val v6db = helper.createDatabase(testDbName, 6)
        insertMonthlyBudget(v6db, id = "mb-1")
        insertYearlyBudget(v6db, id = "yb-1")
        v6db.close()

        val db = helper.runMigrationsAndValidate(testDbName, 7, true, MIGRATION_6_7)

        db.query("SELECT `lastSyncedTransactionsJson` FROM `monthly_budgets` WHERE `id` = 'mb-1'").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertNull("pre-existing monthly_budgets row must default to NULL, not '[]'", cursor.getString(0))
        }
        db.query("SELECT `lastSyncedTransactionsJson` FROM `yearly_budgets` WHERE `id` = 'yb-1'").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertNull("pre-existing yearly_budgets row must default to NULL, not '[]'", cursor.getString(0))
        }
        db.close()
    }

    /** A genuine, row-free v6 -> v7 upgrade, verified against the checked-in `7.json` via
     * `runMigrationsAndValidate`'s schema-hash check. */
    @Test
    fun migrate6To7ConvergesToSchemaMatchingCheckedInV7Json() {
        helper.createDatabase(testDbName, 6).close()

        val db = helper.runMigrationsAndValidate(testDbName, 7, true, MIGRATION_6_7)

        assertEquals(1, db.columnOccurrences("monthly_budgets", "lastSyncedTransactionsJson"))
        assertEquals(1, db.columnOccurrences("yearly_budgets", "lastSyncedTransactionsJson"))
        db.close()
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
