package org.commcare.location

import android.content.Context
import android.location.Location
import kotlinx.coroutines.withTimeoutOrNull

object LocationHelper {
    const val LOCATION_TIMEOUT_MS: Long = 5000L

    suspend fun getCurrentLocationWithTimeout(context: Context): Location? {
        val controller = CommCareLocationControllerFactory.getLocationController(context, null)
        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            controller.getCurrentLocation()
        } ?: controller.getLastKnownLocation()
    }
}
