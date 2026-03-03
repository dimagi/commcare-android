package org.commcare.location

import android.content.Context
import android.location.Location
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object LocationHelper {
    const val LOCATION_TIMEOUT_MS: Long = 5000L

    @JvmStatic
    fun getCurrentLocation(context: Context): Task<Location?> {
        return CommCareLocationControllerFactory.getLocationController(context, null)
            .getCurrentLocation()
    }

    suspend fun getCurrentLocationWithTimeout(context: Context): Location? =
        withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                getCurrentLocation(context).addOnCompleteListener { task ->
                    cont.resume(if (task.isSuccessful) task.result else null)
                }
            }
        }
}
