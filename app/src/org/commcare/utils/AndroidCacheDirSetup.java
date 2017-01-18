package org.commcare.utils;

import android.content.Context;

import org.commcare.core.network.bitcache.BitCacheFactory;

import java.io.File;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class AndroidCacheDirSetup implements BitCacheFactory.CacheDirSetup {
    private final Context context;

    public AndroidCacheDirSetup(Context context) {
        this.context = context;
    }

    @Override
    public File getCacheDir() {
        return context.getCacheDir();
    }
}
