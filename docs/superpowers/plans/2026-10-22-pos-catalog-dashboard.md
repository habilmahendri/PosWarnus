# POS Catalog & Dashboard (Phase 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Phase 1 Dashboard placeholder with a real, offline-capable catalog browsing screen — category/search-filterable menu grid backed by Supabase, cached locally in Room — themed per the extracted design reference, on a responsive (phone/tablet) layout.

**Architecture:** A `CatalogRepository` is the single source of truth the UI reads from: it exposes Room `Flow`s (`categories`, `menuItems`) that the UI collects directly, and a `refresh()` suspend function that fetches from Supabase Postgrest and upserts into Room. The UI never talks to Supabase directly and never blocks on network — a stale/offline Room cache is always immediately visible, matching PRD 4.9's offline-first intent for the catalog read path (write-path/sync-queue for transactions is a later phase). `DashboardViewModel` combines the repository's flows with local search/category-filter UI state via `combine`. The app's `MaterialTheme` is rebuilt from the design reference's tokens (colors, Inter typography, 6dp/10dp radii) so this and all later screens share one visual identity.

**Tech Stack:** Room (`room-runtime`, `room-ktx`, KSP `room-compiler`), Coil (`coil-compose`) for image loading, Robolectric for JVM-executable Room DAO tests (no device available in this dev environment), existing Hilt/Supabase/Compose stack from Phase 1.

**Spec:** [docs/PRD-pos.md](../../PRD-pos.md) section 4.3 (Dashboard — Catalog Browsing) and section 4.9 (Offline-First, read-path only for this phase) · [docs/design-reference.md](../../design-reference.md) (design tokens + Dashboard screen breakdown) — this plan implements both; conflicts between them resolve in the PRD's favor.

**Backend contract (already applied to the live Supabase project "POS Warnus", `mpprrdmyxdhdcvrponra`, via migration `create_catalog_tables`):**
```sql
categories(id uuid pk, name text, sort_order int, created_at timestamptz)
menu_items(id uuid pk, category_id uuid fk -> categories.id, name text, price numeric(12,2), image_url text nullable, is_available boolean default true, created_at timestamptz)
```
Both tables have RLS enabled with a public (`anon`, `authenticated`) `SELECT`-only policy. Sample data (3 categories, 9 items, one `is_available = false`) is seeded for manual testing. Any future admin-website schema change must stay backward compatible with this contract or this plan's DTOs need updating.

## Global Constraints

- Package namespace: `com.example.poswarnus` (existing).
- Currency formatting: Indonesian Rupiah, no decimals, thousands separator as a dot, prefixed `Rp` with no space (e.g. `Rp25.000`) — per design-reference.md's Typography section.
- Design tokens (exact values from [design-reference.md](../../design-reference.md)): accent `#C2410C`, accent dark `#9A3412`, accent tint `#FDEEE4`, background `#F3F2EE`, surface `#FFFFFF`, text `#1C1B19`, text secondary `#5C5951`, line `#E3E0D8`, line strong `#CBC7BB`, error `#B91C1C`, error tint `#FCEBEA`. Font family: Inter (400/500/600/700), fallback system sans. Radius: 6dp controls, 10dp cards/dialogs. Spacing scale: 4/8/12/16/24/32/48dp.
- Responsive breakpoint: tablet layout (catalog + right panel side-by-side) at `maxWidth >= 840.dp`; below that, single-column phone layout. Use `BoxWithConstraints` for this check — no new window-size-class dependency needed for a single breakpoint.
- Out-of-stock items (`is_available = false`) are visually dimmed and not clickable (PRD 4.3 acceptance criteria) — this plan does not yet build the cart or detail dialog (Phase 3), so "not clickable" here means the catalog card's `onClick` is a no-op / absent for unavailable items, not a dialog launch.
- The right-hand panel on tablet layout is an empty placeholder in this phase (cart is Phase 3) — do not build cart UI or state here.
- No business logic beyond catalog browsing (search, category filter, offline cache) belongs in this plan.

## Review Focus

