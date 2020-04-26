package org.commcare.gis

import android.os.Bundle
import android.text.TextUtils
import androidx.appcompat.content.res.AppCompatResources
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions
import io.ona.kujaku.KujakuLibrary
import kotlinx.android.synthetic.main.activity_entity_kujaku_map.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commcare.CommCareApplication
import org.commcare.activities.CommCareActivity
import org.commcare.cases.entity.Entity
import org.commcare.dalvik.BuildConfig
import org.commcare.dalvik.R
import org.commcare.gis.EntityMapUtils.getEntities
import org.commcare.gis.EntityMapUtils.getEntityLocation
import org.commcare.gis.EntityMapUtils.getNeededEntityDatum
import org.commcare.suite.model.Detail
import org.commcare.utils.MediaUtil
import org.commcare.views.EntityView
import org.javarosa.core.model.data.GeoPointData
import org.javarosa.core.model.instance.TreeReference

class EntityKujakuMapActivity : CommCareActivity<EntityKujakuMapActivity>() {

    companion object {
        const val DEFAULT_CASE_ICON = "default_case_icon"
    }

    private lateinit var map: MapboxMap
    private var iconset = java.util.HashSet<String>()
    private lateinit var mapEntities: java.util.ArrayList<MapEntity>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KujakuLibrary.init(this)
        Mapbox.getInstance(this, BuildConfig.MAPBOX_SDK_API_KEY)
        setContentView(R.layout.activity_entity_kujaku_map)
        mapView.onCreate(savedInstanceState)
        initMap()
    }

    private fun initMap() {
        mapView.showCurrentLocationBtn(true)
        mapView.getMapAsync { mapBoxMap ->
            map = mapBoxMap
            GlobalScope.launch(Dispatchers.Default) {
                initEntityData()
                withContext(Dispatchers.Main) {
                    map.setStyle(buildStyle()) { loadedStyle ->
                        addDataToMap(loadedStyle)
                    }
                }
            }
        }
    }

    private fun initEntityData() {
        val selectDatum = getNeededEntityDatum()
        if (selectDatum != null) {
            val detail = CommCareApplication.instance().currentSession
                    .getDetail(selectDatum.shortDetail)
            val entities = getEntities(detail, selectDatum.nodeset)
            val headers = EntityMapUtils.getDetailHeaders(detail)

            iconset = HashSet()
            mapEntities = ArrayList(entities.size)

            entities.map { entity -> toMapEntity(entity, detail, headers) }
                    .map { mapEntity ->
                        if (mapEntity != null) {
                            mapEntities.add(mapEntity)
                            iconset.add(mapEntity.iconPath)
                        }
                    }
        }
    }


    private fun toMapEntity(entity: Entity<TreeReference>, detail: Detail, headers: Array<String?>): MapEntity? {
        var location: GeoPointData? = null
        val visibleProperties = JsonObject()
        var iconPath = ""

        for (i in detail.fields.indices) {
            when (detail.templateForms[i]) {
                EntityView.FORM_ADDRESS -> location = getEntityLocation(entity, detail, i)
                EntityView.FORM_IMAGE -> iconPath = entity.getFieldString(i)
                !in EntityView.FORM_AUDIO, EntityView.FORM_CALLLOUT, EntityView.FORM_GRAPH -> {
                    if (headers[i] != "") {
                        visibleProperties.add(headers[i], JsonPrimitive(entity.getFieldString(i)))
                    }
                }
            }
        }

        if (location != null) {
            return MapEntity(LatLng(location.latitude, location.longitude), visibleProperties, iconPath)
        }
        return null
    }


    private fun buildStyle(): Style.Builder {
        val styleBuilder = Style.Builder().fromUri(Style.MAPBOX_STREETS)
                .withImage(DEFAULT_CASE_ICON, AppCompatResources.getDrawable(this, R.drawable.ic_place)!!)
        for (iconPath in iconset) {
            val bitmap = MediaUtil.inflateDisplayImage(this, iconPath, 50, 50, false)
            if (bitmap != null) {
                styleBuilder.withImage(iconPath, bitmap)
            }
        }
        return styleBuilder
    }

    private fun addDataToMap(loadedStyle: Style) {
        val symbolManager = SymbolManager(mapView, map, loadedStyle)
        symbolManager.iconAllowOverlap = true
        symbolManager.iconTranslate = arrayOf(-4f, 5f)

        symbolManager.addClickListener { symbol ->
            showEntityInfo(symbol.id)
        }

        for (mapEntity in mapEntities) {
            var symbolOptions: SymbolOptions = SymbolOptions()
                    .withLatLng(LatLng(mapEntity.location.latitude, mapEntity.location.longitude))
            if (TextUtils.isEmpty(mapEntity.iconPath)) {
                symbolOptions = symbolOptions.withIconImage(DEFAULT_CASE_ICON)
            } else {
                symbolOptions = symbolOptions.withIconImage(mapEntity.iconPath)
            }

            symbolManager.create(symbolOptions)
        }
    }

    private fun showEntityInfo(id: Long) {
        if (id < mapEntities.size) {
            val mapEntity = mapEntities[id.toInt()]
        }
    }

    override fun shouldShowBreadcrumbBar(): Boolean {
        return false
    }


    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}
