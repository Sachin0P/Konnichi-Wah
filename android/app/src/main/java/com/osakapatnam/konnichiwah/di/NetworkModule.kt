package com.osakapatnam.konnichiwah.di

import com.osakapatnam.konnichiwah.BuildConfig
import com.osakapatnam.konnichiwah.data.remote.KonnichiApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // ignoreUnknownKeys lets the backend add new response fields without
    // breaking app builds that don't know about them yet. encodeDefaults
    // makes requests always send every field (e.g. "avoid": []), so what we
    // send matches docs/api.md exactly.
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // AI answers can be slow: the planner waits up to 20s for Gemini,
            // and with the backend's local model (-llm=local) a plan can take
            // up to 2 minutes on a laptop. OkHttp's default 10s read timeout
            // would give up long before then.
            .readTimeout(130, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideKonnichiApi(retrofit: Retrofit): KonnichiApi = retrofit.create(KonnichiApi::class.java)
}
