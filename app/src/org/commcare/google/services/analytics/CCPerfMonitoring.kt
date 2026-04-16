package org.commcare.google.services.analytics

import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import org.commcare.android.logging.ReportingUtils
import org.javarosa.core.services.Logger

object CCPerfMonitoring {
    // Traces
    // Measures the duration of synchronous case list loading
    const val TRACE_SYNC_ENTITY_LIST_LOADING = "sync_case_list_loading"
    const val TRACE_APP_SYNC_DURATION = "app_sync_duration"
    const val TRACE_CASE_SEARCH_TIME = "case_search_time"
    const val TRACE_FORM_LOADING_TIME = "form_loading_time"
    const val TRACE_FILE_ENCRYPTION_TIME = "file_encryption_time"
    const val TRACE_ENTITY_MAP_READY_TIME = "entity_map_ready_time"
    const val TRACE_ENTITY_MAP_LOADED_TIME = "entity_map_loaded_time"

    // Attributes
    const val ATTR_NUM_CASES_LOADED = "number_of_cases_loaded"
    const val ATTR_RESULTS_COUNT = "case_search_results_count"
    const val ATTR_SEARCH_QUERY_LENGTH = "case_search_query_length"
    const val ATTR_SYNC_SUCESS = "sync_success"
    const val ATTR_SYNC_ITEMS_COUNT = "sync_items_count"
    const val ATTR_SYNC_TYPE = "sync_type"
    const val ATTR_FORM_NAME = "form_name"
    const val ATTR_FORM_XMLNS = "form_xmlns"
    const val ATTR_FILE_SIZE_BYTES = "file_size_bytes"
    const val ATTR_FILE_TYPE = "file_type"
    const val ATTR_MAP_MARKERS = "num_markers"
    const val ATTR_MAP_POLYGONS = "num_polygons"
    const val ATTR_MAP_GEO_POINTS = "num_geo_points"
    const val ATTR_FILE_KEYSTORE_ENCRYPTED = "file_keystore_encrypted"
    const val ATTR_CC_APP = "cc_app"

    fun startTracing(traceName: String): Trace? {
        try {
            val trace = FirebasePerformance.getInstance().newTrace(traceName)
            trace.putAttribute(ATTR_CC_APP,
                ReportingUtils.getDomain() + "|" + ReportingUtils.getAppId() + "|" + ReportingUtils.getAppName()
            )
            trace.putAttribute(CCAnalyticsParam.USERNAME, ReportingUtils.getUser())
            trace.start()
            return trace
        } catch (exception: Exception) {
            Logger.exception("Error starting perf trace: $traceName", exception)
        }
        return null
    }

    fun stopTracing(trace: Trace?, attrs: MutableMap<String, String>?) {
        if (trace == null) return
        try {
            attrs?.forEach { (key, value) -> trace.putAttribute(key, value) }
            trace.stop()
        } catch (exception: Exception) {
            Logger.exception("Error stopping perf trace: ${trace.name}", exception)
        }
    }

    fun stopFileEncryptionTracing(
        trace: Trace?,
        fileSizeBytes: Long,
        fileExtension: String,
        keystoreEncrypted: Boolean
    ) {
        try {
            val attrs: MutableMap<String, String> = HashMap()
            attrs[ATTR_FILE_SIZE_BYTES] = fileSizeBytes.toString()
            attrs[ATTR_FILE_TYPE] = fileExtension
            attrs[ATTR_FILE_KEYSTORE_ENCRYPTED] = keystoreEncrypted.toString()
            stopTracing(trace, attrs)
        } catch (e: java.lang.Exception) {
            Logger.exception("Failed to stop tracing: $TRACE_FILE_ENCRYPTION_TIME", e)
        }
    }

}
