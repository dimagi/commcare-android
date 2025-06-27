package org.commcare.location
import androidx.fragment.app.FragmentActivity
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

    /**
     * Optional helper for simple cases where caller just needs to try resolving and fallback
     */
    fun handleFailureWithResolution(
            failure: CommCareLocationListener.Failure,
            onError: () -> Unit,
            launchResolution: (ResolvableApiException) -> Unit
    ) {
        handleFailure(failure, object : LocationResolutionCallback {
            override fun onResolvableException(exception: ResolvableApiException) {
                try {
                    launchResolution(exception)
                } catch (e: Exception) {
                    onError()
                }
            }

            override fun onNonResolvableFailure() {
                onError()
            }
        })
    }
}
