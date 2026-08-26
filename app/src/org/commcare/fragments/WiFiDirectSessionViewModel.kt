package org.commcare.fragments

import androidx.lifecycle.ViewModel
import org.commcare.activities.CommCareWiFiDirectActivity
import org.commcare.android.database.user.models.FormRecord

class WiFiDirectSessionViewModel : ViewModel() {
    var state: CommCareWiFiDirectActivity.wdState? = null

    var isModeDialogShowing = false

    var cachedRecords: Array<FormRecord>? = null
}
