package com.foxnks.dockshift.di

import com.foxnks.dockshift.data.machine.RoomMachineRepository
import com.foxnks.dockshift.data.settings.DataStoreSettingsRepository
import com.foxnks.dockshift.data.shift.RoomShiftRepository
import com.foxnks.dockshift.domain.machine.MachineRepository
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.shift.ShiftRepository
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

    /** Whoever asks for [ShiftRepository] gets the Room-backed instance. */
    @Binds
    @Singleton
    abstract fun bindShiftRepository(impl: RoomShiftRepository): ShiftRepository

    /** Whoever asks for [MachineRepository] gets the Room-backed instance. */
    @Binds
    @Singleton
    abstract fun bindMachineRepository(impl: RoomMachineRepository): MachineRepository
}
