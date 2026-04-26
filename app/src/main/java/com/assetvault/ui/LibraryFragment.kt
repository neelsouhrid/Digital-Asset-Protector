package com.assetvault.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.assetvault.ai.PHashGenerator
import com.assetvault.data.AppDatabase
import com.assetvault.data.SecurePreferences
import com.assetvault.data.SignatureEntity
import com.assetvault.databinding.FragmentLibraryBinding
import com.assetvault.network.BlockchainManager
import com.assetvault.network.CloudApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * LibraryFragment - Task 7, Option B
 * Shows all device photos in a grid with multi-select.
 * Processes each selected photo through the Task 4 registration flow.
 */
class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private val TAG = "LibraryFragment"

    private lateinit var adapter: PhotoGridAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadPhotos()
        } else {
            Toast.makeText(requireContext(), "Permission needed to access photos", Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PhotoGridAdapter { count ->
            binding.tvSelectedCount.text = if (count == 0) "No photos selected"
                else "$count photo${if (count > 1) "s" else ""} selected"
            binding.btnProtectSelected.isEnabled = count > 0
        }

        binding.recyclerViewPhotos.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerViewPhotos.adapter = adapter

        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.btnProtectSelected.setOnClickListener { protectSelected() }

        requestPermissionAndLoad()
    }

    private fun requestPermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(requireContext(), permission)
            == PackageManager.PERMISSION_GRANTED) {
            loadPhotos()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun loadPhotos() {
        viewLifecycleOwner.lifecycleScope.launch {
            val uris = withContext(Dispatchers.IO) {
                val list = mutableListOf<Uri>()
                val projection = arrayOf(MediaStore.Images.Media._ID)
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

                requireContext().contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection, null, null, sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val uri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()
                        )
                        list.add(uri)
                    }
                }
                list
            }
            adapter.submitList(uris)
        }
    }

    private fun protectSelected() {
        val selectedUris = adapter.getSelectedUris()
        if (selectedUris.isEmpty()) return

        val total = selectedUris.size
        var protectedCount = 0
        var sightingCount = 0

        // Show progress
        binding.recyclerViewPhotos.visibility = View.GONE
        binding.layoutBottom.visibility = View.GONE
        binding.tvProgress.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.max = total
        binding.progressBar.progress = 0

        viewLifecycleOwner.lifecycleScope.launch {
            val prefs = SecurePreferences.getInstance(requireContext())
            val db = AppDatabase.getInstance(requireContext())
            val ownerId = prefs.getOwnerId()

            for ((index, uri) in selectedUris.withIndex()) {
                withContext(Dispatchers.Main) {
                    binding.tvProgress.text = "Protecting ${index + 1} of $total photos..."
                    binding.progressBar.progress = index + 1
                }

                try {
                    // Check if already in DB
                    val existing = db.signatureDao().getByUri(uri.toString())
                    if (existing != null) {
                        sightingCount++
                        continue
                    }

                    // Read bytes
                    val bytes = withContext(Dispatchers.IO) {
                        requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } ?: continue

                    // Generate pHash
                    val pHash = withContext(Dispatchers.Default) {
                        PHashGenerator.generatePHash(bytes)
                    }

                    // Search cloud
                    try {
                        val searchResult = CloudApiClient.searchAsset(
                            phash = pHash,
                            deviceHash = android.provider.Settings.Secure.getString(
                                requireContext().contentResolver,
                                android.provider.Settings.Secure.ANDROID_ID
                            ) ?: "unknown",
                            locationName = "Unknown",
                            lat = 0.0, lng = 0.0
                        )

                        if (searchResult.matchFound) {
                            // SIGHTING
                            val entity = SignatureEntity(
                                uri = uri.toString(),
                                hexVector = pHash,
                                pHash = pHash,
                                timestamp = System.currentTimeMillis(),
                                blockchainTxId = null,
                                status = "SIGHTING",
                                similarity = searchResult.similarity.toFloat(),
                                ownerEmail = ownerId
                            )
                            db.signatureDao().insert(entity)
                            sightingCount++
                        } else {
                            // PROTECT
                            val txHash = BlockchainManager.registerAsset(pHash)
                            CloudApiClient.registerAsset(pHash, ownerId, txHash)

                            val entity = SignatureEntity(
                                uri = uri.toString(),
                                hexVector = pHash,
                                pHash = pHash,
                                timestamp = System.currentTimeMillis(),
                                blockchainTxId = txHash,
                                status = "PROTECTED",
                                ownerEmail = ownerId
                            )
                            db.signatureDao().insert(entity)
                            protectedCount++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Cloud/blockchain error for $uri", e)
                        // Save as pending on network error
                        val entity = SignatureEntity(
                            uri = uri.toString(),
                            hexVector = pHash,
                            pHash = pHash,
                            timestamp = System.currentTimeMillis(),
                            blockchainTxId = null,
                            status = "PENDING",
                            ownerEmail = ownerId
                        )
                        db.signatureDao().insert(entity)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing $uri", e)
                }
            }

            withContext(Dispatchers.Main) {
                binding.tvProgress.text =
                    "Done! $protectedCount protected, $sightingCount already existed"
                binding.progressBar.visibility = View.GONE

                Toast.makeText(
                    requireContext(),
                    "$protectedCount photos protected, $sightingCount already existed",
                    Toast.LENGTH_LONG
                ).show()

                binding.tvProgress.postDelayed({
                    if (isAdded) parentFragmentManager.popBackStack()
                }, 3000)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
