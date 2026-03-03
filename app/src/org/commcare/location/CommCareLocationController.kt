package org.commcare.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import org.commcare.CommCareApplication
import org.commcare.preferences.LocationPreferences
import org.commcare.utils.GeoUtils
import org.javarosa.core.services.Logger
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

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

private fun isFirstLocation(lastLocationString: String?): Boolean = lastLocationString.isNullOrEmpty()

private fun isDifferentLocation(
    newLocation: Location,
    lastGpsTime: Long,
): Boolean = newLocation.time != lastGpsTime

private fun updateLastLocation(location: Location) {
    LocationPreferences.setLastAcceptedLocation(GeoUtils.locationToString(location))
    LocationPreferences.setLastAcceptedLocationGpsTime(location.time)
}

private fun acceptLocation(
    location: Location,
    deviceTime: Long,
    listener: CommCareLocationListener?,
) {
    LocationPreferences.setLastAcceptedLocationTimestamp(deviceTime)
    listener?.onLocationResult(location)
}

private fun discardLocation(location: Location) {
    Logger.exception(
        "Discarding stale repeated location",
        Exception("Discarding stale repeated location ${GeoUtils.locationToString(location)} with accuracy ${location.accuracy}"),
    )
}

private fun getStaleLocationException(
    location: Location,
    currentDeviceTime: Long,
): Throwable {
    val driftInMinutes =
        (currentDeviceTime - location.time).milliseconds.absoluteValue.inWholeMinutes
    return Exception(
        "Stale location with accuracy ${location.accuracy}" +
            " with time ${location.time}" + " and current device time $currentDeviceTime, with drift $driftInMinutes minutes",
    )
}

private fun logStaleLocationIfGpsTimeDrifted(
    location: Location,
    currentDeviceTime: Long,
) {
    val drift = abs(currentDeviceTime - location.time)
    if (drift > DEFAULT_TIME_THRESHOLD) {
        Logger.exception(
            "Received a stale location",
            getStaleLocationException(location, currentDeviceTime),
        )
    }
}

fun onLocationReceived(
    newLocation: Location,
    listener: CommCareLocationListener?,
    setCurrentLocation: (Location) -> Unit,
) {
    val currentDeviceTime = System.currentTimeMillis()
    val lastLocationString = LocationPreferences.getLastAcceptedLocation()
    val lastAcceptedTimestamp = LocationPreferences.getLastAcceptedLocationTimestamp()
    val lastAcceptedGpsTime = LocationPreferences.getLastAcceptedLocationGpsTime()

    if (isFirstLocation(lastLocationString)) {
        updateLastLocation(newLocation)
        setCurrentLocation(newLocation)
        acceptLocation(newLocation, currentDeviceTime, listener)
        return
    }

    if (isDifferentLocation(newLocation, lastAcceptedGpsTime)) {
        updateLastLocation(newLocation)
        setCurrentLocation(newLocation)
        acceptLocation(newLocation, currentDeviceTime, listener)
        logStaleLocationIfGpsTimeDrifted(newLocation, currentDeviceTime)
        return
    }

    // Location values are same as last accepted location
    val timeDifference = currentDeviceTime - lastAcceptedTimestamp
    if (timeDifference > DEFAULT_TIME_THRESHOLD) {
        discardLocation(newLocation)
    } else {
        setCurrentLocation(newLocation)
        acceptLocation(newLocation, currentDeviceTime, listener)
    }
}
