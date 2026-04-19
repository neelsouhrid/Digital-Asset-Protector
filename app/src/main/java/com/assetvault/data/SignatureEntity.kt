package com.assetvault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SignatureEntity - Module 3
 * Data model for protected assets.
 */
@Entity(tableName = "signatures")
data class SignatureEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uri: String,           // Content URI of the protected file
    val hexVector: String,     // 512-dim hex vector from VectorEngine
    val pHash: String,        // Perceptual hash from PHashGenerator
    val timestamp: Long,      // When the asset was added
    val blockchainTxId: String?,  // Polygon transaction ID (null until minted)
    val status: String        // "pending", "protected", "failed"
)