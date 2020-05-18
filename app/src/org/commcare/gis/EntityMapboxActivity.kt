package org.commcare.gis

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mapbox.geojson.Feature
import com.mapbox.mapboxsdk.annotations.BubbleLayout
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.FillOptions
import com.mapbox.mapboxsdk.plugins.annotation.Symbol
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import io.ona.kujaku.manager.AnnotationRepositoryManager
import kotlinx.android.synthetic.main.activity_entity_mapbox.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commcare.CommCareApplication
import org.commcare.cases.entity.Entity
import org.commcare.dalvik.R
import org.commcare.gis.EntityMapUtils.getEntities
import org.commcare.gis.EntityMapUtils.getEntityLocation
import org.commcare.gis.EntityMapUtils.getNeededEntityDatum
import org.commcare.gis.EntityMapUtils.parseBoundaryCoords
import org.commcare.suite.model.Detail
import org.commcare.suite.model.DisplayUnit
import org.commcare.utils.MediaUtil
import org.commcare.views.EntityView
import org.javarosa.core.model.data.GeoPointData
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.services.Logger

class EntityMapboxActivity : BaseMapboxActivity() {

    companion object {

        private const val DEFAULT_CASE_ICON = "default_case_icon"
        private const val ENTITY_INFO_LAYER_ID = "entity_info_layer"
        private const val GEOJSON_SOURCE_ID = "geojson-source"
        private const val INFO_IMAGE_ID = "info-image"

        private const val MAX_ICON_SIZE = 60


        private fun viewToBitmap(view: View): Bitmap {
            val measureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            view.measure(measureSpec, measureSpec)
            val measuredWidth = view.measuredWidth
            val measuredHeight = view.measuredHeight
            view.layout(0, 0, measuredWidth, measuredHeight)
            val bitmap = Bitmap.createBitmap(measuredWidth, measuredHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.TRANSPARENT)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            return bitmap
        }
    }


    private var detail: Detail? = null
    private lateinit var source: GeoJsonSource
    private var iconset = java.util.HashSet<String>()
    private lateinit var mapEntities: java.util.ArrayList<MapEntity>


    override fun getMapLayout(): Int {
        return R.layout.activity_entity_mapbox
    }


    override fun onMapLoaded() {
        jobs.add(GlobalScope.launch(Dispatchers.Default) {
            initEntityData()
            withContext(Dispatchers.Main) {
                map.setStyle(buildStyle()) { loadedStyle ->
                    addOvelaysOnMap(loadedStyle)
                    addEntitiesOnMap(loadedStyle)
                }
            }
        })
    }

