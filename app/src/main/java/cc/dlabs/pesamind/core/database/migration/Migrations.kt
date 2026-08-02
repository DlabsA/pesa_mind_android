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
 * v3 -> v4: adds `processed_messages` — the first migration in this codebase to create a new
 * table rather than alter an existing one. Backs
 * [cc.dlabs.pesamind.core.database.entity.ProcessedMessageEntity], a write-once local-first
 * audit record pushed to the backend's `POST /processed-messages` via the outbox; no backfill
 * needed since it's a brand-new, previously nonexistent table.
 */
val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
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
        }
    }