- Supabase fetch fails (network error, RLS/auth issue) on first-ever launch with an empty Room cache — the PRD requires an explicit "can't load catalog, check connection" state, not a blank/empty-looking screen indistinguishable from "no items in this category."
- Supabase fetch fails but Room already has a previously-cached catalog — the UI must keep showing the stale cache (not clear it or show an error overlay that hides the content) and surface a non-blocking "offline/last synced" indicator.
- Search text filters within the already-active category selection (both filters combine), not independently — typing a query while "Minuman" is selected should only search within `Minuman` items, per PRD 4.3 ("combined with the active category filter").
- An out-of-stock item must never be tappable/clickable, even though it is still visible in the grid.
- Selecting a category with zero matching items (or a search with no matches) shows an explicit empty state, not a silently blank grid.

---

### Task 1: App theme — color tokens, typography, shapes

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/example/poswarnus/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/example/poswarnus/ui/theme/Type.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: a `PosColors` object (or equivalent named `Color` vals) matching the Global Constraints' hex values, wired into `MaterialTheme.colorScheme` via `POSWarNusTheme`'s `lightColorScheme(...)` — later tasks' Compose UI (`DashboardScreen`) reads colors via `MaterialTheme.colorScheme.*`, so the semantic mapping chosen here (which Material3 slot each brand color fills) is what later tasks rely on. Document the mapping in a code comment only if a slot choice is non-obvious (e.g. which M3 slot `accentTint` maps to).

- [ ] **Step 1: Replace `Color.kt`'s placeholder values with the design-reference token set**

Define named `Color` vals for: `AccentOrange = Color(0xFFC2410C)`, `AccentOrangeDark = Color(0xFF9A3412)`, `AccentTint = Color(0xFFFDEEE4)`, `BackgroundCream = Color(0xFFF3F2EE)`, `SurfaceWhite = Color(0xFFFFFFFF)`, `TextPrimary = Color(0xFF1C1B19)`, `TextSecondary = Color(0xFF5C5951)`, `LineDefault = Color(0xFFE3E0D8)`, `LineStrong = Color(0xFFCBC7BB)`, `SuccessGreen = Color(0xFF15803D)`, `SuccessTint = Color(0xFFE5F3E8)`, `ErrorRed = Color(0xFFB91C1C)`, `ErrorTint = Color(0xFFFCEBEA)`. Remove the old `Purple80`/`PurpleGrey80`/etc. placeholders (nothing in the codebase references them outside this theme package).

- [ ] **Step 2: Rebuild `Theme.kt`'s color scheme from these tokens**

Replace `LightColorScheme`/`DarkColorScheme` with `lightColorScheme(primary = AccentOrange, onPrimary = SurfaceWhite, primaryContainer = AccentTint, onPrimaryContainer = AccentOrangeDark, background = BackgroundCream, onBackground = TextPrimary, surface = SurfaceWhite, onSurface = TextPrimary, surfaceVariant = BackgroundCream, onSurfaceVariant = TextSecondary, outline = LineStrong, outlineVariant = LineDefault, error = ErrorRed, onErrorContainer = ErrorRed, errorContainer = ErrorTint)`. For `DarkColorScheme`, keep Compose's default `darkColorScheme()` as a reasonable fallback (the design reference does not specify a dark variant). In `POSWarNusTheme`, change the `dynamicColor` parameter's default from `true` to `false` so the brand color scheme is never overridden by Android 12+ wallpaper-based dynamic color.

- [ ] **Step 3: Set up Inter typography in `Type.kt`**

Add a Google Fonts downloadable-font `FontFamily` for "Inter" at weights 400/500/600/700 (using `androidx.compose.ui.text.googlefonts.GoogleFont` + a `Font.Provider` — this requires adding `androidx.compose.ui:ui-text-google-fonts` to `app/build.gradle.kts`/`gradle/libs.versions.toml`, and a `<meta-data>`/certificate resource per that library's standard setup; if the Google Fonts provider setup proves non-trivial for any reason, fall back to Compose's default `FontFamily.SansSerif` and report this substitution as a concern — do not block the task on font integration). Define a `Typography` instance whose `titleLarge`/`titleMedium`/`bodyLarge`/`bodyMedium`/`labelLarge`/`labelMedium` etc. map to the sizes in the Global Constraints' typography note (exact `TextStyle` assignment per Material3 slot is this task's judgment call — later tasks will use both `MaterialTheme.typography.*` slots and ad-hoc `TextStyle`s for sizes that don't fit a standard M3 slot, such as the 44sp grand total).

