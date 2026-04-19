package com.assetvault.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.assetvault.data.AppDatabase
import com.assetvault.data.SignatureEntity
import com.assetvault.ai.VectorEngine
import com.assetvault.ai.PHashGenerator
import com.assetvault.databinding.FragmentAssetPickerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AssetPickerFragment - Module 1
 * Launches system file picker via ACTION_OPEN_DOCUMENT.
 * Handles persistable URI permission grant.
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

        // Process the selected file - Module 2 & 3
        processSelectedAsset(uri)
    }

    private fun processSelectedAsset(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(requireContext())
                
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

                // Read file bytes from URI
                val bytes = withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }

                if (bytes == null || bytes.isEmpty()) {
                    throw IllegalStateException("Could not read file data")
                }

                Log.d(TAG, "File size: ${bytes.size} bytes")

                // Generate hex vector & pHash on background thread - Module 2
                val (hexVector, pHash) = withContext(Dispatchers.Default) {
                    val hex = VectorEngine.generateHexVector(bytes)
                    val p = PHashGenerator.generatePHash(bytes)
                    Pair(hex, p)
                }
                
                Log.d(TAG, "Generated hex vector (first 64 chars): ${hexVector.take(64)}...")
                Log.d(TAG, "Generated pHash: $pHash")

                // Save to database - Module 3
                val entity = SignatureEntity(
                    uri = uri.toString(),
                    hexVector = hexVector,
                    pHash = pHash,
                    timestamp = System.currentTimeMillis(),
                    blockchainTxId = null,
                    status = "pending"
                )

                val db = AppDatabase.getInstance(requireContext())
                val assetId = db.signatureDao().insert(entity)

                Log.d(TAG, "Saved asset with ID: $assetId")

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvStatus.text = "Asset added! ID: $assetId"
                    Toast.makeText(requireContext(), "Asset protected successfully!", Toast.LENGTH_SHORT).show()

                    // Navigate back to vault after short delay
                    binding.root.postDelayed({
                        parentFragmentManager.popBackStack()
                    }, 1500)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing asset", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvStatus.text = "Error: ${e.message}"
                    Toast.makeText(requireContext(), "Failed to process asset", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}