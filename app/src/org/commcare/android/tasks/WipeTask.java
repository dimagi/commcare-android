package org.commcare.android.tasks;

import android.content.Context;
import android.util.Log;

import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.dalvik.activities.CommCareWiFiDirectActivity;

import java.io.File;

/**
 * @author ctsims
 */
public abstract class WipeTask extends CommCareTask<String, String, Boolean, CommCareWiFiDirectActivity>{

    Context c;
    Long[] results;
    File dumpFolder;
    
    public static final int WIPE_TASK_ID = 9213435;
    
    DataSubmissionListener formSubmissionListener;
    FormRecord[] records;

    private static long MAX_BYTES = (5 * 1048576)-1024; // 5MB less 1KB overhead
    
    public WipeTask(Context c, FormRecord[] records) {
        this.c = c;
        this.taskId = WIPE_TASK_ID;
        this.records = records;
    }
    
    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
    }
    
    public void setListeners(DataSubmissionListener submissionListener) {
        this.formSubmissionListener = submissionListener;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        //These will never get Zero'd otherwise
        c = null;
        results = null;
    }
    
    @Override
    protected Boolean doTaskBackground(String... params) {
        
        Log.d(CommCareWiFiDirectActivity.TAG, "doing wipe task in background");
        for(int i = 0 ; i < records.length ; ++i) {
            FormRecord record = records[i];
            FormRecordCleanupTask.wipeRecord(c, record);
        }
        return true;
    }
}
