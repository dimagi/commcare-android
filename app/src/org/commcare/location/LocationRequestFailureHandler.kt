package org.commcare.location
import com.google.android.gms.common.api.ResolvableApiException

object LocationRequestFailureHandler {

    interface LocationResolutionCallback {
        fun onResolvableException(exception: ResolvableApiException)
        fun onNonResolvableFailure()
    }

    fun handleFailure(
            failure: CommCareLocationListener.Failure,
            callback: LocationResolutionCallback
    ) {
        when (failure) {
            is CommCareLocationListener.Failure.ApiException -> {
                val exception = failure.exception
                if (exception is ResolvableApiException) {
                    callback.onResolvableException(exception)
                } else {
                    callback.onNonResolvableFailure()
                }
            }
            else -> callback.onNonResolvableFailure()
        }
    }
}
