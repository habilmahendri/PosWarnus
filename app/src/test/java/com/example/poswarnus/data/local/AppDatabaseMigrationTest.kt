package com.example.poswarnus.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val items = db.menuItemDao().observeAll().first()
            assertEquals(1, items.size)
            assertEquals("Nasi Goreng", items[0].name)
            assertNull(items[0].description)
            assertEquals(listOf("Makanan"), db.categoryDao().observeAll().first().map { it.name })
            assertEquals(emptyList<OptionGroupWithOptions>(), db.optionGroupDao().observeForItem("x").first())
        } finally {
            db.close()
            file.delete()
        }
    }
}
