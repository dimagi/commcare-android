package org.commcare.android.tasks;

import android.content.Context;

import org.commcare.activities.DataPullController;
import org.commcare.activities.LoginMode;
import org.commcare.dalvik.application.CommCareApp;

/**
 * Setup key record from server for external call. Thus post key setup methods
 * are empty since external calls don't require data pulls post key setup
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class ExternalManageKeyRecordTask extends ManageKeyRecordTask<DataPullController> {
    public ExternalManageKeyRecordTask(Context c, int taskId, String username, String password,
                                       LoginMode loginMode, CommCareApp app, boolean restoreSession) {
        super(c, taskId, username, password, loginMode, app, restoreSession, false);
    }

    @Override
    protected void keysReadyForSync(DataPullController restorer) {
    }

    @Override
    protected void keysLoginComplete(DataPullController restorer) {
    }

    @Override
    protected void keysDoneOther(DataPullController restorer, HttpCalloutOutcomes outcomes) {
    }

    @Override
    protected void deliverUpdate(DataPullController restorer, String... update) {
    }
}