- [ ] **Step 4: Verify the project builds**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/ui/theme/ app/build.gradle.kts gradle/libs.versions.toml
git commit -m "feat: theme the app with POS WarNus brand tokens (color, typography)"
```

---

### Task 2: Room local database — entities, DAOs

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/data/local/CatalogEntities.kt`
- Create: `app/src/main/java/com/example/poswarnus/data/local/CategoryDao.kt`
- Create: `app/src/main/java/com/example/poswarnus/data/local/MenuItemDao.kt`
- Create: `app/src/main/java/com/example/poswarnus/data/local/AppDatabase.kt`
- Create: `app/src/main/java/com/example/poswarnus/di/DatabaseModule.kt`
- Test: `app/src/test/java/com/example/poswarnus/data/local/CategoryDaoTest.kt`
- Test: `app/src/test/java/com/example/poswarnus/data/local/MenuItemDaoTest.kt`

**Interfaces:**
- Consumes: nothing beyond the existing Hilt setup.
- Produces: `@Entity data class CategoryEntity(@PrimaryKey val id: String, val name: String, val sortOrder: Int)` and `@Entity data class MenuItemEntity(@PrimaryKey val id: String, val categoryId: String, val name: String, val price: Double, val imageUrl: String?, val isAvailable: Boolean)`; `interface CategoryDao { fun observeAll(): Flow<List<CategoryEntity>>; suspend fun upsertAll(categories: List<CategoryEntity>) }`; `interface MenuItemDao { fun observeAll(): Flow<List<MenuItemEntity>>; suspend fun upsertAll(items: List<MenuItemEntity>) }`; `@Database(entities = [CategoryEntity::class, MenuItemEntity::class], version = 1) abstract class AppDatabase : RoomDatabase() { abstract fun categoryDao(): CategoryDao; abstract fun menuItemDao(): MenuItemDao }` — Task 4's `CatalogRepository` consumes `CategoryDao`/`MenuItemDao` directly (not `AppDatabase`).

- [ ] **Step 1: Add Room + Robolectric dependencies**

In `gradle/libs.versions.toml`, add versions/libraries for `androidx.room:room-runtime`, `androidx.room:room-ktx`, `androidx.room:room-compiler` (KSP), and `org.robolectric:robolectric` (test-only, latest stable `4.x`). In `app/build.gradle.kts`: `implementation(libs.androidx.room.runtime)`, `implementation(libs.androidx.room.ktx)`, `ksp(libs.androidx.room.compiler)`, `testImplementation(libs.robolectric)`. Room DAO unit tests need a `@RunWith(RobolectricTestRunner::class)` and Robolectric needs a `testOptions { unitTests { isIncludeAndroidResources = true } }` (or equivalent) in the `android {}` block if not already compatible — add whatever Robolectric's standard Gradle setup requires for the installed AGP version.

- [ ] **Step 2: Write the failing DAO tests**

```kotlin
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
```

```kotlin
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
    fun `upsertAll replaces an existing row with the same id`() = runTest {
        dao.upsertAll(listOf(MenuItemEntity(id = "1", categoryId = "a", name = "Old", price = 1.0, imageUrl = null, isAvailable = true)))
        dao.upsertAll(listOf(MenuItemEntity(id = "1", categoryId = "a", name = "New", price = 2.0, imageUrl = null, isAvailable = false)))
        val result = dao.observeAll().first()
        assertEquals(1, result.size)
        assertEquals("New", result.first().name)
        assertEquals(false, result.first().isAvailable)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.local.*"`
Expected: FAIL — `AppDatabase`, `CategoryDao`, `MenuItemDao`, `CategoryEntity`, `MenuItemEntity` unresolved.

- [ ] **Step 4: Implement the entities, DAOs, and database**

