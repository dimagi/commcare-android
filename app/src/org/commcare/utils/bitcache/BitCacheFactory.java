package org.commcare.utils.bitcache;

import java.io.File;

/**
 * @author ctsims
 */
public class BitCacheFactory {
    public static BitCache getCache(CacheDirSetup context, long estimatedSize) {
        if (estimatedSize == -1 || estimatedSize > 1024 * 1024 * 4) {
            return new FileBitCache(context);
        }
        return new MemoryBitCache();
    }

    public interface CacheDirSetup {
        File getCacheDir();
    }
}
