package org.commcare.fragments

import android.app.Application
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import org.commcare.activities.CommCareWiFiDirectActivity
import org.commcare.android.database.user.models.FormRecord

class WiFiDirectSessionViewModel(
    application: Application,
) : AndroidViewModel(application) {
    var state: CommCareWiFiDirectActivity.wdState? = null

    var isModeDialogShowing = false

    var cachedRecords: Array<FormRecord>? = null

    val manager: WifiP2pManager? = application.getSystemService(WifiP2pManager::class.java)

    val channel: WifiP2pManager.Channel? = manager?.initialize(application, Looper.getMainLooper(), null)

    override fun onCleared() {
        super.onCleared()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            channel?.close()
        }
    }
}
