package com.assetvault.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootReceiver - Module 6
 * Ensures the Accessibility Service restarts automatically on phone reboot.
 */
class BootReceiver : BroadcastReceiver() {

    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed - Accessibility service should auto-start")

            // The Accessibility Service will be started by Android
            // if the user has enabled it in Settings.
            // This receiver is mainly for logging and any additional setup.

            // Optionally prompt user to enable the service
            promptEnableService(context)
        }
    }

    private fun promptEnableService(context: Context) {
        // Could show a notification asking user to enable the service
        // For now, just log that boot was completed
        Log.d(TAG, "Device booted - Asset Vault ready")
    }
}