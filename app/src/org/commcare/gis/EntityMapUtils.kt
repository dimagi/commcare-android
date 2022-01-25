package org.commcare.gis

import com.mapbox.mapboxsdk.geometry.LatLng
import java.util.Vector
import javax.annotation.Nullable
import kotlin.collections.ArrayList
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

object EntityMapUtils {

    @JvmStatic
    fun getEntityLocation(entity: Entity<TreeReference>, detail: Detail, fieldIndex: Int): GeoPointData? {
        if ("address" == detail.templateForms[fieldIndex]) {
            val address = entity.getFieldString(fieldIndex).trim { it <= ' ' }
            return getLatLngFromAddress(address)
        }
        return null
    }

    @Nullable
    private fun getLatLngFromAddress(address: String): GeoPointData? {
        try {
            if (!address.contentEquals("")) {
                val data = GeoPointData().cast(UncastData(address))
                return data
            }
        } catch (ignored: IllegalArgumentException) {
        }
        return null
    }

    @JvmStatic
    fun getNeededEntityDatum(): EntityDatum? {
        val datum = CommCareApplication.instance().currentSession.neededDatum
        return if (datum is EntityDatum) {
            datum
        } else null
    }

    @JvmStatic
    fun getEntities(detail: Detail, nodeset: TreeReference): Vector<Entity<TreeReference>> {
        val session = CommCareApplication.instance().currentSession
        val evaluationContext = session.getEvaluationContext(
                AndroidInstanceInitializer(session))
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
    fun getDetailHeaders(detail: Detail): Array<String?> {
        val headers = arrayOfNulls<String>(detail.fields.size)
        for (i in headers.indices) {
            headers[i] = detail.fields[i].header.evaluate()
        }
        return headers
    }

    @JvmStatic
    fun parseBoundaryCoords(boundaryCoords: String): ArrayList<LatLng> {
        val latLngs = ArrayList<LatLng>()
        if (boundaryCoords.isNotEmpty()) {
            val list = boundaryCoords.split("\n", "\\n")
            list.filter { coord -> coord != "" }
                    .map { coord ->
                        val latLngArray = coord.split(",")
                        LatLng(latLngArray[0].toDouble(), latLngArray[1].toDouble())
                    }.toCollection(latLngs)
        }
        return latLngs
    }
}
