package com.assetvault.utils

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.charset.StandardCharsets

object WatermarkUtil {

    /**
     * Embeds a secret text message into the Least Significant Bits of the Bitmap's RGB channels.
     * Appends a null character (0x00) to signify the end of the message.
     */
    fun embedWatermark(original: Bitmap, message: String): Bitmap {
        val width = original.width
        val height = original.height
        val watermarked = original.copy(Bitmap.Config.ARGB_8888, true)

        // Convert message to bits. Append a delimiter (e.g. 0x00 character) to know when to stop reading
        val msgBytes = "$message\u0000".toByteArray(StandardCharsets.UTF_8)
        val bits = BooleanArray(msgBytes.size * 8)
        for (i in msgBytes.indices) {
            val b = msgBytes[i].toInt()
            for (j in 0..7) {
                bits[i * 8 + j] = (b shr (7 - j) and 1) == 1
            }
        }

        var bitIndex = 0
        val totalBits = bits.size

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (bitIndex >= totalBits) return watermarked

                val pixel = watermarked.getPixel(x, y)
                var r = Color.red(pixel)
                var g = Color.green(pixel)
                var b = Color.blue(pixel)
                val a = Color.alpha(pixel)

                // Modify Blue channel LSB
                if (bitIndex < totalBits) {
                    val bit = if (bits[bitIndex++]) 1 else 0
                    b = (b and 0xFE) or bit
                }
                
                // If more capacity needed, use Green and Red
                if (bitIndex < totalBits) {
                    val bit = if (bits[bitIndex++]) 1 else 0
                    g = (g and 0xFE) or bit
                }
                
                if (bitIndex < totalBits) {
                    val bit = if (bits[bitIndex++]) 1 else 0
                    r = (r and 0xFE) or bit
                }

                watermarked.setPixel(x, y, Color.argb(a, r, g, b))
            }
        }

        return watermarked
    }

    /**
     * Extracts the hidden watermark string by reading the Least Significant Bits of the Bitmap's RGB channels.
     * Stops reading when it encounters the null character (0x00).
     */
    fun extractWatermark(watermarked: Bitmap): String {
        val width = watermarked.width
        val height = watermarked.height
        
        val bytes = mutableListOf<Byte>()
        var currentByte = 0
        var bitCount = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = watermarked.getPixel(x, y)
                val b = Color.blue(pixel)
                val g = Color.green(pixel)
                val r = Color.red(pixel)

                // Read Blue
                currentByte = (currentByte shl 1) or (b and 1)
                bitCount++
                if (bitCount == 8) {
                    if (currentByte == 0) return String(bytes.toByteArray(), StandardCharsets.UTF_8)
                    bytes.add(currentByte.toByte())
                    currentByte = 0
                    bitCount = 0
                }

                // Read Green
                currentByte = (currentByte shl 1) or (g and 1)
                bitCount++
                if (bitCount == 8) {
                    if (currentByte == 0) return String(bytes.toByteArray(), StandardCharsets.UTF_8)
                    bytes.add(currentByte.toByte())
                    currentByte = 0
                    bitCount = 0
                }

                // Read Red
                currentByte = (currentByte shl 1) or (r and 1)
                bitCount++
                if (bitCount == 8) {
                    if (currentByte == 0) return String(bytes.toByteArray(), StandardCharsets.UTF_8)
                    bytes.add(currentByte.toByte())
                    currentByte = 0
                    bitCount = 0
                }
            }
        }
        
        return String(bytes.toByteArray(), StandardCharsets.UTF_8)
    }
}
