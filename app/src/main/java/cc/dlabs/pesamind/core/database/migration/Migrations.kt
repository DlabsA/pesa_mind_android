package cc.dlabs.pesamind.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: case-insensitive channel resolution + TID-based transaction dedup (see
 * docs/decisions/ADR-0004-offline-first.md's duplicate-channels/duplicate-transactions
 * section). `PesaMindDatabase` has no `fallbackToDestructiveMigration()` (confirmed clean,
 * ADR-0004 Step 0) — a schema bump with no real [Migration] wipes every local user's
 * unsynced data, so this is required, not optional.
 *
 * Both new columns are nullable and default to `NULL` for every pre-existing row — no
 * backfill is attempted here (that's the ADR's explicitly out-of-scope Phase 3, historical
 * dedup). SQLite treats every `NULL` in a unique index as distinct from every other `NULL`,
 * so existing rows (all `NULL` on these new columns) can never collide with each other or
 * with a future row under either new unique index.
 */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `channels` ADD COLUMN `normalizedSenderKey` TEXT DEFAULT NULL")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_channels_normalizedSenderKey` " +
                    "ON `channels` (`normalizedSenderKey`)",
            )

            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `providerTransactionId` TEXT DEFAULT NULL")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_channelId_providerTransactionId` " +
                    "ON `transactions` (`channelId`, `providerTransactionId`)",
            )
        }
    }
