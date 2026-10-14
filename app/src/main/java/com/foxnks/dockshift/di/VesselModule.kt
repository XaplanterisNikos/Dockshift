package com.foxnks.dockshift.di

import com.foxnks.dockshift.BuildConfig
import com.foxnks.dockshift.data.vessels.FileVesselCacheStore
import com.foxnks.dockshift.data.vessels.HttpPageFetcher
import com.foxnks.dockshift.data.vessels.PageFetcher
import com.foxnks.dockshift.data.vessels.SiteVesselRepository
import com.foxnks.dockshift.data.vessels.VesselCacheStore
import com.foxnks.dockshift.data.vessels.VesselSources
import com.foxnks.dockshift.domain.vessels.VesselRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module of the vessel lists: where they come from and where they are kept. */
@Module
@InstallIn(SingletonComponent::class)
abstract class VesselModule {

    /** The single repository, shared by "Today" and "Vessels". */
    @Binds
    @Singleton
    abstract fun bindVesselRepository(impl: SiteVesselRepository): VesselRepository

    /** Pages are downloaded with the built-in HTTP client. */
    @Binds
    abstract fun bindPageFetcher(impl: HttpPageFetcher): PageFetcher

    /** The lists are kept in a JSON file. */
    @Binds
    abstract fun bindVesselCacheStore(impl: FileVesselCacheStore): VesselCacheStore

    companion object {
        /** The pages' addresses, written into the build from `vessels.properties` (never committed). */
        @Provides
        fun provideVesselSources(): VesselSources =
            VesselSources(workingUrl = BuildConfig.VESSELS_WORKING_URL, scheduledUrl = BuildConfig.VESSELS_SCHEDULED_URL)
    }
}
