package org.commcare.sync;

import android.content.Context;
import android.os.AsyncTask;

import net.sqlcipher.database.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.commcare.CommCareApplication;
import org.commcare.activities.SyncCapableCommCareActivity;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.models.FormRecordProcessor;
import org.commcare.suite.model.Profile;
import org.commcare.tasks.DataSubmissionListener;
import org.commcare.tasks.FormRecordCleanupTask;
import org.commcare.tasks.FormSubmissionProgressBarListener;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.util.LogTypes;
import org.commcare.utils.FormUploadResult;
import org.commcare.utils.FormUploadUtil;
import org.commcare.utils.QuarantineUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.notifications.NotificationMessage;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.commcare.views.notifications.ProcessIssues;
import org.javarosa.core.model.User;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import javax.crypto.spec.SecretKeySpec;

import static org.commcare.sync.FormSubmissionHelper.PROGRESS_ALL_PROCESSED;
import static org.commcare.sync.FormSubmissionHelper.SUBMISSION_BEGIN;
import static org.commcare.sync.FormSubmissionHelper.SUBMISSION_DONE;
import static org.commcare.sync.FormSubmissionHelper.SUBMISSION_FAIL;
import static org.commcare.sync.FormSubmissionHelper.SUBMISSION_NOTIFY;
import static org.commcare.sync.FormSubmissionHelper.SUBMISSION_START;
import static org.commcare.sync.FormSubmissionHelper.SUBMISSION_SUCCESS;

/**
 * @author ctsims
 */
public abstract class ProcessAndSendTask<R> extends CommCareTask<Void, Long, FormUploadResult, R>
        implements CancellationChecker, FormSubmissionProgressListener {

    private final FormSubmissionHelper mFormSubmissionHelper;

    private final int sendTaskId;

    public static final int PROCESSING_PHASE_ID = 8;
    public static final int SEND_PHASE_ID = 9;
    public static final int PROCESSING_PHASE_ID_NO_DIALOG = -8;
    public static final int SEND_PHASE_ID_NO_DIALOG = -9;

    private FormSubmissionProgressBarListener progressBarListener;

    private List<DataSubmissionListener> formSubmissionListeners = new ArrayList<>();


    protected ProcessAndSendTask(Context c) {
        this(c, true);
    }

    /**
     * @param inSyncMode blocks the user with a sync dialog
     */
    protected ProcessAndSendTask(Context c, boolean inSyncMode) {
        mFormSubmissionHelper = new FormSubmissionHelper(c, this, this);

        if (inSyncMode) {
            this.sendTaskId = SEND_PHASE_ID;
            this.taskId = PROCESSING_PHASE_ID;
        } else {
            this.sendTaskId = SEND_PHASE_ID_NO_DIALOG;
            this.taskId = PROCESSING_PHASE_ID_NO_DIALOG;
        }
    }

    @Override
    protected FormUploadResult doTaskBackground(Void... voids) {
        return mFormSubmissionHelper.uploadForms();
    }


    @Override
    protected void onProgressUpdate(Long... values) {
        if (values.length == 1 && values[0] == PROGRESS_ALL_PROCESSED) {
            this.transitionPhase(sendTaskId);
        }
        super.onProgressUpdate(values);
        mFormSubmissionHelper.dispatchProgress(formSubmissionListeners, values);
    }


    public void addProgressBarSubmissionListener(FormSubmissionProgressBarListener listener) {
        this.progressBarListener = listener;
        addSubmissionListener(listener);
    }

    public void addSubmissionListener(DataSubmissionListener submissionListener) {
        formSubmissionListeners.add(submissionListener);
    }

    @Override
    public boolean wasProcessCancelled() {
//        Apparently Cancelled doesn't result in the task status being set to !Running for reasons which baffle me.
        return getStatus() != Status.RUNNING || isCancelled();
    }

    @Override
    protected void onPostExecute(FormUploadResult result) {
        super.onPostExecute(result);
        clearState();
    }

    private void clearState() {
        mFormSubmissionHelper.cleanUp();
    }


    protected String getLabelForFormsSent() {
        int successfulSends = mFormSubmissionHelper.getSuccessfulSends();
        String label;
        switch (successfulSends) {
            case 0:
                label = Localization.get("sync.success.sent.none");
                break;
            case 1:
                label = Localization.get("sync.success.sent.singular");
                break;
            default:
                label = Localization.get("sync.success.sent",
                        new String[]{String.valueOf(successfulSends)});
        }
        return label;
    }


    @Override
    protected void onCancelled() {
        super.onCancelled();

        mFormSubmissionHelper.dispatchProgress(formSubmissionListeners, SUBMISSION_DONE, SUBMISSION_FAIL);

        // If cancellation happened due to logout, notify user
        try {
            CommCareApplication.instance().getSession().getLoggedInUser();
        } catch (SessionUnavailableException e) {
            CommCareApplication.notificationManager().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.LoggedOut));
        }

        clearState();
    }

    @Override
    public void publishUpdateProgress(Long... progress) {
        publishProgress(progress);
    }

    @Override
    public void connect(CommCareTaskConnector<R> connector) {
        super.connect(connector);
        if (progressBarListener != null) {
            progressBarListener.attachToNewActivity(
                    (SyncCapableCommCareActivity)connector.getReceiver());
        }
    }

    protected int getSuccessfulSends() {
        return mFormSubmissionHelper.getSuccessfulSends();
    }
}
