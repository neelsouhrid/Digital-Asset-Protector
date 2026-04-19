package com.assetvault.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.assetvault.data.AppDatabase
import com.assetvault.data.SignatureEntity
import com.assetvault.databinding.FragmentVaultBinding
import kotlinx.coroutines.launch

/**
 * VaultFragment - Module 3
 * Displays list of user-added protected assets with status badges.
 */
class VaultFragment : Fragment() {

    private var _binding: FragmentVaultBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AssetListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVaultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        loadAssets()
    }

    private fun setupRecyclerView() {
        adapter = AssetListAdapter { asset ->
            // Navigate to detail view
            (activity as? MainActivity)?.navigateToAssetDetail(asset.id)
        }

        binding.recyclerViewAssets.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@VaultFragment.adapter
        }
    }

    private fun setupClickListeners() {
        binding.fabAddAsset.setOnClickListener {
            (activity as? MainActivity)?.navigateToAssetPicker()
        }
    }

    private fun loadAssets() {
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            val assets = db.signatureDao().getAll()

            if (assets.isEmpty()) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.recyclerViewAssets.visibility = View.GONE
            } else {
                binding.tvEmptyState.visibility = View.GONE
                binding.recyclerViewAssets.visibility = View.VISIBLE
                adapter.submitList(assets)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadAssets() // Refresh list when returning to this fragment
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}