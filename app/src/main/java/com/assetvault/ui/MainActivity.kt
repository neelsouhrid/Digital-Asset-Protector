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
        setupHeaderAndDrawer()
        setupBottomNavigation()

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

    private fun initializeBlockchain() {
        val prefs = SecurePreferences.getInstance(this)

        val email = prefs.getGoogleEmail()
        if (email == null) {
            Toast.makeText(this, "Not signed in!", Toast.LENGTH_SHORT).show()
            return
        }

        // 1 Google ID = 1 Wallet FOREVER
        val privateKey = BlockchainManager.generateDeterministicWallet(email)
        prefs.setPrivateKey(privateKey)

        try {
            BlockchainManager.initialize(privateKey)

            val walletAddress = BlockchainManager.getWalletAddress()
            if (walletAddress != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val balance = BlockchainManager.getBalance()
                        // If balance < 0.03 POL (30000000000000000 wei), request refill
                        val minBalance = java.math.BigInteger("30000000000000000")
                        if (balance < minBalance) {
                            android.util.Log.d("MainActivity", "Balance low ($balance wei). Requesting auto-faucet for $walletAddress")
                            CloudApiClient.fundWallet(walletAddress, email)
                        } else {
                            android.util.Log.d("MainActivity", "Balance sufficient ($balance wei). Skipping auto-faucet.")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Auto-faucet failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Blockchain init failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupHeaderAndDrawer() {
        val prefs = SecurePreferences.getInstance(this)
        val name = prefs.getGoogleDisplayName() ?: "Unknown User"
        val email = prefs.getGoogleEmail() ?: "No Email"
        val photoUrl = prefs.getGooglePhotoUrl()

        // Main Header
        binding.tvUsername.text = name
        if (photoUrl != null) {
            binding.ivProfile.load(photoUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_image_placeholder)
            }
        }

        binding.ivSignOut.setOnClickListener {
            performSignOut()
        }

        // Nav Header
        val navHeader = binding.navigationView.getHeaderView(0)
        val navHeaderName = navHeader.findViewById<TextView>(R.id.navHeaderName)
        val navHeaderEmail = navHeader.findViewById<TextView>(R.id.navHeaderEmail)
        val navHeaderProfile = navHeader.findViewById<ImageView>(R.id.navHeaderProfile)

        navHeaderName.text = name
        navHeaderEmail.text = email
        if (photoUrl != null) {
            navHeaderProfile.load(photoUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_image_placeholder)
            }
        }

        // Drawer Item Clicks
        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_profile -> { /* TODO */ }
                R.id.nav_protected_assets -> { navigateToVault() }
                R.id.nav_sightings_map -> { /* TODO: OpenStreetMap fragment */ }
                R.id.nav_transfer_ownership -> { /* TODO */ }
                R.id.nav_request_ownership -> { /* TODO */ }
                R.id.nav_balance -> { 
                    val balText = "You can upload up to 20 photos per day.\nYour balance renews up to 0.2 POL only when you extinguish all balance."
                    Toast.makeText(this, balText, Toast.LENGTH_LONG).show()
                }
                R.id.nav_signout -> performSignOut()
            }
            binding.drawerLayout.close()
            true
        }
    }

    private fun setupBottomNavigation() {
        binding.navHome.setOnClickListener {
            showHomeFragment()
        }
        binding.navUpload.setOnClickListener {
            navigateToAssetPicker()
        }
        binding.navVault.setOnClickListener {
            navigateToVault()
        }
        binding.navMenu.setOnClickListener {
            binding.drawerLayout.open()
        }
    }

    private fun performSignOut() {
        // Clear secure prefs
        val prefs = SecurePreferences.getInstance(this)
        prefs.clearGoogleAccount()
        prefs.clearKeys() // Force new wallet for next login
        
        // DO NOT clear local DB to simulate a fresh device, to allow multi-user persistence
        // CoroutineScope(Dispatchers.IO).launch {
        //     com.assetvault.data.AppDatabase.getInstance(this@MainActivity).clearAllTables()
        // }
        
        // Sign out from Google to allow selecting a different account next time
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
        ).build()
        val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso)
        googleSignInClient.signOut().addOnCompleteListener {
            // Go to SignInActivity
            val intent = android.content.Intent(this, SignInActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
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