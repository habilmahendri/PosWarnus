package com.example.poswarnus.data.tax

import com.example.poswarnus.data.local.TaxSettingsDao
import com.example.poswarnus.data.local.TaxSettingsEntity
import com.example.poswarnus.data.remote.TaxRemoteDataSource
import com.example.poswarnus.data.remote.TaxSettingsDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeTaxDao : TaxSettingsDao {
    val row = MutableStateFlow<TaxSettingsEntity?>(null)
    override fun observe(): Flow<TaxSettingsEntity?> = row
    override suspend fun replace(entity: TaxSettingsEntity) { row.value = entity }
}

private class FakeTaxRemote(var result: () -> TaxSettingsDto?) : TaxRemoteDataSource {
    override suspend fun fetchTaxSettings(): TaxSettingsDto? = result()
}

class TaxRepositoryTest {
    private val dao = FakeTaxDao()

    private fun repo(result: () -> TaxSettingsDto?) = TaxRepositoryImpl(dao, FakeTaxRemote(result))

    @Test
    fun `refresh stores basis points from percent`() = runTest {
        repo { TaxSettingsDto("Pajak", 10.0) }.refresh()
        assertEquals(TaxSettingsEntity(1, "Pajak", 1000), dao.row.value)

        repo { TaxSettingsDto("Service", 7.5) }.refresh()
        assertEquals(TaxSettingsEntity(1, "Service", 750), dao.row.value)
    }

    @Test
    fun `taxConfig maps the cached row and is null before any refresh`() = runTest {
        val repository = repo { TaxSettingsDto("Pajak", 10.0) }
        assertNull(repository.taxConfig.first())
        repository.refresh()
        assertEquals(TaxConfig("Pajak", 1000), repository.taxConfig.first())
    }

    @Test
    fun `failed refresh keeps the previously cached value`() = runTest {
        repo { TaxSettingsDto("Pajak", 10.0) }.refresh()
        repo { error("offline") }.refresh()
        assertEquals(TaxSettingsEntity(1, "Pajak", 1000), dao.row.value)
    }

    @Test
    fun `remote returning no row keeps the cache`() = runTest {
        repo { TaxSettingsDto("Pajak", 10.0) }.refresh()
        repo { null }.refresh()
        assertEquals(TaxSettingsEntity(1, "Pajak", 1000), dao.row.value)
    }

    @Test
    fun `refresh swallows exceptions`() = runTest {
        repo { throw RuntimeException("boom") }.refresh()
        assertNull(dao.row.value)
    }
}
