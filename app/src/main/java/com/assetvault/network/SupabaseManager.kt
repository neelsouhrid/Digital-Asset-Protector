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
    val issue: String,
    val image_url: String? = null,
    val status: String = "open"
)

@Serializable
data class AssetSighting(
    val asset_id: String,
    val lat: Double,
    val lng: Double,
    val accuracy_meters: Double
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

    suspend fun fetchSightings(): List<AssetSighting> {
        return withContext(Dispatchers.IO) {
            try {
                if (BuildConfig.SUPABASE_KEY.isEmpty() || BuildConfig.SUPABASE_KEY == "YOUR_SUPABASE_KEY_HERE") {
                    return@withContext emptyList()
                }
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
                if (BuildConfig.SUPABASE_KEY.isEmpty() || BuildConfig.SUPABASE_KEY == "YOUR_SUPABASE_KEY_HERE") {
                    return@withContext false
                }
                client.postgrest["sightings"].insert(sighting)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Exception during pushSighting: ${e.message}", e)
                false
            }
        }
    }

    suspend fun submitTicket(context: Context, issue: String, imageUri: Uri?): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (BuildConfig.SUPABASE_KEY.isEmpty() || BuildConfig.SUPABASE_KEY == "YOUR_SUPABASE_KEY_HERE") {
                    Log.e(TAG, "Supabase key is missing in local.properties")
                    return@withContext false
                }

                var imageUrl: String? = null

                // Upload image if provided
                if (imageUri != null) {
                    val inputStream = context.contentResolver.openInputStream(imageUri)
                    if (inputStream != null) {
                        val bytes = inputStream.readBytes()
                        inputStream.close()
                        
                        val fileName = "ticket_images/${UUID.randomUUID()}.jpg"
                        val bucket = client.storage["tickets"] // Ensure this bucket exists and is public
                        bucket.upload(fileName, bytes)
                        imageUrl = bucket.publicUrl(fileName)
                    }
                }

                // Insert ticket into Database
                val ticket = SupportTicket(issue = issue, image_url = imageUrl)
                client.postgrest["support_tickets"].insert(ticket) // Ensure this table exists with correct schema

                true
            } catch (e: Exception) {
                Log.e(TAG, "Exception during submitTicket: ${e.message}", e)
                false
            }
        }
    }
}
