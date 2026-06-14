package com.assetvault.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.assetvault.databinding.FragmentAdminPanelBinding
import com.assetvault.databinding.ItemAdminTicketBinding
import com.assetvault.network.SupabaseManager
import com.assetvault.network.SupportTicket
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class AdminPanelFragment : Fragment() {

    private var _binding: FragmentAdminPanelBinding? = null
    private val binding get() = _binding!!

    private val ticketAdapter = TicketAdapter(mutableListOf()) { ticket ->
        resolveTicket(ticket)
    }
    private val geminiTicketAdapter = TicketAdapter(mutableListOf()) { ticket ->
        resolveTicket(ticket)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminPanelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupTabs()
        setupRecyclerViews()
        setupOverrideTransfer()

        // Load initial data
        loadTickets()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showTab(tickets = true)
                    1 -> showTab(geminiTickets = true)
                    2 -> showTab(override = true)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showTab(
        tickets: Boolean = false,
        geminiTickets: Boolean = false,
        override: Boolean = false
    ) {
        binding.layoutTickets.visibility = if (tickets) View.VISIBLE else View.GONE
        binding.layoutGeminiTickets.visibility = if (geminiTickets) View.VISIBLE else View.GONE
        binding.layoutOverride.visibility = if (override) View.VISIBLE else View.GONE

        if (tickets) loadTickets()
        if (geminiTickets) loadGeminiTickets()
    }

    private fun setupRecyclerViews() {
        binding.rvTickets.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ticketAdapter
        }
        binding.rvGeminiTickets.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = geminiTicketAdapter
        }
    }

    private fun loadTickets() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val tickets = SupabaseManager.fetchTickets(geminiOnly = false)
                ticketAdapter.updateList(tickets)
                binding.tvEmptyTickets.visibility =
                    if (tickets.isEmpty()) View.VISIBLE else View.GONE
                binding.rvTickets.visibility =
                    if (tickets.isNotEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Failed to load tickets: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadGeminiTickets() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val tickets = SupabaseManager.fetchTickets(geminiOnly = true)
                geminiTicketAdapter.updateList(tickets)
                binding.tvEmptyGeminiTickets.visibility =
                    if (tickets.isEmpty()) View.VISIBLE else View.GONE
                binding.rvGeminiTickets.visibility =
                    if (tickets.isNotEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Failed to load Gemini tickets: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun resolveTicket(ticket: SupportTicket) {
        val ticketId = ticket.id ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                SupabaseManager.resolveTicket(ticketId)
                Toast.makeText(requireContext(), "Ticket resolved", Toast.LENGTH_SHORT).show()
                when (binding.tabLayout.selectedTabPosition) {
                    0 -> loadTickets()
                    1 -> loadGeminiTickets()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupOverrideTransfer() {
        binding.btnOverrideTransfer.setOnClickListener {
            val assetHash = binding.etOverrideAssetHash.text?.toString()?.trim().orEmpty()
            val newOwnerEmail = binding.etOverrideNewOwner.text?.toString()?.trim().orEmpty()

            if (assetHash.isEmpty() || newOwnerEmail.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Please fill in both fields",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Confirm Admin Override")
                .setMessage(
                    "Are you sure you want to force-transfer asset\n\n" +
                            "pHash: $assetHash\n\n" +
                            "to $newOwnerEmail?\n\n" +
                            "This action cannot be undone. The asset will become visible to all (is_enforced = false)."
                )
                .setPositiveButton("Transfer") { _, _ ->
                    performOverrideTransfer(assetHash, newOwnerEmail)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun performOverrideTransfer(assetHash: String, newOwnerEmail: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                SupabaseManager.adminOverrideTransfer(assetHash, newOwnerEmail)
                Toast.makeText(
                    requireContext(),
                    "Asset transferred to $newOwnerEmail (visible to all)",
                    Toast.LENGTH_SHORT
                ).show()
                binding.etOverrideAssetHash.text?.clear()
                binding.etOverrideNewOwner.text?.clear()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Transfer failed: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Inner Adapter
    // ══════════════════════════════════════════════════════════════

    class TicketAdapter(
        private val items: MutableList<SupportTicket>,
        private val onResolve: (SupportTicket) -> Unit
    ) : RecyclerView.Adapter<TicketAdapter.VH>() {

        inner class VH(val binding: ItemAdminTicketBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemAdminTicketBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val ticket = items[position]
            with(holder.binding) {
                tvTicketEmail.text = ticket.user_email
                tvTicketDesc.text = ticket.description
                tvTicketStatus.text = ticket.status
                tvTicketSource.text = if (ticket.raised_by_gemini) "Gemini" else "User"

                val statusColor = if (ticket.status == "resolved") {
                    android.graphics.Color.parseColor("#4CAF50")
                } else {
                    android.graphics.Color.parseColor("#FF9800")
                }
                tvTicketStatus.setTextColor(statusColor)

                btnResolve.visibility =
                    if (ticket.status == "resolved") View.GONE else View.VISIBLE

                btnResolve.setOnClickListener { onResolve(ticket) }
            }
        }

        override fun getItemCount(): Int = items.size

        fun updateList(newItems: List<SupportTicket>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }
    }
}
