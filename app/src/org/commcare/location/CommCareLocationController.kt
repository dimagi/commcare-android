package org.commcare.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import javax.annotation.Nullable

/**
 * @author $|-|!˅@M
 */
interface CommCareLocationController {
    fun start()
    fun stop()
    fun getLocation(): Location?
    fun destroy()
}

fun CommCareLocationController.isLocationPermissionGranted(mContext: Context): Boolean {
    return ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
