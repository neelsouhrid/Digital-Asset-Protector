package com.assetvault.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.assetvault.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest

/**
 * CloudinaryManager - Handles unsigned image uploads to Cloudinary.
 * Uses the unsigned upload preset configured in local.properties.
 */
object CloudinaryManager {

    private const val TAG = "CloudinaryManager"
    private val httpClient = OkHttpClient()

    /**
     * Uploads an image to Cloudinary using unsigned upload.
     * Returns the secure_url on success, null on failure.
     */
    suspend fun uploadImage(context: Context, imageUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
                val apiKey = BuildConfig.CLOUDINARY_API_KEY
                val apiSecret = BuildConfig.CLOUDINARY_API_SECRET

                if (cloudName.isEmpty() || cloudName == "YOUR_CLOUDINARY_CLOUD_NAME") {
                    Log.e(TAG, "Cloudinary cloud name is not configured")
                    return@withContext null
                }

                val inputStream = context.contentResolver.openInputStream(imageUri)
                    ?: return@withContext null
                val bytes = inputStream.readBytes()
                inputStream.close()

                val timestamp = (System.currentTimeMillis() / 1000L).toString()
                
                // Generate SHA-1 signature
                val strToSign = "timestamp=$timestamp$apiSecret"
                val md = MessageDigest.getInstance("SHA-1")
                val digest = md.digest(strToSign.toByteArray(Charsets.UTF_8))
                val signature = digest.joinToString("") { "%02x".format(it) }

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("api_key", apiKey)
                    .addFormDataPart("timestamp", timestamp)
                    .addFormDataPart("signature", signature)
                    .addFormDataPart("file", "ticket_image.jpg",
                        bytes.toRequestBody("image/jpeg".toMediaTypeOrNull()))
                    .build()

                val request = Request.Builder()
                    .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)
                    val secureUrl = json.getString("secure_url")
                    Log.d(TAG, "Image uploaded to Cloudinary: $secureUrl")
                    secureUrl
                } else {
                    Log.e(TAG, "Cloudinary upload failed: ${response.code} $responseBody")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cloudinary upload exception: ${e.message}", e)
                null
            }
        }
    }
}
