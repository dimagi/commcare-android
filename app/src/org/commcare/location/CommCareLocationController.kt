package org.commcare.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import org.commcare.CommCareApplication
import org.javarosa.core.services.Logger

/**
 * @author $|-|!˅@M
 */
interface CommCareLocationController {
    fun start()
    fun stop()
    fun getLocation(): Location?
    fun destroy()
}

fun isLocationPermissionGranted(mContext: Context?): Boolean {
    val context = mContext ?: CommCareApplication.instance()
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

fun isFresh(location: Location, maxAgeMs: Long = 2 * 60 * 1000): Boolean {
    return System.currentTimeMillis() - location.time <= maxAgeMs
}

fun logStaleLocation(location: Location) {
    if (!isFresh(location)) {
        Logger.exception(
            "Received a stale location",
            Exception("Received a stale location for location: $location" + " with time ${location.time}" + " and current device time ${System.currentTimeMillis()}")
        )
    }
}
