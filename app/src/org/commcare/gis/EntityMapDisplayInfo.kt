package org.commcare.gis

import com.google.android.gms.maps.model.LatLng

data class EntityMapDisplayInfo(
    val location: LatLng? = null,
    val boundary: Array<LatLng>? = null,
    val boundaryColorHex: Int? = null,
    val points: Array<LatLng>? = null,
    val pointColorsHex: Array<Int>? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EntityMapDisplayInfo

        if (location != other.location) return false
        if (boundary != null) {
            if (other.boundary == null) return false
            if (!boundary.contentEquals(other.boundary)) return false
        } else if (other.boundary != null) return false
        if (boundaryColorHex != other.boundaryColorHex) return false
        if (points != null) {
            if (other.points == null) return false
            if (!points.contentEquals(other.points)) return false
        } else if (other.points != null) return false
        if (pointColorsHex != null) {
            if (other.pointColorsHex == null) return false
            if (!pointColorsHex.contentEquals(other.pointColorsHex)) return false
        } else if (other.pointColorsHex != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = location?.hashCode() ?: 0
        result = 31 * result + (boundary?.contentHashCode() ?: 0)
        result = 31 * result + (boundaryColorHex ?: 0)
        result = 31 * result + (points?.contentHashCode() ?: 0)
        result = 31 * result + (pointColorsHex?.contentHashCode() ?: 0)
        return result
    }
}