`CatalogEntities.kt` holds both `@Entity` data classes as described above (no behavior, just annotated fields — `@Entity(tableName = "categories")` / `@Entity(tableName = "menu_items")`). `CategoryDao`/`MenuItemDao` are `@Dao interface`s with `@Query("SELECT * FROM categories ORDER BY sortOrder") fun observeAll(): Flow<List<CategoryEntity>>` (`menu_items` table analog without an ORDER BY) and `@Upsert suspend fun upsertAll(categories: List<CategoryEntity>)` (Room's `@Upsert` annotation — insert-or-replace by primary key — satisfies the "replaces an existing row with the same id" test). `AppDatabase` is the `@Database` abstract class above.

- [ ] **Step 5: Implement `DatabaseModule`**

```kotlin
package com.example.poswarnus.di

import android.content.Context
import androidx.room.Room
import com.example.poswarnus.data.local.AppDatabase
import com.example.poswarnus.data.local.CategoryDao
import com.example.poswarnus.data.local.MenuItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "pos_warnus.db").build()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideMenuItemDao(db: AppDatabase): MenuItemDao = db.menuItemDao()
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.local.*"`
Expected: PASS (6 tests).

- [ ] **Step 7: Verify the project builds**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/example/poswarnus/data/local/ \
  app/src/main/java/com/example/poswarnus/di/DatabaseModule.kt \
  app/src/test/java/com/example/poswarnus/data/local/
git commit -m "feat: add Room local database for catalog caching"
```

---

### Task 3: Supabase catalog remote data source

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/data/remote/CatalogDtos.kt`
- Create: `app/src/main/java/com/example/poswarnus/data/remote/CatalogRemoteDataSource.kt`
- Create: `app/src/main/java/com/example/poswarnus/data/remote/SupabaseCatalogRemoteDataSource.kt`
- Create: `app/src/main/java/com/example/poswarnus/di/CatalogRemoteModule.kt`

**Interfaces:**
- Consumes: `SupabaseClient` (Phase 1's `SupabaseModule`).
- Produces: `@Serializable data class CategoryDto(val id: String, val name: String, @SerialName("sort_order") val sortOrder: Int)` and `@Serializable data class MenuItemDto(val id: String, @SerialName("category_id") val categoryId: String, val name: String, val price: Double, @SerialName("image_url") val imageUrl: String?, @SerialName("is_available") val isAvailable: Boolean)`; `interface CatalogRemoteDataSource { suspend fun fetchCategories(): List<CategoryDto>; suspend fun fetchMenuItems(): List<MenuItemDto> }` — Task 4's `CatalogRepository` consumes this interface (fakeable in tests), not `SupabaseCatalogRemoteDataSource` directly.

- [ ] **Step 1: Define the DTOs**

Exactly as described above in `CatalogDtos.kt`, matching the `categories`/`menu_items` table columns in this plan's Backend Contract section verbatim (column names via `@SerialName` where they differ from the Kotlin property's camelCase).

- [ ] **Step 2: Define `CatalogRemoteDataSource`**

The interface above, in its own file.

- [ ] **Step 3: Implement `SupabaseCatalogRemoteDataSource`**

```kotlin
package com.example.poswarnus.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest

class SupabaseCatalogRemoteDataSource(private val client: SupabaseClient) : CatalogRemoteDataSource {
    override suspend fun fetchCategories(): List<CategoryDto> =
        client.postgrest.from("categories").select().decodeList()

    override suspend fun fetchMenuItems(): List<MenuItemDto> =
        client.postgrest.from("menu_items").select().decodeList()
}
```

If the installed `postgrest-kt` version's `select()`/`decodeList()` API differs (exact method names/overloads), use the real API and note the difference in your report — the behavior required is "fetch all rows from the table, decoded into the DTO list," not these exact method names.

- [ ] **Step 4: Wire `CatalogRemoteModule`**

```kotlin
package com.example.poswarnus.di

import com.example.poswarnus.data.remote.CatalogRemoteDataSource
import com.example.poswarnus.data.remote.SupabaseCatalogRemoteDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CatalogRemoteModule {
    @Provides
    @Singleton
    fun provideCatalogRemoteDataSource(client: SupabaseClient): CatalogRemoteDataSource =
        SupabaseCatalogRemoteDataSource(client)
}
```

- [ ] **Step 5: Verify the project builds**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/data/remote/ app/src/main/java/com/example/poswarnus/di/CatalogRemoteModule.kt
git commit -m "feat: add Supabase catalog remote data source"
```

---

### Task 4: `CatalogRepository`

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/data/catalog/CatalogRepository.kt`
- Create: `app/src/main/java/com/example/poswarnus/di/CatalogModule.kt`
- Test: `app/src/test/java/com/example/poswarnus/data/catalog/CatalogRepositoryTest.kt`

**Interfaces:**
- Consumes: `CategoryDao`, `MenuItemDao` (Task 2), `CatalogRemoteDataSource` (Task 3).
- Produces: `data class Category(val id: String, val name: String, val sortOrder: Int)`, `data class MenuItem(val id: String, val categoryId: String, val name: String, val price: Double, val imageUrl: String?, val isAvailable: Boolean)` (plain domain models, distinct from the Room/DTO types — this is what the ViewModel/UI consume), `sealed interface RefreshResult { data object Success : RefreshResult; data class Failure(val message: String) : RefreshResult }`, `interface CatalogRepository { val categories: Flow<List<Category>>; val menuItems: Flow<List<MenuItem>>; suspend fun refresh(): RefreshResult }` — Task 5's `DashboardViewModel` consumes exactly these three members.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.poswarnus.data.catalog

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
}

