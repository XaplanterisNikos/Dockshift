package com.foxnks.dockshift.di

import com.foxnks.dockshift.data.backup.AndroidBackupStorage
import com.foxnks.dockshift.data.backup.BackupFormat
import com.foxnks.dockshift.data.backup.BackupStorage
import com.foxnks.dockshift.data.backup.JsonBackupFormat
import com.foxnks.dockshift.data.backup.RoomBackupRepository
import com.foxnks.dockshift.data.leave.RoomLeaveRepository
import com.foxnks.dockshift.data.machine.RoomMachineRepository
import com.foxnks.dockshift.data.settings.DataStoreSettingsRepository
import com.foxnks.dockshift.data.shift.RoomShiftRepository
import com.foxnks.dockshift.domain.backup.BackupRepository
import com.foxnks.dockshift.domain.leave.LeaveRepository
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

    /** Whoever asks for [LeaveRepository] gets the Room-backed instance. */
    @Binds
    @Singleton
    abstract fun bindLeaveRepository(impl: RoomLeaveRepository): LeaveRepository

    /** Whoever asks for [MachineRepository] gets the Room-backed instance. */
    @Binds
    @Singleton
    abstract fun bindMachineRepository(impl: RoomMachineRepository): MachineRepository

    /** Whoever asks for [BackupRepository] gets the Room + DataStore instance. */
    @Binds
    abstract fun bindBackupRepository(impl: RoomBackupRepository): BackupRepository

    /** Backups are written as JSON. */
    @Binds
    abstract fun bindBackupFormat(impl: JsonBackupFormat): BackupFormat

    /** Backup files are kept through MediaStore and the system file picker. */
    @Binds
    abstract fun bindBackupStorage(impl: AndroidBackupStorage): BackupStorage
}
