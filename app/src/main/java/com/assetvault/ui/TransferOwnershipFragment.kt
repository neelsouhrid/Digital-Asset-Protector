package com.assetvault.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.assetvault.databinding.FragmentTransferOwnershipBinding

class TransferOwnershipFragment : Fragment() {

    private var _binding: FragmentTransferOwnershipBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTransferOwnershipBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnTransfer.setOnClickListener {
            val assetId = binding.etAssetId.text.toString().trim()
            val newOwner = binding.etNewOwnerAddress.text.toString().trim()
            
            if (assetId.isEmpty() || newOwner.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Mocking transfer since smart contract doesn't have it yet
            Toast.makeText(requireContext(), "Ownership transfer initiated for \n$assetId\n to \n$newOwner", Toast.LENGTH_LONG).show()
            binding.etAssetId.text.clear()
            binding.etNewOwnerAddress.text.clear()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
