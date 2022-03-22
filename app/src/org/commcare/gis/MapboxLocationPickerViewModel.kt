package org.commcare.gis

import android.app.Application
import android.location.Geocoder
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mapbox.mapboxsdk.geometry.LatLng
import java.lang.IllegalArgumentException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commcare.dalvik.R
import org.commcare.utils.GeoUtils
import org.commcare.utils.StringUtils
import org.javarosa.core.services.Logger

/**
 * @author $|-|!˅@M
 */
class MapboxLocationPickerViewModel(application: Application) : AndroidViewModel(application) {

    val placeName = MutableLiveData<String>()
    private var location = Location("XForm")

    fun reverseGeocode(latLng: LatLng) {
        viewModelScope.launch(Dispatchers.IO) {
            var addressString = ""
            val geocoder = Geocoder(getApplication(), Locale.getDefault())
            val geoAddress = try {
                geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            } catch (e: Exception) {
                if (e is IllegalArgumentException) {
                    Logger.exception("Error while fetching location from geocoder", e)
                }
                null
            }
            geoAddress?.let {
                if (it.isNotEmpty()) {
                    val addr = it[0]
                    val builder = StringBuilder()
                    for (i in 0..addr.maxAddressLineIndex) {
                        builder.append(addr.getAddressLine(i))
                                .append("\n")
                    }
                    addressString = builder.toString()
                }
            }
            if (addressString.isEmpty()) {
                val builder = StringBuilder()
                builder.append(StringUtils.getStringSpannableRobust(getApplication(), R.string.latitude))
                        .append(": ")
                        .append(GeoUtils.formatGps(latLng.latitude, "lat"))
                        .append("\n")
                        .append(StringUtils.getStringSpannableRobust(getApplication(), R.string.longitude))
                        .append(": ")
                        .append(GeoUtils.formatGps(latLng.longitude, "lon"))
                        .append("\n")
                        .append(StringUtils.getStringSpannableRobust(getApplication(), R.string.altitude))
                        .append(": ")
                        .append(String.format("%.2f", latLng.altitude))
                addressString = builder.toString()
            }
            withContext(Dispatchers.Main) {
                location.latitude = latLng.latitude
                location.longitude = latLng.longitude
                location.altitude = latLng.altitude
                location.accuracy = 10.0f
                placeName.postValue(addressString)
            }
        }
    }

    fun getLocation() = location
}
