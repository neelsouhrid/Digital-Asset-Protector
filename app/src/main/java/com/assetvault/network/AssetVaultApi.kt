package com.assetvault.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface for the Cloud Run pHash API.
 */
interface AssetVaultApi {

    @POST("search")
    suspend fun searchAsset(@Body request: SearchRequest): Response<SearchResponse>

    @POST("register")
    suspend fun registerAsset(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("protect")
    suspend fun protectAsset(@Body request: ProtectRequest): Response<ProtectResponse>

    @GET("enforcement")
    suspend fun checkEnforcement(@Query("hash") phash: String): Response<EnforcementResponse>

    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>

    @POST("fund-wallet")
    suspend fun fundWallet(@Body request: FundWalletRequest): Response<FundWalletResponse>
}
