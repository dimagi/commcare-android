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
        private var interceptorAttached = false

        @JvmStatic
        fun toggleCertificateTransparency(client: OkHttpClient.Builder) {
            if (HiddenPreferences.isCertificateTransparencyEnabled()) {
                if (!interceptorAttached) {
                    client.addNetworkInterceptor(getCTInterceptor())
                    interceptorAttached = true
                }
            } else if (interceptorAttached) {
                removeCTInterceptors(client)
                interceptorAttached = false
            }
        }

        private fun getCTInterceptor(): Interceptor {
            if (interceptor == null) {
                interceptor = certificateTransparencyInterceptor {
                    logger = object : CTLogger {
                        override fun log(host: String, result: VerificationResult) {
                            if (result is VerificationResult.Failure && !previousRequestFailed) {
                                Logger.log(
                                        LogTypes.TYPE_NETWORK,
                                        "Certificate verification failed: $host -> $result")
                            }
                            previousRequestFailed = result is VerificationResult.Failure
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
}