package org.commcare.gis

import com.mapbox.geojson.Polygon
import com.mapbox.mapboxsdk.geometry.LatLng
import io.ona.kujaku.location.clients.SphericalUtil

/**
 * Utility class to calculate polygon sphrical properties like area and perimeter
 */
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
        return SphericalUtil.computeLength(latLngs)
    }

    fun getArea(): Double {
        return SphericalUtil.computeArea(latLngs)
    }

    override fun toString(): String {
        var result = ""
        for (latLng in latLngs) {
            result += String.format("%1s,%2s \n", latLng.latitude, latLng.longitude)
        }

        return result
    }

}