package org.commcare.connect.network.connect.parser

import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.network.base.BaseApiResponseParser
import org.commcare.connect.network.connect.models.DeliveryAppProgressResponseModel
import org.commcare.connect.network.connect.models.ParsedConnectTask
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.model.utils.DateUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.util.Date

class DeliveryAppProgressResponseParser<T> : BaseApiResponseParser<T> {
    override fun parse(
        responseCode: Int,
        responseData: InputStream,
        anyInputObject: Any?,
    ): T {
        val job = anyInputObject as ConnectJobRecord

        var updatedJob = false
        var hasDeliveries = false
        var hasPayment = false
        val parsedTasks: MutableList<ParsedConnectTask> = mutableListOf()

        responseData.use { `in` ->

            try {
                val responseAsString = String(StreamsUtil.inputStreamToByteArray(`in`))
                if (responseAsString.length > 0) {
                    val json = JSONObject(responseAsString)

                    if (json.has("max_payments")) {
                        job.maxVisits = json.getInt("max_payments")
                        updatedJob = true
                    }

                    if (json.has("end_date")) {
                        job.projectEndDate = DateUtils.parseDate(json.getString("end_date"))
                        updatedJob = true
                    }

                    if (json.has("payment_accrued")) {
                        job.paymentAccrued = json.getInt("payment_accrued")
                        updatedJob = true
                    }

                    if (json.has("is_user_suspended")) {
                        job.isUserSuspended = json.getBoolean("is_user_suspended")
                        updatedJob = true
                    }

                    if (updatedJob) {
                        job.lastDeliveryUpdate = Date()
                    }

                    val deliveries: MutableList<ConnectJobDeliveryRecord> =
                        ArrayList(json.length())

                    if (json.has("deliveries")) {
                        hasDeliveries = true
                        val array = json.getJSONArray("deliveries")
                        for (i in 0 until array.length()) {
                            val obj = array[i] as JSONObject
                            deliveries.add(ConnectJobDeliveryRecord.fromJson(obj, job))
                        }

                        job.deliveries = deliveries
                    }

                    val payments: MutableList<ConnectJobPaymentRecord> = ArrayList()

                    if (json.has("payments")) {
                        hasPayment = true
                        val array = json.getJSONArray("payments")
                        for (i in 0 until array.length()) {
                            val obj = array[i] as JSONObject
                            payments.add(ConnectJobPaymentRecord.fromJson(obj, job))
                        }

                        job.payments = payments
                    }

                    if (json.has("assigned_tasks")) {
                        val array = json.getJSONArray("assigned_tasks")
                        for (i in 0 until array.length()) {
                            val obj = array[i] as JSONObject
                            parsedTasks.add(parseTask(obj))
                        }
                    }
                }
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }
        }

        return DeliveryAppProgressResponseModel(
            updatedJob,
            hasDeliveries,
            hasPayment,
            parsedTasks,
        ) as T
    }

    private fun parseTask(json: JSONObject): ParsedConnectTask {
        val assigned = json.getString("status") == "assigned"
        var dateModified: Date? = null

        if (json.has("date_modified")) {
            dateModified = DateUtils.parseDate(json.getString("date_modified"))
        }

        return ParsedConnectTask(assigned, dateModified)
    }
}
