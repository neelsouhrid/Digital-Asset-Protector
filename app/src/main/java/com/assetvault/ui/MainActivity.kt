package com.assetvault.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.commit
import coil.load
import coil.transform.CircleCropTransformation
import com.assetvault.R
import com.assetvault.data.SecurePreferences
import com.assetvault.databinding.ActivityMainBinding
import com.assetvault.network.BlockchainManager
import com.assetvault.network.CloudApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Main entry point for Asset Vault app.
 * Handles permission requests, navigation, and user info display.
 * Now starts with HomeFragment instead of VaultFragment.
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
        initializeBlockchain()
        setupToolbar()

        if (savedInstanceState == null) {
            showHomeFragment()
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

    /**
     * Initialize blockchain wallet — generate on first run, load on subsequent runs.
     */
    private fun initializeBlockchain() {
        val prefs = SecurePreferences.getInstance(this)

        var privateKey = prefs.getPrivateKey()
        val isFirstLaunch = privateKey == null

        if (privateKey == null) {
            // First launch: generate a new wallet
            privateKey = BlockchainManager.generateWallet()
            prefs.setPrivateKey(privateKey)
        }

        try {
            BlockchainManager.initialize(privateKey)

            // If this is a brand new wallet, silently request some gas from our backend Faucet!
            if (isFirstLaunch) {
                val walletAddress = BlockchainManager.getWalletAddress()
                val email = prefs.getGoogleEmail() ?: "unknown@email.com"
                if (walletAddress != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            android.util.Log.d("MainActivity", "Requesting auto-faucet for $walletAddress")
                            CloudApiClient.fundWallet(walletAddress, email)
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Auto-faucet failed", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Blockchain init failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Show signed-in user's name and profile photo in the toolbar.
     */
    private fun setupToolbar() {
        val prefs = SecurePreferences.getInstance(this)

        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)

        val name = prefs.getGoogleDisplayName()
        val photoUrl = prefs.getGooglePhotoUrl()

        if (name != null) {
            toolbar.subtitle = name
        }

        // Add profile photo to toolbar if available
        if (photoUrl != null) {
            val ivProfile = ImageView(this).apply {
                layoutParams = androidx.appcompat.widget.Toolbar.LayoutParams(
                    dpToPx(32), dpToPx(32)
                ).apply {
                    marginEnd = dpToPx(8)
                    gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                }
                load(photoUrl) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                    placeholder(R.drawable.ic_image_placeholder)
                }
            }
            toolbar.addView(ivProfile)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    // ── Navigation ──────────────────────────────────────────────

    private fun showHomeFragment() {
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, HomeFragment())
        }
    }

    fun navigateToAssetPicker() {
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, AssetPickerFragment())
            addToBackStack("picker")
        }
    }

    fun navigateToVault() {
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, VaultFragment())
            addToBackStack("vault")
        }
    }

    fun navigateToLibrary() {
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, LibraryFragment())
            addToBackStack("library")
        }
    }

    fun navigateToAssetDetail(assetId: Long) {
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, AssetDetailFragment.newInstance(assetId))
            addToBackStack("detail")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        BlockchainManager.shutdown()
    }
}