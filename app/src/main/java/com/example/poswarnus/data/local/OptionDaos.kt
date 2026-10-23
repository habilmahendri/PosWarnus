package com.example.poswarnus.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class OptionGroupWithOptions(
    @Embedded val group: OptionGroupEntity,
    @Relation(parentColumn = "id", entityColumn = "groupId")
    val options: List<OptionEntity>,
)

@Dao
interface OptionGroupDao {
    @Transaction
    @Query("SELECT * FROM option_groups WHERE menuItemId = :menuItemId ORDER BY sortOrder")
    fun observeForItem(menuItemId: String): Flow<List<OptionGroupWithOptions>>

    @Upsert
    suspend fun upsertAll(groups: List<OptionGroupEntity>)

    @Query("DELETE FROM option_groups")
    suspend fun clear()
}

@Dao
interface OptionDao {
    @Upsert
    suspend fun upsertAll(options: List<OptionEntity>)

    @Query("DELETE FROM options")
    suspend fun clear()
}
