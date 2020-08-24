package org.commcare.location

import android.location.Location
import java.lang.Exception

/**
 * @author $|-|!˅@M
 */
interface CommCareLocationListener {

    // Inform the listener that we've starting listening for location updates, and it can show a
    // progress dialog or something similar in place.
    fun onLocationRequestStart()

    // This location might not be accurate, so it's the job of the listener to keep listening
    // to a better result and once he's satisfied remove listening for updates.
    fun onLocationResult(result: Location)

    fun missingPermissions()

    fun onLocationRequestFailure(failure: Failure)

    sealed class Failure {
        object NoProvider : Failure()
        data class ApiException(val exception: Exception) : Failure()
    }
}