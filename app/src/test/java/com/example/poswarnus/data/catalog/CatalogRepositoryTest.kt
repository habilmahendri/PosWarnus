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
import com.example.poswarnus.data.local.OptionGroupWithOptions
import com.example.poswarnus.data.remote.CatalogRemoteDataSource
import com.example.poswarnus.data.remote.CategoryDto
import com.example.poswarnus.data.remote.MenuItemDto
import com.example.poswarnus.data.remote.NoSessionException
import com.example.poswarnus.data.remote.OptionDto
import com.example.poswarnus.data.remote.OptionGroupDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

private class FakeOptionGroupDao : OptionGroupDao {
    val stored = MutableStateFlow<List<OptionGroupEntity>>(emptyList())
    // Joins the options fake unordered, like Room's @Relation, so the repository must sort.
    var optionDao: FakeOptionDao? = null
    override fun observeForItem(menuItemId: String): Flow<List<OptionGroupWithOptions>> =
        stored.map { groups ->
            groups.filter { it.menuItemId == menuItemId }.sortedBy { it.sortOrder }.map { g ->
                OptionGroupWithOptions(g, optionDao?.stored?.value.orEmpty().filter { it.groupId == g.id })
            }
        }
    override suspend fun upsertAll(groups: List<OptionGroupEntity>) {
        stored.value = (stored.value + groups).associateBy { it.id }.values.toList()
    }
    override suspend fun clear() { stored.value = emptyList() }
}

private class FakeOptionDao : OptionDao {
    val stored = MutableStateFlow<List<OptionEntity>>(emptyList())
    override suspend fun upsertAll(options: List<OptionEntity>) {
        stored.value = (stored.value + options).associateBy { it.id }.values.toList()
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
    private val optionGroupsResult: Result<List<OptionGroupDto>> = Result.success(emptyList()),
    private val optionsResult: Result<List<OptionDto>> = Result.success(emptyList()),
) : CatalogRemoteDataSource {
    override suspend fun fetchOptionGroups(): List<OptionGroupDto> = optionGroupsResult.getOrThrow()
    override suspend fun fetchOptions(): List<OptionDto> = optionsResult.getOrThrow()
    override suspend fun fetchCategories(): List<CategoryDto> = categoriesResult.getOrThrow()
    override suspend fun fetchMenuItems(): List<MenuItemDto> = itemsResult.getOrThrow()
}

class CatalogRepositoryTest {
    @Test
    fun `categories flow maps Room entities to domain models`() = runTest {
        val categoryDao = FakeCategoryDao()
        categoryDao.stored.value = listOf(CategoryEntity(id = "a", name = "Makanan", sortOrder = 1))
        val repository = CatalogRepositoryImpl(categoryDao, FakeMenuItemDao(), FakeOptionGroupDao(), FakeOptionDao(), FakeCatalogRemoteDataSource(), FakeCatalogTransactionRunner())

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
        val repository = CatalogRepositoryImpl(categoryDao, menuItemDao, FakeOptionGroupDao(), FakeOptionDao(), remote, FakeCatalogTransactionRunner())

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
        val repository = CatalogRepositoryImpl(categoryDao, FakeMenuItemDao(), FakeOptionGroupDao(), FakeOptionDao(), remote, FakeCatalogTransactionRunner())

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
        val repository = CatalogRepositoryImpl(categoryDao, menuItemDao, FakeOptionGroupDao(), FakeOptionDao(), remote, FakeCatalogTransactionRunner())

        val result = repository.refresh()

        assertEquals(RefreshResult.Success, result)
        assertEquals(listOf("a"), categoryDao.stored.value.map { it.id })
        assertEquals(listOf("1"), menuItemDao.stored.value.map { it.id })
    }

    private class Fixture(remote: CatalogRemoteDataSource) {
        val categoryDao = FakeCategoryDao()
        val menuItemDao = FakeMenuItemDao()
        val optionDao = FakeOptionDao()
        val groupDao = FakeOptionGroupDao().also { it.optionDao = optionDao }
        val repository = CatalogRepositoryImpl(categoryDao, menuItemDao, groupDao, optionDao, remote, FakeCatalogTransactionRunner())
    }

