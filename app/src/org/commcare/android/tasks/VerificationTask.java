package org.commcare.android.tasks;

import android.os.AsyncTask;

import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.model.MissingMediaException;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.javarosa.core.util.SizeBoundUniqueVector;
import org.javarosa.core.util.SizeBoundVector;

/**
 * This task is responsible for validating app's installed media
 *
 * @author ctsims
 */
public class VerificationTask extends AsyncTask<String, int[], SizeBoundVector<MissingMediaException>> implements TableStateListener {
    private VerificationTaskListener listener;

    public VerificationTask() {
    }

    @Override
    protected SizeBoundVector<MissingMediaException> doInBackground(String... profileRefs) {
        AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform();

        try {
            //This is replicated in the application in a few places.
            ResourceTable global = platform.getGlobalResourceTable();
            SizeBoundUniqueVector<MissingMediaException> problems = new SizeBoundUniqueVector<MissingMediaException>(10);
            global.setStateListener(this);
            global.verifyInstallation(problems);
            if (problems.size() > 0) {
                return problems;
            }
            return null;
        } catch (Exception e) {
            // to-do: make non-resource missing failures have a better exception
            return null;
        }
    }

    @Override
    protected void onProgressUpdate(int[]... values) {
        super.onProgressUpdate(values);
        if (listener != null) {
            listener.updateVerifyProgress(values[0][0], values[0][1]);
        }
    }

    @Override
    protected void onPostExecute(SizeBoundVector<MissingMediaException> problems) {
        if (problems == null) {
            listener.success();
        } else if (listener != null) {
            if (problems.size() == 0) {
                listener.success();
            } else if (problems.size() > 0) {
                listener.onFinished(problems);
            } else {
                listener.failUnknown();
            }
        }

        listener = null;
    }

    @Override
    public void incrementProgress(int complete, int total) {
        this.publishProgress(new int[]{complete, total});
    }

    @Override
    public void resourceStateUpdated(ResourceTable table) {
        // TODO Auto-generated method stub

    }

    public void setListener(VerificationTaskListener listener) {
        this.listener = listener;
    }


}
