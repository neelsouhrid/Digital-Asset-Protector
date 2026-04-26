package com.assetvault.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.util.Log

/**
 * SecurePreferences - Module 3
 * EncryptedSharedPreferences for Private Keys, UUID, and Google email.
 */
class SecurePreferences(context: Context) {

    private val TAG = "SecurePreferences"

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getOrCreateUuid(): String {
        val existing = prefs.getString(KEY_USER_UUID, null)
        if (existing != null) {
            return existing
        }

        val newUuid = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_USER_UUID, newUuid).apply()
        Log.d(TAG, "Generated new UUID: $newUuid")
        return newUuid
    }

    fun getPrivateKey(): String? {
        return prefs.getString(KEY_PRIVATE_KEY, null)
    }

    fun setPrivateKey(privateKey: String) {
        prefs.edit().putString(KEY_PRIVATE_KEY, privateKey).apply()
        Log.d(TAG, "Private key stored securely")
    }

    fun getPublicKey(): String? {
        return prefs.getString(KEY_PUBLIC_KEY, null)
    }

    fun setPublicKey(publicKey: String) {
        prefs.edit().putString(KEY_PUBLIC_KEY, publicKey).apply()
    }

    // ── Google Sign-In ──────────────────────────────────────────

    fun getGoogleEmail(): String? {
        return prefs.getString(KEY_GOOGLE_EMAIL, null)
    }

    fun setGoogleEmail(email: String) {
        prefs.edit().putString(KEY_GOOGLE_EMAIL, email).apply()
        Log.d(TAG, "Google email stored: $email")
    }

    fun getGoogleDisplayName(): String? {
        return prefs.getString(KEY_GOOGLE_NAME, null)
    }

    fun setGoogleDisplayName(name: String) {
        prefs.edit().putString(KEY_GOOGLE_NAME, name).apply()
    }

    fun getGooglePhotoUrl(): String? {
        return prefs.getString(KEY_GOOGLE_PHOTO, null)
    }

    fun setGooglePhotoUrl(url: String) {
        prefs.edit().putString(KEY_GOOGLE_PHOTO, url).apply()
    }

    /**
     * Returns the owner ID to use everywhere.
     * Prefers Google email, falls back to UUID.
     */
    fun getOwnerId(): String {
        return getGoogleEmail() ?: getOrCreateUuid()
    }

    fun isSignedIn(): Boolean {
        return getGoogleEmail() != null
    }

    fun clearGoogleAccount() {
        prefs.edit()
            .remove(KEY_GOOGLE_EMAIL)
            .remove(KEY_GOOGLE_NAME)
            .remove(KEY_GOOGLE_PHOTO)
            .apply()
        Log.d(TAG, "Google account cleared")
    }

    fun clearKeys() {
        prefs.edit()
            .remove(KEY_PRIVATE_KEY)
            .remove(KEY_PUBLIC_KEY)
            .apply()
        Log.d(TAG, "Keys cleared")
    }

    fun isFirstRun(): Boolean {
        return prefs.getBoolean(KEY_FIRST_RUN, true)
    }

    fun setFirstRunComplete() {
        prefs.edit().putBoolean(KEY_FIRST_RUN, false).apply()
    }

    companion object {
        private const val PREFS_NAME = "asset_vault_secure_prefs"
        private const val KEY_USER_UUID = "user_uuid"
        private const val KEY_PRIVATE_KEY = "private_key"
        private const val KEY_PUBLIC_KEY = "public_key"
        private const val KEY_FIRST_RUN = "first_run"
        private const val KEY_GOOGLE_EMAIL = "google_email"
        private const val KEY_GOOGLE_NAME = "google_display_name"
        private const val KEY_GOOGLE_PHOTO = "google_photo_url"

        @Volatile
        private var INSTANCE: SecurePreferences? = null

        fun getInstance(context: Context): SecurePreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = SecurePreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}