private class FakeMenuItemDao : MenuItemDao {
    val stored = MutableStateFlow<List<MenuItemEntity>>(emptyList())
    override fun observeAll(): Flow<List<MenuItemEntity>> = stored
    override suspend fun upsertAll(items: List<MenuItemEntity>) {
        val byId = (stored.value + items).associateBy { it.id }
        stored.value = byId.values.toList()
    }
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
        val repository = CatalogRepositoryImpl(categoryDao, FakeMenuItemDao(), FakeCatalogRemoteDataSource())

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
        val repository = CatalogRepositoryImpl(categoryDao, menuItemDao, remote)

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
        val repository = CatalogRepositoryImpl(categoryDao, FakeMenuItemDao(), remote)

        val result = repository.refresh()

        assertTrue(result is RefreshResult.Failure)
        assertEquals(1, categoryDao.stored.value.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.catalog.CatalogRepositoryTest"`
Expected: FAIL — `CatalogRepositoryImpl`, `Category`, `RefreshResult` unresolved.

- [ ] **Step 3: Implement `CatalogRepository`**

```kotlin
package com.example.poswarnus.data.catalog

import com.example.poswarnus.data.local.CategoryDao
import com.example.poswarnus.data.local.CategoryEntity
import com.example.poswarnus.data.local.MenuItemDao
import com.example.poswarnus.data.local.MenuItemEntity
import com.example.poswarnus.data.remote.CatalogRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Category(val id: String, val name: String, val sortOrder: Int)
data class MenuItem(val id: String, val categoryId: String, val name: String, val price: Double, val imageUrl: String?, val isAvailable: Boolean)

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
        categoryDao.upsertAll(categoryDtos.map { CategoryEntity(it.id, it.name, it.sortOrder) })
        menuItemDao.upsertAll(itemDtos.map { MenuItemEntity(it.id, it.categoryId, it.name, it.price, it.imageUrl, it.isAvailable) })
        RefreshResult.Success
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        RefreshResult.Failure(e.message ?: "Failed to load catalog")
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.catalog.CatalogRepositoryTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Wire `CatalogModule`**

```kotlin
package com.example.poswarnus.di

import com.example.poswarnus.data.catalog.CatalogRepository
import com.example.poswarnus.data.catalog.CatalogRepositoryImpl
import com.example.poswarnus.data.local.CategoryDao
import com.example.poswarnus.data.local.MenuItemDao
import com.example.poswarnus.data.remote.CatalogRemoteDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {
    @Provides
    @Singleton
    fun provideCatalogRepository(
        categoryDao: CategoryDao,
        menuItemDao: MenuItemDao,
        remote: CatalogRemoteDataSource,
    ): CatalogRepository = CatalogRepositoryImpl(categoryDao, menuItemDao, remote)
}
```

- [ ] **Step 6: Verify the project builds**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/data/catalog/ app/src/main/java/com/example/poswarnus/di/CatalogModule.kt \
  app/src/test/java/com/example/poswarnus/data/catalog/
git commit -m "feat: add CatalogRepository bridging Room cache and Supabase"
```

---

### Task 5: `DashboardViewModel`

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardViewModel.kt`
- Test: `app/src/test/java/com/example/poswarnus/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Consumes: `CatalogRepository`, `Category`, `MenuItem`, `RefreshResult` (Task 4).
- Produces: `data class DashboardUiState(val categories: List<Category> = emptyList(), val selectedCategoryId: String? = null, val searchQuery: String = "", val visibleItems: List<MenuItem> = emptyList(), val isOffline: Boolean = false, val loadError: Boolean = false)`, `class DashboardViewModel(...) : ViewModel()` with `val uiState: StateFlow<DashboardUiState>`, `fun onCategorySelected(categoryId: String?)` (`null` = "All"), `fun onSearchQueryChanged(query: String)`, `fun refresh()` — Task 6's `DashboardScreen` consumes all of these. `selectedCategoryId = null` means "All" selected; `loadError = true` means "never successfully loaded and no cache exists" (distinct from `isOffline`, which means "showing stale cache after a failed refresh").

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.example.poswarnus.ui.dashboard

import app.cash.turbine.test
import com.example.poswarnus.data.catalog.Category
import com.example.poswarnus.data.catalog.CatalogRepository
import com.example.poswarnus.data.catalog.MenuItem
import com.example.poswarnus.data.catalog.RefreshResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class FakeCatalogRepository(
    var refreshResult: RefreshResult = RefreshResult.Success,
) : CatalogRepository {
    val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())
    val menuItemsFlow = MutableStateFlow<List<MenuItem>>(emptyList())
    override val categories: Flow<List<Category>> = categoriesFlow
    override val menuItems: Flow<List<MenuItem>> = menuItemsFlow
    override suspend fun refresh(): RefreshResult = refreshResult
}

private val makanan = Category("a", "Makanan", 1)
private val minuman = Category("b", "Minuman", 2)
private val nasiGoreng = MenuItem("1", "a", "Nasi Goreng", 25000.0, null, true)
private val esTeh = MenuItem("2", "b", "Es Teh", 5000.0, null, true)
private val sotoHabis = MenuItem("3", "a", "Soto Ayam", 22000.0, null, false)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(repository: FakeCatalogRepository) = DashboardViewModel(repository).also {
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `visibleItems shows all items when no category or search filter is set`() = runTest {
        val repository = FakeCatalogRepository()
        repository.categoriesFlow.value = listOf(makanan, minuman)
        repository.menuItemsFlow.value = listOf(nasiGoreng, esTeh)
        val viewModel = viewModel(repository)

        assertEquals(listOf(nasiGoreng, esTeh), viewModel.uiState.value.visibleItems)
    }

    @Test
    fun `selecting a category filters visibleItems to that category only`() = runTest {
        val repository = FakeCatalogRepository()
        repository.menuItemsFlow.value = listOf(nasiGoreng, esTeh)
        val viewModel = viewModel(repository)

        viewModel.onCategorySelected("a")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(nasiGoreng), viewModel.uiState.value.visibleItems)
    }

