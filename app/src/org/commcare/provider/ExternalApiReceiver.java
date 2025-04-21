package org.commcare.provider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.activities.LoginMode;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.AndroidSharedKeyRecord;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.encryption.ByteEncrypter;
import org.commcare.preferences.ServerUrls;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.ExternalManageKeyRecordTask;
import org.commcare.sync.ProcessAndSendTask;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.util.LogTypes;
import org.commcare.utils.FormUploadResult;
import org.javarosa.core.model.User;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.NoSuchElementException;

/**
 * This broadcast receiver is the central point for incoming API calls from other apps.
 * <p/>
 * Right now it's a mess, but at some point we'll go ahead and pull out most of the
 * things you can do here as
 *
 * @author ctsims
 */
public class ExternalApiReceiver extends BroadcastReceiver {

    private final CommCareTaskConnector dummyconnector = new CommCareTaskConnector() {

        @Override
        public void connectTask(CommCareTask task) {
        }

        @Override
        public void startBlockingForTask(int id) {
        }

        @Override
        public void stopBlockingForTask(int id) {
        }

        @Override
        public void taskCancelled() {
        }

        @Override
        public Object getReceiver() {
            return null;
        }

        @Override
        public void startTaskTransition() {
        }

        @Override
        public void stopTaskTransition(int taskId) {
        }

        @Override
        public void hideTaskCancelButton() {

        }
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.hasExtra(AndroidSharedKeyRecord.EXTRA_KEY_ID)) {
            return;
        }

        String keyId = intent.getStringExtra(AndroidSharedKeyRecord.EXTRA_KEY_ID);
        SqlStorage<AndroidSharedKeyRecord> storage = CommCareApplication.instance().getGlobalStorage(AndroidSharedKeyRecord.class);
        AndroidSharedKeyRecord sharingKey;
        try {
            sharingKey = storage.getRecordForValue(AndroidSharedKeyRecord.META_KEY_ID, keyId);
        } catch (NoSuchElementException nsee) {
            Logger.log(LogTypes.TYPE_MAINTENANCE, "Invalid key encountered for intent " + intent.getAction());
            return;
        }

        Bundle b = sharingKey.getIncomingCallout(intent);

        performAction(context, b);
    }

    private void performAction(final Context context, Bundle b) {
        String ccAction = b.getString("commcareaction", "");
        if ("login".equals(ccAction)) {
            String username = b.getString("username");
            String password = b.getString("password");
            tryLocalLogin(context, username, password);
        } else if ("sync".equals(ccAction)) {
            checkAndStartUnsentTask(context);
        }
    }

    private void checkAndStartUnsentTask(final Context context) {

        ProcessAndSendTask<Object> mProcess = new ProcessAndSendTask<Object>(
                context) {
            @Override
            protected void deliverResult(Object receiver, FormUploadResult result) {
                if (result == FormUploadResult.FULL_SUCCESS) {
                    //OK, all forms sent, sync time
                    syncData(context);

                } else {
                    Toast.makeText(context, Localization.get("sync.fail.unsent"), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            protected void deliverUpdate(Object receiver, Long... update) {
            }

            @Override
            protected void deliverError(Object receiver, Exception e) {
            }
        };

        if (CommCareApplication.notificationManager().areNotificationsEnabled()) {
            mProcess.addSubmissionListener(
                    CommCareApplication.instance().getSession().getListenerForSubmissionNotification());
        }
        mProcess.connect(dummyconnector);
        mProcess.execute();
    }

    private void syncData(final Context context) {
        User u = CommCareApplication.instance().getSession().getLoggedInUser();

        SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();

        DataPullTask<Object> mDataPullTask = new DataPullTask<Object>(
                u.getUsername(),
                u.getCachedPwd(),
                u.getUniqueId(),
                ServerUrls.getDataServerKey(),
                context,
                false) {

            @Override
            protected void deliverResult(Object receiver, ResultAndError<PullTaskResult> resultAndErrorMessage) {
                PullTaskResult result = resultAndErrorMessage.data;
                if (result != PullTaskResult.DOWNLOAD_SUCCESS) {
                    Toast.makeText(context, "CommCare couldn't sync. Please try to sync from CommCare directly for more information", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, "CommCare synced!", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            protected void deliverUpdate(Object receiver, Integer... update) {
            }

            @Override
            protected void deliverError(Object receiver, Exception e) {
            }

        };
        mDataPullTask.connect(dummyconnector);
        mDataPullTask.execute();
    }

    private boolean tryLocalLogin(Context context, String uname, String password) {
        try {
            UserKeyRecord matchingRecord = null;
            for (UserKeyRecord record : CommCareApplication.instance().getCurrentApp().getStorage(UserKeyRecord.class)) {
                if (!record.getUsername().equals(uname)) {
                    continue;
                }
                String hash = record.getPasswordHash();
                if (hash.contains("$")) {
                    String alg = "sha1";
                    String salt = hash.split("\\$")[1];
                    String check = hash.split("\\$")[2];
                    MessageDigest md = MessageDigest.getInstance("SHA-1");
                    BigInteger number = new BigInteger(1, md.digest((salt + password).getBytes()));
                    String hashed = number.toString(16);

                    while (hashed.length() < check.length()) {
                        hashed = "0" + hashed;
                    }

                    if (hash.equals(alg + "$" + salt + "$" + hashed)) {
                        matchingRecord = record;
                    }
                }
            }

            if (matchingRecord == null) {
                return false;
            }

            byte[] key = ByteEncrypter.unwrapByteArrayWithString(matchingRecord.getEncryptedKey(), password);
            CommCareApplication.instance().startUserSession(key, matchingRecord, false);
            ExternalManageKeyRecordTask mKeyRecordTask = new ExternalManageKeyRecordTask(context, 0,
                    matchingRecord.getUsername(), password, LoginMode.PASSWORD,
                    CommCareApplication.instance().getCurrentApp(), false);

            mKeyRecordTask.connect(dummyconnector);
            mKeyRecordTask.execute();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
