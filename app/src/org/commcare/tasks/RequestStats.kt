package org.commcare.tasks

import org.commcare.CommCareApp
import org.commcare.CommCareApplication
import org.commcare.resources.model.InstallRequestSource
import org.commcare.utils.SyncDetailCalculations.getDaysBetweenJavaDatetimes
import org.commcare.utils.TimeProvider
import java.util.*

object RequestStats {
    @JvmStatic
    fun register(installRequestSource: InstallRequestSource) {
        register(CommCareApplication.instance().currentApp, installRequestSource)
    }

    @JvmStatic
    fun register(app: CommCareApp, installRequestSource: InstallRequestSource) {
        val firstAttempt = getFirstRequestAttempt(app, installRequestSource);
        if (firstAttempt == -1L) {
            // this is the first Attempt
            app.appPreferences
                    .edit()
                    .putLong(getFirstRequestAttemptKey(installRequestSource), Date().time)
                    .apply()
        }
    }

    @JvmStatic
    fun getRequestAge(app: CommCareApp, requestTag: InstallRequestSource): RequestAge {
        val firstAttempt = getFirstRequestAttempt(app, requestTag)
        return when (if (firstAttempt == -1L) 0 else getDaysBetweenJavaDatetimes(Date(firstAttempt), TimeProvider.getCurrentDate())) {
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
    fun markSuccess(installRequestSource: InstallRequestSource) {
        markSuccess(CommCareApplication.instance().currentApp, installRequestSource)
    }

    @JvmStatic
    fun markSuccess(app: CommCareApp, installRequestSource: InstallRequestSource) {
        app.appPreferences
                .edit()
                .remove(getFirstRequestAttemptKey(installRequestSource))
                .apply()
    }

    private fun getFirstRequestAttempt(app: CommCareApp, requestTag: InstallRequestSource): Long {
        val appPrefrences = app.appPreferences
        return appPrefrences.getLong(getFirstRequestAttemptKey(requestTag), -1L)
    }

    private fun getFirstRequestAttemptKey(requestTag: InstallRequestSource): String {
        return "first_request_attempt_$requestTag"
    }

    enum class RequestAge {
        LT_10, LT_30, LT_60, LT_90, LT_120, LT_150, GT_150
    }
}