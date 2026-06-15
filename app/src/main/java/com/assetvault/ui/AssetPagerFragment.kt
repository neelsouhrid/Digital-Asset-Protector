package com.assetvault.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.assetvault.data.AppDatabase
import com.assetvault.data.SecurePreferences
import com.assetvault.databinding.FragmentAssetPagerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AssetPagerFragment : Fragment() {

    private var _binding: FragmentAssetPagerBinding? = null
    private val binding get() = _binding!!

    private var initialAssetId: Long = -1

    companion object {
        private const val ARG_INITIAL_ASSET_ID = "initial_asset_id"

        fun newInstance(initialAssetId: Long): AssetPagerFragment {
            return AssetPagerFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_INITIAL_ASSET_ID, initialAssetId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialAssetId = arguments?.getLong(ARG_INITIAL_ASSET_ID) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAssetPagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAssets()
    }

    private fun loadAssets() {
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            val prefs = SecurePreferences.getInstance(requireContext())
            val currentUser = prefs.getOwnerId()

            val assets = withContext(Dispatchers.IO) {
                db.signatureDao().getByOwnerEmail(currentUser)
            }

            if (assets.isNotEmpty()) {
                val assetIds = assets.map { it.id }
                val initialIndex = assetIds.indexOf(initialAssetId).takeIf { it >= 0 } ?: 0

                val adapter = AssetPagerAdapter(this@AssetPagerFragment, assetIds)
                binding.viewPager.adapter = adapter
                binding.viewPager.setCurrentItem(initialIndex, false)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class AssetPagerAdapter(
        fragment: Fragment,
        private val assetIds: List<Long>
    ) : FragmentStateAdapter(fragment) {

        override fun getItemCount(): Int = assetIds.size

        override fun createFragment(position: Int): Fragment {
            return AssetDetailFragment.newInstance(assetIds[position])
        }
    }
}
