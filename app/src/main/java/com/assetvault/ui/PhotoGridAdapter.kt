package com.assetvault.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.assetvault.databinding.ItemLibraryPhotoBinding

/**
 * Grid adapter for library photo multi-selection.
 */
class PhotoGridAdapter(
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<PhotoGridAdapter.PhotoViewHolder>() {

    private val photos = mutableListOf<Uri>()
    private val selectedPositions = mutableSetOf<Int>()

    fun submitList(uris: List<Uri>) {
        photos.clear()
        photos.addAll(uris)
        selectedPositions.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun getSelectedUris(): List<Uri> {
        return selectedPositions.map { photos[it] }
    }

    fun getSelectedCount(): Int = selectedPositions.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemLibraryPhotoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photos[position], position)
    }

    override fun getItemCount(): Int = photos.size

    inner class PhotoViewHolder(
        private val binding: ItemLibraryPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(uri: Uri, position: Int) {
            binding.ivPhoto.setImageURI(uri)
            binding.cbSelect.isChecked = selectedPositions.contains(position)

            binding.root.setOnClickListener {
                toggleSelection(position)
            }
            binding.cbSelect.setOnClickListener {
                toggleSelection(position)
            }
        }

        private fun toggleSelection(position: Int) {
            if (selectedPositions.contains(position)) {
                selectedPositions.remove(position)
            } else {
                selectedPositions.add(position)
            }
            binding.cbSelect.isChecked = selectedPositions.contains(position)
            onSelectionChanged(selectedPositions.size)
        }
    }
}
