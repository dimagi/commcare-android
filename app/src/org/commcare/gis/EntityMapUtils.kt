package org.commcare.gis

import androidx.core.graphics.toColorInt
import com.google.android.gms.maps.model.LatLng
import org.commcare.CommCareApplication
import org.commcare.activities.EntitySelectActivity
import org.commcare.cases.entity.Entity
import org.commcare.cases.entity.NodeEntityFactory
import org.commcare.suite.model.Detail
import org.commcare.suite.model.EntityDatum
import org.commcare.utils.AndroidInstanceInitializer
import org.javarosa.core.model.data.GeoPointData
import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.services.Logger
import java.util.Vector
import javax.annotation.Nullable

object EntityMapUtils {

    // Field template form name constants
    private const val TEMPLATE_FORM_ADDRESS = "address"
    private const val TEMPLATE_FORM_GEO_BOUNDARY = "cc_geo_boundary"
    private const val TEMPLATE_FORM_GEO_BOUNDARY_COLOR = "cc_geo_boundary_color"
    private const val TEMPLATE_FORM_GEO_POINTS = "cc_geo_points"
    private const val TEMPLATE_FORM_GEO_POINTS_COLORS = "cc_geo_points_colors"

    @JvmStatic
    fun getEntityLocation(entity: Entity<TreeReference>, detail: Detail, fieldIndex: Int): LatLng? {
        if (TEMPLATE_FORM_ADDRESS == detail.templateForms[fieldIndex]) {
            val address = entity.getFieldString(fieldIndex).trim { it <= ' ' }
            return getLatLngFromAddress(address)
        }
        return null
    }

    @Nullable
    private fun getLatLngFromAddress(address: String): LatLng? {
        try {
            if (!address.contentEquals("")) {
                val data = GeoPointData().cast(UncastData(address))
                return LatLng(data.latitude, data.longitude)
            }
        } catch (ignored: IllegalArgumentException) {
        }
        return null
    }

    @JvmStatic
    fun getEntityBoundary(
        entity: Entity<TreeReference>,
        detail: Detail,
        fieldIndex: Int
    ): Array<LatLng>? {
        if (TEMPLATE_FORM_GEO_BOUNDARY == detail.templateForms[fieldIndex]) {
            val boundaryString = entity.getFieldString(fieldIndex).trim { it <= ' ' }
            return parseBoundaryFromString(boundaryString)
        }
        return null
    }

    @JvmStatic
    fun getEntityBoundaryColor(
        entity: Entity<TreeReference>,
        detail: Detail,
        fieldIndex: Int
    ): Int? {
        if (TEMPLATE_FORM_GEO_BOUNDARY_COLOR == detail.templateForms[fieldIndex]) {
            val colorString = entity.getFieldString(fieldIndex).trim { it <= ' ' }
            return parseHexColor(colorString)
        }
        return null
    }

    @JvmStatic
    fun getEntityPoints(
        entity: Entity<TreeReference>,
        detail: Detail,
        fieldIndex: Int
    ): Array<LatLng>? {
        if (TEMPLATE_FORM_GEO_POINTS == detail.templateForms[fieldIndex]) {
            val boundaryString = entity.getFieldString(fieldIndex).trim { it <= ' ' }
            return parseBoundaryFromString(boundaryString)
        }
        return null
    }

    @JvmStatic
    fun getEntityPointColors(
        entity: Entity<TreeReference>,
        detail: Detail,
        fieldIndex: Int
    ): Array<Int>? {
        if (TEMPLATE_FORM_GEO_POINTS_COLORS == detail.templateForms[fieldIndex]) {
            val colorsString = entity.getFieldString(fieldIndex).trim { it <= ' ' }
            return parseHexColorList(colorsString)
        }
        return null
    }

