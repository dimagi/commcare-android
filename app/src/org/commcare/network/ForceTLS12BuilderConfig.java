package org.commcare.network;

import org.commcare.core.network.HttpBuilderConfig;
import org.javarosa.core.services.Logger;

import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;

public class ForceTLS12BuilderConfig implements HttpBuilderConfig {
    @Override
    public OkHttpClient.Builder performCustomConfig(OkHttpClient.Builder client) {
        try {
            SSLContext sc = SSLContext.getInstance("TLSv1.2");
            sc.init(null, null, null);
            client.sslSocketFactory(new Tls12SocketFactory(sc.getSocketFactory()));

            ConnectionSpec cs = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2)
                    .build();

            List<ConnectionSpec> specs = new ArrayList<>();
            specs.add(cs);
            specs.add(ConnectionSpec.COMPATIBLE_TLS);
            specs.add(ConnectionSpec.CLEARTEXT);

            client.connectionSpecs(specs);
        } catch (Exception exc) {
            Logger.exception("Network.OkHttpTLSCompat", exc);
        }
        return client;
    }
}
