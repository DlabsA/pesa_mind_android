package cc.dlabs.pesamind.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers [MIGRATION_7_8] — adds the `blog_posts` table for cached admin-authored finance
 * write-ups (see [MIGRATION_7_8]'s own doc comment in `Migrations.kt`). Unlike a budget/
 * transaction migration, there's no pre-existing v7 data to preserve here — this is a brand
 * new table — so the only things worth asserting are that the migration runs cleanly against
 * a genuine v7 database and that the resulting schema matches the checked-in `8.json`.
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

    @Test
    fun migrate7To8CreatesBlogPostsTableMatchingCheckedInV8Json() {
        helper.createDatabase(testDbName, 7).close()

        val db = helper.runMigrationsAndValidate(testDbName, 8, true, MIGRATION_7_8)

        assertEquals(1, db.columnOccurrences("blog_posts", "id"))
        assertEquals(1, db.columnOccurrences("blog_posts", "title"))
        assertEquals(1, db.columnOccurrences("blog_posts", "body"))
        assertEquals(1, db.columnOccurrences("blog_posts", "publishedAt"))
        assertEquals(1, db.columnOccurrences("blog_posts", "fetchedAt"))
        assertEquals(1, db.columnOccurrences("blog_posts", "isRead"))
        db.close()
    }

    /** A row can be inserted and read back after the migration — a minimal sanity check that
     * the generated `CREATE TABLE` is actually usable, not just schema-shaped. */
    @Test
    fun blogPostRowSurvivesInsertAfterMigration() {
        helper.createDatabase(testDbName, 7).close()
        val db = helper.runMigrationsAndValidate(testDbName, 8, true, MIGRATION_7_8)

        db.execSQL(
            """
            INSERT INTO `blog_posts` (`id`, `title`, `body`, `publishedAt`, `fetchedAt`, `isRead`)
            VALUES ('post-1', 'Saving basics', 'Start small.', 1000, 2000, 0)
            """.trimIndent(),
        )
        db.query("SELECT `title` FROM `blog_posts` WHERE `id` = 'post-1'").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("Saving basics", cursor.getString(0))
        }
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
