package org.commcare.gis

import android.os.Bundle
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapboxMap
import io.ona.kujaku.KujakuLibrary
import io.ona.kujaku.views.KujakuMapView
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.commcare.activities.CommCareActivity
import org.commcare.dalvik.BuildConfig

/**
 * Base class for Mapbox based map activites
 */
abstract class BaseMapboxActivity : CommCareActivity<BaseMapboxActivity>() {

    val jobs = ArrayList<Job>()
    lateinit var map: MapboxMap

    override fun onCreate(savedInstanceState: Bundle?) {
        KujakuLibrary.init(this)
        Mapbox.getInstance(this, BuildConfig.MAPBOX_SDK_API_KEY)
        super.onCreate(savedInstanceState)

        setContentView(viewBinding.root)

        getMapView().onCreate(savedInstanceState)
        initMap()
    }

    abstract fun getMapView(): KujakuMapView

    private fun initMap() {
        getMapView().showCurrentLocationBtn(true)
        if (shouldFocusUserLocationOnLoad()) {
            getMapView().focusOnUserLocation(true)
        }
        getMapView().getMapAsync { mapBoxMap ->
            map = mapBoxMap
            onMapLoaded()
        }
    }

    open fun shouldFocusUserLocationOnLoad() = true

    abstract fun onMapLoaded()

    override fun shouldShowBreadcrumbBar(): Boolean {
        return false
    }


    override fun onResume() {
        super.onResume()
        getMapView().onResume()
    }

    override fun onStart() {
        super.onStart()
        getMapView().onStart()
    }

    override fun onStop() {
        super.onStop()
        getMapView().onStop()
    }

    override fun onPause() {
        super.onPause()
        getMapView().onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        getMapView().onDestroy()
        jobs.map { job -> job.cancel("Activity Destroyed") }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        getMapView().onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        getMapView().onLowMemory()
    }
}
