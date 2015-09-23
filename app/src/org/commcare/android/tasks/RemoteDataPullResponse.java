package org.commcare.android.tasks;

import android.content.Context;
import android.net.http.AndroidHttpClient;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.android.util.AndroidStreamUtil;
import org.commcare.android.util.bitcache.BitCache;
import org.commcare.android.util.bitcache.BitCacheFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class RemoteDataPullResponse {
    private final DataPullTask task;
    public final int responseCode;
    private final HttpResponse response;

    protected RemoteDataPullResponse(int responseCode) {
        this.responseCode = responseCode;
        this.task = null;
        this.response = null;
    }

    public RemoteDataPullResponse(DataPullTask task, HttpRequestGenerator requestor, String server, boolean useRequestFlags) throws IOException {
        this.response = requestor.makeCaseFetchRequest(server, useRequestFlags);
        this.responseCode = response.getStatusLine().getStatusCode();
        this.task = task;
    }

    /**
     * Retrieves the HttpResponse stream and writes it to an initialized safe
     * local cache. Notifies listeners of progress through the download if its
     * size is available.
     *
     * @throws IOException If there is an issue reading or writing the response.
     */
    public BitCache writeResponseToCache(Context c) throws IOException {
        BitCache cache = null;
        try {
            final long dataSizeGuess = guessDataSize(response);

            cache = BitCacheFactory.getCache(c, dataSizeGuess);

            cache.initializeCache();

            OutputStream cacheOut = cache.getCacheStream();
            InputStream input = getInputStream();

            Log.i("commcare-network", "Starting network read, expected content size: " + dataSizeGuess + "b");
            AndroidStreamUtil.writeFromInputToOutput(new BufferedInputStream(input),
                    cacheOut,
                    new AndroidStreamUtil.StreamReadObserver() {
                        long lastOutput = 0;

                        /** The notification threshold. **/
                        static final int PERCENT_INCREASE_THRESHOLD = 4;

                        @Override
                        public void notifyCurrentCount(long bytesRead) {
                            boolean notify;

                            //We always wanna notify when we get our first bytes
                            if (lastOutput == 0) {
                                Log.i("commcare-network", "First" + bytesRead + " bytes received from network: ");
                            }
                            //After, if we don't know how much data to expect, we can't do
                            //anything useful
                            if (dataSizeGuess == -1) {
                                //set this so the first notification up there doesn't keep firing
                                lastOutput = bytesRead;
                                return;
                            }

                            int percentIncrease = (int)(((bytesRead - lastOutput) * 100) / dataSizeGuess);

                            //Now see if we're over the reporting threshold
                            //TODO: Is this actually necessary? In theory this shouldn't
                            //matter due to android task polling magic?
                            notify = percentIncrease > PERCENT_INCREASE_THRESHOLD;

                            if (notify && task != null) {
                                lastOutput = bytesRead;
                                int totalRead = (int)(((bytesRead) * 100) / dataSizeGuess);
                                task.reportDownloadProgress(totalRead);
                            }
                        }
                    });

            return cache;

            //If something goes wrong while we're reading into the cache
            //we may need to free the storage we reserved.
        } catch (IOException e) {
            cache.release();
            throw e;
        }
    }

    protected InputStream getInputStream() throws IOException {
        return AndroidHttpClient.getUngzippedContent(response.getEntity());
    }

    /**
     * Get an estimation of how large the provided response is.
     *
     * @return -1 for unknown.
     */
    protected long guessDataSize(HttpResponse response) {
        if (response.containsHeader("Content-Length")) {
            String length = response.getFirstHeader("Content-Length").getValue();
            try {
                return Long.parseLong(length);
            } catch (Exception e) {
                //Whatever.
            }
        }
        return -1;
    }
}
