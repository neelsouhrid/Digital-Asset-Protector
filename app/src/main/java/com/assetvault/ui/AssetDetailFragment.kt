package com.assetvault.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.assetvault.R
import com.assetvault.data.AppDatabase
import com.assetvault.data.SecurePreferences
import com.assetvault.databinding.FragmentAssetDetailBinding
import com.assetvault.network.CloudApiClient
import com.assetvault.util.ImageBlurUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AssetDetailFragment - Module 4 & 5
 * Shows asset details, Protect Asset button for owners,
 * and blur enforcement for sighting devices.
 */
class AssetDetailFragment : Fragment() {

    private var _binding: FragmentAssetDetailBinding? = null
    private val binding get() = _binding!!

    private var assetId: Long = -1

    companion object {
        private const val ARG_ASSET_ID = "asset_id"

        fun newInstance(assetId: Long): AssetDetailFragment {
            return AssetDetailFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_ASSET_ID, assetId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assetId = arguments?.getLong(ARG_ASSET_ID) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAssetDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnProtect.setOnClickListener {
            enforceProtection()
        }

        binding.btnVerify.setOnClickListener {
            verifyAsset()
        }

        loadAssetDetails()
    }

    private fun loadAssetDetails() {
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            val asset = db.signatureDao().getById(assetId)

            if (asset == null) {
                Toast.makeText(requireContext(), "Asset not found", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
                return@launch
            }

            val prefs = SecurePreferences.getInstance(requireContext())
            val currentUser = prefs.getOwnerId()
            val isOwner = asset.ownerEmail == currentUser ||
                          asset.status == "PROTECTED"

            // Load image preview
            withContext(Dispatchers.Default) {
                val bitmap = ImageBlurUtil.loadScaledBitmap(requireContext(), asset.uri)
                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        if (asset.status == "SIGHTING" && asset.isEnforced) {
                            // Show blurred image for enforced sightings
                            val blurred = ImageBlurUtil.blurBitmap(bitmap, 25)
                            binding.ivPreview.setImageBitmap(blurred)
                            binding.layoutBlurOverlay.visibility = View.VISIBLE
                        } else {
                            binding.ivPreview.setImageBitmap(bitmap)
                            binding.layoutBlurOverlay.visibility = View.GONE
                        }
                    }
                }
            }

            // Fill info fields
            binding.tvAssetUri.text = "File: ${asset.uri.substringAfterLast("/")}"
            binding.tvHexVector.text = "Vector: ${asset.hexVector.take(32)}..."
            binding.tvPHash.text = "pHash: ${asset.pHash}"
            binding.tvTimestamp.text = "Added: ${formatTimestamp(asset.timestamp)}"

            // Status badge
            when (asset.status) {
                "PROTECTED" -> {
                    binding.tvStatus.text = "Status: ✅ PROTECTED"
                    binding.tvStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_protected)
                    )
                }
                "SIGHTING" -> {
                    val simText = asset.similarity?.let { "%.1f%%".format(it) } ?: "?"
                    binding.tvStatus.text = "Status: ⚠️ SIGHTING ($simText match)"
                    binding.tvStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_sighting)
                    )
                }
                else -> {
                    binding.tvStatus.text = "Status: ${asset.status}"
                    binding.tvStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_pending)
                    )
                }
            }

            // Blockchain tx
            if (asset.blockchainTxId != null) {
                binding.tvBlockchainTx.text = "Tx: ${asset.blockchainTxId}"
                binding.tvBlockchainTx.visibility = View.VISIBLE
            } else {
                binding.tvBlockchainTx.visibility = View.GONE
            }

            // ── Protect button visibility ───────────────────────
            if (isOwner && asset.status == "PROTECTED") {
                // Owner sees Protect button
                binding.btnProtect.visibility = View.VISIBLE

                if (asset.isEnforced) {
                    binding.btnProtect.isEnabled = false
                    binding.btnProtect.text = "Already Protected"
                    binding.tvEnforcementStatus.visibility = View.VISIBLE
                    binding.tvEnforcementStatus.text = getString(R.string.enforcement_active)
                    binding.tvEnforcementStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_protected)
                    )
                } else {
                    binding.btnProtect.isEnabled = true
                    binding.tvEnforcementStatus.visibility = View.VISIBLE
                    binding.tvEnforcementStatus.text = getString(R.string.enforcement_inactive)
                    binding.tvEnforcementStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.text_secondary)
                    )
                }
            } else {
                // Sighting or non-owner — hide protect button
                binding.btnProtect.visibility = View.GONE

                if (asset.status == "SIGHTING") {
                    // Check enforcement status from API
                    checkAndDisplayEnforcement(asset.pHash)
                }
            }
        }
    }

    /**
     * Owner presses "Protect Asset" — calls the API to enforce protection.
     */
    private fun enforceProtection() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnProtect.isEnabled = false

            try {
                val db = AppDatabase.getInstance(requireContext())
                val asset = db.signatureDao().getById(assetId)
                    ?: throw IllegalStateException("Asset not found")

                val prefs = SecurePreferences.getInstance(requireContext())
                val ownerId = prefs.getOwnerId()

                // Call API to enforce protection
                val result = CloudApiClient.protectAsset(asset.pHash, ownerId)

                if (result.success) {
                    // Update local DB
                    asset.isEnforced = true
                    db.signatureDao().update(asset)

                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        binding.btnProtect.isEnabled = false
                        binding.btnProtect.text = "Already Protected"
                        binding.tvEnforcementStatus.visibility = View.VISIBLE
                        binding.tvEnforcementStatus.text = getString(R.string.enforcement_active)
                        binding.tvEnforcementStatus.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.status_protected)
                        )

                        Toast.makeText(
                            requireContext(),
                            "Protection enforced! Sighting devices will blur this image.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    throw Exception("Server returned success=false")
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnProtect.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        "Failed to enforce protection: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * For sighting devices: check with the API if the owner has enforced protection.
     * If yes, blur the image preview.
     */
    private fun checkAndDisplayEnforcement(pHash: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val enforcement = CloudApiClient.checkEnforcement(pHash)

                if (enforcement.isEnforced) {
                    // Update local DB
                    val db = AppDatabase.getInstance(requireContext())
                    val asset = db.signatureDao().getById(assetId)
                    if (asset != null) {
                        asset.isEnforced = true
                        db.signatureDao().update(asset)
                    }

                    withContext(Dispatchers.Main) {
                        // Blur the image
                        val bitmap = withContext(Dispatchers.Default) {
                            ImageBlurUtil.loadScaledBitmap(requireContext(), asset?.uri ?: "")
                        }
                        if (bitmap != null) {
                            val blurred = ImageBlurUtil.blurBitmap(bitmap, 25)
                            binding.ivPreview.setImageBitmap(blurred)
                        }
                        binding.layoutBlurOverlay.visibility = View.VISIBLE

                        binding.tvEnforcementStatus.visibility = View.VISIBLE
                        binding.tvEnforcementStatus.text = getString(R.string.sighting_enforced)
                        binding.tvEnforcementStatus.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.status_sighting)
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.tvEnforcementStatus.visibility = View.VISIBLE
                        binding.tvEnforcementStatus.text = getString(R.string.sighting_not_enforced)
                        binding.tvEnforcementStatus.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.text_secondary)
                        )
                    }
                }
            } catch (e: Exception) {
                // Silently fail — enforcement check is best-effort
            }
        }
    }

    private fun verifyAsset() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.tvStatus.text = "Verifying..."

            try {
                val db = AppDatabase.getInstance(requireContext())
                val asset = db.signatureDao().getById(assetId)
                    ?: throw IllegalStateException("Asset not found")

                // Check on-chain existence
                val exists = com.assetvault.network.BlockchainManager.checkAsset(asset.pHash)

                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = if (exists) {
                    "Verified ✅ — Asset exists on blockchain"
                } else {
                    "Not found on blockchain"
                }

                Toast.makeText(requireContext(), "Verification complete!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = "Verification failed: ${e.message}"
                Toast.makeText(requireContext(), "Verification failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatTimestamp(ts: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(ts))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}