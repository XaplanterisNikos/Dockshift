package com.foxnks.dockshift.di

import com.foxnks.dockshift.data.settings.DataStoreSettingsRepository
import com.foxnks.dockshift.domain.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that maps domain interfaces to their data implementations.
 *
 * The equivalent of `services.AddSingleton<ISettingsRepository, ...>()` in ASP.NET Core.
 * [Binds] needs no code body: Hilt builds the implementation through its
 * `@Inject` constructor and hands it out as the interface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /** Whoever asks for [SettingsRepository] gets the single DataStore-backed instance. */
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
