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

    val uri: String,              // Content URI of the protected file
    val hexVector: String,        // 64-dim vector as hex (from pHash)
    val pHash: String,            // Perceptual hash from PHashGenerator
    val timestamp: Long,          // When the asset was added
    var blockchainTxId: String?,  // Polygon transaction ID (null until minted)
    var status: String,           // "PENDING", "PROTECTED", "SIGHTING"
    var similarity: Float? = null,    // Match % for sightings
    var ownerEmail: String? = null,   // Google account email of the owner
    var isEnforced: Boolean = false   // True = owner activated protection, blur on sighting devices
)