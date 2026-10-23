package com.example.poswarnus.data.catalog

import com.example.poswarnus.data.local.CatalogTransactionRunner
import com.example.poswarnus.data.local.CategoryDao
import com.example.poswarnus.data.local.CategoryEntity
import com.example.poswarnus.data.local.MenuItemDao
import com.example.poswarnus.data.local.MenuItemEntity
import com.example.poswarnus.data.local.OptionDao
import com.example.poswarnus.data.local.OptionEntity
import com.example.poswarnus.data.local.OptionGroupDao
import com.example.poswarnus.data.local.OptionGroupEntity
import com.example.poswarnus.data.remote.CatalogRemoteDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToLong

data class Category(val id: String, val name: String, val sortOrder: Int)
data class MenuItem(val id: String, val categoryId: String, val name: String, val price: Double, val imageUrl: String?, val isAvailable: Boolean, val description: String? = null, val isFeatured: Boolean = false)

sealed interface RefreshResult {
    data object Success : RefreshResult
    data class Failure(val message: String) : RefreshResult
}

interface CatalogRepository {
    val categories: Flow<List<Category>>
    val menuItems: Flow<List<MenuItem>>
    fun observeOptionGroups(menuItemId: String): Flow<List<OptionGroup>>
    suspend fun refresh(): RefreshResult
}

class CatalogRepositoryImpl(
    private val categoryDao: CategoryDao,
    private val menuItemDao: MenuItemDao,
    private val optionGroupDao: OptionGroupDao,
    private val optionDao: OptionDao,
    private val remote: CatalogRemoteDataSource,
    private val transactionRunner: CatalogTransactionRunner,
) : CatalogRepository {
    override val categories: Flow<List<Category>> = categoryDao.observeAll().map { entities ->
        entities.map { Category(it.id, it.name, it.sortOrder) }
    }

    override val menuItems: Flow<List<MenuItem>> = menuItemDao.observeAll().map { entities ->
        entities.map { MenuItem(it.id, it.categoryId, it.name, it.price, it.imageUrl, it.isAvailable, it.description, it.isFeatured) }
    }

    override fun observeOptionGroups(menuItemId: String): Flow<List<OptionGroup>> =
        optionGroupDao.observeForItem(menuItemId).map { rows ->
            rows.mapNotNull { row ->
                val kind = parseKind(row.group.kind) ?: return@mapNotNull null
                val selection = parseSelection(row.group.selection) ?: return@mapNotNull null
                OptionGroup(
                    id = row.group.id,
                    menuItemId = row.group.menuItemId,
                    name = row.group.name,
                    kind = kind,
                    selection = selection,
                    isRequired = row.group.isRequired,
                    sortOrder = row.group.sortOrder,
                    // Room does not order @Relation results.
                    options = row.options.sortedBy { it.sortOrder }.map {
                        Option(it.id, it.groupId, it.name, it.priceDelta, it.sortOrder, it.isAvailable)
                    },
                )
            }.sortedBy { it.sortOrder }
        }

    override suspend fun refresh(): RefreshResult = try {
        val categoryDtos = remote.fetchCategories()
        val itemDtos = remote.fetchMenuItems()
        val groupDtos = remote.fetchOptionGroups()
        val optionDtos = remote.fetchOptions()
        // Full replace, atomically: Room ends up with exactly the fetched rows — nothing stale
        // (server-deleted rows are dropped) and nothing missing (no partial state if the process
        // dies mid-sync).
        transactionRunner.runInTransaction {
            categoryDao.clear()
            categoryDao.upsertAll(categoryDtos.map { CategoryEntity(it.id, it.name, it.sortOrder) })
            menuItemDao.clear()
            menuItemDao.upsertAll(itemDtos.map { MenuItemEntity(it.id, it.categoryId, it.name, it.price, it.imageUrl, it.isAvailable, it.description, it.isFeatured) })
            optionGroupDao.clear()
            optionGroupDao.upsertAll(groupDtos.mapNotNull { dto ->
                // Unknown kind/selection (blocked by DB check constraints) is skipped, never a crash.
                if (parseKind(dto.kind) == null || parseSelection(dto.selection) == null) return@mapNotNull null
                OptionGroupEntity(dto.id, dto.menuItemId, dto.name, dto.kind, dto.selection, dto.isRequired, dto.sortOrder)
            })
            optionDao.clear()
            optionDao.upsertAll(optionDtos.map { OptionEntity(it.id, it.groupId, it.name, it.priceDelta.roundToLong(), it.sortOrder, it.isAvailable) })
        }
        RefreshResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RefreshResult.Failure(e.message ?: "Failed to load catalog")
    }
}

private fun parseKind(raw: String): OptionKind? = OptionKind.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }

private fun parseSelection(raw: String): SelectionMode? = SelectionMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
