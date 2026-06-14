package com.assetvault.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.assetvault.data.SecurePreferences
import com.assetvault.databinding.FragmentTicketBinding
import com.assetvault.network.SupabaseManager
import kotlinx.coroutines.launch

class TicketFragment : Fragment() {

    private var _binding: FragmentTicketBinding? = null
    private val binding get() = _binding!!
    private var attachedImageUri: Uri? = null

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            attachedImageUri = uri
            binding.ivAttachment.setImageURI(uri)
            binding.ivAttachment.visibility = View.VISIBLE
            binding.btnRemoveAttachment.visibility = View.VISIBLE
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTicketBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        binding.btnAttachPhoto.setOnClickListener {
            getContent.launch("image/*")
        }

        binding.btnRemoveAttachment.setOnClickListener {
            attachedImageUri = null
            binding.ivAttachment.visibility = View.GONE
            binding.btnRemoveAttachment.visibility = View.GONE
        }

        binding.btnSubmitTicket.setOnClickListener {
            val issue = binding.etIssueDescription.text.toString().trim()
            if (issue.isEmpty()) {
                Toast.makeText(requireContext(), "Please describe your concern", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            submitTicket(issue, attachedImageUri)
        }
    }

    private fun submitTicket(issue: String, imageUri: Uri?) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSubmitTicket.isEnabled = false
        
        val prefs = SecurePreferences.getInstance(requireContext())
        val email = prefs.getGoogleEmail() ?: "unknown@email.com"
        val name = prefs.getGoogleDisplayName()

        lifecycleScope.launch {
            try {
                val success = SupabaseManager.submitTicket(
                    context = requireContext(),
                    issue = issue,
                    imageUri = imageUri,
                    userEmail = email,
                    userName = name,
                    raisedByGemini = false
                )
                
                if (success) {
                    Toast.makeText(requireContext(), "Ticket submitted successfully!", Toast.LENGTH_LONG).show()
                    requireActivity().supportFragmentManager.popBackStack()
                } else {
                    Toast.makeText(requireContext(), "Failed to submit ticket. Please check Supabase keys and configuration.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnSubmitTicket.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
