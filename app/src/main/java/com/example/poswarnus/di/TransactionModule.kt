package com.example.poswarnus.di

import com.example.poswarnus.data.local.TransactionDao
import com.example.poswarnus.data.transaction.TransactionRepository
import com.example.poswarnus.data.transaction.TransactionRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TransactionModule {
    @Provides
    @Singleton
    fun provideTransactionRepository(dao: TransactionDao): TransactionRepository = TransactionRepositoryImpl(dao)
}
