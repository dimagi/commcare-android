package org.commcare.location

import android.content.Context
import android.provider.Settings
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
            return when (isPlayServiceAvailable(context) && !isAirplaneModeOn(context)) {
                true -> CommCareFusedLocationController(context, mListener)
                false -> CommCareProviderLocationController(context, mListener)
            }
        }

        private fun isPlayServiceAvailable(context: Context): Boolean {
            return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }

        private fun isAirplaneModeOn(context: Context): Boolean {
            return Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON) != 0;
        }
    }
}
