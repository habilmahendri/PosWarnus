package com.example.poswarnus.di

import android.content.Context
import androidx.room.Room
import com.example.poswarnus.data.local.AppDatabase
import com.example.poswarnus.data.local.CatalogTransactionRunner
import com.example.poswarnus.data.local.CategoryDao
import com.example.poswarnus.data.local.MIGRATION_1_2
import com.example.poswarnus.data.local.MIGRATION_2_3
import com.example.poswarnus.data.local.MIGRATION_3_4
import com.example.poswarnus.data.local.TaxSettingsDao
import com.example.poswarnus.data.local.TransactionDao
import com.example.poswarnus.data.local.MenuItemDao
import com.example.poswarnus.data.local.OptionDao
import com.example.poswarnus.data.local.OptionGroupDao
import com.example.poswarnus.data.local.RoomCatalogTransactionRunner
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
        Room.databaseBuilder(context, AppDatabase::class.java, "pos_warnus.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideMenuItemDao(db: AppDatabase): MenuItemDao = db.menuItemDao()

    @Provides
    fun provideOptionGroupDao(db: AppDatabase): OptionGroupDao = db.optionGroupDao()

    @Provides
    fun provideOptionDao(db: AppDatabase): OptionDao = db.optionDao()

    @Provides
    fun provideTaxSettingsDao(db: AppDatabase): TaxSettingsDao = db.taxSettingsDao()

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    @Singleton
    fun provideCatalogTransactionRunner(db: AppDatabase): CatalogTransactionRunner =
        RoomCatalogTransactionRunner(db)
}
