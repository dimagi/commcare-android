package org.commcare.gis

import com.google.gson.JsonObject
import com.mapbox.mapboxsdk.geometry.LatLng

// Supporting model for Adding entities to the Map
data class MapEntity(val location: LatLng, val properties : JsonObject, val iconPath : String)