package org.commcare.google.services.analytics

import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import org.commcare.android.logging.ReportingUtils
import org.javarosa.core.services.Logger

object CCPerfMonitoring {
    // Traces
    // Measures the duration of synchronous case list loading
    const val TRACE_SYNC_ENTITY_LIST_LOADING = "sync_case_list_loading"
    const val TRACE_CASE_SEARCH_TIME = "case_search_time"

    // Attributes
    const val ATTR_NUM_CASES_LOADED = "number_of_cases_loaded"
    const val ATTR_RESULTS_COUNT: String = "case_search_results_count"
    const val ATTR_SEARCH_QUERY_LENGTH: String = "case_search_query_length"

    fun startTracing(traceName: String): Trace? {
        try {
            val trace = FirebasePerformance.getInstance().newTrace(traceName)
            trace.putAttribute(CCAnalyticsParam.CCHQ_DOMAIN, ReportingUtils.getDomain())
            trace.putAttribute(CCAnalyticsParam.CC_APP_ID, ReportingUtils.getAppId())
            trace.putAttribute(CCAnalyticsParam.CC_APP_NAME, ReportingUtils.getAppName())
            trace.putAttribute(CCAnalyticsParam.USERNAME, ReportingUtils.getUser())
            trace.start()
            return trace
        } catch (exception: Exception) {
            Logger.exception("Error starting perf trace: $traceName", exception)
        }
        return null
    }

    fun stopTracing(trace: Trace, attrs: MutableMap<String, String>?) {
        try {
            attrs?.forEach { (key, value) -> trace.putAttribute(key, value) }
            trace.stop()
        } catch (exception: Exception) {
            Logger.exception("Error stopping perf trace: ${trace.name}", exception)
        }
    }
}
