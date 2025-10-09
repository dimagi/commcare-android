package org.commcare.connect.network.base

import android.content.Context
import android.os.Handler
import okhttp3.ResponseBody
import org.commcare.activities.CommCareActivity
import org.commcare.connect.ConnectConstants
import org.commcare.connect.network.IApiCallback
import org.commcare.connect.network.NetworkUtils.getErrorCodes
import org.commcare.connect.network.NetworkUtils.logFailedResponse
import org.commcare.connect.network.NetworkUtils.logNetworkError
import org.commcare.util.LogTypes
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.services.Logger
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

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
                        val stream = if (response.errorBody() != null) response.errorBody()!!
                            .byteStream() else null
                        try {
                            val errorCodes = getErrorCodes(stream)
                            val errorCode = errorCodes.first
                            val errorSubCode = errorCodes.second
                            logFailedResponse(response.message(), response.code(), endPoint, errorCode, errorSubCode)
                            callback.processFailure(response.code(), endPoint, errorCode, errorSubCode)
                        } finally {
                            StreamsUtil.closeStream(stream)
                        }
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
    }

}
