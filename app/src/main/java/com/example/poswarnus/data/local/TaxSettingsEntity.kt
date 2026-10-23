package com.example.poswarnus.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The single cached tax / service charge row; [basisPoints] 1000 means 10%. */
@Entity(tableName = "tax_settings")
data class TaxSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val label: String,
    val basisPoints: Int,
)

@Dao
interface TaxSettingsDao {
    @Query("SELECT * FROM tax_settings WHERE id = 1")
    fun observe(): Flow<TaxSettingsEntity?>

    @Upsert
    suspend fun replace(entity: TaxSettingsEntity)
}
