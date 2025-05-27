package org.commcare.android.integrity

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import org.commcare.android.CommCareViewModelProvider
import org.commcare.utils.HashUtils
import kotlin.jvm.functions.Function2
import org.json.JSONObject
import java.util.LinkedList
import java.util.HashMap

class IntegrityTokenApiRequestHelper(
    lifecycleOwner: LifecycleOwner
) {
    private val integrityTokenViewModel : IntegrityTokenViewModel = CommCareViewModelProvider.getIntegrityTokenViewModel()
    private val pendingRequests = LinkedList<Pair<HashMap<String, String>, Function2<String?, String, Unit>>>()

    private var providerInitialized = false
    private var providerFailed = false

    init {
        integrityTokenViewModel.providerState.observe(lifecycleOwner, object : Observer<IntegrityTokenViewModel.TokenProviderState> {
            override fun onChanged(state: IntegrityTokenViewModel.TokenProviderState) {
                when (state) {
                    is IntegrityTokenViewModel.TokenProviderState.Success -> {
                        providerInitialized = true
                        processPendingRequests()
                    }
                    is IntegrityTokenViewModel.TokenProviderState.Failure -> {
                        providerFailed = true
                        failPendingRequests()
                    }
                }
            }
        })
    }

    fun withIntegrityToken(
        requestBody: HashMap<String, String>,
        onTokenReady: Function2<String?, String, Unit>
    ) {
        val jsonBody = JSONObject(requestBody as Map<*, *>).toString()
        val requestHash = HashUtils.computeHash(jsonBody, HashUtils.HashAlgorithm.SHA256)

        if (providerInitialized) {
            integrityTokenViewModel.requestIntegrityToken(requestHash) { token ->
                onTokenReady.invoke(token, requestHash)
            }
        } else if (providerFailed) {
            onTokenReady.invoke(null, requestHash)
        } else {
            pendingRequests.add(Pair(requestBody, onTokenReady))
        }
    }

    private fun processPendingRequests() {
        while (pendingRequests.isNotEmpty()) {
            val (body, callback) = pendingRequests.removeFirst()
            val jsonBody = JSONObject(body).toString()
            val requestHash = HashUtils.computeHash(jsonBody, HashUtils.HashAlgorithm.SHA256)
            integrityTokenViewModel.requestIntegrityToken(requestHash) { token ->
                callback.invoke(token, requestHash)
            }
        }
    }

    private fun failPendingRequests() {
        while (pendingRequests.isNotEmpty()) {
            val (body, callback) = pendingRequests.removeFirst()
            val jsonBody = JSONObject(body as Map<*, *>).toString()
            val requestHash = HashUtils.computeHash(jsonBody, HashUtils.HashAlgorithm.SHA256)
            callback.invoke(null, requestHash)
        }
    }
}
