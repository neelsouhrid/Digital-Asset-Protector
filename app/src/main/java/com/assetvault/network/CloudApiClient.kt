package com.assetvault.network

import android.util.Log
import com.assetvault.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * CloudApiClient - Module 5
 * Real Retrofit client for the Cloud Run pHash API.
 */
object CloudApiClient {

    private const val TAG = "CloudApiClient"

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.CLOUD_RUN_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val api: AssetVaultApi by lazy {
        retrofit.create(AssetVaultApi::class.java)
    }

    /**
     * POST /search — check if a pHash already exists in the system.
     */
    suspend fun searchAsset(
        phash: String,
        deviceHash: String,
        locationName: String,
        lat: Double,
        lng: Double
    ): SearchResponse = withContext(Dispatchers.IO) {
        Log.d(TAG, "Searching for asset with pHash: ${phash.take(16)}...")
        val request = SearchRequest(phash, deviceHash, locationName, lat, lng)
        val response = api.searchAsset(request)
        if (response.isSuccessful) {
            val body = response.body()
                ?: throw Exception("Empty response from /search")
            Log.d(TAG, "Search result: match=${body.matchFound}, sim=${body.similarity}")
            body
        } else {
            throw Exception("Search failed: ${response.code()} ${response.errorBody()?.string()}")
        }
    }

    /**
     * POST /register — register a new protected asset.
     */
    suspend fun registerAsset(
        phash: String,
        ownerId: String,
        blockchainTx: String
    ): RegisterResponse = withContext(Dispatchers.IO) {
        Log.d(TAG, "Registering asset with pHash: ${phash.take(16)}...")
        val request = RegisterRequest(phash, ownerId, blockchainTx)
        val response = api.registerAsset(request)
        if (response.isSuccessful) {
            val body = response.body()
                ?: throw Exception("Empty response from /register")
            Log.d(TAG, "Register result: registered=${body.registered}, id=${body.assetId}")
            body
        } else {
            throw Exception("Register failed: ${response.code()} ${response.errorBody()?.string()}")
        }
    }

    /**
     * POST /protect — owner enforces protection on their asset.
     * After this call, sighting devices will blur the image.
     */
    suspend fun protectAsset(
        phash: String,
        ownerId: String
    ): ProtectResponse = withContext(Dispatchers.IO) {
        Log.d(TAG, "Enforcing protection for pHash: ${phash.take(16)}...")
        val request = ProtectRequest(phash, ownerId)
        val response = api.protectAsset(request)
        if (response.isSuccessful) {
            val body = response.body()
                ?: throw Exception("Empty response from /protect")
            Log.d(TAG, "Protect result: success=${body.success}")
            body
        } else {
            throw Exception("Protect failed: ${response.code()} ${response.errorBody()?.string()}")
        }
    }

    /**
     * GET /enforcement?phash=X — check if an asset's enforcement is active.
     * Called by sighting devices to know whether to blur.
     */
    suspend fun checkEnforcement(phash: String): EnforcementResponse = withContext(Dispatchers.IO) {
        Log.d(TAG, "Checking enforcement for pHash: ${phash.take(16)}...")
        val response = api.checkEnforcement(phash)
        if (response.isSuccessful) {
            val body = response.body()
                ?: throw Exception("Empty response from /enforcement")
            Log.d(TAG, "Enforcement: isEnforced=${body.isEnforced}")
            body
        } else {
            // If endpoint doesn't exist yet, default to not enforced
            Log.w(TAG, "Enforcement check failed: ${response.code()}")
            EnforcementResponse(isEnforced = false, enforcedAt = null, ownerId = null)
        }
    }

    /**
     * GET /health — check if the Cloud Run service is alive.
     */
    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = api.healthCheck()
            response.isSuccessful && response.body()?.status == "ok"
        } catch (e: Exception) {
            Log.e(TAG, "Health check failed", e)
            false
        }
    }

    /**
     * POST /fund-wallet — requests startup gas from the backend
     */
    suspend fun fundWallet(
        walletAddress: String,
        appEmail: String
    ): FundWalletResponse = withContext(Dispatchers.IO) {
        Log.d(TAG, "Requesting startup gas for: $walletAddress...")
        val request = FundWalletRequest(walletAddress, appEmail)
        val response = api.fundWallet(request)
        if (response.isSuccessful) {
            val body = response.body()
                ?: throw Exception("Empty response from /fund-wallet")
            Log.d(TAG, "Fund wallet result: success=${body.success}")
            body
        } else {
            throw Exception("Fund wallet failed: ${response.code()} ${response.errorBody()?.string()}")
        }
    }
}