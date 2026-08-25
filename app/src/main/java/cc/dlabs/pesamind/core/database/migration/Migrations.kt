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
 * v3 -> v4: adds the channel-onboarding batch flow's account-number field — a phone number
 * for MobileMoney/Airtel channels, a bank account number for Bank channels. Nullable, no
 * backfill; existing rows default to NULL.
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
            db.execSQL("ALTER TABLE `channels` ADD COLUMN `accountNumber` TEXT DEFAULT NULL")
        }
    }

/**
 * v4 -> v5: reconciliation migration, not a real schema change.
 *
 * `2cf9604` (processed_messages table) and `be8695f` (channels.accountNumber) each
 * independently bumped `PesaMindDatabase.version` 3 -> 4 on sibling branches before being
 * merged. [MIGRATION_3_4] above ended up containing the union of both changes, but every
 * device that had already installed a build from just one of those two commits is stamped
 * `version = 4` locally with only half that schema — and since Room only runs a migration on
 * a version *transition*, those devices hit `4 == 4` on the merged app and fail Room's
 * identity-hash check instead of migrating (see the "Room cannot verify the data integrity"
 * crash this migration fixes).
 *
 * This migration is intentionally idempotent so it safely converges all three possible
 * incoming v4 states — accountNumber-only, processed_messages-only, or already-complete via a
 * genuine v3->v4 upgrade through [MIGRATION_3_4] — to the same final v5 schema.
 */
val MIGRATION_4_5 =
    object : Migration(4, 5) {
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

            if (!db.hasColumn("channels", "accountNumber")) {
                db.execSQL("ALTER TABLE `channels` ADD COLUMN `accountNumber` TEXT DEFAULT NULL")
            }
        }

        private fun SupportSQLiteDatabase.hasColumn(
            table: String,
            column: String,
        ): Boolean =
            query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) {
                        found = true
                        break
                    }
                }
                found
            }
    }

/**
 * v5 -> v6: cross-account local-data isolation fix.
 *
 * No local Room table was ever scoped by `userId`, and nothing cleared local data on logout —
 * confirmed as a real bug on a device reused across test accounts: `channels` had a
 * table-wide unique index on `normalizedSenderKey`, so a *previous* account's soft-deleted
 * "MTN Mobile Money"/"Airtel Money" channels silently blocked a *new* account's onboarding
 * from ever creating its own channels under the same provider names (`ChannelDao.
 * findByNormalizedSenderKey` matched the old account's tombstoned row and returned
 * "already exists," inserting nothing). The same table-wide-unique-index shape existed on
 * `transactions.smsSourceKey` and `processed_messages.dedupeKey`, and budget lookups matched
 * only by `year`/`month+year` with no `userId` column to disambiguate at all — any of these
 * could let one account's local data collide with or absorb another's on a shared device.
 *
 * Paired with a logout-time `PesaMindDatabase.clearAllLocalData()` wipe (the primary fix,
 * closing the worse related bug: outbox rows have no owner and were pushed to the server under
 * whichever account's session happened to be active), this migration is defense-in-depth so a
 * future logout-path regression can't silently reopen the leak. `channels` already had a
 * `userId` column (just never queried by); `transactions`/`monthly_budgets`/`yearly_budgets`/
 * `processed_messages` get one here for the first time, defaulted to `''` for any pre-existing
 * row (safe: nothing can match a real account's id, and every row here predates this fix
 * anyway — a self-healing transitional state that clears itself on that row's next logout).
 *
 * `transactions.(channelId, providerTransactionId)` is deliberately left alone — it's
 * transitively user-safe already, since `channelId` FKs to exactly one `ChannelEntity.userId`
 * (see `TransactionEntity`'s doc comment).
 */
