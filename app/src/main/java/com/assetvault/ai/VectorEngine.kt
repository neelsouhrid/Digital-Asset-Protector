package com.assetvault.ai

import android.util.Log

/**
 * VectorEngine - Module 2
 * Converts a pHash hex string into a 64-dimension float array.
 * The pHash IS the semantic vector — each bit becomes a dimension (0.0f or 1.0f).
 */
object VectorEngine {

    private const val TAG = "VectorEngine"
    private const val VECTOR_DIMENSION = 64

    /**
     * Convert a pHash hex string to a 64-dimension float array.
     * Each hex character → 4 bits → 4 float dimensions.
     */
    fun pHashToVector(pHashHex: String): FloatArray {
        Log.d(TAG, "Converting pHash to vector: $pHashHex")

        val binaryString = hexToBinary(pHashHex)

        // Ensure exactly 64 bits
        val padded = binaryString.padEnd(VECTOR_DIMENSION, '0').take(VECTOR_DIMENSION)

        val vector = FloatArray(VECTOR_DIMENSION) { i ->
            if (padded[i] == '1') 1.0f else 0.0f
        }

        Log.d(TAG, "Generated ${vector.size}-dim vector")
        return vector
    }

    /**
     * Compare two float vectors using cosine similarity.
     * Returns 0.0..1.0
     */
    fun compareVectors(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 0f

        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        val denom = Math.sqrt((normA * normB).toDouble()).toFloat()
        return if (denom == 0f) 0f else dot / denom
    }

    private fun hexToBinary(hex: String): String {
        val sb = StringBuilder()
        for (c in hex) {
            val value = c.digitToInt(16)
            sb.append(String.format("%4s", Integer.toBinaryString(value)).replace(' ', '0'))
        }
        return sb.toString()
    }
}