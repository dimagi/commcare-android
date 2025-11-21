package org.commcare.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import org.commcare.utils.GeoUtils.locationServicesEnabledGlobally

/**
 * @author $|-|!˅@M
 */
class CommCareFusedLocationController(
    private var mContext: Context?,
    private var mListener: CommCareLocationListener?,
) : CommCareLocationController {
    private val mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext!!)
    private val settingsClient = LocationServices.getSettingsClient(mContext!!)
    private val mLocationServiceChangeReceiver = LocationChangeReceiverBroadcast()
    private val mLocationRequest =
        LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = LOCATION_UPDATE_INTERVAL
        }
    private var mCurrentLocation: Location? = null
    private val mLocationCallback =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
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
        val locationSettingsRequest =
            LocationSettingsRequest
                .Builder()
                .addLocationRequest(mLocationRequest)
                .setAlwaysShow(true)
                .build()
        settingsClient
            .checkLocationSettings(locationSettingsRequest)
            .addOnSuccessListener {
                mListener?.onLocationRequestStart()
                requestUpdates()
                restartLocationServiceChangeReceiver() //  if already started listening, it should be stopped before starting new
            }.addOnFailureListener { exception ->
                mListener?.onLocationRequestFailure(CommCareLocationListener.Failure.ApiException(exception))
            }
    }

    override fun stop() {
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
    }

    override fun getLocation(): Location? = mCurrentLocation

    override fun destroy() {
        stopLocationServiceChangeReceiver()
        mContext = null
        mListener = null
    }

    fun restartLocationServiceChangeReceiver() {
        stopLocationServiceChangeReceiver()
        startLocationServiceChangeReceiver()
    }

    fun startLocationServiceChangeReceiver() {
        val intentFilter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mContext?.registerReceiver(mLocationServiceChangeReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            mContext?.registerReceiver(mLocationServiceChangeReceiver, intentFilter)
        }
    }

    fun stopLocationServiceChangeReceiver() {
        try {
            mContext?.unregisterReceiver(mLocationServiceChangeReceiver)
        } catch (e: IllegalArgumentException) {
            // This can happen if stop is called multiple times
        }
    }

    inner class LocationChangeReceiverBroadcast : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent,
        ) {
            if (LocationManager.PROVIDERS_CHANGED_ACTION == intent.getAction()) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val locationServiceEnabled = locationServicesEnabledGlobally(lm)
                if (locationServiceEnabled) {
                    start()
                } else {
                    stop()
                }
                mListener?.onLocationServiceChange(locationServiceEnabled)
            }
        }
    }
}
