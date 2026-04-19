package com.assetvault.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.assetvault.data.SignatureEntity
import com.assetvault.databinding.ItemAssetBinding

/**
 * RecyclerView adapter for displaying protected assets in VaultFragment.
 */
class AssetListAdapter(
    private val onItemClick: (SignatureEntity) -> Unit
) : ListAdapter<SignatureEntity, AssetListAdapter.AssetViewHolder>(AssetDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        val binding = ItemAssetBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AssetViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AssetViewHolder(
        private val binding: ItemAssetBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(asset: SignatureEntity) {
            binding.tvAssetUri.text = asset.uri.substringAfterLast("/")
            binding.tvTimestamp.text = formatTimestamp(asset.timestamp)
            binding.tvStatus.text = asset.status.uppercase()

            // Set status badge color based on status
            val statusColor = when (asset.status) {
                "protected" -> android.graphics.Color.GREEN
                "pending" -> android.graphics.Color.YELLOW
                "failed" -> android.graphics.Color.RED
                else -> android.graphics.Color.GRAY
            }
            binding.tvStatus.setTextColor(statusColor)

            binding.root.setOnClickListener {
                onItemClick(asset)
            }
        }

        private fun formatTimestamp(ts: Long): String {
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(ts))
        }
    }

    class AssetDiffCallback : DiffUtil.ItemCallback<SignatureEntity>() {
        override fun areItemsTheSame(oldItem: SignatureEntity, newItem: SignatureEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SignatureEntity, newItem: SignatureEntity): Boolean {
            return oldItem == newItem
        }
    }
}