package com.foxnks.dockshift.di

import com.foxnks.dockshift.data.legacy.AndroidLegacyNotes
import com.foxnks.dockshift.data.legacy.ContentResolverTextReader
import com.foxnks.dockshift.data.legacy.RoomLegacyImportRepository
import com.foxnks.dockshift.data.legacy.TextFileReader
import com.foxnks.dockshift.domain.legacy.LegacyImportRepository
import com.foxnks.dockshift.domain.legacy.LegacyMigration
import com.foxnks.dockshift.domain.legacy.LegacyNotes
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt wiring of the v1 → v2 migration. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LegacyModule {

    /** Storage side of the migration: Room. */
    @Binds
    abstract fun bindLegacyImportRepository(impl: RoomLegacyImportRepository): LegacyImportRepository

    /** Notes added to comments, in the user's language. */
    @Binds
    abstract fun bindLegacyNotes(impl: AndroidLegacyNotes): LegacyNotes

    /** Reading the file the user picked. */
    @Binds
    abstract fun bindTextFileReader(impl: ContentResolverTextReader): TextFileReader

    companion object {
        /**
         * The migration itself lives in :domain, which knows nothing about Hilt,
         * so it is built here by hand from its two dependencies.
         */
        @Provides
        fun provideLegacyMigration(repository: LegacyImportRepository, notes: LegacyNotes): LegacyMigration =
            LegacyMigration(repository, notes)
    }
}
