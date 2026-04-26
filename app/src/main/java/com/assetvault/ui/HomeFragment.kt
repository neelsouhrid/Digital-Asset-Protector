package com.assetvault.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.assetvault.databinding.FragmentHomeBinding

/**
 * HomeFragment - Task 7
 * Shows two access modes: Protect Specific Photo and Protect My Library.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Option A — Protect Specific Photo (uses existing AssetPickerFragment)
        binding.cardProtectSingle.setOnClickListener {
            (activity as? MainActivity)?.navigateToAssetPicker()
        }

        // Option B — Protect My Library (new LibraryFragment)
        binding.cardProtectLibrary.setOnClickListener {
            (activity as? MainActivity)?.navigateToLibrary()
        }

        // View Vault
        binding.btnViewVault.setOnClickListener {
            (activity as? MainActivity)?.navigateToVault()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
