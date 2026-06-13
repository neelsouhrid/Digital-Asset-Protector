package com.assetvault.network

import org.json.JSONArray
import org.json.JSONObject

object MetadataUtil {
    /**
     * Generates a Pinata-compatible metadata JSON string.
     * Supports multiple owners and licensing terms for digital assets.
     */
    fun generatePinataMetadata(
        assetName: String,
        description: String,
        owners: List<String>,
        licenseType: String,
        licenseTerms: String
    ): String {
        val metadata = JSONObject()
        metadata.put("name", assetName)
        
        val keyvalues = JSONObject()
        keyvalues.put("description", description)
        keyvalues.put("license_type", licenseType)
        keyvalues.put("license_terms", licenseTerms)
        
        val ownersArray = JSONArray()
        owners.forEach { ownersArray.put(it) }
        // We store the array as a string representation since Pinata keyvalues strictly expect strings, numbers, or booleans.
        keyvalues.put("co_owners", ownersArray.toString())

        metadata.put("keyvalues", keyvalues)
        return metadata.toString()
    }
}
