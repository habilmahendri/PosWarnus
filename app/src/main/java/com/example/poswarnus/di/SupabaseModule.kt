package com.example.poswarnus.di

import com.example.poswarnus.BuildConfig
import com.example.poswarnus.data.remote.SupabaseConfig
import com.example.poswarnus.data.remote.requireValid
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        val config = SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY).requireValid()
        return createSupabaseClient(config.url, config.anonKey) {
            install(Auth)
            install(Postgrest)
        }
    }
}
