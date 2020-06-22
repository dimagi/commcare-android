package org.commcare.location

import android.content.Context
import android.location.Location
import com.google.android.gms.location.*

/**
 * @author $|-|!˅@M
 */
class CommCareFusedLocationController(private val mContext: Context,
                                      private val mListener: CommCareLocationListener): CommCareLocationController {

    private val mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext)
    private val mLocationRequest = LocationRequest.create().apply {
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        interval = 5000
    }
    private var mCurrentLocation: Location? = null
    private val mLocationCallback = object: LocationCallback() {
        override fun onLocationResult(result: LocationResult?) {
            result ?: return
            mCurrentLocation = result.lastLocation
            mListener.onLocationResult(mCurrentLocation!!)
        }
    }

    companion object {
        const val REQUEST_CHECK_SETTINGS = 101
    }

    private fun requestUpdates() {
        if (isLocationPermissionGranted(mContext)) {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null)
        } else {
            mListener.missingPermissions()
        }
    }

    override fun start() {
        val locationSettingsRequest = LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest)
                .setAlwaysShow(true)
                .build()
        val settingsClient = LocationServices.getSettingsClient(mContext)
        settingsClient.checkLocationSettings(locationSettingsRequest)
                .addOnSuccessListener {
                    mListener.onLocationRequestStart()
                    requestUpdates()
                }
                .addOnFailureListener { exception ->
                    mListener.onLocationRequestFailure(CommCareLocationListener.Failure.ApiException(exception))
                }
    }

    override fun stop() {
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
    }

    override fun getLocation(): Location? {
        return mCurrentLocation
    }
}