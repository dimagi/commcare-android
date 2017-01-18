package org.commcare.network;

import android.net.Uri;

import org.commcare.core.network.ModernHttpRequester;
import org.commcare.core.network.bitcache.BitCacheFactory;
import org.javarosa.core.model.User;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class AndroidModernHttpRequester extends ModernHttpRequester {
    public AndroidModernHttpRequester(BitCacheFactory.CacheDirSetup cacheDirSetup,
                                          URL url, HashMap<String, String> params,
                                          User user, String domain,
                                          boolean isAuthenticatedRequest,
                                          boolean isPostRequest) {
        super(cacheDirSetup, url, params, user, domain,
                isAuthenticatedRequest, isPostRequest);
    }

    @Override
    protected URL buildUrlWithParams() throws MalformedURLException {
        Uri.Builder b = Uri.parse(url.toString()).buildUpon();
        for (Map.Entry<String, String> param : params.entrySet()) {
            b.appendQueryParameter(param.getKey(), param.getValue());
        }
        return new URL(b.build().toString());
    }
}
