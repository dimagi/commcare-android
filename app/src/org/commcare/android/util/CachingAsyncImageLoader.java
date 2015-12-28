package org.commcare.android.util;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.LruCache;
import android.widget.ImageView;

/**
 * Class used for managing the LoadImageTasks that load images into a list. 
 * Ensures that proper caching occurs and attempts to limit overflows
 * 
 * @author wspride
 *
 */
@SuppressLint("NewApi")
public class CachingAsyncImageLoader implements ComponentCallbacks2 {
    private final TCLruCache cache;
    private final int CACHE_DIVISOR =2;
    private final Context context;

    public CachingAsyncImageLoader(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
        int memoryClass = (am.getMemoryClass() * 1024 * 1024)/CACHE_DIVISOR;        //basically, set the heap to be everything we can get
        this.context = context;
        this.cache = new TCLruCache(memoryClass);
    }

    public void display(String url, ImageView imageView, int defaultResource,
                        int boundingWidth, int boundingHeight) {
        imageView.setImageResource(defaultResource);
        Bitmap image;
        synchronized(cache) {
            image = cache.get(url);
        }
        if (image != null) {
            imageView.setImageBitmap(image);
        }
        else {
            new SetImageTask(imageView, this.context, boundingWidth, boundingHeight).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url);
        }
    }

    @SuppressLint("NewApi")
    private class TCLruCache extends LruCache<String, Bitmap> {

        public TCLruCache(int maxSize) {
            super(maxSize);
        }
    }

    /**
     * Simple member class used for asyncronously loading and setting ImageView bitmaps
     * @author wspride
     *
     */
    private class SetImageTask extends AsyncTask<String, Void, Bitmap> {
        private final ImageView mImageView;
        private final Context mContext;
        private final int mBoundingWidth;
        private final int mBoundingHeight;

        public SetImageTask(ImageView imageView, Context context, int maxWidth, int maxHeight) {
            mImageView = imageView;
            mContext = context;
            mBoundingWidth = maxWidth;
            mBoundingHeight = maxHeight;
        }

        protected Bitmap doInBackground(String... file) { 
            return getImageBitmap(file[0]);
        }

        protected void onPostExecute(Bitmap result) {

            if (result != null && mImageView != null) {
                mImageView.setImageBitmap(result);
            }
        }

        public Bitmap getImageBitmap(String filePath) {
            Bitmap bitmap = MediaUtil.inflateDisplayImage(mContext, filePath, mBoundingWidth, mBoundingHeight);

            if (bitmap != null) {
                synchronized(cache) {
                      cache.put(filePath, bitmap);
                  }
              }

            return bitmap;
        }

    }

    /*
     * Override these methods to ensure that our overriding behavior is maintained
     * through these calls. come from ComponentCallsback2
     */
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    }

    @Override
    public void onLowMemory() {
    }

    @Override
    public void onTrimMemory(int level) {
        if (level >= TRIM_MEMORY_MODERATE) {
            cache.evictAll();
        }
    }
}
