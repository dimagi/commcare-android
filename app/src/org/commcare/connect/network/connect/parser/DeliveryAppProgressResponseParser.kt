package org.commcare.connect.network.connect.parser

import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.network.base.BaseApiResponseParser
import org.commcare.connect.network.connect.models.DeliveryAppProgressResponseModel
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.services.Logger
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.Date

class DeliveryAppProgressResponseParser <T>() : BaseApiResponseParser<T> {

    override fun parse(responseCode: Int, responseData: InputStream, anyInputObject:Any?): T {

        val job = anyInputObject as ConnectJobRecord

        var updatedJob = false
        var hasDeliveries=false
        var hasPayment=false

        responseData.use { `in` ->

            try {
                val responseAsString = String(StreamsUtil.inputStreamToByteArray(`in`))
                if (responseAsString.length > 0) {
                    //Parse the JSON
                    val json = JSONObject(responseAsString)


                    var key = "max_payments"
                    if (json.has(key)) {
                        job.maxVisits = json.getInt(key)
                        updatedJob = true
                    }

                    key = "end_date"
                    if (json.has(key)) {
                        job.projectEndDate = DateUtils.parseDate(json.getString(key))
                        updatedJob = true
                    }

                    key = "payment_accrued"
                    if (json.has(key)) {
                        job.paymentAccrued = json.getInt(key)
                        updatedJob = true
                    }

                    key = "is_user_suspended"
                    if (json.has(key)) {
                        job.isUserSuspended = json.getBoolean(key)
                        updatedJob = true
                    }

                    if (updatedJob) {
                        job.lastDeliveryUpdate = Date()
                    }

                    val deliveries: MutableList<ConnectJobDeliveryRecord> =
                        ArrayList(json.length())
                    key = "deliveries"
                    if (json.has(key)) {
                        hasDeliveries=true
                        val array = json.getJSONArray(key)
                        for (i in 0..<array.length()) {
                            val obj = array[i] as JSONObject
                            deliveries.add(ConnectJobDeliveryRecord.fromJson(obj, job.jobId))
                        }

                        job.deliveries = deliveries
                    }

                    val payments: MutableList<ConnectJobPaymentRecord> = ArrayList()
                    key = "payments"
                    if (json.has(key)) {
                        hasPayment=true
                        val array = json.getJSONArray(key)
                        for (i in 0..<array.length()) {
                            val obj = array[i] as JSONObject
                            payments.add(ConnectJobPaymentRecord.fromJson(obj, job.jobId))
                        }

                        job.payments = payments
                    }
                }
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }
        }

        return DeliveryAppProgressResponseModel(updatedJob,hasDeliveries,hasPayment) as T
    }
}