    private fun initEntityData() {
        val selectDatum = getNeededEntityDatum()
        if (selectDatum != null) {
            detail = CommCareApplication.instance().currentSession
                    .getDetail(selectDatum.shortDetail)
            val entities = getEntities(detail!!, selectDatum.nodeset)
            val headers = EntityMapUtils.getDetailHeaders(detail!!)

            iconset = HashSet()
            mapEntities = ArrayList(entities.size)

            entities.map { entity -> toMapEntity(entity, detail!!, headers) }
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
                "" -> visibleProperties.add(headers[i], JsonPrimitive(entity.getFieldString(i)))
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
        iconset.map { iconset -> addIconToStyle(styleBuilder, iconset) }
        setUpInfoWindowStyle(styleBuilder)
        return styleBuilder
    }

    private fun addIconToStyle(styleBuilder: Style.Builder, iconPath: String) {
        val bitmap = MediaUtil.inflateDisplayImage(this, iconPath,
                MAX_ICON_SIZE, MAX_ICON_SIZE, false)
        if (bitmap != null) {
            styleBuilder.withImage(iconPath, bitmap)
        }
    }

    private fun setUpInfoWindowStyle(styleBuilder: Style.Builder) {
        source = GeoJsonSource(GEOJSON_SOURCE_ID)
        styleBuilder.withSource(source)

        styleBuilder.withLayer(SymbolLayer(ENTITY_INFO_LAYER_ID, GEOJSON_SOURCE_ID)
                .withProperties(
                        PropertyFactory.iconImage(INFO_IMAGE_ID),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                        PropertyFactory.iconAllowOverlap(false),
                        PropertyFactory.iconIgnorePlacement(false),  // offset the info window to be above the marker
                        PropertyFactory.iconOffset(arrayOf(-2f, -28f))
                ))
    }

    private fun addOvelaysOnMap(loadedStyle: Style) {
        if (detail != null) {
            val global = detail!!.global
            if (global != null) {
                val geoOverlays = global.geoOverlays
                geoOverlays.forEach {
                    val coordinates = it.coordinates
                    if (coordinates != null) {
                        showOverlay(coordinates, loadedStyle)
                    }
                }
            }
        }
    }

    private fun showOverlay(coordinates: DisplayUnit, loadedStyle: Style) {
        val boundaryCoords = coordinates.evaluate().name
        kotlin.runCatching {
            parseBoundaryCoords(boundaryCoords)
        }.onFailure {
            showToast(R.string.parse_coordinates_failure)
            Logger.exception("Exception while parsing boundary coordinates ", Exception(it))
        }.onSuccess { latlngs ->
            val fillManager = AnnotationRepositoryManager.getFillManagerInstance(mapView, map, loadedStyle)
            val lists = ArrayList<List<LatLng>>()
            lists.add(latlngs)
            fillManager.create(FillOptions()
                    .withLatLngs(lists)
                    .withFillColor("#cfff95")
                    .withFillOpacity(java.lang.Float.valueOf("0.5")))
        }
    }


    private fun addEntitiesOnMap(loadedStyle: Style) {
        val symbolManager = SymbolManager(mapView, map, loadedStyle)
        symbolManager.iconAllowOverlap = true
        symbolManager.iconTranslate = arrayOf(-4f, 5f)

        symbolManager.addClickListener { symbol ->
            showEntityInfo(loadedStyle, symbol)
        }

        for (mapEntity in mapEntities) {
            var symbolOptions: SymbolOptions = SymbolOptions()
                    .withLatLng(LatLng(mapEntity.location.latitude, mapEntity.location.longitude))

            symbolOptions = if (TextUtils.isEmpty(mapEntity.iconPath)) {
                symbolOptions.withIconImage(DEFAULT_CASE_ICON)
            } else {
                symbolOptions.withIconImage(mapEntity.iconPath)
            }

            symbolManager.create(symbolOptions)
        }
    }

    private fun showEntityInfo(loadedStyle: Style, symbol: Symbol) {
        if (symbol.id < mapEntities.size) {
            val mapEntity = mapEntities[symbol.id.toInt()]

            jobs.add(GlobalScope.launch(Dispatchers.Default) {
                val bitmap = generateEntityInfoView(mapEntity)

                withContext(Dispatchers.Main) {
                    loadedStyle.addImage(INFO_IMAGE_ID, bitmap)
                    source.setGeoJson(Feature.fromGeometry(symbol.geometry, mapEntity.properties))
                }
            })
        }
    }


    private fun generateEntityInfoView(mapEntity: MapEntity): Bitmap {

        val inflater = LayoutInflater.from(this)

        val stringBuilder = SpannableStringBuilder()
        val bubbleLayout = inflater.inflate(R.layout.activity_entity_kujaku_map_info_view, null) as BubbleLayout

        for ((key, value) in mapEntity.properties.entrySet()) {
            stringBuilder.append(key)
            stringBuilder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0, key.length,
                    Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
            stringBuilder.append(String.format(": %s", value))
            stringBuilder.append(System.getProperty("line.separator"))
        }

        val propertiesListTextView = bubbleLayout.findViewById<TextView>(R.id.info_window_feature_properties_list)
        propertiesListTextView.text = stringBuilder

        val measureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        bubbleLayout.measure(measureSpec, measureSpec)
        val measuredWidth = bubbleLayout.measuredWidth.toFloat()
        bubbleLayout.arrowPosition = measuredWidth / 2 - 5
        return viewToBitmap(bubbleLayout)
    }

}
