package com.example.selfiememory.di

import android.content.Context
import com.example.selfiememory.data.local.SelfieDao
import com.example.selfiememory.data.local.SelfieDatabase
import com.example.selfiememory.data.local.SettingsDataStore
import com.example.selfiememory.data.repository.SelfieRepository
import com.example.selfiememory.data.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSelfieDatabase(@ApplicationContext context: Context): SelfieDatabase {
        return SelfieDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideSelfieDao(database: SelfieDatabase): SelfieDao {
        return database.selfieDao()
    }

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {
        return SettingsDataStore(context)
    }

    @Provides
    @Singleton
    fun provideSelfieRepository(
        @ApplicationContext context: Context,
        selfieDao: SelfieDao
    ): SelfieRepository {
        return SelfieRepository(context, selfieDao)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(dataStore: SettingsDataStore): SettingsRepository {
        return SettingsRepository(dataStore)
    }
}