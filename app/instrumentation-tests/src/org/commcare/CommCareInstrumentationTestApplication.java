package org.commcare;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import org.commcare.tasks.AsyncRestoreHelper;
import org.commcare.tasks.DataPullTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class CommCareInstrumentationTestApplication extends CommCareApplication implements Application.ActivityLifecycleCallbacks {

    /**
     * We only wanna store the activity that's currently on top of the screen.
     */
    private Activity currentActivity;

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
    public boolean isRunningTest() {
        return true;
    }
}
