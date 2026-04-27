package com.assetvault.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.assetvault.data.AppDatabase
import com.assetvault.data.SignatureEntity
import com.assetvault.databinding.FragmentVaultBinding
import com.assetvault.network.CloudApiClient
import kotlinx.coroutines.launch

/**
 * VaultFragment - Module 3
 * Displays list of user-added protected assets with status badges.
 * Syncs enforcement status for sighting items on load.
 */
class VaultFragment : Fragment() {

    private var _binding: FragmentVaultBinding? = null
    private val binding get() = _binding!!
    private val TAG = "VaultFragment"

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
            val prefs = com.assetvault.data.SecurePreferences.getInstance(requireContext())
            val currentUser = prefs.getOwnerId()
            val assets = db.signatureDao().getByOwnerEmail(currentUser)

            if (assets.isEmpty()) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.recyclerViewAssets.visibility = View.GONE
            } else {
                binding.tvEmptyState.visibility = View.GONE
                binding.recyclerViewAssets.visibility = View.VISIBLE

                // Sync enforcement status for sighting items before displaying
                syncEnforcementStatus(assets, db)

                // Reload after sync to get updated isEnforced values
                val refreshed = db.signatureDao().getByOwnerEmail(currentUser)
                adapter.submitList(refreshed)
            }
        }
    }

    /**
     * For each sighting item that isn't already enforced,
     * check the API to see if the owner has activated enforcement.
     * Update local DB accordingly so the adapter can blur enforced sightings.
     */
    private suspend fun syncEnforcementStatus(
        assets: List<SignatureEntity>,
        db: AppDatabase
    ) {
        val sightings = assets.filter { it.status == "SIGHTING" && !it.isEnforced }

        for (sighting in sightings) {
            try {
                val enforcement = CloudApiClient.checkEnforcement(sighting.pHash)
                if (enforcement.isEnforced) {
                    sighting.isEnforced = true
                    db.signatureDao().update(sighting)
                    Log.d(TAG, "Enforcement synced for pHash: ${sighting.pHash.take(16)}")
                }
            } catch (e: Exception) {
                // Silently fail — enforcement check is best-effort
                Log.w(TAG, "Failed to check enforcement for ${sighting.pHash.take(16)}", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadAssets() // Refresh list + enforcement status when returning
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}