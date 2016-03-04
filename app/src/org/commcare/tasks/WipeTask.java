package org.commcare.tasks;

import android.content.Context;
import android.util.Log;

import org.commcare.activities.CommCareWiFiDirectActivity;
import org.commcare.models.database.user.models.FormRecord;
import org.commcare.tasks.templates.CommCareTask;

/**
 * @author ctsims
 */
public abstract class WipeTask extends CommCareTask<String, String, Boolean, CommCareWiFiDirectActivity> {

    Context c;

    public static final int WIPE_TASK_ID = 9213435;

    private final FormRecord[] records;

    public WipeTask(Context c, FormRecord[] records) {
        this.c = c;
        this.taskId = WIPE_TASK_ID;
        this.records = records;
        TAG = WipeTask.class.getSimpleName();
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        //These will never get Zero'd otherwise
        c = null;
    }

    @Override
    protected Boolean doTaskBackground(String... params) {

        if(records == null){
            // that's OK, might just be transferring already transferred forms.
            return true;
        }

        Log.d(TAG, "doing wipe task in background");
        for (FormRecord record : records) {
            FormRecordCleanupTask.wipeRecord(c, record);
        }
        return true;
    }
}
