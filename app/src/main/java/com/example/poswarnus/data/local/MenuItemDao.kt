package com.example.poswarnus.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuItemDao {
    @Query("SELECT * FROM menu_items")
    fun observeAll(): Flow<List<MenuItemEntity>>

    @Upsert
    suspend fun upsertAll(items: List<MenuItemEntity>)
}
