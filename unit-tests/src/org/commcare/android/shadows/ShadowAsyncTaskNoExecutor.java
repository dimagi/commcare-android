package org.commcare.android.shadows;

import android.os.AsyncTask;

import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowAsyncTask;

import java.util.concurrent.Executor;

@Implements(AsyncTask.class)
public class ShadowAsyncTaskNoExecutor extends ShadowAsyncTask {

    /**
     * Because robolectric doesn't handle executeOnExecutor very well
     */
    @Override
    public AsyncTask executeOnExecutor(Executor executor, Object[] params) {
        return this.execute(params);
    }
}