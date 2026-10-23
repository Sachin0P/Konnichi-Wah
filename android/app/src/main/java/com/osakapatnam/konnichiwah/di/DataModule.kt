package com.osakapatnam.konnichiwah.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.osakapatnam.konnichiwah.data.local.ItineraryDao
import com.osakapatnam.konnichiwah.data.local.KonnichiDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    // There must only ever be one DataStore instance per file, so Hilt
    // provides it as a singleton.
    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("profile") }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KonnichiDatabase =
        Room.databaseBuilder(context, KonnichiDatabase::class.java, "konnichi.db").build()

    @Provides
    fun provideItineraryDao(database: KonnichiDatabase): ItineraryDao = database.itineraryDao()
}
