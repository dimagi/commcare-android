package org.commcare.network

import com.appmattus.certificatetransparency.CTLogger
import com.appmattus.certificatetransparency.VerificationResult
import com.appmattus.certificatetransparency.certificateTransparencyInterceptor

import okhttp3.OkHttpClient
import org.commcare.core.network.HttpBuilderConfig
import org.commcare.preferences.HiddenPreferences

/**
 * Adds and removes Certificate Transparency (CT) network interceptor
 *
 */
class CTInterceptorConfig:HttpBuilderConfig  {


    override fun performCustomConfig(client: OkHttpClient.Builder): OkHttpClient.Builder {
        if(HiddenPreferences.isCertificateTransparencyEnabled()) {
            return client.addNetworkInterceptor(getInterceptor)
        }

        // In case there are CT Interceptors already attached
        removeCTInterceptors(client)
        return client
    }

    private val getInterceptor = certificateTransparencyInterceptor {
        logger = object : CTLogger {
            override fun log(host: String, result: VerificationResult) {
                println("$host -> $result")
            }
        }
    }

    private fun removeCTInterceptors(client: OkHttpClient.Builder) {
        client.networkInterceptors().removeAll { it::class.simpleName?.contains(CT_INTERCEPTOR_CLASS_NAME) == true }
    }

    companion object {
        // Needed as the CertificateTransparencyInterceptor class is internal
        private const val CT_INTERCEPTOR_CLASS_NAME = "CertificateTransparencyInterceptor"
    }


}