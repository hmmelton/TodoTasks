package com.harrison.todotasks.data.di

import com.harrison.todotasks.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** Provides the HTTP client for the sync server. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Provides the single [HttpClient], backed by OkHttp and pointed at the sync server. */
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient =
        createHttpClient(engine = OkHttp.create(), baseUrl = BuildConfig.SYNC_BASE_URL)

    /**
     * Builds an [HttpClient] on [engine] that resolves relative URLs against [baseUrl] and reads
     * and writes JSON, ignoring unknown keys. Tests call this with a mock engine.
     */
    fun createHttpClient(engine: HttpClientEngine, baseUrl: String): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            defaultRequest { url(baseUrl) }
        }
}
