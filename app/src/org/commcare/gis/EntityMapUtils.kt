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
import org.javarosa.core.model.utils.GeoPointUtils
import org.javarosa.core.model.utils.PolygonUtils
import org.javarosa.core.services.Logger
import java.util.Vector
import javax.annotation.Nullable

object EntityMapUtils {

    // Field template form name constants
    private const val TEMPLATE_FORM_ADDRESS = "address"
    private const val TEMPLATE_FORM_GEO_BOUNDARY = "geo_boundary"
    private const val TEMPLATE_FORM_GEO_BOUNDARY_COLOR = "geo_boundary_color_hex"
    private const val TEMPLATE_FORM_GEO_POINTS = "geo_points"
    private const val TEMPLATE_FORM_GEO_POINTS_COLORS = "geo_points_colors_hex"

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
        if (!address.contentEquals("")) {
            val data = GeoPointData().cast(UncastData(address))
            return LatLng(data.latitude, data.longitude)
        }

        return null
    }

    @JvmStatic
    fun getEntityBoundary(
        entity: Entity<TreeReference>,
        detail: Detail,
        fieldIndex: Int
    ): List<LatLng>? {
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
    ): List<LatLng>? {
        if (TEMPLATE_FORM_GEO_POINTS == detail.templateForms[fieldIndex]) {
            val pointListString = entity.getFieldString(fieldIndex).trim { it <= ' ' }
            return parsePointListFromString(pointListString)
        }
        return null
    }

    @JvmStatic
    fun getEntityPointColors(
        entity: Entity<TreeReference>,
        detail: Detail,
        fieldIndex: Int
    ): List<Int>? {
        if (TEMPLATE_FORM_GEO_POINTS_COLORS == detail.templateForms[fieldIndex]) {
            val colorsString = entity.getFieldString(fieldIndex).trim { it <= ' ' }
            return parseHexColorList(colorsString)
        }
        return null
    }

    @Nullable
    private fun parseBoundaryFromString(boundaryString: String): List<LatLng>? {
        val parts = boundaryString.trim().split("\\s+".toRegex())
        val polygon = PolygonUtils.createPolygon(parts)
        return polygon.map { LatLng(it.latitude, it.longitude) }
            .toMutableList()
    }

    @Nullable
    private fun parsePointListFromString(pointListString: String): List<LatLng>? {
        if (pointListString.isEmpty()) {
            return null
        }
        val parts = pointListString.trim().split("\\s+".toRegex())
        val polygon = GeoPointUtils.createPointList(parts)
        return polygon.map { LatLng(it.latitude, it.longitude) }
            .toMutableList()
    }

    @Nullable
    private fun parseHexColor(colorString: String): Int? {
        return colorString.toColorInt()
    }

    @Nullable
    private fun parseHexColorList(colorsString: String): List<Int>? {
        if (colorsString.isEmpty()) {
            return null
        }

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
            colors.toMutableList()
        } else {
            null
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
        var boundary: List<LatLng>? = null
        var boundaryColorHex: Int? = null
        var points: List<LatLng>? = null
        var pointColorsHex: List<Int>? = null
        var errorEncountered = false

        for (i in 0 until detail.headerForms.size) {
            if (location == null) {
                try {
                    location = getEntityLocation(entity, detail, i)
                } catch (e: IllegalArgumentException) {
                    Logger.exception("Error parsing entity location for map display", e)
                    errorEncountered = true
                }
            }

            if (boundary == null) {
                try {
                    boundary = getEntityBoundary(entity, detail, i)
                } catch (e: IllegalArgumentException) {
                    Logger.exception("Error parsing entity boundary for map display", e)
                    errorEncountered = true
                }
            }

            if (boundaryColorHex == null) {
                try {
                    boundaryColorHex = getEntityBoundaryColor(entity, detail, i)
                } catch (e: IllegalArgumentException) {
                    Logger.exception("Error parsing entity boundary color for map display", e)
                    errorEncountered = true
                }
            }

            if (points == null) {
                try {
                    points = getEntityPoints(entity, detail, i)
                } catch (e: IllegalArgumentException) {
                    Logger.exception("Error parsing entity points for map display", e)
                    errorEncountered = true
                }
            }

            if (pointColorsHex == null) {
                try {
                    pointColorsHex = getEntityPointColors(entity, detail, i)
                } catch (e: IllegalArgumentException) {
                    Logger.exception("Error parsing entity point colors for map display", e)
                    errorEncountered = true
                }
            }
        }

        if (pointColorsHex != null && points != null && pointColorsHex.size != points.size) {
            Logger.exception(
                "Mismatched point colors and points for entity map display",
                IllegalArgumentException(
                    "Number of point colors (${pointColorsHex.size}) does not match number of points (${points.size})"
                )
            )
            pointColorsHex = null
            errorEncountered = true
        }

        return if (errorEncountered || location != null || boundary != null || points != null) {
            EntityMapDisplayInfo(
                errorEncountered = errorEncountered,
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