    private val groupDto = OptionGroupDto("g1", "1", "Ukuran", "variant", "single", true, 0)
    private val optionDto = OptionDto("o1", "g1", "Large", 10000.0, 1, true)

    @Test
    fun `refresh writes categories items groups and options`() = runTest {
        val f = Fixture(FakeCatalogRemoteDataSource(
            categoriesResult = Result.success(listOf(CategoryDto("a", "Makanan", 1))),
            itemsResult = Result.success(listOf(MenuItemDto("1", "a", "Nasi Goreng", 25000.0, null, true))),
            optionGroupsResult = Result.success(listOf(groupDto)),
            optionsResult = Result.success(listOf(optionDto)),
        ))

        assertEquals(RefreshResult.Success, f.repository.refresh())

        assertEquals(1, f.categoryDao.stored.value.size)
        assertEquals(1, f.menuItemDao.stored.value.size)
        assertEquals(listOf("g1"), f.groupDao.stored.value.map { it.id })
        assertEquals(listOf("o1"), f.optionDao.stored.value.map { it.id })
    }

    @Test
    fun `refresh stores description`() = runTest {
        val f = Fixture(FakeCatalogRemoteDataSource(
            itemsResult = Result.success(listOf(
                MenuItemDto("1", "a", "Nasi Goreng", 25000.0, null, true, description = "Pedas gurih"),
                MenuItemDto("2", "a", "Es Teh", 5000.0, null, true),
            )),
        ))

        f.repository.refresh()

        assertEquals("Pedas gurih", f.menuItemDao.stored.value.first { it.id == "1" }.description)
        assertNull(f.menuItemDao.stored.value.first { it.id == "2" }.description)
        assertEquals("Pedas gurih", f.repository.menuItems.first().first { it.id == "1" }.description)
    }

    @Test
    fun `observeOptionGroups returns groups with options sorted by sortOrder`() = runTest {
        val f = Fixture(FakeCatalogRemoteDataSource())
        f.groupDao.stored.value = listOf(
            OptionGroupEntity("g2", "1", "Tambahan", "modifier", "multi", false, 2),
            OptionGroupEntity("g1", "1", "Ukuran", "variant", "single", true, 1),
        )
        f.optionDao.stored.value = listOf(
            OptionEntity("o3", "g1", "Large", 10000, 3, true),
            OptionEntity("o1", "g1", "Regular", 0, 1, true),
            OptionEntity("o2", "g1", "Medium", 5000, 2, false),
        )

        val groups = f.repository.observeOptionGroups("1").first()

        assertEquals(listOf("g1", "g2"), groups.map { it.id })
        assertEquals(listOf("o1", "o2", "o3"), groups[0].options.map { it.id })
        assertEquals(OptionKind.VARIANT, groups[0].kind)
        assertEquals(SelectionMode.SINGLE, groups[0].selection)
        assertEquals(OptionKind.MODIFIER, groups[1].kind)
        assertEquals(SelectionMode.MULTI, groups[1].selection)
        assertEquals(Option("o2", "g1", "Medium", 5000L, 2, false), groups[0].options[1])
    }

    @Test
    fun `observeOptionGroups filters by menu item`() = runTest {
        val f = Fixture(FakeCatalogRemoteDataSource())
        f.groupDao.stored.value = listOf(
            OptionGroupEntity("g1", "1", "Ukuran", "variant", "single", true, 1),
            OptionGroupEntity("g9", "2", "Gula", "modifier", "single", false, 1),
        )

        assertEquals(listOf("g9"), f.repository.observeOptionGroups("2").first().map { it.id })
        assertTrue(f.repository.observeOptionGroups("none").first().isEmpty())
    }

    private fun prePopulated(f: Fixture) {
        f.categoryDao.stored.value = listOf(CategoryEntity("old-c", "Lama", 1))
        f.menuItemDao.stored.value = listOf(MenuItemEntity("old-i", "old-c", "Lama", 1000.0, null, true))
        f.groupDao.stored.value = listOf(OptionGroupEntity("old-g", "old-i", "Lama", "modifier", "single", false, 1))
        f.optionDao.stored.value = listOf(OptionEntity("old-o", "old-g", "Lama", 0, 1, true))
    }

