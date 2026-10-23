package com.example.poswarnus.data.local

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MenuItemDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MenuItemDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.menuItemDao()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `observeAll is empty when nothing is stored`() = runTest {
        assertEquals(emptyList<MenuItemEntity>(), dao.observeAll().first())
    }

    @Test
    fun `upsertAll then observeAll returns the stored items`() = runTest {
        val item = MenuItemEntity(id = "1", categoryId = "a", name = "Nasi Goreng", price = 25000.0, imageUrl = null, isAvailable = true)
        dao.upsertAll(listOf(item))
        assertEquals(listOf(item), dao.observeAll().first())
    }

    @Test
    fun `isFeatured round-trips through the dao`() = runTest {
        val featured = MenuItemEntity(id = "1", categoryId = "a", name = "A", price = 1.0, imageUrl = null, isAvailable = true, isFeatured = true)
        val plain = MenuItemEntity(id = "2", categoryId = "a", name = "B", price = 2.0, imageUrl = null, isAvailable = true)
        dao.upsertAll(listOf(featured, plain))
        val byId = dao.observeAll().first().associateBy { it.id }
        assertEquals(true, byId.getValue("1").isFeatured)
        assertEquals(false, byId.getValue("2").isFeatured)
    }

    @Test
    fun `upsertAll replaces an existing row with the same id`() = runTest {
        dao.upsertAll(listOf(MenuItemEntity(id = "1", categoryId = "a", name = "Old", price = 1.0, imageUrl = null, isAvailable = true)))
        dao.upsertAll(listOf(MenuItemEntity(id = "1", categoryId = "a", name = "New", price = 2.0, imageUrl = null, isAvailable = false)))
        val result = dao.observeAll().first()
        assertEquals(1, result.size)
        assertEquals("New", result.first().name)
        assertEquals(false, result.first().isAvailable)
    }
}
