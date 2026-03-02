package org.commcare.location

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.Executors

object LocationHelper {
    private const val LOCATION_TIMEOUT_MS = 2000L

    @JvmStatic
    fun getCurrentLocation(context: Context): Task<Location?> {
        val noOpListener = object : CommCareLocationListener {
            override fun onLocationRequestStart() {}
            override fun onLocationResult(result: Location) {}
            override fun missingPermissions() {}
            override fun onLocationRequestFailure(failure: CommCareLocationListener.Failure) {}
            override fun onLocationServiceChange(locationServiceEnabled: Boolean) {}
        }
        return CommCareLocationControllerFactory.getLocationController(context, noOpListener).getCurrentLocation()
    }

    private fun getCurrentLocationWithTimeout(context: Context): Task<Location?> {
        val tcs = TaskCompletionSource<Location?>()
        val handler = Handler(Looper.getMainLooper())
        val onTimeout = Runnable { tcs.trySetResult(null) }

        handler.postDelayed(onTimeout, LOCATION_TIMEOUT_MS)

        getCurrentLocation(context).addOnCompleteListener { task ->
            handler.removeCallbacks(onTimeout)
            tcs.trySetResult(if (task.isSuccessful) task.result else null)
        }

        return tcs.task
    }

    @JvmStatic
    fun loadMap(
        context: Context,
        entityLoader: Runnable,
        mapReadyTask: Task<GoogleMap>,
    ): Task<Pair<GoogleMap, Location?>> {
        val executor = Executors.newSingleThreadExecutor()
        val entitiesTask = Tasks.call(executor) { entityLoader.run() }
        entitiesTask.addOnCompleteListener { executor.shutdown() }

        val locationTask = getCurrentLocationWithTimeout(context)

        return Tasks.whenAll(entitiesTask, locationTask, mapReadyTask).continueWith {
            if (!it.isSuccessful) throw it.exception!!
            Pair(mapReadyTask.result!!, locationTask.result)
        }
    }
}
