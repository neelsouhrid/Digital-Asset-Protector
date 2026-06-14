package com.assetvault.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.assetvault.R
import com.assetvault.data.SecurePreferences
import com.assetvault.databinding.FragmentRequestOwnershipBinding
import com.assetvault.network.SupabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RequestOwnershipFragment : Fragment() {

    private var _binding: FragmentRequestOwnershipBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRequestOwnershipBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        binding.btnRequest.setOnClickListener {
            val assetId = binding.etAssetId.text.toString().trim()
            
            if (assetId.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.request_asset_hash), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            performOwnershipRequest(assetId)
        }
    }
    
    private fun performOwnershipRequest(assetId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.btnRequest.isEnabled = false
            try {
                val prefs = SecurePreferences.getInstance(requireContext())
                val senderEmail = prefs.getGoogleEmail() ?: throw IllegalStateException("Not signed in")

                // Try to find the original owner. We query assets table.
                val assetDetails = SupabaseManager.fetchAssetByHash(assetId)
                    
                val recipientEmail = assetDetails?.app_email 
                    ?: throw Exception("Asset not found or owner unknown")

                val success = SupabaseManager.createTransferRequest(
                    assetPHash = assetId,
                    senderEmail = senderEmail,
                    recipientEmail = recipientEmail,
                    isCollaborative = false // Receiver requests full ownership usually
                )

                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(requireContext(), getString(R.string.transfer_request_sent), Toast.LENGTH_LONG).show()
                        binding.etAssetId.text.clear()
                        parentFragmentManager.popBackStack()
                    } else {
                        Toast.makeText(requireContext(), "Failed to send request. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Request failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.btnRequest.isEnabled = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
