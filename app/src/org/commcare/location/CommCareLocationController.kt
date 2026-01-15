package org.commcare.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import org.commcare.CommCareApplication
import org.commcare.preferences.HiddenPreferences
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

const val DEFAULT_TIME_THRESHOLD = 2 * 60 * 1000L // 2 minutes in milliseconds

fun isLocationPermissionGranted(mContext: Context?): Boolean {
    val context = mContext ?: CommCareApplication.instance()
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

fun isLocationFresh(location: Location, maxAgeMs: Long = DEFAULT_TIME_THRESHOLD): Boolean {
    return System.currentTimeMillis() - location.time <= maxAgeMs
}

fun shouldDiscardLocation(location: Location): Boolean {
    if(!isLocationFresh(location)) {
        Logger.exception("Received a stale location", getStaleLocationException(location))
        return HiddenPreferences.shouldDiscardStaleLocations()
    }
    return false
}

fun getStaleLocationException(location: Location): Throwable {
    Exception(
        "Stale location with accuracy ${location.accuracy}"  +
            " with time ${location.time}" + " and current device time ${System.currentTimeMillis()}"
    )
}

fun logStaleLocationSaved(location: Location) {
    if (!isLocationFresh(location, DEFAULT_TIME_THRESHOLD)) {
        Logger.exception("Stale location saved in GPS capture", getStaleLocationException(location))
    }
}
