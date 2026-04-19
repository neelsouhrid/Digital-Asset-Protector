package com.assetvault.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.assetvault.R
import com.assetvault.databinding.ActivityMainBinding

/**
 * Main entry point for Asset Vault app.
 * Handles permission requests and navigation between fragments.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notifications disabled. Some features may not work.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNecessaryPermissions()

        if (savedInstanceState == null) {
            showVaultFragment()
        }
    }

    private fun requestNecessaryPermissions() {
        // POST_NOTIFICATIONS for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showVaultFragment() {
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, VaultFragment())
        }
    }

    fun navigateToAssetPicker() {
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, AssetPickerFragment())
            addToBackStack("picker")
        }
    }

    fun navigateToAssetDetail(assetId: Long) {
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, AssetDetailFragment.newInstance(assetId))
            addToBackStack("detail")
        }
    }
}