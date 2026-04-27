package com.assetvault.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.assetvault.databinding.FragmentBalanceBinding
import com.assetvault.network.BlockchainManager
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

class BalanceFragment : Fragment() {

    private var _binding: FragmentBalanceBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBalanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        loadBalance()
    }
    
    private fun loadBalance() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val balanceWei = BlockchainManager.getBalance()
                val weiDecimal = BigDecimal(balanceWei)
                val polDecimal = weiDecimal.divide(BigDecimal("1000000000000000000"))
                val formattedPol = polDecimal.setScale(4, RoundingMode.HALF_UP).toPlainString()
                
                binding.tvBalance.text = "$formattedPol POL"
                
                // Assuming an upload costs ~0.005 POL on Amoy testnet
                val costPerUpload = BigDecimal("0.005")
                val remainingUploads = if (polDecimal > BigDecimal.ZERO) {
                    polDecimal.divide(costPerUpload, RoundingMode.DOWN).toInt()
                } else {
                    0
                }
                
                binding.tvRemainingUploads.text = "You can upload ~${remainingUploads.coerceAtMost(20)} more pictures today."
            } catch (e: Exception) {
                binding.tvBalance.text = "Error loading balance"
                binding.tvRemainingUploads.text = ""
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
