package org.commcare.views.widgets

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import org.commcare.activities.camera.MicroImageActivity
import org.commcare.activities.camera.MicroImageActivity.ALLOW_CAMERA_LENS_SWITCH_EXTRA
import org.commcare.activities.camera.MicroImageActivity.CAMERA_LENS_FACING_EXTRA
import org.commcare.activities.camera.MicroImageActivity.CAPTURE_OUTPUT_MODE_EXTRA
import org.commcare.activities.camera.MicroImageActivity.TITLE_RES_EXTRA
import org.commcare.activities.components.FormEntryConstants
import org.commcare.dalvik.R
import org.commcare.logic.PendingCalloutInterface
import org.commcare.utils.StringUtils
import org.javarosa.form.api.FormEntryPrompt

class FaceCaptureWidget(
    context: Context?,
    prompt: FormEntryPrompt?,
    pic: PendingCalloutInterface?,
) : ImageWidget(context, prompt, pic) {
    init {
        mChooseButton.visibility = GONE
        mCaptureButton.text =
            StringUtils.getStringSpannableRobust(
                context,
                if (mImageView != null && mImageView.drawable != null) {
                    R.string.face_capture_retake_photo
                } else {
                    R.string.capture_image
                },
            )
    }

    override fun takePicture() {
        val i =
            Intent(context, MicroImageActivity::class.java)
                .putExtra(CAMERA_LENS_FACING_EXTRA, CameraSelector.LENS_FACING_BACK)
                .putExtra(CAPTURE_OUTPUT_MODE_EXTRA, MicroImageActivity.CaptureOutputMode.TEMP_FILE.name)
                .putExtra(ALLOW_CAMERA_LENS_SWITCH_EXTRA, true)
                .putExtra(TITLE_RES_EXTRA, R.string.face_capture_activity_title)

        (context as AppCompatActivity).startActivityForResult(i, FormEntryConstants.IMAGE_CAPTURE)
        pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex())
    }

    override fun clearBinaryAttachment() {
        super.clearBinaryAttachment()
        // reset buttons
        mCaptureButton.text = StringUtils.getStringSpannableRobust(context, R.string.capture_image)
    }
}