val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ── channels: normalizedSenderKey uniqueness becomes per-account ──
            db.execSQL("DROP INDEX IF EXISTS `index_channels_normalizedSenderKey`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_channels_userId_normalizedSenderKey` " +
                    "ON `channels` (`userId`, `normalizedSenderKey`)",
            )

            // ── transactions: add userId, smsSourceKey uniqueness becomes per-account ──
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `userId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("DROP INDEX IF EXISTS `index_transactions_smsSourceKey`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_userId_smsSourceKey` " +
                    "ON `transactions` (`userId`, `smsSourceKey`)",
            )

            // ── monthly_budgets: add userId, scope the month+year lookup index ──
            db.execSQL("ALTER TABLE `monthly_budgets` ADD COLUMN `userId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("DROP INDEX IF EXISTS `index_monthly_budgets_month_year`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_monthly_budgets_userId_month_year` " +
                    "ON `monthly_budgets` (`userId`, `month`, `year`)",
            )

            // ── yearly_budgets: add userId, scope the year lookup index ──
            db.execSQL("ALTER TABLE `yearly_budgets` ADD COLUMN `userId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("DROP INDEX IF EXISTS `index_yearly_budgets_year`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_yearly_budgets_userId_year` " +
                    "ON `yearly_budgets` (`userId`, `year`)",
            )

            // ── processed_messages: add userId, dedupeKey uniqueness becomes per-account ──
            db.execSQL("ALTER TABLE `processed_messages` ADD COLUMN `userId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("DROP INDEX IF EXISTS `index_processed_messages_dedupeKey`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_processed_messages_userId_dedupeKey` " +
                    "ON `processed_messages` (`userId`, `dedupeKey`)",
            )
        }
    }

/**
 * v6 -> v7: adds `lastSyncedTransactionsJson` to `monthly_budgets`/`yearly_budgets` — a
 * snapshot of the line-item list as of the last successful server sync, diffed against the
 * live `transactionsJson` at push time to build a precise `transaction_ops` add/update/delete
 * batch instead of the legacy full-replace field.
 *
 * That legacy field is a real, confirmed data-loss bug: [OutboxPusher] sent it on every
 * push (adding or deleting a single line item re-sent the whole local list, with no ids), and
 * the backend's update handler treats that field as authoritative — deleting any existing
 * server-side transaction whose id isn't present in the incoming list. Since the legacy
 * payload never carries ids at all, every push reset every transaction's server-side id, and
 * if the local cache was ever incomplete for any reason (a missed reconcile, a stale row —
 * exactly the class of bug fixed earlier this session), the push would silently delete
 * server-side history the client didn't know about, keeping only what it locally had —
 * confirmed in production: a user's whole month of budget transactions reduced to just the
 * one item they'd most recently added.
 *
 * Nullable, no backfill: every pre-existing row defaults to `NULL` (never synced under the
 * new scheme yet), which [BudgetRepository.buildTransactionOps] treats as an empty baseline —
 * the first push after this migration diffs against nothing, so it correctly emits "add" for
 * every current local item rather than misreading `NULL` as "delete everything" (`NULL` is
 * never a delete signal — only entries actually present in a non-null baseline are eligible
 * to become delete ops).
 */
val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `monthly_budgets` ADD COLUMN `lastSyncedTransactionsJson` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `yearly_budgets` ADD COLUMN `lastSyncedTransactionsJson` TEXT DEFAULT NULL")
        }
    }

/**
 * v7 -> v8: adds `channels.receivingNumber` and widens the provider-uniqueness index from
 * `(userId, normalizedSenderKey)` to `(userId, normalizedSenderKey, receivingNumber)`, so a
 * user can have more than one channel for the same provider (e.g. two MTN MoMo lines) told
 * apart by which phone number actually receives each one's SMS — see
 * [cc.dlabs.pesamind.core.database.entity.ChannelEntity]'s doc comment for the full design
 * (including why the new column defaults to the `UNSPECIFIED` sentinel rather than `NULL`).
 *
 * `DEFAULT 'UNSPECIFIED'` backfills every pre-existing row for free: since it's the same
 * sentinel for all of them, each existing single-channel-per-provider row keeps sole ownership
 * of its `(normalizedSenderKey, 'UNSPECIFIED')` pair and nothing about its matching behavior
 * changes until the user (or a resolved SIM-slot mapping) supplies a real number going forward.
 */
val MIGRATION_7_8 =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `channels` ADD COLUMN `receivingNumber` TEXT NOT NULL DEFAULT 'UNSPECIFIED'")
            db.execSQL("DROP INDEX IF EXISTS `index_channels_userId_normalizedSenderKey`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_channels_userId_normalizedSenderKey_receivingNumber` " +
                    "ON `channels` (`userId`, `normalizedSenderKey`, `receivingNumber`)",
            )
        }
    }
