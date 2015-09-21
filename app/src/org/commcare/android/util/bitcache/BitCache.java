/**
 * 
 */
package org.commcare.android.util.bitcache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 
 * @author ctsims
 *
 */
public interface BitCache {
    public void initializeCache() throws IOException;
    
    public OutputStream getCacheStream() throws IOException;
    
    public InputStream retrieveCache() throws IOException;
    
    public void release();
}
