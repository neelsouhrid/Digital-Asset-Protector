package com.assetvault.data

import android.content.Context
import android.content.SharedPreferences
import android.security.securepreferences.SecureSharedPreferences
import android.util.Log

/**
 * SecurePreferences - Module 3
 * EncryptedSharedPreferences for Private Keys and UUID.
 */
class SecurePreferences(context: Context) {

    private val TAG = "SecurePreferences"

    private val prefs: SharedPreferences = SecureSharedPreferences(
        context,
        PREFS_NAME,
        Context.MODE_PRIVATE
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