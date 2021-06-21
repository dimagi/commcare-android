package org.commcare.gis

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.Symbol
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions
import kotlinx.android.synthetic.main.activity_mapbox_location_picker.*
import org.commcare.activities.components.FormEntryConstants
import org.commcare.dalvik.R
import org.commcare.utils.ConnectivityStatus
import org.commcare.utils.GeoUtils
import org.commcare.views.widgets.GeoPointWidget

class MapboxLocationPickerActivity : BaseMapboxActivity() {

    companion object {
        const val MARKER_ICON_IMAGE_ID = "marker_icon_image"
    }

    private lateinit var viewModel: MapboxLocationPickerViewModel
    private var loadedStyle: Style? = null
    private val mapStyles = arrayOf(
            Style.MAPBOX_STREETS,
            Style.SATELLITE,
            Style.SATELLITE_STREETS,
            Style.OUTDOORS
    )
    private var currentMapStyleIndex = 0
    private var cameraPosition: CameraPosition? = null
    private var symbolManager: SymbolManager? = null
    private var symbol: Symbol? = null
    private val mapClickListener = MapboxMap.OnMapClickListener { point ->
        // Add marker.
        updateMarker(point)
        viewModel.reverseGeocode(point)
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))
                .get(MapboxLocationPickerViewModel::class.java)
        attachListener()

        // Check extras
        if (intent.hasExtra(GeoPointWidget.LOCATION)) {
            val inputLocation = intent.getDoubleArrayExtra(GeoPointWidget.LOCATION)!!
            cameraPosition = CameraPosition.Builder()
                    .target(LatLng(inputLocation[0], inputLocation[1]))
                    .zoom(10.0)
                    .tilt(20.0)
                    .build()
        }
        mapView.showCurrentLocationBtn(false)
    }

    private fun attachListener() {
        confirm_location_button.visibility = if (inViewMode()) {
            View.GONE
        } else {
            View.VISIBLE
        }
        confirm_location_button.setOnClickListener {
            // Use the map target's coordinates to make a reverse geocoding search
            Intent().apply {
                putExtra(FormEntryConstants.LOCATION_RESULT, GeoUtils.locationToString(viewModel.getLocation()))
            }.also {
                setResult(RESULT_OK, it)
                finish()
            }
        }
        cancel_button.setOnClickListener {
            finish()
        }
        switch_map.setOnClickListener {
            if (ConnectivityStatus.isNetworkAvailable(this)) {
                currentMapStyleIndex = (currentMapStyleIndex + 1) % mapStyles.size
                val style = Style.Builder()
                        .fromUri(mapStyles[currentMapStyleIndex])
                        .withImage(MARKER_ICON_IMAGE_ID, ContextCompat.getDrawable(this, R.drawable.marker)!!)
                map.setStyle(style) {
                    val loc = viewModel.getLocation()
                    updateMarker(LatLng(loc.latitude, loc.longitude, loc.altitude))
                }
            }
        }
        current_location.setOnClickListener {
            mapView.focusOnUserLocation(true)
            if (!inViewMode()) {
                val location = map.locationComponent.lastKnownLocation
                location?.let {
                    updateMarker(LatLng(it.latitude, it.longitude, it.altitude))
                }
            }
        }
    }

    private fun inViewMode() = intent.getBooleanExtra(GeoPointWidget.EXTRA_VIEW_ONLY, false)

    override fun onResume() {
        super.onResume()
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.placeName.observe(this, androidx.lifecycle.Observer {
            confirm_location_button.isEnabled = true
            location.text = it
        })
    }

    override fun onDestroy() {
        map.removeOnMapClickListener(mapClickListener)
        super.onDestroy()
    }

    override fun getMapLayout(): Int {
        return R.layout.activity_mapbox_location_picker
    }

    override fun shouldFocusUserLocationOnLoad(): Boolean {
        return false
    }

    override fun onMapLoaded() {
        if (!inViewMode()) {
            map.addOnMapClickListener(mapClickListener)
        }
        setMapStyle()
    }

    private fun setMapStyle() {
        val style = Style.Builder()
                .fromUri(mapStyles[currentMapStyleIndex])
                .withImage(MARKER_ICON_IMAGE_ID, ContextCompat.getDrawable(this, R.drawable.marker)!!)
        map.setStyle(style) {
            loadedStyle = it
            enableLocationComponent(it)
            addMarker(it, map.cameraPosition.target)
            initialMarkerPosition()
        }
    }

    private fun enableLocationComponent(style: Style) {
        val component = map.locationComponent
        component.activateLocationComponent(LocationComponentActivationOptions.builder(
                this, style
        ).build())
        component.isLocationComponentEnabled = true
        component.cameraMode = CameraMode.TRACKING
        component.renderMode = RenderMode.NORMAL
    }

    private fun addMarker(style: Style, point: LatLng) {
        symbolManager = SymbolManager(mapView, map, style)
        symbolManager?.let { symbolManager ->
            symbolManager.iconAllowOverlap = true
            val symbolOptions = SymbolOptions()
                    .withLatLng(point)
                    .withIconImage(MARKER_ICON_IMAGE_ID)
            symbol = symbolManager.create(symbolOptions)
        }
    }

    private fun initialMarkerPosition() {
        if (cameraPosition != null) {
            cameraPosition?.let { pos ->
                viewModel.reverseGeocode(pos.target)
                updateMarker(pos.target)
            }
        } else {
            map.locationComponent.locationEngine
                    ?.getLastLocation(object : LocationEngineCallback<LocationEngineResult> {
                        override fun onSuccess(result: LocationEngineResult?) {
                            result ?: return
                            result.lastLocation?.let { location ->
                                val point = LatLng(location.latitude, location.longitude, location.altitude)
                                viewModel.reverseGeocode(point)
                                updateMarker(point)
                            }
                        }

                        override fun onFailure(exception: Exception) {}
                    })
        }
    }

    private fun updateMarker(point: LatLng) {
        symbol?.let { symbol ->
            symbol.latLng = point
            symbolManager?.update(symbol)
            val pos = CameraPosition.Builder()
                    .target(point)
                    .zoom(15.0)
                    .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 10)
        }
    }
}
