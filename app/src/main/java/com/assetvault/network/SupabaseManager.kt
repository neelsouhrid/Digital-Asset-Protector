package com.assetvault.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.assetvault.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class SupportTicket(
    val id: String? = null,
    val user_id: String? = null,
    val user_email: String = "",
    val user_name: String? = null,
    val description: String = "",
    val cloudinary_image_url: String? = null,
    val status: String = "open",
    val raised_by_gemini: Boolean = false,
    val created_at: String? = null
)

@Serializable
data class AssetSighting(
    val asset_id: String,
    val lat: Double,
    val lng: Double,
    val accuracy_meters: Double
)

@Serializable
data class SupabaseAssetRecord(
    val id: String? = null,
    val user_id: String? = null,
    val name: String? = null,
    val hash: String = "",
    val storage_path: String? = null,
    val status: String? = null,
    val blockchain_tx: String? = null,
    val app_email: String? = null,
    val is_enforced: Boolean = false,
    val created_at: String? = null
)

@Serializable
data class ProtectedAssetRecord(
    val id: Long? = null,
    val phash: String = "",
    val owner_id: String = "",
    val blockchain_tx: String? = null,
    val created_at: String? = null
)

@Serializable
data class TransferRequestRecord(
    val id: String? = null,
    val asset_id: String? = null,
    val sender_id: String? = null,
    val recipient_id: String? = null,
    val recipient_email: String = "",
    val status: String = "pending",
    val is_collaborative: Boolean = false,
    val created_at: String? = null
)

@Serializable
data class ProfileRecord(
    val id: String? = null,
    val user_id: String? = null,
    val display_name: String? = null,
    val email: String? = null,
    val is_admin: Boolean = false,
    val created_at: String? = null,
    val updated_at: String? = null,
    val text: String? = null
)

object SupabaseManager {

    private const val TAG = "SupabaseManager"

