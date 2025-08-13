package org.commcare.google.services.analytics

import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace

object CCPerfMonitoring {
    // Traces
    // Measures the duration of synchronous case list loading
    const val SYNC_ENTITY_LIST_LOADING = "sync_case_list_loading"

    // Attributes
    const val NUM_CASES_LOADED = "number_of_cases_loaded"

    fun createTrace(traceName: String): Trace? {
        try{
            return FirebasePerformance.getInstance().newTrace(traceName)
        } catch (ignored: Exception) { }
        return null;
    }
}