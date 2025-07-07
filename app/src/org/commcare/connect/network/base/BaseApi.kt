package org.commcare.connect.network.base

import android.content.Context
import android.os.Handler
import okhttp3.ResponseBody
import org.commcare.activities.CommCareActivity
import org.commcare.connect.ConnectConstants
import org.commcare.connect.network.IApiCallback
import org.commcare.util.LogTypes
import org.javarosa.core.services.Logger
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class BaseApi {

    companion object {
        fun callApi(context: Context, call: Call<ResponseBody>, callback: IApiCallback) {
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
                            // Handle error when reading the stream
                            callback.processFailure(response.code(), null)
                        }
                    } else {
                        // Handle validation errors
                        logNetworkError(response)
                        val stream = if (response.errorBody() != null) response.errorBody()!!
                            .byteStream() else null
                        callback.processFailure(response.code(), stream)
                    }
                }

                override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
                    dismissProgressDialog(context)
                    // Handle network errors, etc.
                    handleNetworkError(t)
                    callback.processNetworkFailure()
                }
            })
        }


        fun showProgressDialog(context: Context) {
            if (context is CommCareActivity<*>) {
                val handler = Handler(context.getMainLooper())
                handler.post {
                    try {
                        (context as CommCareActivity<*>).showProgressDialog(ConnectConstants.NETWORK_ACTIVITY_ID)
                    } catch (e: Exception) {
                        //Ignore, ok if showing fails
                    }
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


        fun logNetworkError(response: Response<*>) {
            val message = response.message()
            if (response.code() == 400) {
                // Bad request (e.g., validation failed)
                Logger.log(
                    LogTypes.TYPE_ERROR_SERVER_COMMS,
                    "Bad Request: $message"
                )
            } else if (response.code() == 401) {
                // Unauthorized (e.g., invalid credentials)
                Logger.log(
                    LogTypes.TYPE_ERROR_SERVER_COMMS,
                    "Unauthorized: $message"
                )
            } else if (response.code() == 404) {
                // Not found
                Logger.log(
                    LogTypes.TYPE_ERROR_SERVER_COMMS,
                    "Not Found: $message"
                )
            } else if (response.code() >= 500) {
                // Server error
                Logger.log(
                    LogTypes.TYPE_ERROR_SERVER_COMMS,
                    "Server Error: $message"
                )
            } else {
                Logger.log(
                    LogTypes.TYPE_ERROR_SERVER_COMMS,
                    "API Error: $message"
                )
            }
        }


        fun handleNetworkError(t: Throwable) {
            val message = t.message
            if (t is IOException) {
                // IOException is usually a network error (no internet, timeout, etc.)
                Logger.log(
                    LogTypes.TYPE_ERROR_SERVER_COMMS,
                    "Network Error: $message"
                )
            } else if (t is HttpException) {
                // Handle HTTP exceptions separately if needed
                Logger.log(
                    LogTypes.TYPE_ERROR_SERVER_COMMS,
                    "HTTP Error: $message"
                )
            } else {
                Logger.log(
                    LogTypes.TYPE_ERROR_SERVER_COMMS,
                    "Unexpected Error: $message"
                )
            }
        }

    }

}