    private fun assertUntouched(f: Fixture) {
        assertEquals(listOf("old-c"), f.categoryDao.stored.value.map { it.id })
        assertEquals(listOf("old-i"), f.menuItemDao.stored.value.map { it.id })
        assertEquals(listOf("old-g"), f.groupDao.stored.value.map { it.id })
        assertEquals(listOf("old-o"), f.optionDao.stored.value.map { it.id })
    }

    @Test
    fun `failing options fetch leaves previous cache intact and returns Failure`() = runTest {
        val f = Fixture(FakeCatalogRemoteDataSource(
            categoriesResult = Result.success(listOf(CategoryDto("a", "Makanan", 1))),
            itemsResult = Result.success(listOf(MenuItemDto("1", "a", "Nasi Goreng", 25000.0, null, true))),
            optionGroupsResult = Result.success(listOf(groupDto)),
            optionsResult = Result.failure(RuntimeException("options down")),
        ))
        prePopulated(f)

        assertTrue(f.repository.refresh() is RefreshResult.Failure)
        assertUntouched(f)
    }

    @Test
    fun `failing option groups fetch leaves cache intact`() = runTest {
        val f = Fixture(FakeCatalogRemoteDataSource(
            categoriesResult = Result.success(listOf(CategoryDto("a", "Makanan", 1))),
            optionGroupsResult = Result.failure(RuntimeException("groups down")),
            optionsResult = Result.success(listOf(optionDto)),
        ))
        prePopulated(f)

        assertTrue(f.repository.refresh() is RefreshResult.Failure)
        assertUntouched(f)
    }

    @Test
    fun `option fetch without a session returns Failure and leaves all four stores untouched`() = runTest {
        // Mirrors SupabaseCatalogRemoteDataSource: anon reads of the RLS-protected option tables
        // would return [] and wipe the cache, so the source throws when no session exists.
        val noSession = object : CatalogRemoteDataSource {
            override suspend fun fetchCategories() = listOf(CategoryDto("a", "Makanan", 1))
            override suspend fun fetchMenuItems() = listOf(MenuItemDto("1", "a", "Nasi Goreng", 25000.0, null, true))
            override suspend fun fetchOptionGroups(): List<OptionGroupDto> = throw NoSessionException()
            override suspend fun fetchOptions(): List<OptionDto> = throw NoSessionException()
        }
        val f = Fixture(noSession)
        prePopulated(f)

        assertTrue(f.repository.refresh() is RefreshResult.Failure)
        assertUntouched(f)
    }

    @Test
    fun `unknown kind group is skipped without failing refresh`() = runTest {
        val f = Fixture(FakeCatalogRemoteDataSource(
            optionGroupsResult = Result.success(listOf(groupDto, groupDto.copy(id = "bad", kind = "weird"), groupDto.copy(id = "bad2", selection = "nope"))),
        ))

        assertEquals(RefreshResult.Success, f.repository.refresh())
        assertEquals(listOf("g1"), f.groupDao.stored.value.map { it.id })
    }

    @Test
    fun `unparseable group already in cache is skipped when observing`() = runTest {
        val f = Fixture(FakeCatalogRemoteDataSource())
        f.groupDao.stored.value = listOf(
            OptionGroupEntity("g1", "1", "Ukuran", "variant", "single", true, 1),
            OptionGroupEntity("bad", "1", "X", "weird", "single", true, 2),
        )

        assertEquals(listOf("g1"), f.repository.observeOptionGroups("1").first().map { it.id })
    }

    @Test
    fun `price_delta 5000_0 maps to 5000L`() = runTest {
        val f = Fixture(FakeCatalogRemoteDataSource(optionsResult = Result.success(listOf(optionDto.copy(priceDelta = 5000.0)))))

        f.repository.refresh()

        assertEquals(5000L, f.optionDao.stored.value.single().priceDelta)
    }
}
