package org.commcare.tasks

import org.commcare.CommCareApplication
import org.commcare.resources.model.InstallRequestSource
import org.commcare.utils.SyncDetailCalculations.getDaysBetweenJavaDatetimes
import java.util.*

object RequestStats {
    @JvmStatic
    fun register(requestTag: InstallRequestSource) {
        val firstAttempt = getFirstRequestAttempt(requestTag);
        if (firstAttempt == -1L) {
            // this is the first Attempt
            CommCareApplication.instance().currentApp.appPreferences
                    .edit()
                    .putLong(getFirstRequestAttemptKey(requestTag), Date().time)
                    .apply()
        }
    }

    @JvmStatic
    fun getRequestAge(requestTag: InstallRequestSource): RequestAge {
        val firstAttempt = getFirstRequestAttempt(requestTag)
        return when (if (firstAttempt == -1L) 0 else getDaysBetweenJavaDatetimes(Date(firstAttempt), Date())) {
            in 0..10 -> RequestAge.LT_10
            in 10..30 -> RequestAge.LT_30
            in 30..60 -> RequestAge.LT_60
            in 60..90 -> RequestAge.LT_90
            in 90..120 -> RequestAge.LT_120
            in 120..150 -> RequestAge.LT_150
            else -> RequestAge.GT_150
        }
    }

    @JvmStatic
    fun markSuccess(requestTag: InstallRequestSource) {
        CommCareApplication.instance().currentApp.appPreferences
                .edit()
                .remove(getFirstRequestAttemptKey(requestTag))
                .apply()
    }

    private fun getFirstRequestAttempt(requestTag: InstallRequestSource): Long {
        val appPrefrences = CommCareApplication.instance().currentApp.appPreferences
        return appPrefrences.getLong(getFirstRequestAttemptKey(requestTag), -1L)
    }

    private fun getFirstRequestAttemptKey(requestTag: InstallRequestSource): String {
        return "first_request_attempt_$requestTag"
    }

    enum class RequestAge {
        LT_10, LT_30, LT_60, LT_90, LT_120, LT_150, GT_150
    }
}