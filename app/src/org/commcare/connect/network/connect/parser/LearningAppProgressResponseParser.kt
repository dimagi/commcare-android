package org.commcare.connect.network.connect.parser

import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord
import org.commcare.android.database.connect.models.ConnectJobLearningRecord
import org.commcare.connect.network.base.BaseApiResponseParser
import org.commcare.connect.network.connect.models.LearningAppProgressResponseModel
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.services.Logger
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream

class LearningAppProgressResponseParser<T>() : BaseApiResponseParser<T> {

    override fun parse(responseCode: Int, responseData: InputStream, anyInputObject:Any?): T {
        var connectJobLearningRecords: ArrayList<ConnectJobLearningRecord> = ArrayList()
        val connectJobAssessmentRecords: ArrayList<ConnectJobAssessmentRecord> = ArrayList()
        val jobId:Int = anyInputObject as Int
        try {
            responseData.use { `in` ->
                val responseAsString = String(StreamsUtil.inputStreamToByteArray(`in`))
                if (responseAsString.length > 0) {
                    //Parse the JSON
                    val json = JSONObject(responseAsString)

                    var key = "completed_modules"
                    val modules = json.getJSONArray(key)
                    for (i in 0 until modules.length()) {
                        val obj = modules[i] as JSONObject
                        val record = ConnectJobLearningRecord.fromJson(obj, jobId)
                        connectJobLearningRecords.add(record)
                    }

                    key = "assessments"
                    val assessments = json.getJSONArray(key)
                    for (i in 0 until assessments.length()) {
                        val obj = assessments[i] as JSONObject
                        val record = ConnectJobAssessmentRecord.fromJson(obj, jobId)
                        connectJobAssessmentRecords.add(record)
                    }
                }
            }
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }

        return LearningAppProgressResponseModel(connectJobLearningRecords,connectJobAssessmentRecords) as T
    }
}