    @Test
    fun `search query filters within the active category`() = runTest {
        val repository = FakeCatalogRepository()
        repository.menuItemsFlow.value = listOf(nasiGoreng, sotoHabis)
        val viewModel = viewModel(repository)

        viewModel.onCategorySelected("a")
        viewModel.onSearchQueryChanged("nasi")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(nasiGoreng), viewModel.uiState.value.visibleItems)
    }

    @Test
    fun `refresh failure with an existing cache sets isOffline without clearing items`() = runTest {
        val repository = FakeCatalogRepository(refreshResult = RefreshResult.Failure("network down"))
        repository.menuItemsFlow.value = listOf(nasiGoreng)
        val viewModel = viewModel(repository)

        viewModel.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isOffline)
        assertEquals(listOf(nasiGoreng), viewModel.uiState.value.visibleItems)
    }

    @Test
    fun `refresh failure with no cache at all sets loadError`() = runTest {
        val repository = FakeCatalogRepository(refreshResult = RefreshResult.Failure("network down"))
        val viewModel = viewModel(repository)

        viewModel.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.loadError)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.ui.dashboard.DashboardViewModelTest"`
Expected: FAIL — `DashboardViewModel`, `DashboardUiState` unresolved.

- [ ] **Step 3: Implement `DashboardViewModel`**

