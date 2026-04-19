package com.assetvault.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.assetvault.data.AppDatabase
import com.assetvault.databinding.FragmentAssetDetailBinding
import com.assetvault.network.BlockchainManager
import com.assetvault.network.CloudApiClient
import kotlinx.coroutines.launch

/**
 * AssetDetailFragment - Module 4 & 5
 * Shows asset details and allows minting on blockchain.
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

        binding.btnMint.setOnClickListener {
            mintAsset()
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

            if (asset != null) {
                binding.tvAssetUri.text = "URI: ${asset.uri}"
                binding.tvHexVector.text = "Hex: ${asset.hexVector.take(100)}..."
                binding.tvPHash.text = "pHash: ${asset.pHash}"
                binding.tvTimestamp.text = "Added: ${formatTimestamp(asset.timestamp)}"
                binding.tvStatus.text = "Status: ${asset.status.uppercase()}"

                // Show blockchain info if minted
                if (asset.blockchainTxId != null) {
                    binding.tvBlockchainTx.text = "Tx: ${asset.blockchainTxId}"
                    binding.tvBlockchainTx.visibility = View.VISIBLE
                    binding.btnMint.isEnabled = false
                    binding.btnMint.text = "Already Minted"
                } else {
                    binding.tvBlockchainTx.visibility = View.GONE
                }
            } else {
                Toast.makeText(requireContext(), "Asset not found", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun mintAsset() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.tvStatus.text = "Minting..."

            try {
                val db = AppDatabase.getInstance(requireContext())
                val asset = db.signatureDao().getById(assetId)

                if (asset == null) {
                    throw IllegalStateException("Asset not found")
                }

                // Mint on blockchain - Module 4
                val txId = BlockchainManager.mintAsset(asset.hexVector, asset.pHash)

                // Update database with tx ID
                asset.blockchainTxId = txId
                asset.status = "protected"
                db.signatureDao().update(asset)

                binding.progressBar.visibility = View.GONE
                binding.tvBlockchainTx.text = "Tx: $txId"
                binding.tvBlockchainTx.visibility = View.VISIBLE
                binding.tvStatus.text = "Status: PROTECTED"
                binding.btnMint.isEnabled = false
                binding.btnMint.text = "Already Minted"

                Toast.makeText(requireContext(), "Asset minted successfully!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = "Minting failed: ${e.message}"
                Toast.makeText(requireContext(), "Minting failed", Toast.LENGTH_SHORT).show()
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

                if (asset == null) {
                    throw IllegalStateException("Asset not found")
                }

                // Query Vertex AI Vector Search - Module 5
                val result = CloudApiClient.verifyAsset(asset.hexVector)

                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = "Verification: $result"

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