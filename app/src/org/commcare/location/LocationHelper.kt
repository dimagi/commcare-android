package org.commcare.location

import android.content.Context
import android.location.Location
import kotlinx.coroutines.withTimeoutOrNull

object LocationHelper {
    const val LOCATION_TIMEOUT_MS: Long = 5000L

    suspend fun getCurrentLocation(context: Context): Location? {
        return CommCareLocationControllerFactory.getLocationController(context, null)
            .getCurrentLocation()
    }

    suspend fun getCurrentLocationWithTimeout(context: Context): Location? =
        withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            getCurrentLocation(context)
        }
}
