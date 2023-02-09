package org.commcare.gis

import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.viewbinding.ViewBinding
import com.mapbox.geojson.Polygon
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import io.ona.kujaku.manager.DrawingManager
import io.ona.kujaku.views.KujakuMapView
import org.commcare.activities.components.FormEntryInstanceState
import org.commcare.android.javarosa.IntentCallout
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ActivityDrawingBoundaryBinding
import org.commcare.gis.EntityMapUtils.parseBoundaryCoords
import org.commcare.interfaces.CommCareActivityUIController
import org.commcare.interfaces.WithUIController
import org.commcare.utils.FileUtil
import org.commcare.utils.ImageType
import org.commcare.utils.StringUtils
import org.javarosa.core.services.Logger
import java.io.File

/**
 * Used to draw or walk a boundary on mapbox based map
 */
class DrawingBoundaryActivity : BaseMapboxActivity(), WithUIController, LocationListener, MapboxMap.SnapshotReadyCallback {

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

    private lateinit var uiController: DrawingBoundaryActivityUIController
    private lateinit var viewBinding: ActivityDrawingBoundaryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                            Integer.valueOf(params.getString(EXTRA_KEY_ACCURACY, LOCATION_MIN_MIN_ACCURACY.toString()))))


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
        requestedOrientation = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            when (orientation) {
                Configuration.ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        } else {
            when (orientation) {
                Configuration.ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            }
        }
    }

    override fun onMapLoaded() {
        map.setStyle(Style.MAPBOX_STREETS) { loadedStyle ->
            this.loadedStyle = loadedStyle
            onStyleLoaded()
        }
    }

    private fun onStyleLoaded() {
        viewBinding.mapView.isWarmGps = true
        drawingManager = DrawingManager(viewBinding.mapView, map, loadedStyle)
        map.addOnMapClickListener {
            updateMetrics();
            false
        }
        setUiFromBoundaryCoords()
        uiController.readyToTrack();
    }

    // updates the polygon and refresh the UI
    private fun updateMetrics() {
        polygon = drawingManager.currentPolygon
        uiController.refreshView()
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

    fun startTracking() {
        requestLocationServices()
    }

    private fun requestLocationServices() {
        viewBinding.mapView.setWarmGps(true, null, null) {
            viewBinding.mapView.focusOnUserLocation(true)
            uiController.trackingUIState()
            startTrackingInner()
        }
    }

    private fun startTrackingInner() {
        isRecording = true
        if (!isManual) {
            viewBinding.mapView.locationClient!!.addLocationListener(this)
        } else {
            drawingManager.startDrawing(null)
        }
    }

    fun stopTracking() {
        isRecording = false
        viewBinding.mapView.locationClient!!.removeLocationListener(this)
        updateMetrics()
    }

    fun finishTracking() {
        polygon = drawingManager.stopDrawingAndDisplayLayer()
        if (isImageReturnRequired) {
            map.snapshot(this)
        } else {
            returnResult()
        }
    }

    fun redoTracking() {
        drawingManager.clearDrawing()
        startTracking()
    }

    override fun onLocationChanged(location: Location) {
        if (location != null && location.accuracy <= locationMinAccuracy) {
            val addLocation = previousLocation == null ||
                    (location.distanceTo(previousLocation) >= location.accuracy + previousLocation!!.accuracy &&
                            location.time - previousLocation!!.time >= recordingIntervalMillis &&
                            location.distanceTo(previousLocation) >= recordingIntervalMeters)
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

    override fun initUIController() {
        uiController = DrawingBoundaryActivityUIController(this)
    }

    override fun getUIController(): CommCareActivityUIController {
        return uiController
    }

    fun getArea(): Double {
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

    private fun setupUI() {
        viewBinding.startTrackingButton.setOnClickListener {
            this.startTracking()
        }
        viewBinding.stopTrackingButton.setOnClickListener {
            stoppedUIState()
            this.stopTracking()
        }
        viewBinding.okTrackingButton.setOnClickListener {
            this.finishTracking()
        }
        viewBinding.redoTrackingButton.setOnClickListener {
            trackingUIState()
            this.redoTracking()
        }
        viewBinding.areaTv.text = StringUtils.getStringRobust(this, R.string.area_format, "0.00")
    }

    private fun stoppedUIState() {
        viewBinding.startTrackingButton.visibility = View.GONE
        viewBinding.stopTrackingButton.visibility = View.GONE
        viewBinding.okTrackingButton.visibility = View.VISIBLE
        viewBinding.redoTrackingButton.visibility = View.VISIBLE
    }

    fun trackingUIState() {
        viewBinding.startTrackingButton.visibility = View.GONE
        viewBinding.stopTrackingButton.visibility = View.VISIBLE
        viewBinding.okTrackingButton.visibility = View.GONE
        viewBinding.redoTrackingButton.visibility = View.GONE
    }

    fun readyToTrack() {
        viewBinding.startTrackingButton.visibility = View.VISIBLE
    }
}