package com.bubul.signatureengine

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

data class SignatureObject(
    val phash         : String,
    val faceVectorHex : String,
    val combinedHash  : String,
    val version       : String = "1.0"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("phash",           phash)
        put("face_vector_hex", faceVectorHex)
        put("combined_hash",   combinedHash)
        put("version",         version)
    }
}

data class MatchResult(
    val phashSimilarity     : Float,
    val embeddingSimilarity : Float,
    val combinedScore       : Float,
    val isMatch             : Boolean,
    val confidence          : String
)

class SignatureManager(context: Context) {

    private val embeddingEngine = EmbeddingEngine(context)
    private val pHashEngine     = PHashEngine()
    private var isInitialized   = false

    suspend fun initialize() = withContext(Dispatchers.IO) {
        embeddingEngine.initialize()
        isInitialized = true
    }

    suspend fun generateSignature(imagePath: String): SignatureObject =
        withContext(Dispatchers.IO) {
            check(isInitialized) { "Call initialize() first" }
            val phash        = pHashEngine.computePHash(imagePath)
            val embedding    = embeddingEngine.computeEmbedding(imagePath)
            val embeddingHex = embeddingEngine.embeddingToHex(embedding)
            val combinedHash = sha256(phash + embeddingHex)
            SignatureObject(phash, embeddingHex, combinedHash)
        }

    suspend fun generateSignature(bitmap: Bitmap): SignatureObject =
        withContext(Dispatchers.IO) {
            check(isInitialized) { "Call initialize() first" }
            val phash        = pHashEngine.computePHash(bitmap)
            val embedding    = embeddingEngine.computeEmbedding(bitmap)
            val embeddingHex = embeddingEngine.embeddingToHex(embedding)
            val combinedHash = sha256(phash + embeddingHex)
            SignatureObject(phash, embeddingHex, combinedHash)
        }

    fun compareSignatures(sig1: SignatureObject, sig2: SignatureObject): MatchResult {
        val phashSim     = pHashEngine.similarity(sig1.phash, sig2.phash)
        val vec1         = embeddingEngine.hexToEmbedding(sig1.faceVectorHex)
        val vec2         = embeddingEngine.hexToEmbedding(sig2.faceVectorHex)
        val embeddingSim = embeddingEngine.cosineSimilarity(vec1, vec2)
        val combined     = (phashSim * 0.6f) + (embeddingSim * 0.4f)
        return MatchResult(
            phashSimilarity     = phashSim,
            embeddingSimilarity = embeddingSim,
            combinedScore       = combined,
            isMatch             = combined >= 0.80f,
            confidence          = when {
                combined > 0.90f -> "HIGH"
                combined > 0.80f -> "MEDIUM"
                else             -> "LOW"
            }
        )
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun release() { embeddingEngine.close() }
}
