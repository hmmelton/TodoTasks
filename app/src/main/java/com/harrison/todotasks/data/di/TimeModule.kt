package com.harrison.todotasks.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/** Provides the time source. */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    /** Provides the system clock in UTC. */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
