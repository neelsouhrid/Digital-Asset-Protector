package com.assetvault.ui

import android.Manifest
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.CellInfoLte
import android.telephony.CellInfoGsm
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.assetvault.databinding.FragmentMapBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

class MapFragment : Fragment() {
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    // Runtime permission launcher - requests both READ_PHONE_STATE + ACCESS_FINE_LOCATION
    // Android requires ACCESS_FINE_LOCATION to read cell data, AND requires global location to be ON.
    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val phoneGranted = results[Manifest.permission.READ_PHONE_STATE] == true
        val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (phoneGranted && locationGranted) {
            binding.tvLocationStatus.text = "Locating..."
            binding.tvCellInfo.text = "📡 Scanning cell towers..."
            startCellTriangulation()
        } else {
            binding.tvLocationStatus.text = "Permission denied"
            binding.tvCellInfo.text = "📡 Permissions denied.\n\nThis feature reads cell tower IDs — NOT your GPS. Android requires 'Phone' + 'Location' permissions to access raw tower data.\n\nTo enable: Settings → Apps → Asset Vault → Permissions"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Configuration.getInstance().userAgentValue = requireActivity().packageName
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        checkAndRequestPermissions()
        fetchAndPlotGlobalSightings()
    }

    private fun fetchAndPlotGlobalSightings() {
        viewLifecycleOwner.lifecycleScope.launch {
            val sightings = com.assetvault.network.SupabaseManager.fetchSightings()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                sightings.forEach { sighting ->
                    val point = GeoPoint(sighting.lat, sighting.lng)
                    val marker = Marker(binding.mapView)
                    marker.position = point
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.title = "Sighting: ${sighting.asset_id}"
                    marker.icon = ContextCompat.getDrawable(requireContext(), org.osmdroid.library.R.drawable.marker_default) // Standard marker
                    binding.mapView.overlays.add(marker)
                }
                binding.mapView.invalidate()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val phoneGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val locationGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        when {
            phoneGranted && locationGranted -> startCellTriangulation()
            else -> {
                binding.tvCellInfo.text = "📡 To show your approximate location using cell towers (no GPS), this app needs Phone + Location permissions.\n\nThis does NOT use GPS — only reads cell tower IDs."
                binding.tvLocationStatus.text = "Tap to allow"
                binding.root.setOnClickListener {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                }
            }
        }
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        // Default center on India
        binding.mapView.controller.setZoom(5.0)
        binding.mapView.controller.setCenter(GeoPoint(20.5937, 78.9629))
    }

    @Suppress("MissingPermission")
    private fun startCellTriangulation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // 1. Display raw cell data (just for UI)
        try {
            val tm = requireContext().getSystemService(android.content.Context.TELEPHONY_SERVICE) as TelephonyManager
            val allCellInfo = tm.allCellInfo
            
            var validCells = 0
            var cellSummary = ""
            
            if (!allCellInfo.isNullOrEmpty()) {
                for (info in allCellInfo) {
                    // Filter out Integer.MAX_VALUE (2147483647) which means unavailable
                    when (info) {
                        is CellInfoLte -> {
                            val id = info.cellIdentity
                            if (id.ci != Integer.MAX_VALUE) { validCells++; cellSummary += "  LTE: CI=${id.ci}\n" }
                        }
                        is CellInfoGsm -> {
                            val id = info.cellIdentity
                            if (id.cid != Integer.MAX_VALUE) { validCells++; cellSummary += "  GSM: CID=${id.cid}\n" }
                        }
                        is CellInfoWcdma -> {
                            val id = info.cellIdentity
                            if (id.cid != Integer.MAX_VALUE) { validCells++; cellSummary += "  WCDMA: CID=${id.cid}\n" }
                        }
                    }
                }
            }
            
            val finalSummary = if (validCells > 0) "📡 Valid cell towers found: $validCells\n$cellSummary" else "📡 Real cell IDs masked by OS. Using Google Play Services for triangulation."
            binding.tvCellInfo.text = finalSummary.trim()
        } catch (e: Exception) {
            binding.tvCellInfo.text = "📡 Unable to read raw tower IDs."
        }

        binding.tvLocationStatus.text = "Locating via Network..."

        // 2. Get location using NETWORK_PROVIDER (Cell/WiFi Triangulation, NO GPS)
        val locationManager = requireContext().getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        
        val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        if (!isNetworkEnabled) {
            binding.tvLocationStatus.text = "Location OFF"
            binding.tvApproxLocation.text = "📍 Android requires 'Location Services' to be globally ON to triangulate cell towers. Please turn on Location in quick settings."
            return
        }

        try {
            locationManager.requestSingleUpdate(
                android.location.LocationManager.NETWORK_PROVIDER,
                { location ->
                    val lat = location.latitude
                    val lng = location.longitude
                    val accuracy = location.accuracy.toDouble()
                    
                    binding.tvApproxLocation.text = "📍 Approximate location: %.4f°N, %.4f°E (±%.0fm)".format(lat, lng, accuracy)
                    binding.tvLocationStatus.text = "Located ✓"
                    placeMarkerOnMap(lat, lng, accuracy)
                },
                android.os.Looper.getMainLooper()
            )
        } catch (e: Exception) {
            binding.tvLocationStatus.text = "Error"
            binding.tvApproxLocation.text = "📍 Error getting network location: ${e.message}"
        }
    }

    private fun placeMarkerOnMap(lat: Double, lng: Double, accuracyMeters: Double) {
        val point = GeoPoint(lat, lng)

        // Zoom in to approximate location
        binding.mapView.controller.setZoom(14.0)
        binding.mapView.controller.setCenter(point)

        // Add a marker
        val marker = Marker(binding.mapView)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "Approximate Location (Cell Triangulation)"
        marker.snippet = "Accuracy: ±${accuracyMeters.toInt()}m"

        // Add accuracy circle
        val circle = Polygon()
        circle.points = Polygon.pointsAsCircle(point, accuracyMeters)
        circle.fillColor = 0x226200EE  // transparent purple
        circle.strokeColor = 0xFF6200EE.toInt()
        circle.strokeWidth = 2f

        binding.mapView.overlays.clear()
        binding.mapView.overlays.add(circle)
        binding.mapView.overlays.add(marker)
        binding.mapView.invalidate()
        
        // Push local sighting to global map
        viewLifecycleOwner.lifecycleScope.launch {
            val sighting = com.assetvault.network.AssetSighting(
                asset_id = "local-device",
                lat = lat,
                lng = lng,
                accuracy_meters = accuracyMeters
            )
            com.assetvault.network.SupabaseManager.pushSighting(sighting)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
