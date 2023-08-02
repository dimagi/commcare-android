package org.commcare.network;

import android.os.Build;

import org.commcare.core.network.HttpBuilderConfig;

import okhttp3.OkHttpClient;

/**
 * The purpose of this class is to offer a single entry point for additional OkHttpClient.Builder
 * configs. As it stands, it serves to attach the ISRG Root X1 Certificate and enable/disable
 * Certificate Transparency
 */
public class OkHttpBuilderCustomConfig implements HttpBuilderConfig {
    private CTInterceptorConfig ctInterceptorConfig;
    private ISRGCertConfig isrgCertConfig;

    public OkHttpBuilderCustomConfig(){
         ctInterceptorConfig = new CTInterceptorConfig();
         isrgCertConfig = new ISRGCertConfig();
    }

    @Override
    public OkHttpClient.Builder performCustomConfig(OkHttpClient.Builder okHttpBuilder) {
        // Enable or Disable CT, depending on the current value of the preference
        ctInterceptorConfig.toggleCertificateTransparency(okHttpBuilder);

        // Attach ISRG Root Certificate when running Android 7.1 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            isrgCertConfig.attachISRGRootCertificate(okHttpBuilder);
        }

        return okHttpBuilder;
    }
}
