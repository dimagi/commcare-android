package org.commcare.network

import com.appmattus.certificatetransparency.CTLogger
import com.appmattus.certificatetransparency.VerificationResult
import com.appmattus.certificatetransparency.certificateTransparencyInterceptor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.commcare.core.network.HttpBuilderConfig
import org.commcare.preferences.HiddenPreferences
import org.commcare.util.LogTypes
import org.javarosa.core.services.Logger

/**
 * Adds and removes Certificate Transparency (CT) network interceptor
 *
 */
class CTInterceptorConfig:HttpBuilderConfig {


    override fun performCustomConfig(client: OkHttpClient.Builder): OkHttpClient.Builder {
        if (HiddenPreferences.isCertificateTransparencyEnabled()) {
            return client.addNetworkInterceptor(getCTInterceptor())
        }

        // In case there are CT Interceptors already attached
        removeCTInterceptors(client)
        return client
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
                            previousRequestFailed = false;
                        }

                    }
                }
            }
        }
        return interceptor!!
    }

    private fun removeCTInterceptors(client: OkHttpClient.Builder) {
        client.networkInterceptors().removeAll { it::class.simpleName?.contains(CT_INTERCEPTOR_CLASS_NAME) == true }
    }

    companion object {
        // Needed as the CertificateTransparencyInterceptor class is internal
        private const val CT_INTERCEPTOR_CLASS_NAME = "CertificateTransparencyInterceptor"
        private var interceptor: Interceptor? = null
        private var previousRequestFailed = false
    }


}