```kotlin
package com.example.poswarnus.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poswarnus.data.catalog.CatalogRepository
import com.example.poswarnus.data.catalog.Category
import com.example.poswarnus.data.catalog.MenuItem
import com.example.poswarnus.data.catalog.RefreshResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val searchQuery: String = "",
    val visibleItems: List<MenuItem> = emptyList(),
    val isOffline: Boolean = false,
    val loadError: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: CatalogRepository,
) : ViewModel() {
    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val loadStatus = MutableStateFlow(LoadStatus())

    private data class LoadStatus(val isOffline: Boolean = false, val loadError: Boolean = false)

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.categories, repository.menuItems, selectedCategoryId, searchQuery, loadStatus,
    ) { categories, items, categoryId, query, status ->
        val filtered = items
            .filter { categoryId == null || it.categoryId == categoryId }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        DashboardUiState(
            categories = categories,
            selectedCategoryId = categoryId,
            searchQuery = query,
            visibleItems = filtered,
            isOffline = status.isOffline,
            loadError = status.loadError,
        )
    }.let { flow ->
        val state = MutableStateFlow(DashboardUiState())
        viewModelScope.launch { flow.collect { state.value = it } }
        state.asStateFlow()
    }

    init {
        refresh()
    }

    fun onCategorySelected(categoryId: String?) { selectedCategoryId.value = categoryId }
    fun onSearchQueryChanged(query: String) { searchQuery.value = query }

    fun refresh() {
        viewModelScope.launch {
            when (val result = repository.refresh()) {
                is RefreshResult.Success -> loadStatus.update { it.copy(isOffline = false, loadError = false) }
                is RefreshResult.Failure -> {
                    val hasCache = uiState.value.visibleItems.isNotEmpty() || uiState.value.categories.isNotEmpty()
                    loadStatus.update { LoadStatus(isOffline = hasCache, loadError = !hasCache) }
                }
            }
        }
    }
}
```

Note the open design choice in `uiState`'s construction: `combine` of 5 flows produces a `Flow`, not a `StateFlow` directly, so it is manually collected into a `MutableStateFlow` here. If the installed `kotlinx-coroutines` version's `stateIn(scope, SharingStarted.Eagerly, DashboardUiState())` operator is simpler/preferred, use that instead — the required behavior is "a `StateFlow<DashboardUiState>` that updates whenever any input flow changes," not this exact construction.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.ui.dashboard.DashboardViewModelTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardViewModel.kt \
  app/src/test/java/com/example/poswarnus/ui/dashboard/DashboardViewModelTest.kt
git commit -m "feat: add DashboardViewModel with search/category filtering and offline state"
```

---

### Task 6: `DashboardScreen` — responsive catalog UI

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardComponents.kt`
- Modify: `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt`

