package org.commcare.android.integrity

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode.*
import org.commcare.android.CommCareViewModelProvider
import org.commcare.utils.HashUtils
import org.commcare.dalvik.R;
import org.json.JSONObject
import java.util.LinkedList
import java.util.HashMap
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.Result

/**
 * Helper class to get the integrity token for an API request and is meant to abstract common boilerplate when dealing with [IntegrityTokenViewModel]
 */
class IntegrityTokenApiRequestHelper(
    lifecycleOwner: LifecycleOwner
) {
    private val integrityTokenViewModel : IntegrityTokenViewModel = CommCareViewModelProvider.getIntegrityTokenViewModel()
    private val pendingRequests = LinkedList<Pair<HashMap<String, String>, IntegrityTokenViewModel.IntegrityTokenCallback>>()

    private var providerInitialized = false
    private var providerFailed = false
    private var providerFailedException = Exception("Integrity Token Provider failed to initialize")

    init {
        integrityTokenViewModel.providerState.observe(lifecycleOwner
        ) { value ->
            when (value) {
                is IntegrityTokenViewModel.TokenProviderState.Success -> {
                    providerInitialized = true
                    processPendingRequests()
                }

                is IntegrityTokenViewModel.TokenProviderState.Failure -> {
                    providerFailed = true
                    providerFailedException = value.exception
                    failPendingRequests()
                }
            }
        }
    }

    fun withIntegrityToken(
        requestBody: HashMap<String, String>,
        callback: IntegrityTokenViewModel.IntegrityTokenCallback
    ) {
        val jsonBody = JSONObject(requestBody as Map<*, *>).toString()
        val requestHash = HashUtils.computeHash(jsonBody, HashUtils.HashAlgorithm.SHA256)

        if (providerInitialized) {
            integrityTokenViewModel.requestIntegrityToken(requestHash, false, callback)
        } else if (providerFailed) {
            callback.onTokenFailure(providerFailedException)
        } else {
            pendingRequests.add(Pair(requestBody, callback))
        }
    }

    private fun processPendingRequests() {
        while (pendingRequests.isNotEmpty()) {
            val (body, callback) = pendingRequests.removeFirst()
            val jsonBody = JSONObject(body as Map<*, *>).toString()
            val requestHash = HashUtils.computeHash(jsonBody, HashUtils.HashAlgorithm.SHA256)
            integrityTokenViewModel.requestIntegrityToken(requestHash, false, callback)
        }
    }

    private fun failPendingRequests() {
        while (pendingRequests.isNotEmpty()) {
            val (_, callback) = pendingRequests.removeFirst()
            callback.onTokenFailure(providerFailedException)
        }
    }

    fun getErrorForException(context: Context, exception: Exception): String {
        var errorMessage = context.getString(R.string.personalid_configuration_process_failed_subtitle)
        if (exception is StandardIntegrityException) {
            val integrityErrorCode = exception.errorCode
            when (integrityErrorCode) {
                API_NOT_AVAILABLE,
                PLAY_STORE_NOT_FOUND,
                PLAY_SERVICES_NOT_FOUND,
                PLAY_STORE_VERSION_OUTDATED,
                PLAY_SERVICES_VERSION_OUTDATED,
                CANNOT_BIND_TO_SERVICE -> {
                    errorMessage = context.getString(R.string.personalid_configuration_process_failed_play_services)
                }

                CLOUD_PROJECT_NUMBER_IS_INVALID,
                REQUEST_HASH_TOO_LONG,
                APP_UID_MISMATCH -> {
                    throw RuntimeException(exception)
                }

                TOO_MANY_REQUESTS,
                GOOGLE_SERVER_UNAVAILABLE -> {
                    errorMessage = context.getString(R.string.personalid_configuration_process_failed_temporary_unavailable)
                }

                CLIENT_TRANSIENT_ERROR,
                INTEGRITY_TOKEN_PROVIDER_INVALID -> {
                    errorMessage = context.getString(R.string.personalid_configuration_process_failed_unexpected_error)
                }

                NETWORK_ERROR -> {
                    errorMessage = context.getString(R.string.personalid_configuration_process_failed_network_error)
                }
            }
        }
        return errorMessage
    }

    companion object {
        /**
         * Suspend function to fetch the integrity token for background/worker use.
         * Returns Result.success(token) or Result.failure(exception)
         */
        suspend fun fetchIntegrityToken(requestHash: String): Result<Pair<String, String>> = suspendCancellableCoroutine { cont ->
            val viewModel = CommCareViewModelProvider.getIntegrityTokenViewModel()

            fun requestToken() {
                try {
                    viewModel.requestIntegrityToken(requestHash, false, object : IntegrityTokenViewModel.IntegrityTokenCallback {
                        override fun onTokenReceived(token: String, requestHash: String) {
                            cont.resume(Result.success(Pair(token, requestHash)))
                        }
                        override fun onTokenFailure(exception: Exception) {
                            cont.resume(Result.failure(exception))
                        }
                    })
                } catch (e: Exception) {
                    cont.resume(Result.failure(e))
                }
            }

            val observer = object : Observer<IntegrityTokenViewModel.TokenProviderState> {
                override fun onChanged(value: IntegrityTokenViewModel.TokenProviderState) {
                    when (value) {
                        is IntegrityTokenViewModel.TokenProviderState.Success -> {
                            viewModel.providerState.removeObserver(this)
                            requestToken()
                        }
                        is IntegrityTokenViewModel.TokenProviderState.Failure -> {
                            viewModel.providerState.removeObserver(this)
                            cont.resume(Result.failure(value.exception))
                        }
                    }
                }
            }
            viewModel.providerState.observeForever(observer)
            cont.invokeOnCancellation { viewModel.providerState.removeObserver(observer) }
        }
    }
}
