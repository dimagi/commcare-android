package org.commcare.gis

import com.google.android.gms.maps.model.LatLng

data class EntityMapDisplayInfo(
    val location: LatLng? = null,
    val boundary: List<LatLng>? = null,
    val boundaryColorHex: Int? = null,
    val points: List<LatLng>? = null,
    val pointColorsHex: List<Int>? = null
)
