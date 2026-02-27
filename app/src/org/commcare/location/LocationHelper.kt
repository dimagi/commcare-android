package org.commcare.location

import android.content.Context
import android.location.Location
import com.google.android.gms.tasks.Task

object LocationHelper {
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
}
