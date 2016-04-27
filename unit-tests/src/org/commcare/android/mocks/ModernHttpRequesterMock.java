package org.commcare.android.mocks;

import android.content.Context;

import org.commcare.network.ModernHttpRequester;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ModernHttpRequesterMock extends ModernHttpRequester {
    private static final List<Integer> responseCodeStack = new ArrayList<>();
    private static boolean shouldNextRequestThrowIOStream = false;

    public ModernHttpRequesterMock(Context context, URL url,
                                   Hashtable<String, String> params,
                                   boolean isAuthenticatedRequest,
                                   boolean isPostRequest) {
        super(context, url, params, isAuthenticatedRequest, isPostRequest);
    }

    /**
     * Set the response code for the next N requests
     */
    public static void setResponseCodes(Integer[] responseCodes) {
        responseCodeStack.clear();
        Collections.addAll(responseCodeStack, responseCodes);
    }

    public static void makeNextRequestThrowIOException() {
        shouldNextRequestThrowIOStream = true;
    }

    @Override
    protected HttpURLConnection setupConnection() throws IOException {
        if (shouldNextRequestThrowIOStream) {
            shouldNextRequestThrowIOStream = false;
            return new HttpURLConnectionMock(url, responseCodeStack.remove(0), true);
        } else {
            return new HttpURLConnectionMock(url, responseCodeStack.remove(0), false);
        }
    }
}

