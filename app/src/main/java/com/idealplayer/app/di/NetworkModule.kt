package com.idealplayer.app.di

import com.idealplayer.app.core.network.TmdbApi
import com.idealplayer.app.core.network.XtreamApi
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "IdealPlayer/Android")
                        .build()
                )
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideXtreamApi(client: OkHttpClient): XtreamApi {
        // The shared client also serves potentially large XMLTV/APK downloads. Bound only the
        // Xtream catalog requests so an unresponsive provider cannot leave a playlist refresh
        // pending indefinitely without cutting off legitimate long-lived downloads elsewhere.
        val xtreamClient = client.newBuilder()
            .callTimeout(2, TimeUnit.MINUTES)
            .build()
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(xtreamClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(XtreamApi::class.java)
    }

    @Provides
    @Singleton
    fun provideTmdbApi(client: OkHttpClient): TmdbApi {
        return Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TmdbApi::class.java)
    }
}
