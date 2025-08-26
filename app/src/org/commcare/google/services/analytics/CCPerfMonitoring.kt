package org.commcare.google.services.analytics

import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import org.commcare.android.logging.ReportingUtils

object CCPerfMonitoring {
    // Traces
    // Measures the duration of synchronous case list loading
    const val TRACE_SYNC_ENTITY_LIST_LOADING = "sync_case_list_loading"

    // Attributes
    const val ATTR_NUM_CASES_LOADED = "number_of_cases_loaded"

    fun startTracing (traceName: String): Trace? {
        try {
            val trace = FirebasePerformance.getInstance().newTrace(traceName)
            trace.putAttribute(CCAnalyticsParam.CCHQ_DOMAIN, ReportingUtils.getDomain())
            trace.putAttribute(CCAnalyticsParam.CC_APP_ID, ReportingUtils.getAppId())
            trace.putAttribute(CCAnalyticsParam.CC_APP_NAME, ReportingUtils.getAppName())
            trace.putAttribute(CCAnalyticsParam.USERNAME, ReportingUtils.getUser())
            trace.start();
            return trace
        } catch (ignored: Exception) { }
        return null
    }

    fun stopTracing(trace: Trace?) {
        try {
            trace?.stop();
        } catch (ignored: Exception) { }
    }
}