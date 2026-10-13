package com.foxnks.dockshift.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.foxnks.dockshift.data.db.DatabaseMigrations
import com.foxnks.dockshift.data.db.DockshiftDatabase
import com.foxnks.dockshift.data.db.MachineSeedCallback
import com.foxnks.dockshift.data.db.dao.ImportDao
import com.foxnks.dockshift.data.db.dao.LeaveDao
import com.foxnks.dockshift.data.db.dao.MachineDao
import com.foxnks.dockshift.data.db.dao.ShiftDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

/** Hilt module that creates the Room database and exposes its DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * The single database instance of the app.
     *
     * Deliberately no `fallbackToDestructiveMigration`: a missing migration must
     * crash in testing, never silently wipe a user's shifts.
     *
     * @param context the application context, injected by Hilt.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DockshiftDatabase =
        Room.databaseBuilder<DockshiftDatabase>(context, DockshiftDatabase.NAME)
            // SQLite shipped inside the app: identical behavior on every Android version
            .setDriver(BundledSQLiteDriver())
            // Queries run off the main thread
            .setQueryCoroutineContext(Dispatchers.IO)
            // Fills the machine list when the database file is first created
            .addCallback(MachineSeedCallback())
            // Upgrades older database files step by step, keeping all data
            .addMigrations(*DatabaseMigrations.ALL)
            .build()

    /** Shift data access, from the single database. */
    @Provides
    fun provideShiftDao(database: DockshiftDatabase): ShiftDao = database.shiftDao()

    /** Machine data access, from the single database. */
    @Provides
    fun provideMachineDao(database: DockshiftDatabase): MachineDao = database.machineDao()

    /** Leave data access, from the single database. */
    @Provides
    fun provideLeaveDao(database: DockshiftDatabase): LeaveDao = database.leaveDao()

    /** Bulk-import data access, from the single database. */
    @Provides
    fun provideImportDao(database: DockshiftDatabase): ImportDao = database.importDao()
}
