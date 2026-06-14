package com.assetvault.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.assetvault.R
import com.assetvault.data.AppDatabase
import com.assetvault.data.SecurePreferences
import com.assetvault.data.SignatureEntity
import com.assetvault.databinding.FragmentTransferOwnershipBinding
import com.assetvault.databinding.ItemOwnershipRequestBinding
import com.assetvault.network.SupabaseManager
import com.assetvault.network.TransferRequestRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransferOwnershipFragment : Fragment() {

    private var _binding: FragmentTransferOwnershipBinding? = null
    private val binding get() = _binding!!
    private var userAssets: List<SignatureEntity> = emptyList()
    private var walletLookupJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTransferOwnershipBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        setupTabs()
        loadUserAssetsForSpinner()

        // Wallet-to-email lookup as user types
        binding.etNewOwnerAddress.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString()?.trim() ?: return
                walletLookupJob?.cancel()
                if (input.startsWith("0x") && input.length >= 10) {
                    binding.tvResolvedEmail.text = getString(R.string.resolving_email)
                    binding.tvResolvedEmail.visibility = View.VISIBLE
                    walletLookupJob = viewLifecycleOwner.lifecycleScope.launch {
                        delay(600)
                        val email = SupabaseManager.lookupEmailByWalletAddress(input)
                        binding.tvResolvedEmail.text = if (email != null) "→ $email" else getString(R.string.email_not_found)
                    }
                } else {
                    binding.tvResolvedEmail.visibility = View.GONE
                    walletLookupJob?.cancel()
                }
            }
        })

        binding.btnTransfer.setOnClickListener {
            val selectedIndex = binding.spinnerAssets.selectedItemPosition
            val recipientInput = binding.etNewOwnerAddress.text.toString().trim()
            val isCollaborative = binding.switchCollaborative.isChecked

            if (selectedIndex < 0 || selectedIndex >= userAssets.size) {
                Toast.makeText(requireContext(), "Please select an asset", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (recipientInput.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter the recipient's email or wallet address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedAsset = userAssets[selectedIndex]
            performTransferRequest(selectedAsset, recipientInput, isCollaborative)
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.layoutTransfer.visibility = View.VISIBLE
                        binding.layoutRequests.visibility = View.GONE
                    }
                    1 -> {
                        binding.layoutTransfer.visibility = View.GONE
                        binding.layoutRequests.visibility = View.VISIBLE
                        loadPendingRequests()
                    }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun loadUserAssetsForSpinner() {
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            val prefs = SecurePreferences.getInstance(requireContext())
            val currentUser = prefs.getOwnerId()
            userAssets = db.signatureDao().getByOwnerEmail(currentUser)
                .filter { it.status == "PROTECTED" }

            val assetNames = userAssets.map {
                "${it.uri.substringAfterLast("/")} (${it.pHash.take(12)}...)"
            }

            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                assetNames
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerAssets.adapter = adapter
        }
    }

    private fun loadPendingRequests() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val prefs = SecurePreferences.getInstance(requireContext())
                val email = prefs.getGoogleEmail() ?: return@launch
                val requests = SupabaseManager.fetchIncomingTransferRequests(email)

                val uiModels = requests.mapNotNull { req ->
                    val senderUuid = req.sender_id ?: return@mapNotNull null
                    val assetUuid = req.asset_id ?: return@mapNotNull null
                    val resolvedEmail = SupabaseManager.lookupEmailByUuid(senderUuid) ?: senderUuid
                    val resolvedPHash = SupabaseManager.lookupAssetPHashByUuid(assetUuid) ?: assetUuid
                    TransferRequestUIModel(req, resolvedEmail, resolvedPHash)
                }

                if (uiModels.isEmpty()) {
                    binding.tvEmptyRequests.text = getString(R.string.no_pending_requests)
                    binding.tvEmptyRequests.visibility = View.VISIBLE
                    binding.rvRequests.visibility = View.GONE
                } else {
                    binding.tvEmptyRequests.visibility = View.GONE
                    binding.rvRequests.visibility = View.VISIBLE
                    binding.rvRequests.layoutManager = LinearLayoutManager(requireContext())
                    binding.rvRequests.adapter = TransferRequestsAdapter(uiModels) { uiModel, approved ->
                        handleRequestAction(uiModel, approved)
                    }
                }
            } catch (e: Exception) {
                binding.tvEmptyRequests.text = "Error loading requests: ${e.message}"
                binding.tvEmptyRequests.visibility = View.VISIBLE
            }
        }
    }

    private fun handleRequestAction(uiModel: TransferRequestUIModel, approved: Boolean) {
        val request = uiModel.request
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val requestId = request.id ?: return@launch
                val assetUuid = request.asset_id ?: return@launch
                val senderEmail = uiModel.senderEmail

                if (approved) {
                    val success = SupabaseManager.approveTransferAndUpdateOwner(
                        requestId = requestId,
                        assetUuid = assetUuid,
                        newOwnerEmail = senderEmail,
                        isCollaborative = request.is_collaborative
                    )
                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(requireContext(), getString(R.string.transfer_request_approved), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(requireContext(), "Failed to approve. Try again.", Toast.LENGTH_SHORT).show()
                        }
                        loadPendingRequests() // Refresh list
                    }
                } else {
                    SupabaseManager.updateTransferRequestStatus(requestId, "rejected")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), getString(R.string.transfer_request_rejected), Toast.LENGTH_SHORT).show()
                        loadPendingRequests() // Refresh list
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Action failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun performTransferRequest(asset: SignatureEntity, recipientInput: String, isCollaborative: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.btnTransfer.isEnabled = false

            try {
                val prefs = SecurePreferences.getInstance(requireContext())
                val senderEmail = prefs.getGoogleEmail() ?: throw IllegalStateException("Not signed in")

                // Resolve wallet address to email if needed
                val recipientEmail = if (recipientInput.startsWith("0x")) {
                    SupabaseManager.lookupEmailByWalletAddress(recipientInput)
                        ?: throw Exception(getString(R.string.email_not_found))
                } else {
                    recipientInput
                }

                val success = SupabaseManager.createTransferRequest(
                    assetPHash = asset.pHash,
                    senderEmail = senderEmail,
                    recipientEmail = recipientEmail,
                    isCollaborative = isCollaborative
                )

                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(requireContext(), getString(R.string.transfer_request_sent), Toast.LENGTH_LONG).show()
                        loadUserAssetsForSpinner()
                        binding.etNewOwnerAddress.text.clear()
                        binding.tvResolvedEmail.visibility = View.GONE
                        binding.switchCollaborative.isChecked = false
                    } else {
                        Toast.makeText(requireContext(), "Failed to send request. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Transfer failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.btnTransfer.isEnabled = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        walletLookupJob?.cancel()
        _binding = null
    }
}

// ── Inner RecyclerView Adapter ──────────────────────────────────────────────

data class TransferRequestUIModel(
    val request: TransferRequestRecord,
    val senderEmail: String,
    val assetPHash: String
)

class TransferRequestsAdapter(
    private val requests: List<TransferRequestUIModel>,
    private val onAction: (TransferRequestUIModel, Boolean) -> Unit
) : RecyclerView.Adapter<TransferRequestsAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemOwnershipRequestBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOwnershipRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val uiModel = requests[position]
        val request = uiModel.request
        holder.binding.tvRequesterName.text = uiModel.senderEmail
        holder.binding.tvRequesterAddress.text = "Asset: ${uiModel.assetPHash.take(20)}..."
        val typeText = if (request.is_collaborative) "📤 Collaborative Ownership Request" else "📤 Full Transfer Request"
        holder.binding.tvAssetRequested.text = typeText

        holder.binding.btnApprove.setOnClickListener { onAction(uiModel, true) }
        holder.binding.btnReject.setOnClickListener { onAction(uiModel, false) }
    }

    override fun getItemCount() = requests.size
}
