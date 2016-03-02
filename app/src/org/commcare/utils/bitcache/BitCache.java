package org.commcare.utils.bitcache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author ctsims
 */
public interface BitCache {
    void initializeCache() throws IOException;

    OutputStream getCacheStream() throws IOException;

    InputStream retrieveCache() throws IOException;

    void release();
}
