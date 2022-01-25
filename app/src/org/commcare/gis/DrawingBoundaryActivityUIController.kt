package org.commcare.gis

import android.view.View
import android.widget.Button
import android.widget.TextView
import org.commcare.dalvik.R
import org.commcare.interfaces.CommCareActivityUIController
import org.commcare.utils.StringUtils
import org.commcare.views.ManagedUi
import org.commcare.views.UiElement

@ManagedUi(R.layout.activity_drawing_boundary)
class DrawingBoundaryActivityUIController(private val drawingBoundaryActivity: DrawingBoundaryActivity) : CommCareActivityUIController {

    @UiElement(value = R.id.start_tracking_button, locale = "drawing.boundary.map.start.tracking")
    lateinit var startTrackingButton: Button

    @UiElement(value = R.id.stop_tracking_button, locale = "drawing.boundary.map.stop.tracking")
    private lateinit var stopTrackingButton: Button

    @UiElement(value = R.id.ok_tracking_button, locale = "drawing.boundary.map.ok.tracking")
    private lateinit var okTrackingButton: Button

    @UiElement(value = R.id.redo_tracking_button, locale = "drawing.boundary.map.redo.tracking")
    private lateinit var redoTrackingButton: Button

    @UiElement(value = R.id.area_tv)
    private lateinit var areaTv: TextView

    override fun refreshView() {
        areaTv.text = StringUtils.getStringRobust(drawingBoundaryActivity, R.string.area_format, formatArea(drawingBoundaryActivity.getArea()))
    }

    private fun formatArea(num: Double): String {
        return String.format("%.2f", num)
    }

    override fun setupUI() {
        startTrackingButton.setOnClickListener {
            drawingBoundaryActivity.startTracking()
        }
        stopTrackingButton.setOnClickListener {
            stoppedUIState()
            drawingBoundaryActivity.stopTracking()
        }
        okTrackingButton.setOnClickListener {
            drawingBoundaryActivity.finishTracking()
        }
        redoTrackingButton.setOnClickListener {
            trackingUIState()
            drawingBoundaryActivity.redoTracking()
        }
        areaTv.text = StringUtils.getStringRobust(drawingBoundaryActivity, R.string.area_format, "0.00")
    }

    private fun stoppedUIState() {
        startTrackingButton.visibility = View.GONE
        stopTrackingButton.visibility = View.GONE
        okTrackingButton.visibility = View.VISIBLE
        redoTrackingButton.visibility = View.VISIBLE
    }

    fun trackingUIState() {
        startTrackingButton.visibility = View.GONE
        stopTrackingButton.visibility = View.VISIBLE
        okTrackingButton.visibility = View.GONE
        redoTrackingButton.visibility = View.GONE
    }

    fun readyToTrack() {
        startTrackingButton.visibility = View.VISIBLE
    }
}
