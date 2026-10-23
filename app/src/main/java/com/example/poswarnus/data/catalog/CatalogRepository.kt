package com.example.poswarnus.data.catalog

import com.example.poswarnus.data.local.CatalogTransactionRunner
import com.example.poswarnus.data.local.CategoryDao
import com.example.poswarnus.data.local.CategoryEntity
import com.example.poswarnus.data.local.MenuItemDao
import com.example.poswarnus.data.local.MenuItemEntity
import com.example.poswarnus.data.remote.CatalogRemoteDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Category(val id: String, val name: String, val sortOrder: Int)
data class MenuItem(val id: String, val categoryId: String, val name: String, val price: Double, val imageUrl: String?, val isAvailable: Boolean, val description: String? = null)

sealed interface RefreshResult {
    data object Success : RefreshResult
    data class Failure(val message: String) : RefreshResult
}

interface CatalogRepository {
    val categories: Flow<List<Category>>
    val menuItems: Flow<List<MenuItem>>
    suspend fun refresh(): RefreshResult
}

class CatalogRepositoryImpl(
    private val categoryDao: CategoryDao,
    private val menuItemDao: MenuItemDao,
    private val remote: CatalogRemoteDataSource,
    private val transactionRunner: CatalogTransactionRunner,
) : CatalogRepository {
    override val categories: Flow<List<Category>> = categoryDao.observeAll().map { entities ->
        entities.map { Category(it.id, it.name, it.sortOrder) }
    }

    override val menuItems: Flow<List<MenuItem>> = menuItemDao.observeAll().map { entities ->
        entities.map { MenuItem(it.id, it.categoryId, it.name, it.price, it.imageUrl, it.isAvailable) }
    }

    override suspend fun refresh(): RefreshResult = try {
        val categoryDtos = remote.fetchCategories()
        val itemDtos = remote.fetchMenuItems()
        // Full replace, atomically: Room ends up with exactly the fetched rows — nothing stale
        // (server-deleted rows are dropped) and nothing missing (no partial state if the process
        // dies mid-sync).
        transactionRunner.runInTransaction {
            categoryDao.clear()
            categoryDao.upsertAll(categoryDtos.map { CategoryEntity(it.id, it.name, it.sortOrder) })
            menuItemDao.clear()
            menuItemDao.upsertAll(itemDtos.map { MenuItemEntity(it.id, it.categoryId, it.name, it.price, it.imageUrl, it.isAvailable) })
        }
        RefreshResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RefreshResult.Failure(e.message ?: "Failed to load catalog")
    }
}
