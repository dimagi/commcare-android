package org.commcare.network

import com.appmattus.certificatetransparency.CTLogger
import com.appmattus.certificatetransparency.VerificationResult
import com.appmattus.certificatetransparency.certificateTransparencyInterceptor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.commcare.preferences.HiddenPreferences
import org.commcare.util.LogTypes
import org.javarosa.core.services.Logger

/**
 * Adds and removes Certificate Transparency (CT) network interceptor
 *
 */
class CTInterceptorConfig {

    companion object {
        private var interceptor: Interceptor? = null
        private var previousRequestFailed = false
        private var interceptorEnabled = false
    }

    fun toggleCertificateTransparency(client: OkHttpClient.Builder){
        if (HiddenPreferences.isCertificateTransparencyEnabled()){
            if(!interceptorEnabled) {
                client.addNetworkInterceptor(getCTInterceptor())
                interceptorEnabled = true
            }
        }
        else {
            // In case there are CT Interceptors already attached
            if (interceptorEnabled) {
                removeCTInterceptors(client)
                interceptorEnabled = false
            }
        }
    }

    private fun getCTInterceptor(): Interceptor {
        if (interceptor == null) {
            interceptor = certificateTransparencyInterceptor {
                logger = object : CTLogger {
                    override fun log(host: String, result: VerificationResult) {
                        if (result is VerificationResult.Failure && !previousRequestFailed) {
                            Logger.log(LogTypes.TYPE_NETWORK, "$host -> $result")
                            previousRequestFailed = true
                        } else if (result is VerificationResult.Success && previousRequestFailed) {
                            previousRequestFailed = false
                        }

                    }
                }
            }
        }
        return interceptor!!
    }

    private fun removeCTInterceptors(client: OkHttpClient.Builder) {
        client.networkInterceptors().removeAll { it === interceptor }
    }
}