    @Nullable
    private fun parseBoundaryFromString(boundaryString: String): Array<LatLng>? {
        if (boundaryString.isEmpty()) {
            return null
        }

        try {
            val parts = boundaryString.trim().split("\\s+".toRegex())

            // Must have even number of values (pairs of lat/lng)
//            if (parts.size < 2 || parts.size % 2 != 0) {
//                return null
//            }

            val points = mutableListOf<LatLng>()

            for (i in 0 until parts.size step 2) {
                val lat = parts[i].toDouble()
                val lng = parts[i + 1].toDouble()
                points.add(LatLng(lat, lng))
            }

            return if (points.isNotEmpty()) {
                points.toTypedArray()
            } else {
                null
            }
        } catch (e: NumberFormatException) {
            Logger.exception("Error parsing entity boundary for map display", e)
            return null
        }
    }

    @Nullable
    private fun parseHexColor(colorString: String): Int? {
        if (colorString.isEmpty()) {
            return null
        }

        try {
            return colorString.toColorInt()
        } catch (e: IllegalArgumentException) {
            Logger.exception("Error parsing hex color: $colorString", e)
            return null
        }
    }

    @Nullable
    private fun parseHexColorList(colorsString: String): Array<Int>? {
        if (colorsString.isEmpty()) {
            return null
        }

        try {
            // Split by either spaces or commas (or both), handling multiple delimiters
            val parts = colorsString.trim()
                .split("[\\s,]+".toRegex())
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val colors = mutableListOf<Int>()
            for (part in parts) {
                val color = parseHexColor(part)
                if (color != null) {
                    colors.add(color)
                }
            }

            return if (colors.isNotEmpty()) {
                colors.toTypedArray()
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.exception("Error parsing hex color list for map display", e)
            return null
        }
    }

    @JvmStatic
    fun getNeededEntityDatum(): EntityDatum? {
        val datum = CommCareApplication.instance().currentSession.neededDatum
        return datum as? EntityDatum
    }

    @JvmStatic
    fun getEntities(detail: Detail, nodeset: TreeReference): Vector<Entity<TreeReference>> {
        val session = CommCareApplication.instance().currentSession
        val evaluationContext = session.getEvaluationContext(AndroidInstanceInitializer(session))
        evaluationContext.addFunctionHandler(EntitySelectActivity.getHereFunctionHandler())

        val references = evaluationContext.expandReference(nodeset)
        val entities = Vector<Entity<TreeReference>>()

        val factory = NodeEntityFactory(detail, evaluationContext)
        for (ref in references) {
            entities.add(factory.getEntity(ref))
        }
        return entities
    }

    @JvmStatic
    @Nullable
    fun getDisplayInfoForEntity(
        entity: Entity<TreeReference>,
        detail: Detail
    ): EntityMapDisplayInfo? {
        var location: LatLng? = null
        var boundary: Array<LatLng>? = null
        var boundaryColorHex: Int? = null
        var points: Array<LatLng>? = null
        var pointColorsHex: Array<Int>? = null

        for (i in 0 until detail.headerForms.size) {
            if (location == null) {
                location = getEntityLocation(entity, detail, i)
            }

            if (boundary == null) {
                boundary = getEntityBoundary(entity, detail, i)
            }

            if (boundaryColorHex == null) {
                boundaryColorHex = getEntityBoundaryColor(entity, detail, i)
            }

            if (points == null) {
                points = getEntityPoints(entity, detail, i)
            }

            if (pointColorsHex == null) {
                pointColorsHex = getEntityPointColors(entity, detail, i)
            }
        }

        if (boundary != null && boundaryColorHex == null) {
            boundary = null
            Logger.exception(
                "Entity has boundary but no boundary color specified for map display",
                RuntimeException()
            )
        }

        if (points != null) {
            if (pointColorsHex == null) {
                points = null
                Logger.exception(
                    "Entity has points but no point colors specified for map display",
                    RuntimeException()
                )
            } else if (points.size != pointColorsHex.size) {
                points = null
                Logger.exception(
                    "Entity has points and point colors with different lengths for map display",
                    RuntimeException()
                )
            }
        }

        return if (location != null || boundary != null || points != null) {
            EntityMapDisplayInfo(
                location = location,
                boundary = boundary,
                boundaryColorHex = boundaryColorHex,
                points = points,
                pointColorsHex = pointColorsHex
            )
        } else {
            null
        }
    }
}