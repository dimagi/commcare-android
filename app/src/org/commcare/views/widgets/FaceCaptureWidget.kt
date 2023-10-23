package org.commcare.views.widgets

import android.content.Context
import org.commcare.logic.PendingCalloutInterface
import org.javarosa.form.api.FormEntryPrompt

class FaceCaptureWidget(context: Context?, prompt: FormEntryPrompt?, pic: PendingCalloutInterface?) :
    ImageWidget(context, prompt, pic) {
    init {
        mChooseButton.visibility = GONE
    }

    override fun takePicture() {}
}
