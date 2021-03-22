package org.commcare.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import org.commcare.CommCareApplication

/**
 * @author $|-|!˅@M
 */
interface CommCareLocationController {
    fun start()
    fun stop()
    fun getLocation(): Location?
    fun destroy()
}

fun CommCareLocationController.isLocationPermissionGranted(mContext: Context?): Boolean {
    val context = mContext ?: CommCareApplication.instance()
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
