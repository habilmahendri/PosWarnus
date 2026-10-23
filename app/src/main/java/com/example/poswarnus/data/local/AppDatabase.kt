package com.example.poswarnus.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class, MenuItemEntity::class, OptionGroupEntity::class, OptionEntity::class,
        TaxSettingsEntity::class, TransactionEntity::class, TransactionLineEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun menuItemDao(): MenuItemDao
    abstract fun optionGroupDao(): OptionGroupDao
    abstract fun optionDao(): OptionDao
    abstract fun taxSettingsDao(): TaxSettingsDao
    abstract fun transactionDao(): TransactionDao
}

/** v1 -> v2: menu item description plus option groups/options. Keeps the cached catalog (offline-safe). */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE menu_items ADD COLUMN description TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS option_groups (id TEXT NOT NULL, menuItemId TEXT NOT NULL, name TEXT NOT NULL, " +
                "kind TEXT NOT NULL, selection TEXT NOT NULL, isRequired INTEGER NOT NULL, sortOrder INTEGER NOT NULL, PRIMARY KEY(id))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_option_groups_menuItemId ON option_groups (menuItemId)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS options (id TEXT NOT NULL, groupId TEXT NOT NULL, name TEXT NOT NULL, " +
                "priceDelta INTEGER NOT NULL, sortOrder INTEGER NOT NULL, isAvailable INTEGER NOT NULL, PRIMARY KEY(id))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_options_groupId ON options (groupId)")
    }
}

/** v2 -> v3: admin-curated featured flag on menu items. Existing rows default to not featured. */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE menu_items ADD COLUMN isFeatured INTEGER NOT NULL DEFAULT 0")
    }
}

/** v3 -> v4: cached tax config plus locally stored sales. Purely additive; the catalog cache is untouched. */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `tax_settings` (`id` INTEGER NOT NULL, `label` TEXT NOT NULL, `basisPoints` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transactions` (`id` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `cashierId` TEXT NOT NULL, " +
                "`subtotal` INTEGER NOT NULL, `taxLabel` TEXT NOT NULL, `taxBasisPoints` INTEGER NOT NULL, `taxAmount` INTEGER NOT NULL, " +
                "`grandTotal` INTEGER NOT NULL, `cashReceived` INTEGER NOT NULL, `changeAmount` INTEGER NOT NULL, " +
                "`isTraining` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transaction_lines` (`id` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `menuItemId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `basePrice` INTEGER NOT NULL, `quantity` INTEGER NOT NULL, `selectionsJson` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_lines_transactionId` ON `transaction_lines` (`transactionId`)")
    }
}

/** v4 -> v5: upload bookkeeping on sales. Existing sales were never uploaded, so they default to PENDING. */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'PENDING'")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `syncedAt` INTEGER")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `syncAttempts` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `lastSyncError` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_syncStatus` ON `transactions` (`syncStatus`)")
    }
}

/** Runs a block of DAO calls atomically. Abstracted so repositories stay unit-testable without Room. */
interface CatalogTransactionRunner {
    suspend fun runInTransaction(block: suspend () -> Unit)
}

class RoomCatalogTransactionRunner(private val db: AppDatabase) : CatalogTransactionRunner {
    override suspend fun runInTransaction(block: suspend () -> Unit) {
        db.withTransaction { block() }
    }
}
