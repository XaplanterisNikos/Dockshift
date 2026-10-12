package com.foxnks.dockshift.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that creates the settings DataStore.
 *
 * Uses [Provides] because DataStore is built by a factory, not by a constructor
 * Hilt could call itself.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    /**
     * The single settings DataStore of the app.
     *
     * [Singleton] is required: DataStore allows only one active instance per file,
     * and a second one would throw at runtime.
     *
     * @param context the application context, injected by Hilt.
     */
    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            // Stored in the app's private files as datastore/settings.preferences_pb
            produceFile = { context.preferencesDataStoreFile("settings") },
        )
}
