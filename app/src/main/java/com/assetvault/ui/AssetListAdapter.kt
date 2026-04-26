package com.assetvault.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.assetvault.data.SignatureEntity
import com.assetvault.databinding.ItemAssetBinding
import com.assetvault.util.ImageBlurUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RecyclerView adapter for displaying protected assets in VaultFragment.
 * Blurs thumbnails for enforced sightings.
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

            // Status badge
            when (asset.status) {
                "PROTECTED" -> {
                    binding.tvStatus.text = "PROTECTED"
                    binding.tvStatus.setTextColor(0xFF2E7D32.toInt()) // green
                }
                "SIGHTING" -> {
                    val label = if (asset.isEnforced) "🔒 ENFORCED" else "SIGHTING"
                    binding.tvStatus.text = label
                    binding.tvStatus.setTextColor(0xFFE65100.toInt()) // orange
                }
                "PENDING" -> {
                    binding.tvStatus.text = "PENDING"
                    binding.tvStatus.setTextColor(0xFFF9A825.toInt()) // yellow
                }
                else -> {
                    binding.tvStatus.text = asset.status.uppercase()
                    binding.tvStatus.setTextColor(android.graphics.Color.GRAY)
                }
            }

            // Thumbnail: blur if this is an enforced sighting
            if (asset.status == "SIGHTING" && asset.isEnforced) {
                // Load blurred thumbnail on background thread
                CoroutineScope(Dispatchers.Default).launch {
                    val bitmap = ImageBlurUtil.loadScaledBitmap(
                        binding.root.context, asset.uri, 100
                    )
                    if (bitmap != null) {
                        val blurred = ImageBlurUtil.blurBitmap(bitmap, 15)
                        withContext(Dispatchers.Main) {
                            binding.ivThumbnail.setImageBitmap(blurred)
                        }
                    }
                }
            } else {
                // Normal thumbnail
                try {
                    binding.ivThumbnail.setImageURI(android.net.Uri.parse(asset.uri))
                } catch (e: Exception) {
                    // Keep placeholder on error
                }
            }

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