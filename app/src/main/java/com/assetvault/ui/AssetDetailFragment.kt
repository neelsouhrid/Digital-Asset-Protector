package com.assetvault.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.assetvault.R
import com.assetvault.data.AppDatabase
import com.assetvault.data.SecurePreferences
import com.assetvault.databinding.FragmentAssetDetailBinding
import com.assetvault.network.BlockchainManager
import com.assetvault.network.CloudApiClient
import com.assetvault.network.SupabaseManager
import com.assetvault.util.ImageBlurUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AssetDetailFragment - Module 4 & 5
 * Shows asset details, Protect/Unprotect toggle, Transfer Ownership (with collaborative option),
 * DMCA template generator, and blur enforcement for sighting devices.
 */
class AssetDetailFragment : Fragment() {

    private var _binding: FragmentAssetDetailBinding? = null
    private val binding get() = _binding!!

    private var assetId: Long = -1
    private var walletLookupJob: Job? = null

    companion object {
        private const val ARG_ASSET_ID = "asset_id"

        fun newInstance(assetId: Long): AssetDetailFragment {
            return AssetDetailFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_ASSET_ID, assetId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assetId = arguments?.getLong(ARG_ASSET_ID) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAssetDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnProtect.setOnClickListener {
            handleProtectToggle()
        }

        binding.btnVerify.setOnClickListener {
            verifyAsset()
        }

        binding.btnTransferOwnership.setOnClickListener {
            showTransferOwnershipDialog()
        }

        binding.btnDmca.setOnClickListener {
            generateDmcaTemplate()
        }

        // Copy pHash on click
        binding.tvPHash.setOnClickListener {
            val pHash = binding.tvPHash.text.toString().removePrefix("pHash: ")
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("pHash", pHash))
            Toast.makeText(requireContext(), "pHash copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        loadAssetDetails()
    }

    private fun loadAssetDetails() {
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            val asset = db.signatureDao().getById(assetId)

            if (asset == null) {
                Toast.makeText(requireContext(), "Asset not found", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
                return@launch
            }

            val prefs = SecurePreferences.getInstance(requireContext())
            val currentUser = prefs.getOwnerId()
            val isOwner = asset.ownerEmail == currentUser || asset.status == "PROTECTED"

            // Store wallet address in Supabase profile for reverse lookup
            val walletAddress = BlockchainManager.getWalletAddress()
            val email = prefs.getGoogleEmail()
            if (walletAddress != null && email != null) {
                launch(Dispatchers.IO) {
                    SupabaseManager.ensureWalletAddressStored(email, walletAddress)
                }
            }

            // Load image preview
            withContext(Dispatchers.Default) {
                val bitmap = ImageBlurUtil.loadScaledBitmap(requireContext(), asset.uri)
                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        if (asset.status == "SIGHTING" && asset.isEnforced) {
                            val blurred = ImageBlurUtil.blurBitmap(bitmap, 25)
                            binding.ivPreview.setImageBitmap(blurred)
                            binding.layoutBlurOverlay.visibility = View.VISIBLE
                        } else {
                            binding.ivPreview.setImageBitmap(bitmap)
                            binding.layoutBlurOverlay.visibility = View.GONE
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        try {
                            binding.ivPreview.setImageURI(android.net.Uri.parse(asset.uri))
                            if (asset.status == "SIGHTING" && asset.isEnforced) {
                                binding.layoutBlurOverlay.visibility = View.VISIBLE
                            } else {
                                binding.layoutBlurOverlay.visibility = View.GONE
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AssetDetailFragment", "Fallback setImageURI failed", e)
                        }
                    }
                }
            }

            // Fill info fields
            binding.tvAssetUri.text = "File: ${asset.uri.substringAfterLast("/")}"
            binding.tvHexVector.text = "Vector: ${asset.hexVector.take(32)}..."
            binding.tvPHash.text = "pHash: ${asset.pHash}"
            binding.tvTimestamp.text = "Added: ${formatTimestamp(asset.timestamp)}"

            // Status badge
            when (asset.status) {
                "PROTECTED" -> {
                    binding.tvStatus.text = "Status: ✅ PROTECTED"
                    binding.tvStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_protected)
                    )
                }
                "SIGHTING" -> {
                    val simText = asset.similarity?.let { "%.1f%%".format(it) } ?: "?"
                    binding.tvStatus.text = "Status: ⚠️ SIGHTING ($simText match)"
                    binding.tvStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_sighting)
                    )
                }
                else -> {
                    binding.tvStatus.text = "Status: ${asset.status}"
                    binding.tvStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_pending)
                    )
                }
            }

            // Blockchain tx
            if (asset.blockchainTxId != null) {
                binding.tvBlockchainTx.text = "Tx: ${asset.blockchainTxId}"
                binding.tvBlockchainTx.visibility = View.VISIBLE
            } else {
                binding.tvBlockchainTx.visibility = View.GONE
            }

            // ── Protect, Transfer & DMCA button visibility ───────────────────────
            if (isOwner && asset.status == "PROTECTED") {
                binding.btnProtect.visibility = View.VISIBLE
                binding.btnTransferOwnership.visibility = View.VISIBLE
                binding.btnDmca.visibility = View.VISIBLE

                if (asset.isEnforced) {
                    binding.btnProtect.isEnabled = true
                    binding.btnProtect.text = "Unprotect Asset"
                    binding.tvEnforcementStatus.visibility = View.VISIBLE
                    binding.tvEnforcementStatus.text = getString(R.string.enforcement_active)
                    binding.tvEnforcementStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_protected)
                    )
                } else {
                    binding.btnProtect.isEnabled = true
                    binding.btnProtect.text = "Protect Asset"
                    binding.tvEnforcementStatus.visibility = View.VISIBLE
                    binding.tvEnforcementStatus.text = getString(R.string.enforcement_inactive)
                    binding.tvEnforcementStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.text_secondary)
                    )
                }
            } else {
                binding.btnProtect.visibility = View.GONE
                binding.btnTransferOwnership.visibility = View.GONE
                binding.btnDmca.visibility = View.GONE

                if (asset.status == "SIGHTING") {
                    checkAndDisplayEnforcement(asset.pHash)
                }
            }
        }
    }

    // ── Transfer Ownership ──────────────────────────────────────────────────

    private fun showTransferOwnershipDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            val asset = db.signatureDao().getById(assetId) ?: return@launch

            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, 0)
            }

            val etEmail = EditText(requireContext()).apply {
                hint = getString(R.string.enter_recipient_email)
                inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            }

            val tvResolvedEmail = TextView(requireContext()).apply {
                textSize = 12f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                visibility = View.GONE
            }

            val cbCollaborative = CheckBox(requireContext()).apply {
                text = getString(R.string.collaborative_ownership)
            }

            val tvCollaborativeDesc = TextView(requireContext()).apply {
                text = getString(R.string.collaborative_ownership_desc)
                textSize = 11f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                val topMargin = (4 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, topMargin, 0, 0) }
            }

            container.addView(etEmail)
            container.addView(tvResolvedEmail)
            container.addView(cbCollaborative)
            container.addView(tvCollaborativeDesc)

            // Wallet-to-email lookup on text change (debounced)
            etEmail.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val input = s?.toString()?.trim() ?: return
                    walletLookupJob?.cancel()
                    if (input.startsWith("0x") && input.length >= 10) {
                        tvResolvedEmail.text = getString(R.string.resolving_email)
                        tvResolvedEmail.visibility = View.VISIBLE
                        walletLookupJob = viewLifecycleOwner.lifecycleScope.launch {
                            delay(600) // debounce
                            val email = SupabaseManager.lookupEmailByWalletAddress(input)
                            tvResolvedEmail.text = if (email != null) "→ $email" else getString(R.string.email_not_found)
                        }
                    } else {
                        tvResolvedEmail.visibility = View.GONE
                        walletLookupJob?.cancel()
                    }
                }
            })

            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.transfer_ownership))
                .setMessage("Send an ownership request to the recipient. They will need to approve it.")
                .setView(container)
                .setPositiveButton("Send Request") { _, _ ->
                    val recipientInput = etEmail.text.toString().trim()
                    val isCollaborative = cbCollaborative.isChecked
                    if (recipientInput.isEmpty()) {
                        Toast.makeText(requireContext(), "Please enter a recipient email or wallet address", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    performTransferRequest(asset.pHash, recipientInput, isCollaborative)
                }
                .setNegativeButton("Cancel", null)
                .create()
                .show()
        }
    }

    private fun performTransferRequest(assetPHash: String, recipientInput: String, isCollaborative: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnTransferOwnership.isEnabled = false

            try {
                val prefs = SecurePreferences.getInstance(requireContext())
                val senderEmail = prefs.getGoogleEmail() ?: throw IllegalStateException("Not signed in")

                // If input looks like a wallet address, try to resolve to email first
                val recipientEmail = if (recipientInput.startsWith("0x")) {
                    SupabaseManager.lookupEmailByWalletAddress(recipientInput)
                        ?: throw Exception(getString(R.string.email_not_found))
                } else {
                    recipientInput
                }

                val success = SupabaseManager.createTransferRequest(
                    assetPHash = assetPHash,
                    senderEmail = senderEmail,
                    recipientEmail = recipientEmail,
                    isCollaborative = isCollaborative
                )

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnTransferOwnership.isEnabled = true
                    if (success) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.transfer_request_sent),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to send request. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnTransferOwnership.isEnabled = true
                    Toast.makeText(requireContext(), "Transfer failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── DMCA Template Generation ────────────────────────────────────────────

    private fun generateDmcaTemplate() {
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            val asset = db.signatureDao().getById(assetId) ?: return@launch
            val prefs = SecurePreferences.getInstance(requireContext())
            val ownerName = prefs.getGoogleDisplayName() ?: "Asset Owner"
            val ownerEmail = prefs.getGoogleEmail() ?: "owner@example.com"
            val walletAddress = BlockchainManager.getWalletAddress() ?: "N/A"
            val fileName = asset.uri.substringAfterLast("/")
            val dateStr = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault())
                .format(java.util.Date())

            val dmcaText = """
DMCA TAKEDOWN NOTICE
=====================

Date: $dateStr

To Whom It May Concern,

I, the undersigned, am the owner or authorized agent of the exclusive intellectual property rights in the work described below.

--- COPYRIGHTED WORK ---
File Name   : $fileName
pHash       : ${asset.pHash}
Blockchain Tx: ${asset.blockchainTxId ?: "N/A"}
Wallet Address: $walletAddress
Registration Date: ${formatTimestamp(asset.timestamp)}

--- OWNERSHIP PROOF ---
This asset is registered on the Polygon Amoy blockchain via the Digital Asset Protector platform.
Contract Address: 0xDAb9faE854cEa0A0E4281BBBE627dD1721ED4479
Transaction ID: ${asset.blockchainTxId ?: "See blockchain explorer for full record"}

--- INFRINGING MATERIAL ---
[DESCRIBE the URL(s) or location(s) where the infringing content was found]

--- STATEMENT ---
I have a good-faith belief that use of the copyrighted work described above is not authorized by the copyright owner, its agent, or the law.

I swear, under penalty of perjury, that the information in this notification is accurate and that I am the copyright owner or am authorized to act on behalf of the owner.

Sincerely,
$ownerName
$ownerEmail

---
Generated by Digital Asset Protector
            """.trimIndent()

            withContext(Dispatchers.Main) {
                // Show preview dialog with share option
                AlertDialog.Builder(requireContext())
                    .setTitle("DMCA Template Ready")
                    .setMessage(dmcaText.take(600) + "\n\n[...tap Share to see full template]")
                    .setPositiveButton("Share") { _, _ ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "DMCA Takedown Notice - $fileName")
                            putExtra(Intent.EXTRA_TEXT, dmcaText)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share DMCA Notice via"))
                    }
                    .setNeutralButton("Copy") { _, _ ->
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("DMCA Notice", dmcaText))
                        Toast.makeText(requireContext(), "DMCA template copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }
    }

    // ── Protect / Unprotect ─────────────────────────────────────────────────

    private fun handleProtectToggle() {
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            val asset = db.signatureDao().getById(assetId) ?: return@launch
            if (asset.isEnforced) {
                showUnprotectConfirmation(asset)
            } else {
                enforceProtection()
            }
        }
    }

    private fun showUnprotectConfirmation(asset: com.assetvault.data.SignatureEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Unprotect Asset?")
            .setMessage("This will make your asset visible to everyone who has detected it as a sighting. Other devices will no longer blur this image.\n\nAre you sure?")
            .setPositiveButton("Yes, Unprotect") { _, _ ->
                performUnprotect(asset)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performUnprotect(asset: com.assetvault.data.SignatureEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnProtect.isEnabled = false

            try {
                SupabaseManager.unprotectAsset(asset.pHash)
                val db = AppDatabase.getInstance(requireContext())
                asset.isEnforced = false
                db.signatureDao().update(asset)

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnProtect.isEnabled = true
                    binding.btnProtect.text = "Protect Asset"
                    binding.tvEnforcementStatus.text = getString(R.string.enforcement_inactive)
                    binding.tvEnforcementStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.text_secondary)
                    )
                    binding.layoutBlurOverlay.visibility = View.GONE
                    val bitmap = withContext(Dispatchers.Default) {
                        ImageBlurUtil.loadScaledBitmap(requireContext(), asset.uri)
                    }
                    if (bitmap != null) binding.ivPreview.setImageBitmap(bitmap)
                    Toast.makeText(requireContext(), "Asset unprotected. It is now visible to all.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnProtect.isEnabled = true
                    Toast.makeText(requireContext(), "Failed to unprotect: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun enforceProtection() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnProtect.isEnabled = false

            try {
                val db = AppDatabase.getInstance(requireContext())
                val asset = db.signatureDao().getById(assetId)
                    ?: throw IllegalStateException("Asset not found")
                val prefs = SecurePreferences.getInstance(requireContext())
                val ownerId = prefs.getOwnerId()
                val result = CloudApiClient.protectAsset(asset.pHash, ownerId)

                if (result.success) {
                    asset.isEnforced = true
                    db.signatureDao().update(asset)
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        binding.btnProtect.isEnabled = true
                        binding.btnProtect.text = "Unprotect Asset"
                        binding.tvEnforcementStatus.visibility = View.VISIBLE
                        binding.tvEnforcementStatus.text = getString(R.string.enforcement_active)
                        binding.tvEnforcementStatus.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.status_protected)
                        )
                        Toast.makeText(requireContext(), "Protection enforced! Sighting devices will blur this image.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    throw Exception("Server returned success=false")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnProtect.isEnabled = true
                    Toast.makeText(requireContext(), "Failed to enforce protection: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── Sighting Enforcement Check ──────────────────────────────────────────

    private fun checkAndDisplayEnforcement(pHash: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val enforcement = CloudApiClient.checkEnforcement(pHash)
                if (enforcement.isEnforced) {
                    val db = AppDatabase.getInstance(requireContext())
                    val asset = db.signatureDao().getById(assetId)
                    if (asset != null) {
                        asset.isEnforced = true
                        db.signatureDao().update(asset)
                    }
                    withContext(Dispatchers.Main) {
                        val bitmap = withContext(Dispatchers.Default) {
                            ImageBlurUtil.loadScaledBitmap(requireContext(), asset?.uri ?: "")
                        }
                        if (bitmap != null) {
                            val blurred = ImageBlurUtil.blurBitmap(bitmap, 25)
                            binding.ivPreview.setImageBitmap(blurred)
                        }
                        binding.layoutBlurOverlay.visibility = View.VISIBLE
                        binding.tvEnforcementStatus.visibility = View.VISIBLE
                        binding.tvEnforcementStatus.text = getString(R.string.sighting_enforced)
                        binding.tvEnforcementStatus.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.status_sighting)
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.tvEnforcementStatus.visibility = View.VISIBLE
                        binding.tvEnforcementStatus.text = getString(R.string.sighting_not_enforced)
                        binding.tvEnforcementStatus.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.text_secondary)
                        )
                    }
                }
            } catch (e: Exception) {
                // Silently fail — enforcement check is best-effort
            }
        }
    }

    // ── Verify ─────────────────────────────────────────────────────────────

    private fun verifyAsset() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.tvStatus.text = "Verifying..."
            try {
                val db = AppDatabase.getInstance(requireContext())
                val asset = db.signatureDao().getById(assetId)
                    ?: throw IllegalStateException("Asset not found")
                val exists = BlockchainManager.checkAsset(asset.pHash)
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = if (exists) "Verified ✅ — Asset exists on blockchain" else "Not found on blockchain"
                Toast.makeText(requireContext(), "Verification complete!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = "Verification failed: ${e.message}"
                Toast.makeText(requireContext(), "Verification failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatTimestamp(ts: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(ts))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        walletLookupJob?.cancel()
        _binding = null
    }
}