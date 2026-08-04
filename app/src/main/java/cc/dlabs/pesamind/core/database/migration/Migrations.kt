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

/**
 * v2 -> v3: surfaces the backend's per-channel `available_balance` (server-computed from
 * transactions) in the channels UI. Every existing local row defaults to 0.0 — the next full
 * pull ([cc.dlabs.pesamind.core.data.ChannelRepository.reconcileFromServer]) overwrites it with
 * the real server value, same as any other server-owned field.
 */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `channels` ADD COLUMN `availableBalance` REAL NOT NULL DEFAULT 0.0")
        }
    }

/**
 * v3 -> v4: adds the channel-onboarding batch flow's account-number field — a phone number
 * for MobileMoney/Airtel channels, a bank account number for Bank channels. Nullable, no
 * backfill; existing rows default to NULL.
 */
val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `channels` ADD COLUMN `accountNumber` TEXT DEFAULT NULL")
        }
    }
