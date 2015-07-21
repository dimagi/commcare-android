/**
 * 
 */
package org.commcare.android.util.bitcache;

import android.content.Context;
import android.support.annotation.NonNull;

/**
 * @author ctsims
 *
 */
public class BitCacheFactory {
    @NonNull
    public static BitCache getCache(Context context, long estimatedSize) {
        if(estimatedSize == -1 || estimatedSize > 1024*1024*4) {
            return getCache(context);
        }
        return new MemoryBitCache();
    }
    @NonNull
    public static BitCache getCache(Context context) {
        //gotta be conservative if we don't have a guess
        return new FileBitCache(context);
    }
}
