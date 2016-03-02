package org.commcare.utils.bitcache;

import android.content.Context;

/**
 * @author ctsims
 */
public class BitCacheFactory {
    public static BitCache getCache(Context context, long estimatedSize) {
        if (estimatedSize == -1 || estimatedSize > 1024 * 1024 * 4) {
            return getCache(context);
        }
        return new MemoryBitCache();
    }

    private static BitCache getCache(Context context) {
        //gotta be conservative if we don't have a guess
        return new FileBitCache(context);
    }
}
