package com.assetvault.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.assetvault.R
import com.assetvault.databinding.FragmentResultBinding

/**
 * ResultFragment - Task 5
 * Shows result card after the protection flow completes.
 */
class ResultFragment : Fragment() {

    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_STATUS = "status"
        private const val ARG_TX_HASH = "tx_hash"
        private const val ARG_SIMILARITY = "similarity"
        private const val ARG_URI = "uri"

        fun newInstance(
            status: String,
            txHash: String? = null,
            similarity: Float? = null,
            uri: String? = null
        ): ResultFragment {
            return ResultFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_STATUS, status)
                    txHash?.let { putString(ARG_TX_HASH, it) }
                    similarity?.let { putFloat(ARG_SIMILARITY, it) }
                    uri?.let { putString(ARG_URI, it) }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val status = arguments?.getString(ARG_STATUS) ?: "UNKNOWN"
        val txHash = arguments?.getString(ARG_TX_HASH)
        val similarity = if (arguments?.containsKey(ARG_SIMILARITY) == true)
            arguments?.getFloat(ARG_SIMILARITY) else null
        val uriString = arguments?.getString(ARG_URI)

        // Load thumbnail
        uriString?.let {
            try {
                binding.ivThumbnail.setImageURI(Uri.parse(it))
            } catch (e: Exception) {
                // Keep placeholder
            }
        }

        when (status) {
            "PROTECTED" -> {
                binding.tvStatusTitle.text = "✅ Photo Protected!"
                binding.tvStatusTitle.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.status_protected)
                )
                binding.tvStatusDetail.text = "Registered on blockchain successfully."

                txHash?.let {
                    binding.tvBlockchainTx.visibility = View.VISIBLE
                    val truncated = if (it.length > 20) "${it.take(10)}...${it.takeLast(8)}" else it
                    binding.tvBlockchainTx.text = "Tx: $truncated"
                }
            }

            "SIGHTING" -> {
                binding.tvStatusTitle.text = "⚠️ Match Found"
                binding.tvStatusTitle.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.status_sighting)
                )

                val simPercent = similarity?.let { "%.1f".format(it) } ?: "?"
                binding.tvStatusDetail.text =
                    "This photo already exists in our system ($simPercent% match).\nIt has been flagged as a sighting."

                binding.tvSimilarity.visibility = View.VISIBLE
                binding.tvSimilarity.text = "Flagged — $simPercent% match"
                binding.tvSimilarity.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.status_sighting)
                )
            }

            else -> {
                binding.tvStatusTitle.text = "Processing Complete"
                binding.tvStatusDetail.text = "Status: $status"
            }
        }

        binding.btnDone.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
