package org.commcare.android.tasks;

import java.io.File;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.activities.CommCareWiFiDirectActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.util.CommCarePlatform;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * @author ctsims
 *
 */
public abstract class WipeTask extends CommCareTask<String, String, Boolean, CommCareWiFiDirectActivity>{

    @Nullable
    Context c;
    @Nullable
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
    
    /* (non-Javadoc)
     * @see android.os.AsyncTask#onProgressUpdate(Progress[])
     */
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
    }
    
    public void setListeners(DataSubmissionListener submissionListener) {
        this.formSubmissionListener = submissionListener;
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.android.tasks.templates.CommCareTask#onPostExecute(java.lang.Object)
     */
    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        //These will never get Zero'd otherwise
        c = null;
        results = null;
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.tasks.templates.CommCareTask#doTaskBackground(java.lang.Object[])
     */
    @NonNull
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
