package org.commcare.connect.network.connect.parser

import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.network.base.BaseApiResponseParser
import org.commcare.connect.network.connect.models.ConnectOpportunitiesResponseModel
import org.commcare.models.connect.ConnectLoginJobListModel
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.services.Logger
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
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
                }
            }
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }

        return ConnectOpportunitiesResponseModel(jobs, corruptJobs) as T


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