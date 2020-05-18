package org.commcare.gis

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.mapbox.geojson.Polygon
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import io.ona.kujaku.manager.DrawingManager
import kotlinx.android.synthetic.main.activity_entity_mapbox.*
import org.commcare.activities.components.FormEntryInstanceState
import org.commcare.android.javarosa.IntentCallout
import org.commcare.dalvik.R
import org.commcare.gis.EntityMapUtils.parseBoundaryCoords
import org.commcare.interfaces.CommCareActivityUIController
import org.commcare.interfaces.WithUIController
import org.commcare.utils.FileUtil
import org.commcare.utils.ImageType
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        freezeOrientation()
        initExtras()
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
        mapView.isWarmGps = true
        drawingManager = DrawingManager(mapView, map, loadedStyle)
        map.addOnMapClickListener {
            polygon = drawingManager.currentPolygon
            uiController.refreshView()
            false
        }
        setUiFromBoundaryCoords()
    }

    private fun setUiFromBoundaryCoords() {
        kotlin.runCatching {
            parseBoundaryCoords(boundaryCoords)
        }.onFailure {
            showToast(R.string.parse_coordinates_failure)
            setResult(Activity.RESULT_CANCELED)
            Logger.exception("Exception while loading boundary coordinates ", Exception(it))
            finish()
        }.onSuccess { latlngs ->
            latlngs.map { latlng -> drawingManager.drawCircle(latlng) }
        }
    }


    fun startTracking() {
        isRecording = true
        if (!isManual) {
            if (mapView.locationClient != null) {
                mapView.locationClient!!.addLocationListener(this)
            }
        } else {
            drawingManager.startDrawing(null)
        }
    }

    fun stopTracking() {
        isRecording = false
        mapView.locationClient!!.removeLocationListener(this)
        polygon = drawingManager.currentPolygon
        uiController.refreshView()
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

    override fun onLocationChanged(location: Location?) {
        if (location != null && location.accuracy <= locationMinAccuracy) {
            val addLocation = previousLocation == null ||
                    (location.distanceTo(previousLocation) >= location.accuracy + previousLocation!!.accuracy &&
                            location.time - previousLocation!!.time >= recordingIntervalMillis &&
                            location.distanceTo(previousLocation) >= recordingIntervalMeters)
            Toast.makeText(this, "loc $addLocation", Toast.LENGTH_SHORT).show()
            if (addLocation && isRecording) {
                previousLocation = location
                val latLng = LatLng(location.latitude, location.longitude)
                drawingManager.drawCircle(latLng)
                uiController.refreshView()
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
        data.putExtra(IntentCallout.INTENT_RESULT_VALUE, areaCalculator.getArea().toString())
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // deprecated, never called
    }

    override fun onProviderEnabled(provider: String?) {
        // connection set, nothing more to do here
    }

    override fun onProviderDisabled(provider: String?) {
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
}