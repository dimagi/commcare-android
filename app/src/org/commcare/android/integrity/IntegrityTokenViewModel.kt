package org.commcare.android.integrity

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import org.apache.commons.lang3.StringUtils
import org.commcare.CommCareApplication
import org.commcare.dalvik.BuildConfig
import org.javarosa.core.services.Logger

class IntegrityTokenViewModel() : AndroidViewModel(application = CommCareApplication.instance()) {

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
        val standardIntegrityManager = IntegrityManagerFactory.createStandard(CommCareApplication.instance())
        val cloudProjectNumber = BuildConfig.GOOGLE_CLOUD_PROJECT_NUMBER
        require(cloudProjectNumber!= -1L) { "Google Cloud Project Number is not defined" }

        standardIntegrityManager.prepareIntegrityToken(
            PrepareIntegrityTokenRequest.builder()
                .setCloudProjectNumber(cloudProjectNumber)
                .build()
        ).addOnSuccessListener { tokenProvider ->
            integrityTokenProvider = tokenProvider
            _providerState.postValue(TokenProviderState.Success(tokenProvider))
        }.addOnFailureListener { exception -> _providerState.postValue(TokenProviderState.Failure(exception)) }
    }

    /**
     * Retrieves a Google Play Integrity token asynchronously.
     *
     * @param requestHash hash of the complete request we are planning to send the token with
     * @param callback A callback function to handle the result
     */
    fun requestIntegrityToken(requestHash: String, callback: IntegrityTokenCallback) {
        require(integrityTokenProvider != null) {"StandardIntegrityTokenProvider is not warmed up yet. Please try again"}
        val integrityTokenResponse = integrityTokenProvider!!.request(
            StandardIntegrityTokenRequest.builder()
                .setRequestHash(requestHash)
                .build()
        )
        integrityTokenResponse
            .addOnSuccessListener { response -> callback.onTokenReceived(response.token()) }
            .addOnFailureListener { exception ->
                Logger.exception("Error retrieving Google Play Integrity token", exception)
                callback.onTokenReceived(null)
            }
    }

    sealed class TokenProviderState {
        data class Success(val provider: StandardIntegrityTokenProvider) : TokenProviderState()
        data class Failure(val exception: Exception) : TokenProviderState()
    }

    fun interface IntegrityTokenCallback {
        fun onTokenReceived(token: String?)
    }
}
