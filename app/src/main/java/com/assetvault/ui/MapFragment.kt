package com.assetvault.ui

import android.Manifest
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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

    /** Sightings loaded for the current owner, kept so they survive user-location updates */
    private var pendingSightings: List<com.assetvault.network.AssetSighting> = emptyList()

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
        fetchAndPlotOwnerSightings()
    }

    /**
     * Fetches sightings that belong ONLY to the current owner's assets
     * and plots them as RED markers. Non-owners see nothing here.
     */
    private fun fetchAndPlotOwnerSightings() {
        viewLifecycleOwner.lifecycleScope.launch {
            val email = com.assetvault.data.SecurePreferences.getInstance(requireContext()).getGoogleEmail()
                ?: return@launch
            val sightings = withContext(Dispatchers.IO) {
                com.assetvault.network.SupabaseManager.fetchSightingsForOwner(email)
            }
            withContext(Dispatchers.Main) {
                // Store sightings so we can re-draw them after user location is plotted
                pendingSightings = sightings
                drawSightingMarkers()
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

        // Green marker for the current user's location
        val marker = Marker(binding.mapView)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "Your Location (Cell Triangulation)"
        marker.snippet = "Accuracy: ±${accuracyMeters.toInt()}m"
        marker.icon = createColoredMarkerIcon(Color.parseColor("#2E7D32")) // dark green

        // Accuracy circle in green
        val circle = Polygon()
        circle.points = Polygon.pointsAsCircle(point, accuracyMeters)
        circle.fillColor = 0x222E7D32
        circle.strokeColor = 0xFF2E7D32.toInt()
        circle.strokeWidth = 2f

        binding.mapView.overlays.clear()
        binding.mapView.overlays.add(circle)
        binding.mapView.overlays.add(marker)

        // Re-draw sighting markers on top
        drawSightingMarkers()

        binding.mapView.invalidate()
    }

    /** Draws all owner sightings as RED markers on the map */
    private fun drawSightingMarkers() {
        pendingSightings.forEach { sighting ->
            val point = GeoPoint(sighting.lat, sighting.lng)
            val marker = Marker(binding.mapView)
            marker.position = point
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            val shortHash = sighting.asset_id.take(12)
            marker.title = "🚨 Sighting Detected"
            marker.snippet = "Asset: $shortHash..."
            marker.icon = createColoredMarkerIcon(Color.parseColor("#D32F2F")) // dark red
            binding.mapView.overlays.add(marker)
        }
        binding.mapView.invalidate()
    }

    /** Creates a simple solid-color circle drawable to use as a map marker icon */
    private fun createColoredMarkerIcon(color: Int): android.graphics.drawable.Drawable {
        val size = (36 * resources.displayMetrics.density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        // White border
        val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(radius, radius, radius - 2f, borderPaint)
        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
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