    val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            install(Postgrest)
            install(Storage)
        }
    }

    /** Service-role client – bypasses Row-Level Security (RLS). */
    private val serviceClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_SERVICE_KEY
        ) {
            install(Postgrest)
        }
    }

    private fun isConfigured(): Boolean {
        return BuildConfig.SUPABASE_KEY.isNotEmpty() && BuildConfig.SUPABASE_KEY != "YOUR_SUPABASE_KEY_HERE"
    }

    // ── Sightings ───────────────────────────────────────────────────────

    suspend fun fetchSightings(): List<AssetSighting> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext emptyList()
                client.postgrest["sightings"].select().decodeList<AssetSighting>()
            } catch (e: Exception) {
                Log.e(TAG, "Exception during fetchSightings: ${e.message}", e)
                emptyList()
            }
        }
    }

    suspend fun pushSighting(sighting: AssetSighting): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext false
                client.postgrest["sightings"].insert(sighting)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Exception during pushSighting: ${e.message}", e)
                false
            }
        }
    }

    // ── Vault Cloud Sync ────────────────────────────────────────────────

    suspend fun fetchUserAssets(email: String): List<SupabaseAssetRecord> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext emptyList()
                client.postgrest["assets"]
                    .select { filter { eq("app_email", email) } }
                    .decodeList<SupabaseAssetRecord>()
            } catch (e: Exception) {
                Log.e(TAG, "fetchUserAssets error: ${e.message}", e)
                emptyList()
            }
        }
    }

    suspend fun fetchAssetByHash(hash: String): SupabaseAssetRecord? {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext null
                client.postgrest["assets"]
                    .select { filter { eq("hash", hash) } }
                    .decodeList<SupabaseAssetRecord>()
                    .firstOrNull()
            } catch (e: Exception) {
                Log.e(TAG, "fetchAssetByHash error: ${e.message}", e)
                null
            }
        }
    }

    suspend fun fetchProtectedAssets(ownerId: String): List<ProtectedAssetRecord> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext emptyList()
                client.postgrest["protected_assets"]
                    .select { filter { eq("owner_id", ownerId) } }
                    .decodeList<ProtectedAssetRecord>()
            } catch (e: Exception) {
                Log.e(TAG, "fetchProtectedAssets error: ${e.message}", e)
                emptyList()
            }
        }
    }

    // ── Tickets ─────────────────────────────────────────────────────────

    suspend fun submitTicket(
        context: Context,
        issue: String,
        imageUri: Uri?,
        userEmail: String,
        userName: String?,
        raisedByGemini: Boolean = false
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) {
                    Log.e(TAG, "Supabase key is missing")
                    return@withContext false
                }

                var cloudinaryUrl: String? = null

                // Upload image to Cloudinary if provided
                if (imageUri != null) {
                    cloudinaryUrl = CloudinaryManager.uploadImage(context, imageUri)
                }

                val ticket = SupportTicket(
                    user_id = userEmail,
                    user_email = userEmail,
                    user_name = userName,
                    description = issue,
                    cloudinary_image_url = cloudinaryUrl,
                    raised_by_gemini = raisedByGemini
                )
                client.postgrest["support_tickets"].insert(ticket)
                true
            } catch (e: Exception) {
                Log.e(TAG, "submitTicket error: ${e.message}", e)
                false
            }
        }
    }

    suspend fun fetchTickets(geminiOnly: Boolean): List<SupportTicket> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext emptyList()
                if (geminiOnly) {
                    client.postgrest["support_tickets"]
                        .select { filter { eq("raised_by_gemini", true) } }
                        .decodeList<SupportTicket>()
                } else {
                    client.postgrest["support_tickets"]
                        .select { filter { eq("raised_by_gemini", false) } }
                        .decodeList<SupportTicket>()
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchTickets error: ${e.message}", e)
                emptyList()
            }
        }
    }

    suspend fun resolveTicket(ticketId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext false
                client.postgrest["support_tickets"].update(
                    { set("status", "resolved") }
                ) { filter { eq("id", ticketId) } }
                true
            } catch (e: Exception) {
                Log.e(TAG, "resolveTicket error: ${e.message}", e)
                false
            }
        }
    }

    // ── Transfer Requests ───────────────────────────────────────────────

    suspend fun fetchIncomingTransferRequests(recipientEmail: String): List<TransferRequestRecord> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext emptyList()
                client.postgrest["transfer_requests"]
                    .select { filter {
                        eq("recipient_email", recipientEmail)
                        eq("status", "pending")
                    } }
                    .decodeList<TransferRequestRecord>()
            } catch (e: Exception) {
                Log.e(TAG, "fetchIncomingTransferRequests error: ${e.message}", e)
                emptyList()
            }
        }
    }

    suspend fun countPendingTransferRequests(recipientEmail: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext 0
                val list = client.postgrest["transfer_requests"]
                    .select { filter {
                        eq("recipient_email", recipientEmail)
                        eq("status", "pending")
                    } }
                    .decodeList<TransferRequestRecord>()
                list.size
            } catch (e: Exception) {
                Log.e(TAG, "countPendingTransferRequests error: ${e.message}", e)
                0
            }
        }
    }

    // ── Transfer Request Functions ────────────────────────────────────────

    suspend fun createTransferRequest(
        assetPHash: String,
        senderEmail: String,
        recipientEmail: String,
        isCollaborative: Boolean = false
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext false
                
                // asset_id in transfer_requests is a UUID, so we need to fetch the asset's UUID
                val assetDetails = fetchAssetByHash(assetPHash)
                val assetUuid = assetDetails?.id ?: throw Exception("Asset not found in cloud database")

                // fetch sender's UUID since sender_id is typed as UUID
                val senderProfiles = client.postgrest["profiles"]
                    .select { filter { eq("email", senderEmail) } }
                    .decodeList<ProfileRecord>()
                val senderUuid = senderProfiles.firstOrNull()?.id ?: throw Exception("Sender profile not found")

                // fetch recipient's UUID since recipient_id is NOT NULL
                val recipientProfiles = client.postgrest["profiles"]
                    .select { filter { eq("email", recipientEmail) } }
                    .decodeList<ProfileRecord>()
                var recipientUuid = recipientProfiles.firstOrNull()?.id

                // If recipient doesn't have a profile yet, create one
                if (recipientUuid == null) {
                    val newId = UUID.randomUUID().toString()
                    serviceClient.postgrest["profiles"].insert(
                        ProfileRecord(id = newId, email = recipientEmail, is_admin = false)
                    )
                    recipientUuid = newId
                    Log.d(TAG, "Created profile for new recipient: $recipientEmail -> $recipientUuid")
                }

                val record = TransferRequestRecord(
                    asset_id = assetUuid,
                    sender_id = senderUuid,
                    recipient_id = recipientUuid,
                    recipient_email = recipientEmail,
                    status = "pending",
                    is_collaborative = isCollaborative
                )
                serviceClient.postgrest["transfer_requests"].insert(record)
                Log.d(TAG, "Transfer request created: $senderEmail -> $recipientEmail for UUID $assetUuid")
                true
            } catch (e: Exception) {
                Log.e(TAG, "createTransferRequest error: ${e.message}", e)
                false
            }
        }
    }

    suspend fun updateTransferRequestStatus(requestId: String, newStatus: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext false
                serviceClient.postgrest["transfer_requests"].update(
                    { set("status", newStatus) }
                ) { filter { eq("id", requestId) } }
                Log.d(TAG, "Transfer request $requestId -> $newStatus")
                true
            } catch (e: Exception) {
                Log.e(TAG, "updateTransferRequestStatus error: ${e.message}", e)
                false
            }
        }
    }

    suspend fun approveTransferAndUpdateOwner(
        requestId: String,
        assetUuid: String,
        newOwnerEmail: String,
        isCollaborative: Boolean
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext false

                // We need the pHash to update the protected_assets table and assets table correctly
                val assetDetails = serviceClient.postgrest["assets"]
                    .select { filter { eq("id", assetUuid) } }
                    .decodeList<SupabaseAssetRecord>()
                    .firstOrNull() ?: throw Exception("Asset not found for UUID $assetUuid")
                
                val assetPHash = assetDetails.hash

                // Mark request as accepted
                updateTransferRequestStatus(requestId, "accepted")

                // If not collaborative, do a full ownership transfer
                if (!isCollaborative) {
                    adminOverrideTransfer(assetPHash, newOwnerEmail)
                } else {
                    // Collaborative: just update enforcement status, keep original owner
                    serviceClient.postgrest["assets"].update(
                        { set("is_enforced", false) }
                    ) { filter { eq("hash", assetPHash) } }

                    // Add new protected_asset record for the new owner so it shows in their vault
                    val newRecord = ProtectedAssetRecord(
                        phash = assetPHash,
                        owner_id = newOwnerEmail
                    )
                    serviceClient.postgrest["protected_assets"].insert(newRecord)
                }
                Log.d(TAG, "Transfer approved. Collaborative=$isCollaborative, newOwner=$newOwnerEmail")
                true
            } catch (e: Exception) {
                Log.e(TAG, "approveTransferAndUpdateOwner error: ${e.message}", e)
                false
            }
        }
    }

    suspend fun lookupEmailByWalletAddress(walletAddress: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext null
                val profiles = client.postgrest["profiles"]
                    .select { filter { eq("wallet_address", walletAddress) } }
                    .decodeList<ProfileRecord>()
                profiles.firstOrNull()?.email
            } catch (e: Exception) {
                Log.e(TAG, "lookupEmailByWalletAddress error: ${e.message}", e)
                null
            }
        }
    }

    suspend fun lookupEmailByUuid(uuid: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext null
                val profiles = client.postgrest["profiles"]
                    .select { filter { eq("id", uuid) } }
                    .decodeList<ProfileRecord>()
                profiles.firstOrNull()?.email
            } catch (e: Exception) {
                Log.e(TAG, "lookupEmailByUuid error: ${e.message}", e)
                null
            }
        }
    }

    suspend fun lookupAssetPHashByUuid(uuid: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext null
                val assets = client.postgrest["assets"]
                    .select { filter { eq("id", uuid) } }
                    .decodeList<SupabaseAssetRecord>()
                assets.firstOrNull()?.hash
            } catch (e: Exception) {
                Log.e(TAG, "lookupAssetPHashByUuid error: ${e.message}", e)
                null
            }
        }
    }

    suspend fun ensureWalletAddressStored(email: String, walletAddress: String) {
        withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext
                client.postgrest["profiles"].update(
                    { set("wallet_address", walletAddress) }
                ) { filter { eq("email", email) } }
            } catch (e: Exception) {
                Log.e(TAG, "ensureWalletAddressStored error: ${e.message}", e)
            }
        }
    }

    // ── Admin Functions ─────────────────────────────────────────────────

    suspend fun adminOverrideTransfer(assetHash: String, newOwnerEmail: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext false
                // Update protected_assets owner
                serviceClient.postgrest["protected_assets"].update(
                    { set("owner_id", newOwnerEmail) }
                ) { filter { eq("phash", assetHash) } }

                // Update assets table: set is_enforced to false and change owner email
                serviceClient.postgrest["assets"].update(
                    {
                        set("app_email", newOwnerEmail)
                        set("is_enforced", false)
                    }
                ) { filter { eq("hash", assetHash) } }

                Log.d(TAG, "Admin override transfer complete: $assetHash -> $newOwnerEmail")
                true
            } catch (e: Exception) {
                Log.e(TAG, "adminOverrideTransfer error: ${e.message}", e)
                false
            }
        }
    }

    suspend fun isUserAdmin(email: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext false
                val profiles = client.postgrest["profiles"]
                    .select { filter { eq("email", email) } }
                    .decodeList<ProfileRecord>()
                
                if (profiles.isEmpty()) {
                    // Create the profile so the user can easily find it in Supabase and edit is_admin
                    try {
                        client.postgrest["profiles"].insert(
                            ProfileRecord(email = email, is_admin = false)
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to create default profile: ${e.message}")
                    }
                    return@withContext false
                }
                
                profiles.any { it.is_admin }
            } catch (e: Exception) {
                Log.e(TAG, "isUserAdmin error: ${e.message}", e)
                false
            }
        }
    }

    // ── Unprotect Asset ─────────────────────────────────────────────────

    suspend fun unprotectAsset(pHash: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) return@withContext false
                client.postgrest["assets"].update(
                    { set("is_enforced", false) }
                ) { filter { eq("hash", pHash) } }
                true
            } catch (e: Exception) {
                Log.e(TAG, "unprotectAsset error: ${e.message}", e)
                false
            }
        }
    }
}
