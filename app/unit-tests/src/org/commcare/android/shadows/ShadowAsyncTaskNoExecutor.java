package org.commcare.android.shadows;

import android.os.AsyncTask;

import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowAsyncTask;
import org.robolectric.shadows.ShadowLegacyAsyncTask;
import org.robolectric.shadows.ShadowPausedAsyncTask;

import java.util.concurrent.Executor;

@Implements(AsyncTask.class)
public class ShadowAsyncTaskNoExecutor extends ShadowPausedAsyncTask {

}