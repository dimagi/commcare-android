package org.commcare;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import org.commcare.tasks.AsyncRestoreHelper;
import org.commcare.tasks.DataPullTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.espresso.idling.concurrent.IdlingThreadPoolExecutor;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.ExecutorsKt;

public class CommCareInstrumentationTestApplication extends CommCareApplication implements Application.ActivityLifecycleCallbacks {

    /**
     * We only wanna store the activity that's currently on top of the screen.
     */
    private Activity currentActivity;
    public IdlingThreadPoolExecutor idlingThreadPoolExecutor = new IdlingThreadPoolExecutor("testDispatcher",
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors(),
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue(),
            Executors.defaultThreadFactory());
    private CoroutineDispatcher asyncDispatcher = ExecutorsKt.from(idlingThreadPoolExecutor);

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public AsyncRestoreHelper getAsyncRestoreHelper(DataPullTask task) {
        return new AsyncRestoreHelperMock(task);
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {

    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {

    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        currentActivity = activity;
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {

    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {

    }

    public Activity getCurrentActivity() {
        return currentActivity;
    }

    @Override
    public CoroutineDispatcher serialDispatcher() {
        return asyncDispatcher;
    }

    @Override
    public CoroutineDispatcher parallelDispatcher() {
        return asyncDispatcher;
    }
}
