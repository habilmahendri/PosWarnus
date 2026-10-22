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
