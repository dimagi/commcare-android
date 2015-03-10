package org.commcare.android.tasks;

import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.activities.CommCareVerificationActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.commcare.resources.model.MissingMediaException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.javarosa.core.util.SizeBoundUniqueVector;
import org.javarosa.core.util.SizeBoundVector;

import android.content.Context;
import android.os.AsyncTask;

/**
 * This task is responsible for 
 * 
 * @author ctsims
 *
 */
public class VerificationTask extends AsyncTask<String, int[], SizeBoundVector<MissingMediaException>> implements TableStateListener {
    
    VerificationTaskListener listener;
    Context c;
    
    public static final int PHASE_CHECKING = 0;
    public static final int PHASE_DOWNLOAD = 1;
    public static final int PHASE_COMMIT = 2;
    
    Resource missingResource = null;
    int badReqCode = -1;
    private int phase = -1;
    
    //Results passed by inherited AsyncTask functions to determine exit behavior
    
    public static final int STATUS_VERIFY_SUCCESS = 0;
    public static final int STATUS_VERIFY_FAIL = 1;
    public static final int STATUS_FAIL_UNKNOWN = 2;
    
    public VerificationTask(Context c) throws SessionUnavailableException{
        this.c = c;
    }
    
    /* (non-Javadoc)
     * @see android.os.AsyncTask#doInBackground(Params[])
     */
    protected SizeBoundVector<MissingMediaException> doInBackground(String... profileRefs) {
        AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform();
        
        try {
            //This is replicated in the application in a few places.
            ResourceTable global = platform.getGlobalResourceTable();
            SizeBoundUniqueVector<MissingMediaException> problems = new SizeBoundUniqueVector<MissingMediaException>(10);
            global.setStateListener(this);
            global.verifyInstallation(problems);
            if(problems.size()>0){
                return problems;
            }
            return null;
        }
        catch(Exception e) {
            // to-do: make non-resource missing failures have a better exception
            return null;
        }
    }
    
    /* (non-Javadoc)
     * @see android.os.AsyncTask#onProgressUpdate(Progress[])
     */
    @Override
    protected void onProgressUpdate(int[]... values) {
        super.onProgressUpdate(values);
        if(listener != null) {
            ((CommCareVerificationActivity)listener).updateVerifyProgress(values[0][0], values[0][1]);
        }
    }

    public void setListener(VerificationTaskListener listener) {
        this.listener = listener;
    }

    /*
     * (non-Javadoc)
     * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
     */
    @Override
    protected void onPostExecute(SizeBoundVector<MissingMediaException> problems) {
        if(problems == null){
            listener.success();
        }
        else if(listener != null) {
            // ignores the validation result when superuser mode is enabled, useful for dev/testing
            if(problems.size() == 0 || DeveloperPreferences.isSuperuserEnabled()){
                listener.success();
            } else if(problems.size() > 0){
                listener.onFinished(problems);
            } else{
                listener.failUnknown();
            }
        }

        listener = null;
        c = null;
    }

    public void incrementProgress(int complete, int total) {
        this.publishProgress(new int[] {complete, total});
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.TableStateListener#resourceStateUpdated(org.commcare.resources.model.ResourceTable)
     */
    @Override
    public void resourceStateUpdated(ResourceTable table) {
        // TODO Auto-generated method stub
        
    }
    
}
