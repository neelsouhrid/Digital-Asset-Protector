package com.assetvault.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * ContentMonitorAccessibilityService - Module 6
 * Monitors screen state to detect "View Once" or sensitive content displays.
 * Triggers "Analog Hole" protection when sensitive content is displayed.
 */
class ContentMonitorAccessibilityService : AccessibilityService() {

    private val TAG = "ContentMonitorService"

    // Sensitive content indicators
    private val sensitivePackages = listOf(
        "com.whatsapp",
        "com.instagram.android",
        "com.snapchat.android",
        "com.facebook.katana",
        "org.telegram.messenger",
        "com.signal",
        "net.azalabs.app" // View Once indicator
    )

    private var isMonitoring = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        isMonitoring = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isMonitoring) return

        val packageName = event.packageName?.toString() ?: return

        // Check for sensitive content being displayed
        if (sensitivePackages.any { packageName.contains(it) }) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    Log.d(TAG, "Window changed to: $packageName")
                    checkForSensitiveContent(event)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    // Could analyze content for "view once" indicators
                    Log.d(TAG, "Content changed in: $packageName")
                }
            }
        }
    }

    private fun checkForSensitiveContent(event: AccessibilityEvent) {
        // Check if the content is likely "view once" or sensitive
        // This would analyze the content hierarchy for indicators

        val text = event.text?.joinToString(" ") ?: ""
        val contentDescription = event.contentDescription ?: ""

        val isSensitive = text.contains("view once", ignoreCase = true) ||
                text.contains("disappearing", ignoreCase = true) ||
                contentDescription.contains("view once", ignoreCase = true)

        if (isSensitive) {
            Log.w(TAG, "Sensitive content detected - triggering protection")
            triggerAnalogHoleProtection(event)
        }
    }

    private fun triggerAnalogHoleProtection(event: AccessibilityEvent) {
        // Notify the app about potential analog hole risk
        // In production, this would:
        // 1. Capture screenshot detection
        // 2. Run VectorEngine + PHashGenerator on captured content
        // 3. Query Vertex AI for matches
        // 4. Alert user if unauthorized copy detected

        Log.i(TAG, "Analog hole protection triggered")

        // Broadcast intent to main app
        val intent = Intent(ACTION_ANALOG_HOLE_DETECTED).apply {
            putExtra(EXTRA_PACKAGE, event.packageName?.toString())
            putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
        sendBroadcast(intent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isMonitoring = false
        Log.d(TAG, "Accessibility service destroyed")
    }

    companion object {
        const val ACTION_ANALOG_HOLE_DETECTED = "com.assetvault.ANALOG_HOLE_DETECTED"
        const val EXTRA_PACKAGE = "package_name"
        const val EXTRA_TIMESTAMP = "timestamp"
    }
}