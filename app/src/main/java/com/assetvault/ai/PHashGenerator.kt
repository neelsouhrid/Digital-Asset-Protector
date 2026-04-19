package com.assetvault.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import kotlin.math.abs

/**
 * PHashGenerator - Module 2
 * Generates perceptual hash for analog-hole resistance.
 * Uses DCT (Discrete Cosine Transform) based pHash algorithm.
 */
object PHashGenerator {

    private const val TAG = "PHashGenerator"
    private const val HASH_SIZE = 8 // 8x8 = 64 bit hash

    /**
     * Generate a perceptual hash from image bytes.
     * This is resistant to resizing, compression, and minor modifications.
     */
    fun generatePHash(imageBytes: ByteArray): String {
        Log.d(TAG, "Generating pHash for ${imageBytes.size} bytes")

        // Decode image
        val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: throw IllegalArgumentException("Could not decode image")

        return try {
            // Resize to 32x32 for DCT
            val bitmap = Bitmap.createScaledBitmap(originalBitmap, 32, 32, true)

            // Convert to grayscale
            val grayscale = toGrayscale(bitmap)

            // Apply DCT
            val dct = applyDCT(grayscale)

            // Get top-left 8x8 (excluding DC component)
            val dctValues = DoubleArray(HASH_SIZE * HASH_SIZE)
            for (y in 0 until HASH_SIZE) {
                for (x in 0 until HASH_SIZE) {
                    dctValues[y * HASH_SIZE + x] = dct[y][x]
                }
            }

            // Calculate median
            val sorted = dctValues.sorted()
            val median = sorted[sorted.size / 2]

            // Generate hash
            val hash = StringBuilder()
            for (value in dctValues) {
                hash.append(if (value > median) "1" else "0")
            }

            // Convert to hex for storage
            val hexHash = binaryToHex(hash.toString())

            Log.d(TAG, "Generated pHash: $hexHash")
            hexHash

        } finally {
            originalBitmap.recycle()
        }
    }

    private fun toGrayscale(bitmap: Bitmap): Array<IntArray> {
        val width = bitmap.width
        val height = bitmap.height
        val grayscale = Array(height) { IntArray(width) }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                // Luminance formula
                grayscale[y][x] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            }
        }

        return grayscale
    }

    private fun applyDCT(matrix: Array<IntArray>): Array<DoubleArray> {
        val n = matrix.size
        val dct = Array(n) { DoubleArray(n) }

        for (u in 0 until n) {
            for (v in 0 until n) {
                var sum = 0.0
                for (x in 0 until n) {
                    for (y in 0 until n) {
                        val pixel = matrix[y][x]
                        sum += pixel * kotlin.math.cos((2 * x + 1) * u * Math.PI / (2 * n)) *
                                kotlin.math.cos((2 * y + 1) * v * Math.PI / (2 * n))
                    }
                }

                val cu = if (u == 0) 1.0 / kotlin.math.sqrt(2.0) else 1.0
                val cv = if (v == 0) 1.0 / kotlin.math.sqrt(2.0) else 1.0
                dct[u][v] = (2.0 / n) * cu * cv * sum
            }
        }

        return dct
    }

    private fun binaryToHex(binary: String): String {
        val sb = StringBuilder()
        for (i in binary.indices step 4) {
            val chunk = binary.substring(i, minOf(i + 4, binary.length))
            val value = chunk.toInt(2)
            sb.append(Integer.toHexString(value))
        }
        return sb.toString()
    }

    /**
     * Compare two pHash values and return similarity (0.0 to 1.0).
     * Uses Hamming distance.
     */
    fun compareHash(hash1: String, hash2: String): Float {
        val bin1 = hexToBinary(hash1)
        val bin2 = hexToBinary(hash2)

        if (bin1.length != bin2.length) {
            Log.w(TAG, "Hash lengths don't match")
            return 0f
        }

        var matchingBits = 0
        for (i in bin1.indices) {
            if (bin1[i] == bin2[i]) matchingBits++
        }

        return matchingBits.toFloat() / bin1.length
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