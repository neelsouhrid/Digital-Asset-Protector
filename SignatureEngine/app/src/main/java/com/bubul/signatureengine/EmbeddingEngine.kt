package com.bubul.signatureengine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class EmbeddingEngine(private val context: Context) {

    private var interpreter: Interpreter? = null

    companion object {
        const val MODEL_FILE     = "image_embedder.tflite"
        const val IMG_SIZE       = 224
        const val EMBEDDING_SIZE = 1280
    }

    fun initialize() {
        val model   = loadModelFile()
        val options = Interpreter.Options().apply { numThreads = 4 }
        interpreter = Interpreter(model, options)
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fd          = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fd.fileDescriptor)
        val channel     = inputStream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    fun computeEmbedding(imagePath: String): FloatArray {
        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: throw IllegalArgumentException("Cannot load image: $imagePath")
        return computeEmbedding(bitmap)
    }

    fun computeEmbedding(bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, IMG_SIZE, IMG_SIZE, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * IMG_SIZE * IMG_SIZE * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        inputBuffer.rewind()
        for (y in 0 until IMG_SIZE) {
            for (x in 0 until IMG_SIZE) {
                val pixel = resized.getPixel(x, y)
                inputBuffer.putFloat(((pixel shr 16 and 0xFF) / 127.5f) - 1f)
                inputBuffer.putFloat(((pixel shr  8 and 0xFF) / 127.5f) - 1f)
                inputBuffer.putFloat(((pixel        and 0xFF) / 127.5f) - 1f)
            }
        }
        val outputSize   = interpreter?.getOutputTensor(0)?.shape()?.last() ?: EMBEDDING_SIZE
        val outputBuffer = Array(1) { FloatArray(outputSize) }
        interpreter?.run(inputBuffer, outputBuffer)
            ?: throw IllegalStateException("Call initialize() first")
        return normalizeVector(outputBuffer[0])
    }

    private fun normalizeVector(vector: FloatArray): FloatArray {
        val mag = sqrt(vector.map { it * it }.sum())
        return if (mag > 0f) FloatArray(vector.size) { i -> vector[i] / mag } else vector
    }

    fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        var dot = 0f; var mag1 = 0f; var mag2 = 0f
        for (i in vec1.indices) {
            dot  += vec1[i] * vec2[i]
            mag1 += vec1[i] * vec1[i]
            mag2 += vec2[i] * vec2[i]
        }
        val denom = sqrt(mag1) * sqrt(mag2)
        return if (denom > 0f) dot / denom else 0f
    }

    fun embeddingToHex(embedding: FloatArray): String {
        val buf = ByteBuffer.allocate(embedding.size * 4)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        embedding.forEach { buf.putFloat(it) }
        return buf.array().joinToString("") { "%02x".format(it) }
    }

    fun hexToEmbedding(hex: String): FloatArray {
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val buf   = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buf.getFloat() }
    }

    fun close() { interpreter?.close(); interpreter = null }
}