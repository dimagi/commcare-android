package org.commcare.connect.network.connect.parser

import android.content.Context
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.network.base.BaseApiResponseParser
import org.commcare.connect.network.connect.models.ConnectOpportunitiesResponseModel
import org.commcare.connect.workers.ConnectReleaseTogglesWorker
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.models.connect.ConnectLoginJobListModel
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.services.Logger
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream

class ConnectOpportunitiesParser<T>() : BaseApiResponseParser<T> {


    override fun parse(responseCode: Int, responseData: InputStream, anyInputObject: Any?): T {
        val corruptJobs: ArrayList<ConnectLoginJobListModel> = ArrayList()
        val jobs: ArrayList<ConnectJobRecord> = ArrayList()
        try {
            responseData.use { `in` ->
                val responseAsString = String(StreamsUtil.inputStreamToByteArray(`in`))
                if (!responseAsString.isEmpty()) {
                    //Parse the JSON
                    val json = JSONArray(responseAsString)
                    for (i in 0 until json.length()) {
                        var obj: JSONObject? = null
                        try {
                            obj = json[i] as JSONObject
                            jobs.add(ConnectJobRecord.fromJson(obj))
                        } catch (e: JSONException) {
                            Logger.exception("Parsing return from Opportunities request", e)
                            handleCorruptJob(obj, corruptJobs)
                        }
                    }

                    val context = anyInputObject as Context
                    val newJobs = ConnectJobUtils.storeJobs(context, jobs, true)

                    // Fetch feature release toggles if there is a new job.
                    if (newJobs > 0) {
                        ConnectReleaseTogglesWorker.scheduleOneTimeFetch(context)
                    }

                    reportApiCall(true, jobs.size, newJobs)
                }
            }
        } catch (e: JSONException) {
            reportApiCall(false, 0, 0)
            throw RuntimeException(e)
        }
        return ConnectOpportunitiesResponseModel(jobs, corruptJobs) as T

    }


    private fun reportApiCall(success: Boolean, totalJobs: Int, newJobs: Int) {
        FirebaseAnalyticsUtil.reportCccApiJobs(success, totalJobs, newJobs)
    }

    private fun handleCorruptJob(obj: JSONObject?,corruptJobs: ArrayList<ConnectLoginJobListModel>) {
        if (obj != null) {
            try {
                corruptJobs.add(createJobModel(ConnectJobRecord.corruptJobFromJson(obj)))
            } catch (e: JSONException) {
                Logger.exception("JSONException while retrieving corrupt opportunity title", e)
            }
        }
    }

    private fun createJobModel(job: ConnectJobRecord): ConnectLoginJobListModel {
        return ConnectLoginJobListModel(job.title, job)
    }
}