package org.commcare.gis

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.mapbox.geojson.Polygon
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import io.ona.kujaku.manager.DrawingManager
import io.ona.kujaku.views.KujakuMapView
import java.io.File
import org.commcare.activities.components.FormEntryInstanceState
import org.commcare.android.javarosa.IntentCallout
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ActivityDrawingBoundaryBinding
import org.commcare.gis.EntityMapUtils.parseBoundaryCoords
import org.commcare.utils.FileUtil
import org.commcare.utils.ImageType
import org.commcare.utils.StringUtils
import org.javarosa.core.services.Logger
import org.javarosa.core.services.locale.Localization

/**
 * Used to draw or walk a boundary on mapbox based map
 */
class DrawingBoundaryActivity : BaseMapboxActivity(), LocationListener, MapboxMap.SnapshotReadyCallback {

    companion object {
        // Incoming Intent Extras
        private const val EXTRA_KEY_ACCURACY = "accuracy"
        private const val EXTRA_KEY_IMAGE = "image"
        private const val EXTRA_KEY_INTERVAL_METERS = "interval_meters"
        private const val EXTRA_KEY_INTERVAL_MILLIS = "interval_millis"
        private const val EXTRA_KEY_TITLE = "title"
        private const val EXTRA_KEY_DETAIL = "detail"
        private const val EXTRA_KEY_MANUAL = "manual"

        // Result Intent Extras
        private const val EXTRA_KEY_COORDINATES = "coordinates"
        private const val EXTRA_KEY_PERIMETER = "perimeter"

        private const val LOCATION_MIN_MAX_ACCURACY = 50
        private const val LOCATION_MIN_MIN_ACCURACY = 10
    }

    private var mapSnapshotPath: String? = null
    private lateinit var loadedStyle: Style
    private lateinit var boundaryCoords: String
    private var polygon: Polygon? = null
    private var isManual: Boolean = false
    private lateinit var drawingManager: DrawingManager
    private var isRecording: Boolean = false
    private var previousLocation: Location? = null
    private var recordingIntervalMeters = 0
    private var recordingIntervalMillis = 0
    private var isImageReturnRequired = false
    private var title: String? = null
    private var detail: String? = null
    private var locationMinAccuracy = 35

