package com.example.poswarnus.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.withTransaction

@Database(entities = [CategoryEntity::class, MenuItemEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun menuItemDao(): MenuItemDao
}

/** Runs a block of DAO calls atomically. Abstracted so repositories stay unit-testable without Room. */
interface CatalogTransactionRunner {
    suspend fun runInTransaction(block: suspend () -> Unit)
}

class RoomCatalogTransactionRunner(private val db: AppDatabase) : CatalogTransactionRunner {
    override suspend fun runInTransaction(block: suspend () -> Unit) {
        db.withTransaction { block() }
    }
}
