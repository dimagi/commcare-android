package org.commcare.connect.network.base

import android.content.Context
import android.os.Handler
import okhttp3.ResponseBody
import org.commcare.activities.CommCareActivity
import org.commcare.connect.ConnectConstants
import org.commcare.connect.network.IApiCallback
import org.commcare.util.LogTypes
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.services.Logger
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.nio.charset.StandardCharsets

class BaseApi {

    companion object {
        fun callApi(
            context: Context,
            call: Call<ResponseBody>,
            callback: IApiCallback,
            endPoint: String
        ) {
            showProgressDialog(context)
            call.enqueue(object : Callback<ResponseBody?> {
                override fun onResponse(
                    call: Call<ResponseBody?>,
                    response: Response<ResponseBody?>
                ) {
                    dismissProgressDialog(context)
                    if (response.isSuccessful && response.body() != null) {
                        // Handle success
                        try {
                            response.body()!!.byteStream().use { responseStream ->
                                callback.processSuccess(response.code(), responseStream)
                            }
                        } catch (e: IOException) {
                            Logger.exception("Error reading response stream", e);
                            // Handle error when reading the stream
                            callback.processFailure(response.code(), endPoint, "", "")
                        }
                    } else {
                        // Handle validation errors
                        val stream = if (response.errorBody() != null) response.errorBody()!!
                            .byteStream() else null
                        var errorCode = ""
                        var errorSubCode = ""
                        try {
                            if (stream != null) {
                                val errorBytes = StreamsUtil.inputStreamToByteArray(stream)
                                val jsonStr = String(errorBytes, StandardCharsets.UTF_8)
                                val json = JSONObject(jsonStr)
                                errorCode = json.optString("error_code", "");
                                errorSubCode = json.optString("error_sub_code", "");
                            }
                        } catch (e: Exception) {
                            Logger.exception("Error parsing error_code", e);
                        }
                        logFailedResponse(response,  endPoint, errorCode, errorSubCode)
                        callback.processFailure(response.code(), endPoint, errorCode, errorSubCode)
                    }
                }

                override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
                    dismissProgressDialog(context)
                    // Handle network errors, etc.
                    logNetworkError(t, endPoint)
                    callback.processNetworkFailure()
                }
            })
        }


        fun showProgressDialog(context: Context) {
            if (context is CommCareActivity<*>) {
                val handler = Handler(context.getMainLooper())
                handler.post {
                    (context as CommCareActivity<*>).showProgressDialog(ConnectConstants.NETWORK_ACTIVITY_ID)
                }
            }
        }

        fun dismissProgressDialog(context: Context) {
            if (context is CommCareActivity<*>) {
                val handler = Handler(context.getMainLooper())
                handler.post {
                    (context as CommCareActivity<*>).dismissProgressDialogForTask(ConnectConstants.NETWORK_ACTIVITY_ID)
                }
            }
        }


        fun logFailedResponse(response: Response<*>, endPoint: String, errorCode: String, errorSubCode: String) {
            var message = response.message()
            message += if (errorCode.isNotEmpty()) " | error_code: $errorCode" else ""
            message += if (errorSubCode.isNotEmpty()) " | error_sub_code: $errorSubCode" else ""
            var errorMessage = when (response.code()) {
                400 -> "Bad Request: $message"
                401 -> "Unauthorized: $message"
                404 -> "Not Found: $message"
                500 -> "Server Error: $message"
                else -> "API Error: $message"

            }
            errorMessage += " for url ${endPoint ?: "url not found"}"

            Logger.log(
                LogTypes.TYPE_ERROR_SERVER_COMMS,
                errorMessage
            )
            Logger.exception(LogTypes.TYPE_ERROR_SERVER_COMMS, Throwable(errorMessage))
        }


        fun logNetworkError(t: Throwable, endPoint: String) {
            val message = t.message

            var errorMessage = when (t) {
                is IOException -> "Network Error: $message"
                is HttpException -> "HTTP Error: $message"
                else -> "Unexpected Error: $message"
            }

            errorMessage += " for url ${endPoint ?: "url not found"}"
            Logger.log(
                LogTypes.TYPE_ERROR_SERVER_COMMS,
                errorMessage
            )
            Logger.exception(errorMessage, t)
        }

    }

}
