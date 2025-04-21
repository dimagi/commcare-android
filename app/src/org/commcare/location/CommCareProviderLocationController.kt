package org.commcare.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import org.commcare.utils.GeoUtils

/**
 * @author $|-|!˅@M
 */
class CommCareProviderLocationController(private var mContext: Context?,
                                         private var mListener: CommCareLocationListener?): CommCareLocationController {

    private val mLocationManager = mContext?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var mCurrentLocation: Location? = null
    private var mProviders = GeoUtils.evaluateProviders(mLocationManager)
    private val mReceiver = ProviderChangedReceiver()
    private var mLocationRequestStarted = false
    private val mLocationListener = object: LocationListener {
        override fun onLocationChanged(location: Location) {
            location ?: return
            mCurrentLocation = location
            mListener?.onLocationResult(mCurrentLocation!!)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            //This callback will never be invoked.
        }

        override fun onProviderEnabled(provider: String) {

        }

        override fun onProviderDisabled(provider: String) {

        }

    }

    override fun start() {
        if (!isLocationPermissionGranted(mContext)) {
            mListener?.missingPermissions()
            return
        }
        val intentFilter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mContext?.registerReceiver(mReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            mContext?.registerReceiver(mReceiver, intentFilter)
        }
        checkProviderAndRequestLocation()
    }

    override fun stop() {
        mLocationRequestStarted = false
        mLocationManager.removeUpdates(mLocationListener)
        try {
            mContext?.unregisterReceiver(mReceiver)
        } catch (e: IllegalArgumentException) {
            // This can happen if stop is called multiple times.
            e.printStackTrace()
        }
    }

    override fun getLocation(): Location? {
        return mCurrentLocation
    }

    private fun checkProviderAndRequestLocation() {
        mProviders = GeoUtils.evaluateProviders(mLocationManager)
        if (mProviders.isEmpty()) {
            mListener?.onLocationRequestFailure(CommCareLocationListener.Failure.NoProvider)
            return
        }
        startLocationRequest()
    }

    private fun startLocationRequest() {
        if (mLocationRequestStarted) {
            // We've already started location request so no need to request again!
            return
        }
        mLocationRequestStarted = true
        mListener?.onLocationRequestStart()
        for (provider in mProviders) {
            mLocationManager.requestLocationUpdates(provider, 0L, 0.0f, mLocationListener)
        }
    }

    override fun destroy() {
        mContext = null
        mListener = null
    }

    inner class ProviderChangedReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            checkProviderAndRequestLocation()
        }
    }

}