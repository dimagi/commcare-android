package org.commcare.connect.network.connect.parser

import org.commcare.CommCareApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.network.base.BaseApiResponseParser
import org.commcare.connect.workers.ConnectReleaseTogglesWorker
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.services.Logger
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream

class ConnectOpportunitiesParser<T> : BaseApiResponseParser<T> {
    override fun parse(
        responseCode: Int,
        responseData: InputStream,
        anyInputObject: Any?,
    ): T {
        val jobs: ArrayList<ConnectJobRecord> = ArrayList()
        try {
            responseData.use { `in` ->
                val responseAsString = String(StreamsUtil.inputStreamToByteArray(`in`))
                if (!responseAsString.isEmpty()) {
                    var corruptOpp = false
                    val json = JSONArray(responseAsString)
                    for (i in 0 until json.length()) {
                        var obj: JSONObject?
                        try {
                            obj = json[i] as JSONObject
                            jobs.add(ConnectJobRecord.fromJson(obj))
                        } catch (e: JSONException) {
                            Logger.exception("Parsing return from Opportunities request", e)
                            corruptOpp = true
                        }
                    }

                    val context = CommCareApplication.instance()
                    val newJobs = ConnectJobUtils.storeJobs(context, jobs, true)

                    // Fetch feature release toggles if there is a new job.
                    if (newJobs > 0) {
                        ConnectReleaseTogglesWorker.scheduleOneTimeFetch(context)
                    }

                    if (corruptOpp) {
                        throw JSONException("One or more opportunities were corrupt and could not be parsed")
                    }

                    reportApiCall(true, jobs.size, newJobs)
                }
            }
        } catch (e: JSONException) {
            reportApiCall(false, 0, 0)
            throw e
        }
        return jobs as T
    }

    private fun reportApiCall(
        success: Boolean,
        totalJobs: Int,
        newJobs: Int,
    ) {
        FirebaseAnalyticsUtil.reportCccApiJobs(success, totalJobs, newJobs)
    }
}
