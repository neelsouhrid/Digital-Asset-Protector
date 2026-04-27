package com.assetvault.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.assetvault.databinding.FragmentRequestOwnershipBinding

class RequestOwnershipFragment : Fragment() {

    private var _binding: FragmentRequestOwnershipBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRequestOwnershipBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnRequest.setOnClickListener {
            val assetId = binding.etAssetId.text.toString().trim()
            
            if (assetId.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter an Asset ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Mocking request ownership
            Toast.makeText(requireContext(), "Ownership request sent for \n$assetId", Toast.LENGTH_LONG).show()
            binding.etAssetId.text.clear()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
