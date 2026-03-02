package org.commcare.location

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource

object LocationHelper {
    const val LOCATION_TIMEOUT_MS: Long = 2000L

    @JvmStatic
    fun getCurrentLocation(context: Context): Task<Location?> {
        val noOpListener = object : CommCareLocationListener {
            override fun onLocationRequestStart() {}
            override fun onLocationResult(result: Location) {}
            override fun missingPermissions() {}
            override fun onLocationRequestFailure(failure: CommCareLocationListener.Failure) {}
            override fun onLocationServiceChange(locationServiceEnabled: Boolean) {}
        }
        return CommCareLocationControllerFactory.getLocationController(context, noOpListener)
            .getCurrentLocation()
    }

    @JvmStatic
    fun getCurrentLocationWithTimeout(context: Context): Task<Location?> {
        val tcs = TaskCompletionSource<Location?>()
        val handler = Handler(Looper.getMainLooper())
        val onTimeout = Runnable { tcs.trySetResult(null) }

        handler.postDelayed(onTimeout, LOCATION_TIMEOUT_MS)

        getCurrentLocation(context)
            .addOnCompleteListener { task: Task<Location?>? ->
                handler.removeCallbacks(onTimeout)
                tcs.trySetResult(if (task!!.isSuccessful) task.getResult() else null)
            }

        return tcs.getTask()
    }
}
