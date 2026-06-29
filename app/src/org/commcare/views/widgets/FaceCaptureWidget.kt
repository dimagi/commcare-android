package org.commcare.views.widgets

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import org.commcare.activities.camera.MicroImageActivity
import org.commcare.activities.camera.MicroImageActivity.ALLOW_CAMERA_LENS_SWITCH_EXTRA
import org.commcare.activities.camera.MicroImageActivity.CAMERA_LENS_FACING_EXTRA
import org.commcare.activities.camera.MicroImageActivity.CAPTURE_OUTPUT_MODE_EXTRA
import org.commcare.activities.components.FormEntryConstants
import org.commcare.logic.PendingCalloutInterface
import org.javarosa.form.api.FormEntryPrompt

class FaceCaptureWidget(context: Context?, prompt: FormEntryPrompt?, pic: PendingCalloutInterface?) :
    ImageWidget(context, prompt, pic) {
    init {
        mChooseButton.visibility = GONE
    }

    override fun takePicture() {
        val i = Intent(getContext(), MicroImageActivity::class.java)
            .putExtra(CAMERA_LENS_FACING_EXTRA, CameraSelector.LENS_FACING_FRONT)
            .putExtra(CAPTURE_OUTPUT_MODE_EXTRA, MicroImageActivity.CaptureOutputMode.TEMP_FILE.name)
            .putExtra(ALLOW_CAMERA_LENS_SWITCH_EXTRA, true)

        (context as AppCompatActivity).startActivityForResult(i, FormEntryConstants.FACE_CAPTURE)
        pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex())
    }
}