    private lateinit var viewBinding: ActivityDrawingBoundaryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initUI()
        freezeOrientation()
        initExtras()
    }

    override fun getMapView(): KujakuMapView {
        return viewBinding.mapView
    }

    override fun getViewBinding(): ViewBinding {
        if (!::viewBinding.isInitialized) {
            viewBinding = ActivityDrawingBoundaryBinding.inflate(layoutInflater)
        }
        return viewBinding
    }

    private fun initExtras() {
        val params = intent.extras
        if (params != null) {
            locationMinAccuracy = LOCATION_MIN_MIN_ACCURACY.coerceAtLeast(
                LOCATION_MIN_MAX_ACCURACY.coerceAtMost(
                        Integer.valueOf(params.getString(EXTRA_KEY_ACCURACY, LOCATION_MIN_MIN_ACCURACY.toString()
                        )
                        )
                )
            )

            recordingIntervalMeters = Integer.valueOf(params.getString(EXTRA_KEY_INTERVAL_METERS, "0"))
            recordingIntervalMillis = Integer.valueOf(params.getString(EXTRA_KEY_INTERVAL_MILLIS, "0"))
            isImageReturnRequired = params.getString(EXTRA_KEY_IMAGE, "false")!!.toBoolean()
            title = params.getString(EXTRA_KEY_TITLE, "")
            detail = params.getString(EXTRA_KEY_DETAIL, "")
            isManual = params.getString(EXTRA_KEY_MANUAL, "false")!!.toBoolean()
            boundaryCoords = params.getString(EXTRA_KEY_COORDINATES, "")
        }
    }

    private fun freezeOrientation() {
        val orientation = resources.configuration.orientation
        requestedOrientation =
            when (orientation) {
                Configuration.ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            }
    }

    override fun onMapLoaded() {
        map.setStyle(Style.MAPBOX_STREETS) { loadedStyle ->
            this.loadedStyle = loadedStyle
            onStyleLoaded()
        }
    }

    private fun onStyleLoaded() {
        getMapView().isWarmGps = true
        drawingManager = DrawingManager(getMapView(), map, loadedStyle)
        map.addOnMapClickListener {
            updateMetrics()
            false
        }
        setUiFromBoundaryCoords()
        readyToTrack()
    }

    // updates the polygon and refresh the UI
    private fun updateMetrics() {
        polygon = drawingManager.currentPolygon
        refreshView()
    }

    private fun setUiFromBoundaryCoords() {
        kotlin.runCatching {
            parseBoundaryCoords(boundaryCoords)
        }.onFailure {
            showToast(R.string.parse_coordinates_failure)
            setResult(AppCompatActivity.RESULT_CANCELED)
            Logger.exception("Exception while loading boundary coordinates ", Exception(it))
            finish()
        }.onSuccess { latlngs ->
            latlngs.map { latlng -> drawingManager.drawCircle(latlng) }
            updateMetrics()
        }
    }

    private fun startTracking() {
        requestLocationServices()
    }

    private fun requestLocationServices() {
        getMapView().setWarmGps(true, null, null) {
            getMapView().focusOnUserLocation(true)
            trackingUIState()
            startTrackingInner()
        }
    }

    private fun startTrackingInner() {
        isRecording = true
        if (!isManual) {
            getMapView().locationClient!!.addLocationListener(this)
        } else {
            drawingManager.startDrawing(null)
        }
    }

    private fun stopTracking() {
        isRecording = false
        getMapView().locationClient!!.removeLocationListener(this)
        updateMetrics()
    }

    private fun finishTracking() {
        polygon = drawingManager.stopDrawingAndDisplayLayer()
        if (isImageReturnRequired) {
            map.snapshot(this)
        } else {
            returnResult()
        }
    }

    private fun redoTracking() {
        drawingManager.clearDrawing()
        startTracking()
    }

    override fun onLocationChanged(location: Location) {
        val prevLocation = previousLocation
        if (location != null && location.accuracy <= locationMinAccuracy) {
            val addLocation = prevLocation == null ||
                (
                location.distanceTo(prevLocation) >= location.accuracy + prevLocation.accuracy &&
                location.time - prevLocation.time >= recordingIntervalMillis &&
                location.distanceTo(prevLocation) >= recordingIntervalMeters
                )
            if (addLocation && isRecording) {
                previousLocation = location
                val latLng = LatLng(location.latitude, location.longitude)
                drawingManager.drawCircle(latLng)
                updateMetrics()
            }
        }
    }

    private fun returnResult() {
        val result = Bundle()

        val areaCalculator = AreaCalculator(polygon!!)
        result.putString(EXTRA_KEY_PERIMETER, areaCalculator.getPerimeter().toString())
        result.putString(EXTRA_KEY_COORDINATES, areaCalculator.toString())

        if (isImageReturnRequired) {
            result.putString(EXTRA_KEY_IMAGE, mapSnapshotPath)
        }

        val data = Intent()
        data.putExtra(IntentCallout.INTENT_RESULT_EXTRAS_BUNDLE, result)
        var area = areaCalculator.getArea()
        data.putExtra(IntentCallout.INTENT_RESULT_VALUE, String.format("%.4f", area))
        setResult(AppCompatActivity.RESULT_OK, data)
        finish()
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // deprecated, never called
    }

    override fun onProviderEnabled(provider: String) {
        // connection set, nothing more to do here
    }

    override fun onProviderDisabled(provider: String) {
        showToast(R.string.location_provider_disabled)
    }

    private fun getArea(): Double {
        return if (polygon != null) AreaCalculator(polygon!!).getArea() else 0.0
    }

    override fun onSnapshotReady(snapshot: Bitmap) {
        val imageFilename = System.currentTimeMillis().toString() + "." + "png"
        mapSnapshotPath = FormEntryInstanceState.getInstanceFolder() + imageFilename
        FileUtil.writeBitmapToDiskAndCleanupHandles(snapshot, ImageType.PNG, File(mapSnapshotPath!!))
        returnResult()
    }

    private fun refreshView() {
        viewBinding.areaTv.text = StringUtils.getStringRobust(this, R.string.area_format, formatArea(getArea()))
    }

    private fun formatArea(num: Double): String {
        return String.format("%.2f", num)
    }

    private fun initUI() {
        viewBinding.startTrackingButton.text = Localization.get("drawing.boundary.map.start.tracking")
        viewBinding.startTrackingButton.setOnClickListener {
            startTracking()
        }

        viewBinding.stopTrackingButton.text = Localization.get("drawing.boundary.map.stop.tracking")
        viewBinding.stopTrackingButton.setOnClickListener {
            stoppedUIState()
            stopTracking()
        }
        viewBinding.okTrackingButton.text = Localization.get("drawing.boundary.map.ok.tracking")
        viewBinding.okTrackingButton.setOnClickListener {
            finishTracking()
        }
        viewBinding.redoTrackingButton.text = Localization.get("drawing.boundary.map.redo.tracking")
        viewBinding.redoTrackingButton.setOnClickListener {
            trackingUIState()
            redoTracking()
        }
        viewBinding.areaTv.text = StringUtils.getStringRobust(this, R.string.area_format, "0.00")
    }

    private fun stoppedUIState() {
        viewBinding.startTrackingButton.visibility = View.GONE
        viewBinding.stopTrackingButton.visibility = View.GONE
        viewBinding.okTrackingButton.visibility = View.VISIBLE
        viewBinding.redoTrackingButton.visibility = View.VISIBLE
    }

    private fun trackingUIState() {
        viewBinding.startTrackingButton.visibility = View.GONE
        viewBinding.stopTrackingButton.visibility = View.VISIBLE
        viewBinding.okTrackingButton.visibility = View.GONE
        viewBinding.redoTrackingButton.visibility = View.GONE
    }

    private fun readyToTrack() {
        viewBinding.startTrackingButton.visibility = View.VISIBLE
    }
}
