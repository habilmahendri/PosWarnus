package com.example.poswarnus.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {
    @Test
    fun `migration 1 to 2 keeps existing rows and creates option tables`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.cacheDir, "migration_test.db").apply { delete() }

        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        raw.execSQL("CREATE TABLE IF NOT EXISTS categories (id TEXT NOT NULL, name TEXT NOT NULL, sortOrder INTEGER NOT NULL, PRIMARY KEY(id))")
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS menu_items (id TEXT NOT NULL, categoryId TEXT NOT NULL, name TEXT NOT NULL, " +
                "price REAL NOT NULL, imageUrl TEXT, isAvailable INTEGER NOT NULL, PRIMARY KEY(id))",
        )
        raw.execSQL("INSERT INTO categories VALUES ('c1', 'Makanan', 1)")
        raw.execSQL("INSERT INTO menu_items VALUES ('m1', 'c1', 'Nasi Goreng', 25000.0, NULL, 1)")
        raw.version = 1
        raw.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val items = db.menuItemDao().observeAll().first()
            assertEquals(1, items.size)
            assertEquals("Nasi Goreng", items[0].name)
            assertNull(items[0].description)
            assertEquals(false, items[0].isFeatured)
            assertEquals(listOf("Makanan"), db.categoryDao().observeAll().first().map { it.name })
            assertEquals(emptyList<OptionGroupWithOptions>(), db.optionGroupDao().observeForItem("x").first())
        } finally {
            db.close()
            file.delete()
        }
    }

    @Test
    fun `v2 database upgrades with MIGRATION_2_3 keeping rows and defaulting isFeatured to false`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.cacheDir, "migration_test_v2.db").apply { delete() }

        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        raw.execSQL("CREATE TABLE IF NOT EXISTS categories (id TEXT NOT NULL, name TEXT NOT NULL, sortOrder INTEGER NOT NULL, PRIMARY KEY(id))")
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS menu_items (id TEXT NOT NULL, categoryId TEXT NOT NULL, name TEXT NOT NULL, " +
                "price REAL NOT NULL, imageUrl TEXT, isAvailable INTEGER NOT NULL, description TEXT, PRIMARY KEY(id))",
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS option_groups (id TEXT NOT NULL, menuItemId TEXT NOT NULL, name TEXT NOT NULL, " +
                "kind TEXT NOT NULL, selection TEXT NOT NULL, isRequired INTEGER NOT NULL, sortOrder INTEGER NOT NULL, PRIMARY KEY(id))",
        )
        raw.execSQL("CREATE INDEX IF NOT EXISTS index_option_groups_menuItemId ON option_groups (menuItemId)")
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS options (id TEXT NOT NULL, groupId TEXT NOT NULL, name TEXT NOT NULL, " +
                "priceDelta INTEGER NOT NULL, sortOrder INTEGER NOT NULL, isAvailable INTEGER NOT NULL, PRIMARY KEY(id))",
        )
        raw.execSQL("CREATE INDEX IF NOT EXISTS index_options_groupId ON options (groupId)")
        raw.execSQL("INSERT INTO categories VALUES ('c1', 'Makanan', 1)")
        raw.execSQL("INSERT INTO menu_items VALUES ('m1', 'c1', 'Nasi Goreng', 25000.0, NULL, 1, 'Pedas')")
        raw.execSQL("INSERT INTO option_groups VALUES ('g1', 'm1', 'Ukuran', 'variant', 'single', 1, 1)")
        raw.execSQL("INSERT INTO options VALUES ('o1', 'g1', 'Pedas', 0, 1, 1)")
        raw.version = 2
        raw.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val items = db.menuItemDao().observeAll().first()
            assertEquals(1, items.size)
            assertEquals("Nasi Goreng", items[0].name)
            assertEquals("Pedas", items[0].description)
            assertEquals(false, items[0].isFeatured)
            assertEquals(listOf("Makanan"), db.categoryDao().observeAll().first().map { it.name })
            val groups = db.optionGroupDao().observeForItem("m1").first()
            assertEquals(listOf("g1"), groups.map { it.group.id })
            assertEquals(listOf("o1"), groups.single().options.map { it.id })
        } finally {
            db.close()
            file.delete()
        }
    }

    @Test
    fun `v3 database upgrades with MIGRATION_3_4 keeping catalog rows and creating the new tables`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.cacheDir, "migration_test_v3.db").apply { delete() }

        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        raw.execSQL("CREATE TABLE IF NOT EXISTS categories (id TEXT NOT NULL, name TEXT NOT NULL, sortOrder INTEGER NOT NULL, PRIMARY KEY(id))")
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS menu_items (id TEXT NOT NULL, categoryId TEXT NOT NULL, name TEXT NOT NULL, " +
                "price REAL NOT NULL, imageUrl TEXT, isAvailable INTEGER NOT NULL, description TEXT, " +
                "isFeatured INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(id))",
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS option_groups (id TEXT NOT NULL, menuItemId TEXT NOT NULL, name TEXT NOT NULL, " +
                "kind TEXT NOT NULL, selection TEXT NOT NULL, isRequired INTEGER NOT NULL, sortOrder INTEGER NOT NULL, PRIMARY KEY(id))",
        )
        raw.execSQL("CREATE INDEX IF NOT EXISTS index_option_groups_menuItemId ON option_groups (menuItemId)")
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS options (id TEXT NOT NULL, groupId TEXT NOT NULL, name TEXT NOT NULL, " +
                "priceDelta INTEGER NOT NULL, sortOrder INTEGER NOT NULL, isAvailable INTEGER NOT NULL, PRIMARY KEY(id))",
        )
        raw.execSQL("CREATE INDEX IF NOT EXISTS index_options_groupId ON options (groupId)")
        raw.execSQL("INSERT INTO categories VALUES ('c1', 'Makanan', 1)")
        raw.execSQL("INSERT INTO menu_items VALUES ('m1', 'c1', 'Nasi Goreng', 25000.0, NULL, 1, 'Pedas', 1)")
        raw.version = 3
        raw.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val items = db.menuItemDao().observeAll().first()
            assertEquals(listOf("Nasi Goreng"), items.map { it.name })
            assertEquals(true, items.single().isFeatured)
            assertEquals(listOf("Makanan"), db.categoryDao().observeAll().first().map { it.name })
            assertEquals(emptyList<TransactionEntity>(), db.transactionDao().observeAll().first())
            assertNull(db.taxSettingsDao().observe().first())
        } finally {
            db.close()
            file.delete()
        }
    }

    @Test
    fun `v4 database upgrades with MIGRATION_4_5 keeping sales and marking them PENDING`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.cacheDir, "migration_test_v4.db").apply { delete() }

        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        raw.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        raw.execSQL("CREATE TABLE IF NOT EXISTS `menu_items` (`id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `name` TEXT NOT NULL, `price` REAL NOT NULL, `imageUrl` TEXT, `isAvailable` INTEGER NOT NULL, `description` TEXT, `isFeatured` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
        raw.execSQL("CREATE TABLE IF NOT EXISTS `option_groups` (`id` TEXT NOT NULL, `menuItemId` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `selection` TEXT NOT NULL, `isRequired` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        raw.execSQL("CREATE INDEX IF NOT EXISTS `index_option_groups_menuItemId` ON `option_groups` (`menuItemId`)")
        raw.execSQL("CREATE TABLE IF NOT EXISTS `options` (`id` TEXT NOT NULL, `groupId` TEXT NOT NULL, `name` TEXT NOT NULL, `priceDelta` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `isAvailable` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        raw.execSQL("CREATE INDEX IF NOT EXISTS `index_options_groupId` ON `options` (`groupId`)")
        raw.execSQL("CREATE TABLE IF NOT EXISTS `tax_settings` (`id` INTEGER NOT NULL, `label` TEXT NOT NULL, `basisPoints` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        raw.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`id` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `cashierId` TEXT NOT NULL, `subtotal` INTEGER NOT NULL, `taxLabel` TEXT NOT NULL, `taxBasisPoints` INTEGER NOT NULL, `taxAmount` INTEGER NOT NULL, `grandTotal` INTEGER NOT NULL, `cashReceived` INTEGER NOT NULL, `changeAmount` INTEGER NOT NULL, `isTraining` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
        raw.execSQL("CREATE TABLE IF NOT EXISTS `transaction_lines` (`id` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `menuItemId` TEXT NOT NULL, `name` TEXT NOT NULL, `basePrice` INTEGER NOT NULL, `quantity` INTEGER NOT NULL, `selectionsJson` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        raw.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_lines_transactionId` ON `transaction_lines` (`transactionId`)")
        raw.execSQL("INSERT INTO categories VALUES ('c1', 'Makanan', 1)")
        raw.execSQL("INSERT INTO menu_items VALUES ('m1', 'c1', 'Nasi Goreng', 25000.0, NULL, 1, 'Pedas', 1)")
        raw.execSQL("INSERT INTO transactions VALUES ('t1', 1000, 'u1', 43000, 'Pajak', 1000, 4300, 47300, 50000, 2700, 0)")
        raw.execSQL("INSERT INTO transactions VALUES ('t2', 2000, 'u1', 25000, 'Pajak', 1000, 2500, 27500, 30000, 2500, 1)")
        raw.execSQL("INSERT INTO transaction_lines VALUES ('t1:0', 't1', 'm1', 'Nasi Goreng', 25000, 1, '[]')")
        raw.execSQL("INSERT INTO transaction_lines VALUES ('t1:1', 't1', 'm2', 'Es Teh', 18000, 1, '[]')")
        raw.execSQL("INSERT INTO transaction_lines VALUES ('t2:0', 't2', 'm1', 'Nasi Goreng', 25000, 1, '[]')")
        raw.version = 4
        raw.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.transactionDao()
            val byId = dao.observeAll().first().associateBy { it.id }
            assertEquals(setOf("t1", "t2"), byId.keys)
            assertEquals(47300L, byId.getValue("t1").grandTotal)
            assertEquals(27500L, byId.getValue("t2").grandTotal)
            byId.values.forEach {
                assertEquals("PENDING", it.syncStatus)
                assertNull(it.syncedAt)
                assertEquals(0, it.syncAttempts)
                assertNull(it.lastSyncError)
            }
            assertEquals(listOf("Nasi Goreng", "Es Teh"), dao.linesFor("t1").map { it.name })
            assertEquals(listOf("Nasi Goreng"), dao.linesFor("t2").map { it.name })
            assertEquals(listOf("Nasi Goreng"), db.menuItemDao().observeAll().first().map { it.name })
            assertEquals(listOf("Makanan"), db.categoryDao().observeAll().first().map { it.name })

            val indexNames = mutableListOf<String>()
            db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'index'").use {
                while (it.moveToNext()) indexNames += it.getString(0)
            }
            assertTrue(indexNames.toString(), "index_transactions_syncStatus" in indexNames)

            assertEquals(listOf("t1"), dao.pendingBatch("u1", Long.MIN_VALUE, "", 10).map { it.id })
        } finally {
            db.close()
            file.delete()
        }
    }
}
