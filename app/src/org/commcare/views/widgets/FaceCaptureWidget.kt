package org.commcare.views.widgets

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import org.commcare.activities.components.FormEntryConstants
import org.commcare.fragments.MicroImageActivity
import org.commcare.logic.PendingCalloutInterface
import org.javarosa.form.api.FormEntryPrompt

class FaceCaptureWidget(context: Context?, prompt: FormEntryPrompt?, pic: PendingCalloutInterface?) :
    ImageWidget(context, prompt, pic) {
    init {
        mChooseButton.visibility = GONE
    }

    override fun takePicture() {
        val i = Intent(getContext(), MicroImageActivity::class.java)

        (context as AppCompatActivity).startActivityForResult(i, FormEntryConstants.FACE_CAPTURE)
        pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex())
    }
}
