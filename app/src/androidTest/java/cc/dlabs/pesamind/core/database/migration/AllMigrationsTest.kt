package cc.dlabs.pesamind.core.database.migration

import androidx.room.Database
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Canonical full-chain migration test: always runs every migration in [ALL_MIGRATIONS] against
 * a genuine v1 baseline and validates the result against [PesaMindDatabase]'s current schema.
 * [MigrationTestHelper.runMigrationsAndValidate]'s `validateDroppedTables = true` performs the
 * same full-database `TableInfo` diff Room runs at real app startup — this is the test that
 * would have caught the `transactions` foreign-key defect fixed by MIGRATION_9_10's rebuild
 * step, and the only maintenance a future migration needs here is being appended to
 * [ALL_MIGRATIONS] and PesaMindDatabase's `version` bumped — this test picks it up automatically.
 */
@RunWith(AndroidJUnit4::class)
class AllMigrationsTest {
    private val testDbName = "all-migrations-test.db"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            PesaMindDatabase::class.java,
        )

    @Test
    fun migratingFromV1ToCurrentVersionValidatesCleanly() {
        helper.createDatabase(testDbName, 1).close()
        val currentVersion = PesaMindDatabase::class.java.getAnnotation(Database::class.java)!!.version
        helper.runMigrationsAndValidate(testDbName, currentVersion, true, *ALL_MIGRATIONS).close()
    }
}
