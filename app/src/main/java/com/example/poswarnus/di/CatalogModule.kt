package com.example.poswarnus.di

import com.example.poswarnus.data.catalog.CatalogRepository
import com.example.poswarnus.data.catalog.CatalogRepositoryImpl
import com.example.poswarnus.data.local.CatalogTransactionRunner
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
        transactionRunner: CatalogTransactionRunner,
    ): CatalogRepository = CatalogRepositoryImpl(categoryDao, menuItemDao, remote, transactionRunner)
}
