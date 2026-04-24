package com.bubul.signatureengine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

class PHashEngine {

    companion object {
        const val HASH_SIZE = 8
        const val DCT_SIZE  = 32
    }

    fun computePHash(imagePath: String): String {
        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: throw IllegalArgumentException("Cannot load image: $imagePath")
        return computePHash(bitmap)
    }

    fun computePHash(bitmap: Bitmap): String {
        val small = Bitmap.createScaledBitmap(bitmap, DCT_SIZE, DCT_SIZE, true)
        val gray = Array(DCT_SIZE) { y ->
            DoubleArray(DCT_SIZE) { x ->
                val px = small.getPixel(x, y)
                0.299 * Color.red(px) + 0.587 * Color.green(px) + 0.114 * Color.blue(px)
            }
        }
        val dct     = applyDCT(gray)
        val topLeft = Array(HASH_SIZE) { y -> DoubleArray(HASH_SIZE) { x -> dct[y][x] } }
        var sum = 0.0; var count = 0
        for (y in 0 until HASH_SIZE) for (x in 0 until HASH_SIZE) {
            if (y == 0 && x == 0) continue
            sum += topLeft[y][x]; count++
        }
        val mean = sum / count
        val bits = StringBuilder()
        for (y in 0 until HASH_SIZE) for (x in 0 until HASH_SIZE)
            bits.append(if (topLeft[y][x] > mean) '1' else '0')
        return binaryToHex(bits.toString())
    }

    private fun applyDCT(input: Array<DoubleArray>): Array<DoubleArray> {
        val n      = input.size
        val output = Array(n) { DoubleArray(n) }
        for (u in 0 until n) for (v in 0 until n) {
            var sum = 0.0
            for (i in 0 until n) for (j in 0 until n)
                sum += input[i][j] *
                        cos((2 * i + 1) * u * PI / (2 * n)) *
                        cos((2 * j + 1) * v * PI / (2 * n))
            val cu = if (u == 0) 1.0 / sqrt(2.0) else 1.0
            val cv = if (v == 0) 1.0 / sqrt(2.0) else 1.0
            output[u][v] = (2.0 / n) * cu * cv * sum
        }
        return output
    }

    private fun binaryToHex(binary: String): String =
        binary.chunked(4).joinToString("") { it.toInt(2).toString(16) }

    fun hammingDistance(h1: String, h2: String): Int {
        val b1 = h1.map { it.digitToInt(16).toString(2).padStart(4, '0') }.joinToString("")
        val b2 = h2.map { it.digitToInt(16).toString(2).padStart(4, '0') }.joinToString("")
        return b1.zip(b2).count { (a, b) -> a != b }
    }

    fun similarity(h1: String, h2: String): Float = 1f - (hammingDistance(h1, h2) / 64f)

    fun isMatch(h1: String, h2: String, threshold: Float = 0.85f): Boolean =
        similarity(h1, h2) >= threshold
}