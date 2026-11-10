package com.idealplayer.app.core.network

import com.idealplayer.app.core.network.dto.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("search/multi")
    suspend fun searchMulti(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("year") year: Int? = null,
        @Query("page") page: Int = 1,
        @Query("language") language: String? = null
    ): TmdbSearchResponse

    @GET("search/movie")
    suspend fun searchMovie(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("year") year: Int? = null,
        @Query("page") page: Int = 1,
        @Query("language") language: String? = null
    ): TmdbSearchResponse

    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("first_air_date_year") year: Int? = null,
        @Query("page") page: Int = 1,
        @Query("language") language: String? = null
    ): TmdbSearchResponse

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,external_ids,translations,videos",
        @Query("language") language: String? = null
    ): TmdbDetailResponse

    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,external_ids,translations,videos,aggregate_credits",
        @Query("language") language: String? = null
    ): TmdbDetailResponse
}
