package com.foxnks.dockshift.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/**
 * Provides the current time through [Clock] instead of calling `now()` directly,
 * so tests can fix the time (e.g. "02:00 after a night shift").
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    /** The phone's clock, in its own time zone. */
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
