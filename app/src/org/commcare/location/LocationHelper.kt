package org.commcare.location

import android.content.Context
import android.location.Location

object LocationHelper {
    @JvmStatic
    fun getCurrentLocation(context: Context, callback: CommCareLocationController.CurrentLocationCallback) {
        val noOpListener = object : CommCareLocationListener {
            override fun onLocationRequestStart() {}
            override fun onLocationResult(result: Location) {}
            override fun missingPermissions() {}
            override fun onLocationRequestFailure(failure: CommCareLocationListener.Failure) {}
            override fun onLocationServiceChange(locationServiceEnabled: Boolean) {}
        }
        CommCareLocationControllerFactory.getLocationController(context, noOpListener).getCurrentLocation(callback)
    }
}
