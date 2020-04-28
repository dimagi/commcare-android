package org.commcare.gis

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import com.mapbox.geojson.Polygon

class AreaCalculator(val polygon: Polygon) {

    private var latLngs = ArrayList<LatLng>()

    init {
        kotlin.runCatching {
            polygon.coordinates().first().map { point ->
                LatLng(point.latitude(), point.longitude())
            }.toCollection(latLngs)
        }.onFailure { e ->
            if (e !is NoSuchElementException) {
                throw e
            }
        }
    }

    fun getPerimeter(): Double {
        return if (latLngs.size > 3) {
            SphericalUtil.computeLength(latLngs)
        } else {
            0.0
        }
    }

    fun getArea(): Double {
        return if (latLngs.size > 3) {
            SphericalUtil.computeArea(latLngs)
        } else {
            0.0
        }
    }


    override fun toString(): String {
        var result = ""
        for (latLng in latLngs) {
            result += String.format("%1s,%2s \n",latLng.latitude,latLng.longitude)
        }

        return result
    }

}