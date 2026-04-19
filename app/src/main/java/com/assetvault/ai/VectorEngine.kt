package com.assetvault.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayInputStream
import java.security.MessageDigest

/**
 * VectorEngine - Module 2
 * Handles TFLite model inference - reads bytes from URI and converts to 512-dim hex vector.
 * Uses a simple hash-based approach for the prototype (replace with actual TFLite in production).
 */
object VectorEngine {

    private const val TAG = "VectorEngine"
    private const val VECTOR_DIMENSION = 512

    /**
     * Generate a 512-dimensional hex vector from raw image bytes.
     * In production, this would run actual TFLite/MediaPipe inference.
     * For prototype, we use SHA-256 hashing with padding.
     */
    fun generateHexVector(imageBytes: ByteArray): String {
        Log.d(TAG, "Generating hex vector for ${imageBytes.size} bytes")

        // Try to decode as bitmap for potential image processing
        val bitmap = try {
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Could not decode bitmap", e)
            null
        }

        // Generate deterministic vector from image data
        val vector = generateDeterministicVector(imageBytes, bitmap)

        // Convert to hex string
        val hexString = vector.joinToString("") { String.format("%02x", it) }

        Log.d(TAG, "Generated hex vector (length ${hexString.length})")
        return hexString
    }

    private fun generateDeterministicVector(bytes: ByteArray, bitmap: Bitmap?): ByteArray {
        val vector = ByteArray(VECTOR_DIMENSION)

        // Use multiple hash rounds for better distribution
        var currentHash = bytes

        for (i in 0 until 8) {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(currentHash)
            digest.update(i.toByte())
            val hash = digest.digest()

            // Fill vector slots
            for (j in 0 until minOf(64, VECTOR_DIMENSION - i * 64)) {
                val pos = i * 64 + j
                if (pos < VECTOR_DIMENSION) {
                    vector[pos] = hash[j % hash.size]
                }
            }

            currentHash = hash
        }

        // Add bitmap-based variation if available
        bitmap?.let { bmp ->
            // Add some variation based on image dimensions and average color
            val avgR = (0 until minOf(100, bmp.width)).sumOf { x ->
                (0 until minOf(100, bmp.height)).sumOf { y ->
                    val pixel = bmp.getPixel(x, y)
                    android.graphics.Color.red(pixel)
                }
            } / (minOf(100, bmp.width) * minOf(100, bmp.height))

            val avgG = (0 until minOf(100, bmp.width)).sumOf { x ->
                (0 until minOf(100, bmp.height)).sumOf { y ->
                    val pixel = bmp.getPixel(x, y)
                    android.graphics.Color.green(pixel)
                }
            } / (minOf(100, bmp.width) * minOf(100, bmp.height))

            val avgB = (0 until minOf(100, bmp.width)).sumOf { x ->
                (0 until minOf(100, bmp.height)).sumOf { y ->
                    val pixel = bmp.getPixel(x, y)
                    android.graphics.Color.blue(pixel)
                }
            } / (minOf(100, bmp.width) * minOf(100, bmp.height))

            // Add color-based variation to vector
            for (i in 0 until 16) {
                vector[i] = (vector[i].toInt() + avgR).toByte()
                vector[i + 16] = (vector[i + 16].toInt() + avgG).toByte()
                vector[i + 32] = (vector[i + 32].toInt() + avgB).toByte()
            }
        }

        return vector
    }

    /**
     * Compare two hex vectors and return similarity score (0.0 to 1.0).
     * Uses Hamming distance for comparison.
     */
    fun compareVectors(vector1: String, vector2: String): Float {
        if (vector1.length != vector2.length) {
            Log.w(TAG, "Vector lengths don't match")
            return 0f
        }

        val bytes1 = hexToBytes(vector1)
        val bytes2 = hexToBytes(vector2)

        var matchingBits = 0
        val totalBits = bytes1.size * 8

        for (i in bytes1.indices) {
            val xor = (bytes1[i].toInt() xor bytes2[i].toInt())
            matchingBits += Integer.bitCount(xor)
        }

        return 1f - (matchingBits.toFloat() / totalBits)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }
}