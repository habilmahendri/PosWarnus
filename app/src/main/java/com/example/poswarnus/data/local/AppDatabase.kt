package com.example.poswarnus.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CategoryEntity::class, MenuItemEntity::class, OptionGroupEntity::class, OptionEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun menuItemDao(): MenuItemDao
    abstract fun optionGroupDao(): OptionGroupDao
    abstract fun optionDao(): OptionDao
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

/** Runs a block of DAO calls atomically. Abstracted so repositories stay unit-testable without Room. */
interface CatalogTransactionRunner {
    suspend fun runInTransaction(block: suspend () -> Unit)
}

class RoomCatalogTransactionRunner(private val db: AppDatabase) : CatalogTransactionRunner {
    override suspend fun runInTransaction(block: suspend () -> Unit) {
        db.withTransaction { block() }
    }
}
