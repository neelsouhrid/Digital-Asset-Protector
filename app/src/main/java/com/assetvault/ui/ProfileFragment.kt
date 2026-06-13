package com.assetvault.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import coil.load
import coil.transform.CircleCropTransformation
import com.assetvault.R
import com.assetvault.data.SecurePreferences
import com.assetvault.databinding.FragmentProfileBinding
import com.assetvault.network.BlockchainManager

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        val prefs = SecurePreferences.getInstance(requireContext())
        val name = prefs.getGoogleDisplayName() ?: "Unknown User"
        val email = prefs.getGoogleEmail() ?: "No Email"
        val photoUrl = prefs.getGooglePhotoUrl()

        binding.tvName.text = name
        binding.tvEmail.text = email

        if (photoUrl != null) {
            binding.ivProfilePic.load(photoUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_image_placeholder)
            }
        }

        // Get wallet address securely
        val walletAddress = BlockchainManager.getWalletAddress() ?: "Wallet not initialized"
        binding.tvWalletAddress.text = walletAddress

        binding.btnCopyWallet.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Wallet Address", walletAddress)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Wallet address copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
