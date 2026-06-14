package com.assetvault.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
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
import com.assetvault.network.SupabaseManager
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
        purgeOldChats()

        // We no longer automatically hide the navbar just because we entered GeminiChatFragment.
        // Instead, we hide it when the keyboard opens, anywhere in the app.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val isKeyboardVisible = insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
            setNavbarVisible(!isKeyboardVisible)
            insets
        }
        
        supportFragmentManager.addOnBackStackChangedListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (currentFragment is GeminiChatFragment) {
                binding.fabGemini.visibility = View.GONE
            } else {
                binding.fabGemini.visibility = View.VISIBLE
            }
        }

        if (savedInstanceState == null) {
            showHomeFragment()
        }
    }

    /** Show or hide the bottom navbar and Gemini FAB (e.g. hide when inside Gemini chat). */
    fun setNavbarVisible(show: Boolean) {
        val visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        binding.bottomNavCard.visibility = visibility
        binding.fabGemini.visibility = visibility
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

        // Setup notification badge for Transfer Ownership requests
        val transferMenuItem = binding.navigationView.menu.findItem(R.id.nav_transfer_ownership)
        val actionView = transferMenuItem.actionView
        val tvBadge = actionView?.findViewById<TextView>(R.id.tvBadge)
        if (tvBadge != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val pendingRequests = SupabaseManager.countPendingTransferRequests(email)
                runOnUiThread {
                    if (pendingRequests > 0) {
                        tvBadge.text = pendingRequests.toString()
                        tvBadge.visibility = View.VISIBLE
                    } else {
                        tvBadge.visibility = View.GONE
                    }
                }
            }
        }

        // Drawer Item Clicks
        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_profile -> { navigateToProfile() }
                R.id.nav_protected_assets -> { navigateToVault() }
                R.id.nav_sightings_map -> {
                    updateNavbarSelection(R.id.navMap)
                    supportFragmentManager.commit {
                        replace(R.id.fragmentContainer, MapFragment())
                        addToBackStack(null)
                    }
                }
                R.id.nav_raise_ticket -> {
                    supportFragmentManager.commit {
                        replace(R.id.fragmentContainer, TicketFragment())
                        addToBackStack("ticket")
                    }
                    updateNavbarSelection(-1)
                }
                R.id.nav_transfer_ownership -> { navigateToTransferOwnership() }
                R.id.nav_request_ownership -> { navigateToRequestOwnership() }
                R.id.nav_gemini_history -> { navigateToGeminiChat() }
                R.id.nav_balance -> { navigateToBalance() }
                R.id.nav_language -> { showLanguageDialog() }
                R.id.nav_admin -> { navigateToAdmin() }
                R.id.nav_signout -> performSignOut()
            }
            binding.drawerLayout.close()
            true
        }
        
        // Check if user is admin
        checkAdminStatus(email)
    }
    
    private fun checkAdminStatus(email: String) {
        // Query Supabase for admin status
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isAdmin = SupabaseManager.isUserAdmin(email)
                runOnUiThread {
                    android.widget.Toast.makeText(this@MainActivity, "Admin check for $email: $isAdmin", android.widget.Toast.LENGTH_LONG).show()
                    if (isAdmin) {
                        binding.navigationView.menu.findItem(R.id.nav_admin)?.isVisible = true
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "Hindi", "Bengali", "Telugu", "Marathi")
        val locales = arrayOf("en", "hi", "bn", "te", "mr")
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Select Language")
            .setItems(languages) { _, which ->
                val locale = locales[which]
                val appLocale = androidx.core.os.LocaleListCompat.forLanguageTags(locale)
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale)
            }
            .show()
    }

    private fun navigateToAdmin() {
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, AdminPanelFragment())
            addToBackStack("admin")
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
        binding.navMap.setOnClickListener {
            updateNavbarSelection(R.id.navMap)
            supportFragmentManager.commit {
                replace(R.id.fragmentContainer, MapFragment())
                addToBackStack(null)
            }
        }
        binding.navMenu.setOnClickListener {
            updateNavbarSelection(R.id.navMenu)
            binding.drawerLayout.open()
        }
        binding.fabGemini.setOnClickListener {
            navigateToGeminiChat()
        }
    }

    private fun navigateToGeminiChat() {
        // Reuse existing session if already in back stack — never create a new one each time
        val existing = supportFragmentManager.findFragmentByTag("gemini_chat")
        if (existing != null && existing.isAdded) {
            supportFragmentManager.popBackStack(
                "gemini_chat",
                0
            )
        } else {
            supportFragmentManager.commit {
                setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                replace(R.id.fragmentContainer, GeminiChatFragment(), "gemini_chat")
                addToBackStack("gemini_chat")
            }
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

    private fun updateNavbarSelection(selectedId: Int) {
        val navItems = listOf(
            binding.navHome to R.id.navHome,
            binding.navUpload to R.id.navUpload,
            binding.navMap to R.id.navMap,
            binding.navVault to R.id.navVault,
            binding.navMenu to R.id.navMenu
        )
        
        val defaultTint = android.graphics.Color.parseColor("#757575") // Grey
        val selectedTint = androidx.core.content.ContextCompat.getColor(this, R.color.purple_700)

        for ((view, id) in navItems) {
            val icon = view.getChildAt(0) as android.widget.ImageView
            val text = view.getChildAt(1) as android.widget.TextView
            
            if (id == selectedId) {
                icon.setColorFilter(selectedTint)
                text.setTextColor(selectedTint)
                view.setBackgroundResource(R.drawable.nav_selected_bg)
                
                // Animation: lock opens up, turns around and closes
                val animatorSet = android.animation.AnimatorSet()
                val rotate = android.animation.ObjectAnimator.ofFloat(icon, "rotationY", 0f, 360f)
                val scaleXUp = android.animation.ObjectAnimator.ofFloat(icon, "scaleX", 1f, 1.2f)
                val scaleYUp = android.animation.ObjectAnimator.ofFloat(icon, "scaleY", 1f, 1.2f)
                val scaleXDown = android.animation.ObjectAnimator.ofFloat(icon, "scaleX", 1.2f, 1f)
                val scaleYDown = android.animation.ObjectAnimator.ofFloat(icon, "scaleY", 1.2f, 1f)
                
                scaleXUp.duration = 200
                scaleYUp.duration = 200
                scaleXDown.duration = 200
                scaleYDown.duration = 200
                rotate.duration = 400
                
                val upSet = android.animation.AnimatorSet()
                upSet.playTogether(scaleXUp, scaleYUp)
                val downSet = android.animation.AnimatorSet()
                downSet.playTogether(scaleXDown, scaleYDown)
                
                val scaleSeq = android.animation.AnimatorSet()
                scaleSeq.playSequentially(upSet, downSet)
                
                animatorSet.playTogether(rotate, scaleSeq)
                animatorSet.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                animatorSet.start()
            } else {
                icon.setColorFilter(defaultTint)
                text.setTextColor(defaultTint)
                val typedValue = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
                view.setBackgroundResource(typedValue.resourceId)
            }
        }
    }

    private fun showHomeFragment() {
        updateNavbarSelection(R.id.navHome)
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, HomeFragment())
        }
    }

    fun navigateToAssetPicker() {
        updateNavbarSelection(R.id.navUpload)
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, AssetPickerFragment())
            addToBackStack("picker")
        }
    }

    fun navigateToVault() {
        updateNavbarSelection(R.id.navVault)
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

    private fun navigateToProfile() {
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, ProfileFragment())
            addToBackStack("profile")
        }
    }

    fun navigateToAssetDetail(assetId: Long) {
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, AssetDetailFragment.newInstance(assetId))
            addToBackStack("detail")
        }
    }

    fun navigateToTransferOwnership() {
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, TransferOwnershipFragment())
            addToBackStack("transfer")
        }
    }

    fun navigateToRequestOwnership() {
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, RequestOwnershipFragment())
            addToBackStack("request")
        }
    }

    fun navigateToBalance() {
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, BalanceFragment())
            addToBackStack("balance")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        BlockchainManager.shutdown()
    }

    /** Deletes chat sessions (and their messages cascade) older than 30 days. Runs silently in background. */
    private fun purgeOldChats() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cutoff = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                val db = com.assetvault.data.AppDatabase.getInstance(this@MainActivity)
                db.chatDao().deleteOldSessions(cutoff)
                android.util.Log.d("MainActivity", "Old chat sessions purged (cutoff: $cutoff)")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Chat purge failed", e)
            }
        }
    }
}