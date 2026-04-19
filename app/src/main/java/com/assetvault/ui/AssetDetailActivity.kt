package com.assetvault.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.assetvault.databinding.ActivityAssetDetailBinding

/**
 * AssetDetailActivity - Hosts AssetDetailFragment
 * Shows the "Distribution Map" for a specific asset.
 */
class AssetDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAssetDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAssetDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val assetId = intent.getLongExtra("asset_id", -1)

        if (savedInstanceState == null && assetId != -1L) {
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainer.id, AssetDetailFragment.newInstance(assetId))
                .commit()
        }
    }
}