package org.commcare.home

import android.app.Activity
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.model.InstallErrorCode
import org.commcare.appupdate.AppUpdateState
import org.commcare.appupdate.FlexibleAppUpdateController

/** Scriptable [FlexibleAppUpdateController]; [fireCallback] stands in for a Play Core state change. */
class FakeAppUpdateController : FlexibleAppUpdateController {
    var state: AppUpdateState = AppUpdateState.UNAVAILABLE
    var availableVersion: Int? = null
    var installErrorCode: Int = InstallErrorCode.NO_ERROR
    var downloadProgress: Int? = null

    var registerCount: Int = 0
    var unregisterCount: Int = 0
    var startUpdateCount: Int = 0
    var completeUpdateCount: Int = 0

    var callback: Runnable? = null

    fun fireCallback() {
        callback?.run()
    }

    override fun register() {
        registerCount++
    }

    override fun unregister() {
        unregisterCount++
    }

    override fun getStatus(): AppUpdateState = state

    override fun completeUpdate() {
        completeUpdateCount++
    }

    override fun getProgress(): Int? = downloadProgress

    override fun getErrorCode(): Int = installErrorCode

    override fun startUpdate(activity: Activity) {
        startUpdateCount++
    }

    override fun availableVersionCode(): Int? = availableVersion

    override fun onStateUpdate(state: InstallState) = Unit
}
