package org.commcare.location

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import org.commcare.utils.GeoUtils

/**
 * @author $|-|!˅@M
 */
class CommCareProviderLocationController(private val mContext: Context,
                                         private val mListener: CommCareLocationListener): CommCareLocationController {

    private val mLocationManager = mContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var mCurrentLocation: Location? = null
    private val mProviders = GeoUtils.evaluateProviders(mLocationManager)
    private val mLocationListener = object: LocationListener {
        override fun onLocationChanged(location: Location?) {
            location ?: return
            mCurrentLocation = location
            mListener.onLocationResult(mCurrentLocation!!)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            //This callback will never be invoked.
        }

        override fun onProviderEnabled(provider: String?) {
            TODO("Not yet implemented")
        }

        override fun onProviderDisabled(provider: String?) {
            TODO("Not yet implemented")
        }

    }

    override fun start() {
        if (!isLocationPermissionGranted(mContext)) {
            mListener.missingPermissions()
            return
        }
        if (mProviders.isEmpty()) {
            mListener.onLocationRequestFailure(CommCareLocationListener.Failure.NoProvider)
            return
        }
        mListener.onLocationRequestStart()
        for (provider in mProviders) {
            mLocationManager.requestLocationUpdates(provider, 0L, 0.0f, mLocationListener)
        }
    }

    override fun stop() {
        mLocationManager.removeUpdates(mLocationListener)
    }

    override fun getLocation(): Location? {
        return mCurrentLocation
    }

}