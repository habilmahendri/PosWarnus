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
class OptionGroupDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var groupDao: OptionGroupDao
    private lateinit var optionDao: OptionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        groupDao = db.optionGroupDao()
        optionDao = db.optionDao()
    }

    @After
    fun tearDown() { db.close() }

    private fun group(id: String, menuItemId: String, sortOrder: Int) =
        OptionGroupEntity(id, menuItemId, "G$id", "variant", "single", true, sortOrder)

    private fun option(id: String, groupId: String, sortOrder: Int = 0) =
        OptionEntity(id, groupId, "O$id", 1000L, sortOrder, true)

    @Test
    fun `observeForItem is empty when nothing stored`() = runTest {
        assertEquals(emptyList<OptionGroupWithOptions>(), groupDao.observeForItem("x").first())
    }

    @Test
    fun `observeForItem returns only groups of that item ordered by sortOrder`() = runTest {
        groupDao.upsertAll(listOf(group("g2", "a", 2), group("g1", "a", 1), group("g3", "b", 0)))
        val result = groupDao.observeForItem("a").first()
        assertEquals(listOf("g1", "g2"), result.map { it.group.id })
    }

    @Test
    fun `relation attaches the options of each group`() = runTest {
        groupDao.upsertAll(listOf(group("g1", "a", 1), group("g2", "a", 2)))
        optionDao.upsertAll(listOf(option("o1", "g1"), option("o2", "g1", 1), option("o3", "g2")))
        val result = groupDao.observeForItem("a").first()
        assertEquals(setOf("o1", "o2"), result[0].options.map { it.id }.toSet())
        assertEquals(listOf("o3"), result[1].options.map { it.id })
    }

    @Test
    fun `clear removes groups`() = runTest {
        groupDao.upsertAll(listOf(group("g1", "a", 1)))
        groupDao.clear()
        assertEquals(emptyList<OptionGroupWithOptions>(), groupDao.observeForItem("a").first())
    }
}
