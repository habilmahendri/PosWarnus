package com.example.poswarnus.data.tax

import com.example.poswarnus.data.local.TaxSettingsDao
import com.example.poswarnus.data.local.TaxSettingsEntity
import com.example.poswarnus.data.remote.TaxRemoteDataSource
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TaxRepository {
    /** Last cached tax config; null until one fetch ever succeeded. */
    val taxConfig: Flow<TaxConfig?>

    /** Fetches and caches the config. Never throws (except cancellation); a failure keeps the cache. */
    suspend fun refresh()
}

class TaxRepositoryImpl(
    private val dao: TaxSettingsDao,
    private val remote: TaxRemoteDataSource,
) : TaxRepository {
    override val taxConfig: Flow<TaxConfig?> =
        dao.observe().map { row -> row?.let { TaxConfig(it.label, it.basisPoints) } }

    override suspend fun refresh() {
        try {
            val dto = remote.fetchTaxSettings() ?: return
            dao.replace(TaxSettingsEntity(label = dto.label, basisPoints = (dto.percent * 100).roundToInt()))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline, no session or table not applied yet: keep whatever is cached.
        }
    }
}
