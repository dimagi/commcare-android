package org.commcare.gis

import android.view.View
import android.widget.Button
import android.widget.TextView
import org.commcare.dalvik.R
import org.commcare.interfaces.CommCareActivityUIController
import org.commcare.utils.StringUtils
import org.commcare.views.ManagedUi
import org.commcare.views.UiElement
import org.javarosa.core.services.locale.Localization

@ManagedUi(R.layout.activity_drawing_boundary)
class DrawingBoundaryActivityUIController(private val drawingBoundaryActivity: DrawingBoundaryActivity) : CommCareActivityUIController {

    @UiElement(value = R.id.start_tracking_button, locale = "drawing.boundary.map.start.tracking")
    lateinit var startTrackingButton: Button

    @UiElement(value = R.id.stop_tracking_button, locale = "drawing.boundary.map.stop.tracking")
    private lateinit var stopTrackingButton: Button

    @UiElement(value = R.id.ok_tracking_button, locale = "drawing.boundary.map.ok.tracking")
    private lateinit var okTrackingButton: Button

    @UiElement(value = R.id.area_tv)
    private lateinit var areaTv: TextView

    override fun refreshView() {
        areaTv.text = StringUtils.getStringRobust(drawingBoundaryActivity, R.string.area_format, formatDecimal(drawingBoundaryActivity.getArea()))
    }

    private fun formatDecimal(num: Double): String {
        return String.format("%.2f", num)
    }

    override fun setupUI() {
        startTrackingButton.setOnClickListener {
            trackingUIState()
            drawingBoundaryActivity.startTracking()
        }
        stopTrackingButton.setOnClickListener {
            stoppedUIState()
            drawingBoundaryActivity.stopTracking()
        }
        okTrackingButton.setOnClickListener {
            drawingBoundaryActivity.finishTracking()
        }
        areaTv.text = StringUtils.getStringRobust(drawingBoundaryActivity, R.string.area_format, "0.00")
    }

    private fun stoppedUIState() {
        startTrackingButton.visibility = View.GONE
        stopTrackingButton.visibility = View.GONE
        okTrackingButton.visibility = View.VISIBLE
    }

    private fun trackingUIState() {
        startTrackingButton.visibility = View.GONE
        stopTrackingButton.visibility = View.VISIBLE
        okTrackingButton.visibility = View.GONE
    }

}