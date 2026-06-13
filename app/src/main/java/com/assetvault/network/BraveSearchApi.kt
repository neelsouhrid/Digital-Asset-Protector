package com.assetvault.network

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

data class BraveSearchResponse(
    val web: WebResults?
)

data class WebResults(
    val results: List<SearchResult>?
)

data class SearchResult(
    val title: String,
    val url: String,
    val description: String
)

interface BraveSearchApi {
    @GET("res/v1/web/search")
    suspend fun search(
        @Header("Accept") accept: String = "application/json",
        @Header("Accept-Encoding") acceptEncoding: String = "gzip",
        @Header("X-Subscription-Token") apiKey: String,
        @Query("q") query: String
    ): BraveSearchResponse
}
