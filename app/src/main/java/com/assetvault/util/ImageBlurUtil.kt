package com.assetvault.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * ImageBlurUtil — creates a pixelated/blurred version of an image.
 * Uses scale-down + scale-up for cross-API-level compatibility (no RenderScript needed).
 */
object ImageBlurUtil {

    /**
     * Blur a bitmap by pixelation.
     * @param pixelSize Higher = more blur. Default 20 creates a strong privacy blur.
     */
    fun blurBitmap(original: Bitmap, pixelSize: Int = 20): Bitmap {
        val width = original.width
        val height = original.height
        val smallWidth = maxOf(width / pixelSize, 1)
        val smallHeight = maxOf(height / pixelSize, 1)

        val small = Bitmap.createScaledBitmap(original, smallWidth, smallHeight, true)
        return Bitmap.createScaledBitmap(small, width, height, false)
    }

    /**
     * Load a bitmap from a content URI.
     * Returns null if the URI can't be read.
     */
    fun loadBitmapFromUri(context: Context, uriString: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageBlurUtil", "Error loading bitmap from uri: $uriString", e)
            null
        }
    }

    /**
     * Load a bitmap from URI, scaled down to save memory.
     */
    fun loadScaledBitmap(context: Context, uriString: String, maxDim: Int = 400): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)

            // First pass: get dimensions
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            // Calculate sample size
            val sampleSize = maxOf(
                options.outWidth / maxDim,
                options.outHeight / maxDim,
                1
            )

            // Second pass: decode with sample size
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageBlurUtil", "Error loading scaled bitmap from uri: $uriString", e)
            null
        }
    }
}
