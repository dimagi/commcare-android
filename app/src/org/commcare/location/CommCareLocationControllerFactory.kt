package org.commcare.location

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * @author $|-|!˅@M
 */
class CommCareLocationControllerFactory {

    companion object {
        @JvmStatic
        fun getLocationController(context: Context, mListener: CommCareLocationListener): CommCareLocationController {
            // We only wanna use FusedLocationClient when play services are available.
            // Otherwise, we'll fallback to using LocationManager, rather than asking user to update playservices.
            return when (isPlayServiceAvailable(context)) {
                true -> CommCareFusedLocationController(context, mListener)
                false -> CommCareProviderLocationController(context, mListener)
            }
        }

        private fun isPlayServiceAvailable(context: Context): Boolean {
            return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }
    }

}