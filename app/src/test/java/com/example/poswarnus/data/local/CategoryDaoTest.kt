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
class CategoryDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: CategoryDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.categoryDao()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `observeAll is empty when nothing is stored`() = runTest {
        assertEquals(emptyList<CategoryEntity>(), dao.observeAll().first())
    }

    @Test
    fun `upsertAll then observeAll returns the stored categories ordered by sortOrder`() = runTest {
        dao.upsertAll(listOf(
            CategoryEntity(id = "b", name = "Minuman", sortOrder = 2),
            CategoryEntity(id = "a", name = "Makanan", sortOrder = 1),
        ))
        val result = dao.observeAll().first()
        assertEquals(listOf("Makanan", "Minuman"), result.map { it.name })
    }

    @Test
    fun `upsertAll replaces an existing row with the same id`() = runTest {
        dao.upsertAll(listOf(CategoryEntity(id = "a", name = "Old", sortOrder = 1)))
        dao.upsertAll(listOf(CategoryEntity(id = "a", name = "New", sortOrder = 1)))
        assertEquals(1, dao.observeAll().first().size)
        assertEquals("New", dao.observeAll().first().first().name)
    }
}
