package com.assetvault.network

import com.google.gson.annotations.SerializedName

// ── POST /search ────────────────────────────────────────────────
data class SearchRequest(
    @SerializedName("hash") val phash: String,
    @SerializedName("device_hash") val deviceHash: String,
    @SerializedName("location_name") val locationName: String,
    @SerializedName("lat") val lat: Double,
    @SerializedName("lng") val lng: Double
)

data class SearchResponse(
    @SerializedName("match_found") val matchFound: Boolean,
    @SerializedName("similarity") val similarity: Double,
    @SerializedName("matched_phash") val matchedPhash: String?,
    @SerializedName("owner_blockchain_tx") val ownerBlockchainTx: String?,
    @SerializedName("sighting_id") val sightingId: String?,
    @SerializedName("is_enforced") val isEnforced: Boolean = false
)

// ── POST /register ──────────────────────────────────────────────
data class RegisterRequest(
    @SerializedName("hash") val phash: String,
    @SerializedName("user_id") val ownerId: String,
    @SerializedName("blockchain_tx") val blockchainTx: String
)

data class RegisterResponse(
    @SerializedName("registered") val registered: Boolean,
    @SerializedName("asset_id") val assetId: String?
)

// ── POST /protect — owner enforces protection ───────────────────
data class ProtectRequest(
    @SerializedName("hash") val phash: String,
    @SerializedName("user_id") val ownerId: String
)

data class ProtectResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("protected_at") val protectedAt: String?
)

// ── GET /enforcement?hash=X — check if enforcement is active ────
data class EnforcementResponse(
    @SerializedName("is_enforced") val isEnforced: Boolean,
    @SerializedName("enforced_at") val enforcedAt: String?,
    @SerializedName("user_id") val ownerId: String?
)

// ── GET /health ─────────────────────────────────────────────────
data class HealthResponse(
    @SerializedName("status") val status: String
)

// ── POST /fund-wallet ───────────────────────────────────────────────
data class FundWalletRequest(
    @SerializedName("wallet_address") val walletAddress: String,
    @SerializedName("app_email") val appEmail: String
)

data class FundWalletResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("tx_hash") val txHash: String?,
    @SerializedName("error") val error: String?
)
