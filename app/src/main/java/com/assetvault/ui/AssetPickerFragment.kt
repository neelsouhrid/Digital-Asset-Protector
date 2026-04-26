package com.assetvault.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.assetvault.ai.PHashGenerator
import com.assetvault.data.AppDatabase
import com.assetvault.data.SecurePreferences
import com.assetvault.data.SignatureEntity
import com.assetvault.databinding.FragmentAssetPickerBinding
import com.assetvault.network.BlockchainManager
import com.assetvault.network.CloudApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AssetPickerFragment - Module 1
 * Launches system file picker via ACTION_OPEN_DOCUMENT.
 * Handles persistable URI permission grant.
 * Now implements the full Task 4 registration flow:
 *   pHash → /search → blockchain → /register → Room DB → ResultFragment
 */
class AssetPickerFragment : Fragment() {

    private var _binding: FragmentAssetPickerBinding? = null
    private val binding get() = _binding!!

    private val TAG = "AssetPickerFragment"

    // Using ActivityResultContracts.OpenDocument for scoped access
    private val pickDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            handleSelectedUri(uri)
        } else {
            Log.d(TAG, "No file selected")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAssetPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSelectAsset.setOnClickListener {
            launchFilePicker()
        }

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun launchFilePicker() {
        // Open any image file with persistable permission
        pickDocument.launch(arrayOf("image/*"))
    }

    private fun handleSelectedUri(uri: Uri) {
        Log.d(TAG, "Selected URI: $uri")

        // Take persistable URI permission
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Log.d(TAG, "Persistable permission granted for: $uri")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to take persistable permission", e)
            Toast.makeText(requireContext(), "Could not get persistent access", Toast.LENGTH_SHORT).show()
        }

        // Show processing state
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Processing asset..."

        // Run the full Task 4 registration flow
        processAssetWithRegistration(uri)
    }

    /**
     * Full Task 4 registration flow:
     * Step 1: Generate pHash
     * Step 2: POST /search with pHash + location
     * Step 3a: If match → save as SIGHTING
     * Step 3b: If no match → blockchain register → POST /register → save as PROTECTED
     * Step 4: Show ResultFragment
     */
    private fun processAssetWithRegistration(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(requireContext())
                val prefs = SecurePreferences.getInstance(requireContext())

                // Check if already processed (Rule 6)
                val existing = db.signatureDao().getByUri(uri.toString())
                if (existing != null) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        binding.tvStatus.text = "Asset already exists!"
                        Toast.makeText(requireContext(), "Asset already in vault", Toast.LENGTH_SHORT).show()
                        binding.root.postDelayed({
                            parentFragmentManager.popBackStack()
                        }, 1500)
                    }
                    return@launch
                }

                // Step 1: Read file bytes and generate pHash
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "Generating signature..."
                }

                val bytes = withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }

                if (bytes == null || bytes.isEmpty()) {
                    throw IllegalStateException("Could not read file data")
                }

                Log.d(TAG, "File size: ${bytes.size} bytes")

                val pHash = withContext(Dispatchers.Default) {
                    PHashGenerator.generatePHash(bytes)
                }

                Log.d(TAG, "Generated pHash: $pHash")

                // Step 2: Get device info for search
                val deviceHash = Settings.Secure.getString(
                    requireContext().contentResolver,
                    Settings.Secure.ANDROID_ID
                ) ?: "unknown"

                val ownerId = prefs.getOwnerId()

                // Step 3: Search cloud for existing match
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "Checking for existing copies..."
                }

                val searchResult = CloudApiClient.searchAsset(
                    phash = pHash,
                    deviceHash = deviceHash,
                    locationName = "Unknown",
                    lat = 0.0,
                    lng = 0.0
                )

                if (searchResult.matchFound) {
                    // ── Step 4a: SIGHTING ──────────────────────────────
                    Log.d(TAG, "Match found! Similarity: ${searchResult.similarity}%")

                    val entity = SignatureEntity(
                        uri = uri.toString(),
                        hexVector = pHash,
                        pHash = pHash,
                        timestamp = System.currentTimeMillis(),
                        blockchainTxId = null,
                        status = "SIGHTING",
                        similarity = searchResult.similarity.toFloat(),
                        ownerEmail = ownerId
                    )
                    db.signatureDao().insert(entity)

                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        showResult(
                            status = "SIGHTING",
                            similarity = searchResult.similarity.toFloat(),
                            uri = uri.toString()
                        )
                    }
                } else {
                    // ── Step 4b: PROTECT ────────────────────────────────
                    withContext(Dispatchers.Main) {
                        binding.tvStatus.text = "Registering on blockchain..."
                    }

                    // Register on blockchain
                    val txHash = BlockchainManager.registerAsset(pHash)
                    Log.d(TAG, "Blockchain tx: $txHash")

                    // Register on cloud
                    withContext(Dispatchers.Main) {
                        binding.tvStatus.text = "Registering with cloud..."
                    }

                    CloudApiClient.registerAsset(pHash, ownerId, txHash)

                    // Save to Room
                    val entity = SignatureEntity(
                        uri = uri.toString(),
                        hexVector = pHash,
                        pHash = pHash,
                        timestamp = System.currentTimeMillis(),
                        blockchainTxId = txHash,
                        status = "PROTECTED",
                        ownerEmail = ownerId
                    )
                    db.signatureDao().insert(entity)

                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        showResult(
                            status = "PROTECTED",
                            txHash = txHash,
                            uri = uri.toString()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing asset", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvStatus.text = "Error: ${e.message}"
                    Toast.makeText(requireContext(), "Failed to process asset: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showResult(
        status: String,
        txHash: String? = null,
        similarity: Float? = null,
        uri: String? = null
    ) {
        val resultFragment = ResultFragment.newInstance(
            status = status,
            txHash = txHash,
            similarity = similarity,
            uri = uri
        )
        parentFragmentManager.beginTransaction()
            .replace(com.assetvault.R.id.fragmentContainer, resultFragment)
            .addToBackStack("result")
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}