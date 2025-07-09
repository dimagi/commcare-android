package org.commcare.android.integrity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import org.commcare.dalvik.BuildConfig
import org.commcare.util.LogTypes
import org.javarosa.core.services.Logger

class IntegrityTokenViewModel(application: Application) : AndroidViewModel(application) {

    private val _providerState = MutableLiveData<TokenProviderState>()
    val providerState: LiveData<TokenProviderState> = _providerState

    var integrityTokenProvider: StandardIntegrityTokenProvider? = null

    init {
        prepareTokenProvider()
    }

    /**
     * Prepares Integrity token provider.
     * This is an asynchronous request to reduce the latency when we try to get the Integrity token.
     * This should be used as a one off request typically at application start up time or start of the process
     * that you anticipate will need to get the integrity token down the line.
     * Also note that each app instance can only prepare the integrity token up to 5 times per minute.
     */
    fun prepareTokenProvider() {
        val standardIntegrityManager = IntegrityManagerFactory.createStandard(getApplication())
        val cloudProjectNumber = BuildConfig.GOOGLE_CLOUD_PROJECT_NUMBER
        require(cloudProjectNumber!= -1L) { "Google Cloud Project Number is not defined" }

        standardIntegrityManager.prepareIntegrityToken(
            PrepareIntegrityTokenRequest.builder()
                .setCloudProjectNumber(cloudProjectNumber)
                .build()
        ).addOnSuccessListener { tokenProvider ->
            integrityTokenProvider = tokenProvider
            _providerState.postValue(TokenProviderState.Success(tokenProvider))
        }.addOnFailureListener { exception ->
            _providerState.postValue(TokenProviderState.Failure(exception))
            Logger.exception("Error preparing Google Play Integrity token provider", exception)
        }
    }

    /**
     * Retrieves a Google Play Integrity token asynchronously.
     *
     * @param requestHash hash of the complete request we are planning to send the token with
     * @param callback A callback function to handle the result
     * @param hasRetried Indicates if this is a retry attempt after a failure
     */
    fun requestIntegrityToken(requestHash: String, hasRetried: Boolean, callback: IntegrityTokenCallback) {
        require(integrityTokenProvider != null) {"StandardIntegrityTokenProvider is not warmed up yet. Please try again"}
        val integrityTokenResponse = integrityTokenProvider!!.request(
            StandardIntegrityTokenRequest.builder()
                .setRequestHash(requestHash)
                .build()
        )
        integrityTokenResponse
            .addOnSuccessListener { response ->
                callback.onTokenReceived(requestHash,response) }
            .addOnFailureListener { exception ->
                handleRequestFailureAndRetry(exception, requestHash, callback, hasRetried)
            }
    }

    private fun handleRequestFailureAndRetry(
        exception: java.lang.Exception,
        requestHash: String,
        callback: IntegrityTokenCallback,
        hasRetried: Boolean
    ) {
        if (exception is StandardIntegrityException && shouldRetryForIntegrityError(exception) && !hasRetried) {
            Logger.log(LogTypes.TYPE_MAINTENANCE, "Integrity provider is invalid or outdated, re-preparing and retrying...")
            prepareTokenProvider()

            // Observe the new preparation and retry once
            _providerState.observeForever(object : Observer<TokenProviderState> {
                override fun onChanged(state: TokenProviderState) {
                    if (state is TokenProviderState.Success) {
                        _providerState.removeObserver(this)
                        requestIntegrityToken(requestHash, true, callback)
                    } else if (state is TokenProviderState.Failure) {
                        _providerState.removeObserver(this)
                        Logger.log("Error re-preparing token provider after failure", state.exception.message )
                        callback.onTokenFailure(state.exception)
                    }
                }
            })
        } else {
            callback.onTokenFailure(exception)
        }
        Logger.exception("Error retrieving Google Play Integrity token", exception)
    }

    private fun shouldRetryForIntegrityError(exception: StandardIntegrityException): Boolean {
        return when (exception.errorCode) {
            StandardIntegrityErrorCode.CLIENT_TRANSIENT_ERROR,
            StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID -> true
            else -> false
        }
    }

    sealed class TokenProviderState {
        data class Success(val provider: StandardIntegrityTokenProvider) : TokenProviderState()
        data class Failure(val exception: Exception) : TokenProviderState()
    }

    interface IntegrityTokenCallback {
        fun onTokenReceived(requestHash: String, integrityTokeResponse: StandardIntegrityManager.StandardIntegrityToken)
        fun onTokenFailure(exception: java.lang.Exception)
    }
}