**Interfaces:**
- Consumes: `DashboardViewModel`, `DashboardUiState` (Task 5); `Category`, `MenuItem` (Task 4). Keeps Phase 1's existing `DashboardViewModel`/`DashboardScreen` naming for the logout button — merge the new catalog UI into the existing `DashboardScreen.kt`, replacing its placeholder body (the existing `DashboardViewModel` from Phase 1 that only calls `authRepository.logout()` must be renamed or merged — see Step 1).
- Produces: `DashboardScreen(onLoggedOut: () -> Unit)` (same public signature as Phase 1 — `PosNavGraph` in Task 7 doesn't need to change its call site) rendering the full catalog UI; `@Composable fun MenuItemCard(item: MenuItem, onClick: () -> Unit)`, `@Composable fun CategoryChip(category: Category?, isSelected: Boolean, onClick: () -> Unit)` as reusable pieces Phase 3 may reuse (e.g. the catalog shown while building a new order).

- [ ] **Step 1: Resolve the two `DashboardViewModel` names**

Phase 1's `DashboardScreen.kt` currently defines a `DashboardViewModel` that only wraps `authRepository.logout()`. Rename that one to `LogoutViewModel` (its sole responsibility) in the same file, and let Task 5's new `DashboardViewModel` (catalog state) be the screen's primary ViewModel. `DashboardScreen` now takes both: `@Composable fun DashboardScreen(onLoggedOut: () -> Unit, dashboardViewModel: DashboardViewModel = hiltViewModel(), logoutViewModel: LogoutViewModel = hiltViewModel())`.

- [ ] **Step 2: Implement `MenuItemCard` and `CategoryChip` in `DashboardComponents.kt`**

`MenuItemCard`: a `Card`/`Surface` with `shape = RoundedCornerShape(10.dp)`, `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)`, containing (top to bottom or as a row, per design-reference.md's Dashboard section) an image loaded via Coil's `AsyncImage` (fallback placeholder box if `imageUrl` is null), the item name (`MaterialTheme.typography` slot sized ~16sp/600 per the design reference), and the price formatted as `Rp25.000` (helper function `formatRupiah(price: Double): String` using `java.text.NumberFormat` for Indonesian locale grouping, or manual thousands-separator formatting — either is fine as long as the exact `Rp25.000` format from the Global Constraints is produced). When `item.isAvailable == false`: apply `alpha = 0.5f` (or similar dimming) to the whole card and make `onClick` a no-op (don't call the passed lambda) — do not add any click ripple/indication in that case either, consistent with "not clickable."

`CategoryChip`: a `FilterChip`-like pill (`shape = RoundedCornerShape(6.dp)` per the Global Constraints' control radius) showing the category name (or "Semua"/"All" when `category == null`), with `isSelected` driving a visually distinct selected state (e.g. `MaterialTheme.colorScheme.primaryContainer` background + `onPrimaryContainer` text when selected, vs `surface`/`onSurface` with a `1.dp` outline border when not).

- [ ] **Step 3: Implement the responsive `DashboardScreen` body**

```kotlin
@Composable
fun DashboardScreen(
    onLoggedOut: () -> Unit,
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    logoutViewModel: LogoutViewModel = hiltViewModel(),
) {
    val state by dashboardViewModel.uiState.collectAsState()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 840.dp
        Row(modifier = Modifier.fillMaxSize()) {
            CatalogPane(
                state = state,
                onCategorySelected = dashboardViewModel::onCategorySelected,
                onSearchQueryChanged = dashboardViewModel::onSearchQueryChanged,
                onRefresh = dashboardViewModel::refresh,
                onLogout = { logoutViewModel.logout(onLoggedOut) },
                modifier = if (isTablet) Modifier.weight(1f) else Modifier.fillMaxWidth(),
            )
            if (isTablet) {
                Column(modifier = Modifier.weight(0.4f).fillMaxHeight()) {
                    // Empty placeholder panel — cart UI lands in Phase 3.
                }
            }
        }
    }
}
```

`CatalogPane` (a private composable in the same file, or in `DashboardComponents.kt`) contains: a top bar/header (app name or a logout action, per design-reference.md), a search `OutlinedTextField`/`TextField` bound to `state.searchQuery` → `onSearchQueryChanged`, a horizontally scrollable row of `CategoryChip`s built from `state.categories` plus a leading "Semua"/All chip (`category = null`), and the item grid/list (`LazyVerticalGrid` with `GridCells.Adaptive(minSize = 160.dp)` is a reasonable choice — exact column count isn't spec'd) rendering `state.visibleItems` via `MenuItemCard`. Handle the three states from the Review Focus: `state.loadError` → a centered "Tidak bisa memuat menu, periksa koneksi" message with a retry button calling `onRefresh`; `state.isOffline` → a small non-blocking banner/text ("Offline — menampilkan data tersimpan") above the grid while still showing `visibleItems`; `state.visibleItems.isEmpty()` (and not `loadError`) → a centered "Menu tidak ditemukan" empty state.

- [ ] **Step 4: Add the Coil dependency**

Add `coil-compose` to `gradle/libs.versions.toml` / `app/build.gradle.kts` (`implementation(libs.coil.compose)`) if not already present.

- [ ] **Step 5: Verify the project builds**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
  app/src/main/java/com/example/poswarnus/ui/dashboard/
git commit -m "feat: implement responsive Dashboard catalog UI (search, category filter, offline states)"
```

---

### Task 7: Manual end-to-end verification

**Files:** none (verification only).

- [ ] **Step 1: Install and run on a device/emulator**

Run: `./gradlew installDebug`, launch the app, log in with the test cashier account from Phase 1.
Expected: Dashboard loads the 3 seeded categories and 9 seeded menu items from the live Supabase project within a couple seconds.

- [ ] **Step 2: Verify the Review Focus behaviors manually**

(1) Turn off network before first-ever launch (fresh install, cleared app data) → "can't load catalog" state with retry, not a blank screen. (2) Launch once online (cache populated), then go offline and pull-to-refresh or reopen the Dashboard → stale items still visible plus an offline indicator. (3) Select "Minuman" category, type a query matching only a "Makanan" item → zero results shown (filters combine, not OR). (4) The seeded unavailable item ("Soto Ayam") is dimmed and tapping it does nothing. (5) Select a category/search combination with no matches → explicit empty state, not blank.

- [ ] **Step 3: Verify responsive layout**

Run on a tablet-sized emulator (or resize an emulator window ≥840dp wide) and a phone-sized one — confirm the right-side empty panel appears only on the tablet layout.

- [ ] **Step 4: Record the result**

No commit for this task — if all checks pass, Phase 2 is done; if any fails, fix forward in a new commit on top of the relevant task before moving to Phase 3.
