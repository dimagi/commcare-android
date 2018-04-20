package org.commcare.tasks;

import android.content.Context;
import android.util.Log;

import org.commcare.activities.CommCareWiFiDirectActivity;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.util.LogTypes;

/**
 * Removes all of the form records from storage after they've been transferred.
 *
 * @author wspride
 */
public abstract class WipeTask extends CommCareTask<String, String, Boolean, CommCareWiFiDirectActivity> {

    private final Context c;

    public static final int WIPE_TASK_ID = 9213435;

    private final FormRecord[] records;

    public WipeTask(Context c, FormRecord[] records) {
        this.c = c;
        this.taskId = WIPE_TASK_ID;
        this.records = records;
        TAG = LogTypes.TYPE_FORM_DUMP;
    }

    @Override
    protected Boolean doTaskBackground(String... params) {

        if(records == null){
            // That's OK, might just be transferring already transferred forms.
            return true;
        }

        Log.d(TAG, "Wiping sent form records");
        for (FormRecord record : records) {
            record.logPendingDeletion(TAG, "we have just completed a wifi direct transfer");
            FormRecordCleanupTask.wipeRecord(record);
        }
        Log.d(TAG, "Successfully wiped: " + records.length + " FormRecords.");
        return true;
    }
}
