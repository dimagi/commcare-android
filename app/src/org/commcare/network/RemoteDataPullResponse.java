package org.commcare.network;

import android.content.Context;
import android.net.http.AndroidHttpClient;
import android.util.Log;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.commcare.tasks.DataPullTask;
import org.commcare.utils.AndroidStreamUtil;
import org.commcare.utils.bitcache.BitCache;
import org.commcare.utils.bitcache.BitCacheFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Performs data pulling http request and provides logic to retrieve the
 * response into a local cache.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class RemoteDataPullResponse {
    private final DataPullTask task;
    public final int responseCode;
    private final HttpResponse response;
    public final Header retryHeader;

    /**
     * Makes data pulling request and keeps response for local caching
     *
     * @param task     For progress reporting
     * @param response Contains data pull response stream and status code
     */
    protected RemoteDataPullResponse(DataPullTask task,
                                     HttpResponse response) throws IOException {
        this.response = response;
        this.responseCode = response.getStatusLine().getStatusCode();
        this.retryHeader = response.getFirstHeader("Retry-After");
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
            final long dataSizeGuess = guessDataSize();

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
    protected long guessDataSize() {
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
