package com.assetvault.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
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
 * Handles images, videos, and cloud-synced collaborative assets (which have no local URI).
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

            // Reset to placeholder before async load to avoid stale images on recycled views
            binding.ivThumbnail.setImageResource(com.assetvault.R.drawable.ic_image_placeholder)

            // Determine if the URI is a valid local resource we can load
            val isPlaceholderUri = asset.uri.isBlank() ||
                    asset.uri == "content://collaborative_asset" ||
                    asset.uri.startsWith("content://collaborative")

            if (isPlaceholderUri) {
                // Cloud-synced asset with no local file — show a generic icon
                binding.ivThumbnail.setImageResource(com.assetvault.R.drawable.ic_image_placeholder)
                return
            }

            // Load thumbnail on a background thread
            CoroutineScope(Dispatchers.Default).launch {
                val thumbnail: Bitmap? = tryLoadThumbnail(asset)
                withContext(Dispatchers.Main) {
                    if (thumbnail != null) {
                        val final = if (asset.status == "SIGHTING" && asset.isEnforced) {
                            ImageBlurUtil.blurBitmap(thumbnail, 15)
                        } else {
                            thumbnail
                        }
                        binding.ivThumbnail.setImageBitmap(final)
                    }
                    // If null, placeholder from the reset above stays shown
                }
            }

            binding.root.setOnClickListener {
                onItemClick(asset)
            }
        }

        /**
         * Tries to load a thumbnail bitmap for the asset.
         * - For images: uses ImageBlurUtil.loadScaledBitmap
         * - For videos: uses MediaMetadataRetriever to grab a frame at 1s
         * Returns null on any failure so the placeholder remains.
         */
        private fun tryLoadThumbnail(asset: SignatureEntity): Bitmap? {
            val context = binding.root.context
            val uri = Uri.parse(asset.uri)

            // Detect video by MIME type or extension
            val mimeType = try {
                context.contentResolver.getType(uri)
            } catch (e: Exception) { null }

            val isVideo = mimeType?.startsWith("video/") == true ||
                    asset.uri.endsWith(".mp4", true) ||
                    asset.uri.endsWith(".mov", true) ||
                    asset.uri.endsWith(".mkv", true)

            return if (isVideo) {
                extractVideoThumbnail(asset.uri)
            } else {
                try {
                    ImageBlurUtil.loadScaledBitmap(context, asset.uri, 100)
                } catch (e: Exception) {
                    null
                }
            }
        }

        /** Extracts the first frame of a video at ~1 second as a thumbnail */
        private fun extractVideoThumbnail(uriString: String): Bitmap? {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(binding.root.context, Uri.parse(uriString))
                retriever.getFrameAtTime(1_000_000L) // 1 second in microseconds
            } catch (e: Exception) {
                null
            } finally {
                try { retriever.release() } catch (_: Exception) {}
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