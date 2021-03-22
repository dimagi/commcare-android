package org.commcare.location

import android.content.Context
import android.location.Location
import com.google.android.gms.location.*

/**
 * @author $|-|!˅@M
 */
class CommCareFusedLocationController(private var mContext: Context?,
                                      private var mListener: CommCareLocationListener?): CommCareLocationController {

    private val mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext!!)
    private val settingsClient = LocationServices.getSettingsClient(mContext!!)
    private val mLocationRequest = LocationRequest.create().apply {
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        interval = LOCATION_UPDATE_INTERVAL
    }
    private var mCurrentLocation: Location? = null
    private val mLocationCallback = object: LocationCallback() {
        override fun onLocationResult(result: LocationResult?) {
            result ?: return
            mCurrentLocation = result.lastLocation
            mListener?.onLocationResult(mCurrentLocation!!)
        }
    }

    companion object {
        const val LOCATION_UPDATE_INTERVAL = 5000L
    }

    private fun requestUpdates() {
        if (isLocationPermissionGranted(mContext)) {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null)
        } else {
            mListener?.missingPermissions()
        }
    }

    override fun start() {
        val locationSettingsRequest = LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest)
                .setAlwaysShow(true)
                .build()
        settingsClient.checkLocationSettings(locationSettingsRequest)
                .addOnSuccessListener {
                    mListener?.onLocationRequestStart()
                    requestUpdates()
                }
                .addOnFailureListener { exception ->
                    mListener?.onLocationRequestFailure(CommCareLocationListener.Failure.ApiException(exception))
                }
    }

    override fun stop() {
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
    }

    override fun getLocation(): Location? {
        return mCurrentLocation
    }

    override fun destroy() {
        mContext = null
        mListener = null
    }
}