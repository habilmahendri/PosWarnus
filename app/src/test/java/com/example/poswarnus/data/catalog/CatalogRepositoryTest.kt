package com.example.poswarnus.data.catalog

import com.example.poswarnus.data.local.CatalogTransactionRunner
import com.example.poswarnus.data.local.CategoryDao
import com.example.poswarnus.data.local.CategoryEntity
import com.example.poswarnus.data.local.MenuItemDao
import com.example.poswarnus.data.local.MenuItemEntity
import com.example.poswarnus.data.remote.CatalogRemoteDataSource
import com.example.poswarnus.data.remote.CategoryDto
import com.example.poswarnus.data.remote.MenuItemDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeCategoryDao : CategoryDao {
    val stored = MutableStateFlow<List<CategoryEntity>>(emptyList())
    override fun observeAll(): Flow<List<CategoryEntity>> = stored
    override suspend fun upsertAll(categories: List<CategoryEntity>) {
        val byId = (stored.value + categories).associateBy { it.id }
        stored.value = byId.values.sortedBy { it.sortOrder }
    }
    override suspend fun clear() { stored.value = emptyList() }
}

private class FakeMenuItemDao : MenuItemDao {
    val stored = MutableStateFlow<List<MenuItemEntity>>(emptyList())
    override fun observeAll(): Flow<List<MenuItemEntity>> = stored
    override suspend fun upsertAll(items: List<MenuItemEntity>) {
        val byId = (stored.value + items).associateBy { it.id }
        stored.value = byId.values.toList()
    }
    override suspend fun clear() { stored.value = emptyList() }
}

/** Runs the block directly (no real transaction) — sufficient for unit-testing sync logic; Room's own atomicity guarantee is trusted library behavior. */
private class FakeCatalogTransactionRunner : CatalogTransactionRunner {
    override suspend fun runInTransaction(block: suspend () -> Unit) = block()
}

private class FakeCatalogRemoteDataSource(
    private val categoriesResult: Result<List<CategoryDto>> = Result.success(emptyList()),
    private val itemsResult: Result<List<MenuItemDto>> = Result.success(emptyList()),
) : CatalogRemoteDataSource {
    override suspend fun fetchCategories(): List<CategoryDto> = categoriesResult.getOrThrow()
    override suspend fun fetchMenuItems(): List<MenuItemDto> = itemsResult.getOrThrow()
}

class CatalogRepositoryTest {
    @Test
    fun `categories flow maps Room entities to domain models`() = runTest {
        val categoryDao = FakeCategoryDao()
        categoryDao.stored.value = listOf(CategoryEntity(id = "a", name = "Makanan", sortOrder = 1))
        val repository = CatalogRepositoryImpl(categoryDao, FakeMenuItemDao(), FakeCatalogRemoteDataSource(), FakeCatalogTransactionRunner())

        assertEquals(listOf(Category(id = "a", name = "Makanan", sortOrder = 1)), repository.categories.first())
    }

    @Test
    fun `refresh success fetches remote data and upserts it into Room`() = runTest {
        val categoryDao = FakeCategoryDao()
        val menuItemDao = FakeMenuItemDao()
        val remote = FakeCatalogRemoteDataSource(
            categoriesResult = Result.success(listOf(CategoryDto(id = "a", name = "Makanan", sortOrder = 1))),
            itemsResult = Result.success(listOf(MenuItemDto(id = "1", categoryId = "a", name = "Nasi Goreng", price = 25000.0, imageUrl = null, isAvailable = true))),
        )
        val repository = CatalogRepositoryImpl(categoryDao, menuItemDao, remote, FakeCatalogTransactionRunner())

        val result = repository.refresh()

        assertEquals(RefreshResult.Success, result)
        assertEquals(1, categoryDao.stored.value.size)
        assertEquals(1, menuItemDao.stored.value.size)
    }

    @Test
    fun `refresh failure returns Failure and does not clear existing cache`() = runTest {
        val categoryDao = FakeCategoryDao()
        categoryDao.stored.value = listOf(CategoryEntity(id = "a", name = "Makanan", sortOrder = 1))
        val remote = FakeCatalogRemoteDataSource(categoriesResult = Result.failure(RuntimeException("network down")))
        val repository = CatalogRepositoryImpl(categoryDao, FakeMenuItemDao(), remote, FakeCatalogTransactionRunner())

        val result = repository.refresh()

        assertTrue(result is RefreshResult.Failure)
        assertEquals(1, categoryDao.stored.value.size)
    }

    @Test
    fun `refresh removes rows no longer present on the server`() = runTest {
        val categoryDao = FakeCategoryDao()
        categoryDao.stored.value = listOf(
            CategoryEntity(id = "a", name = "Makanan", sortOrder = 1),
            CategoryEntity(id = "stale-category", name = "Dihapus", sortOrder = 2),
        )
        val menuItemDao = FakeMenuItemDao()
        menuItemDao.stored.value = listOf(
            MenuItemEntity(id = "1", categoryId = "a", name = "Nasi Goreng", price = 25000.0, imageUrl = null, isAvailable = true),
            MenuItemEntity(id = "stale-item", categoryId = "a", name = "Dihapus", price = 1000.0, imageUrl = null, isAvailable = true),
        )
        val remote = FakeCatalogRemoteDataSource(
            categoriesResult = Result.success(listOf(CategoryDto(id = "a", name = "Makanan", sortOrder = 1))),
            itemsResult = Result.success(listOf(MenuItemDto(id = "1", categoryId = "a", name = "Nasi Goreng", price = 25000.0, imageUrl = null, isAvailable = true))),
        )
        val repository = CatalogRepositoryImpl(categoryDao, menuItemDao, remote, FakeCatalogTransactionRunner())

        val result = repository.refresh()

        assertEquals(RefreshResult.Success, result)
        assertEquals(listOf("a"), categoryDao.stored.value.map { it.id })
        assertEquals(listOf("1"), menuItemDao.stored.value.map { it.id })
    }
}
