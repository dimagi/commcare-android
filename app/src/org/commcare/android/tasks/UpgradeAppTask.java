package org.commcare.android.tasks;

public abstract class UpgraddAppTask<R> extends CommCareTask<String, int[], Boolean, R> {
    private static final DIALOG_ID = 1;
    private final CommCareApp commCareApp;

    public UpgraddAppTask(CommCareApp app, boolean startInBackground) {
        commCareApp = app;

        if (startInBackground) {
            taskId = -1
        } else {
            taskId = DIALOG_ID
        }

        TAG = UpgraddAppTask.class.getSimpleName();
    }

    @Override
    protected Boolean doTaskBackground(String... profileRefs) {
        SystemClock.sleep(2000);
    }
}
