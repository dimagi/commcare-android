package org.commcare.gis

import com.google.android.gms.maps.model.LatLng

data class EntityMapDisplayInfo(
    val location: LatLng? = null,
    val boundary: Array<LatLng>? = null,
    val boundaryColorHex: Int? = null,
    val points: Array<LatLng>? = null,
    val pointColorsHex: Array<Int>? = null
)
