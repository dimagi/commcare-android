package org.commcare.connect;

import android.app.Activity;
import android.content.Context;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.commcaresupportlibrary.CommCareLauncher;
import org.commcare.connect.database.ConnectAppDatabaseUtil;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.tasks.templates.CommCareTaskConnector;

import java.security.SecureRandom;
import java.util.ArrayList;

/**
 * Manager class for Connect, handles workflow navigation and opportunity management
 *
 * @author dviggiano
 */
public class ConnectManager {
    private static final int APP_DOWNLOAD_TASK_ID = 4;

    /**
     * Interface for handling callbacks when a Connect activity finishes
     */
    public interface ConnectActivityCompleteListener {
        void connectActivityComplete(boolean success);
    }

    private static volatile ConnectManager manager = null;
    private String primedAppIdForAutoLogin = null;

    //Singleton, private constructor
    private ConnectManager() {
        // Protect against reflection
        if (manager != null) {
            throw new IllegalStateException("Already initialized.");
        }
    }

    private static ConnectManager getInstance() {
        if (manager == null) {
            synchronized (ConnectManager.class) {
                if (manager == null) {
                    manager = new ConnectManager();
                }
            }
        }
        return manager;
    }

    public static ConnectUserRecord getUser(Context context) {
        return ConnectUserDatabaseUtil.getUser(context);
    }

    public static boolean wasAppLaunchedFromConnect(String appId) {
        String primed = getInstance().primedAppIdForAutoLogin;
        getInstance().primedAppIdForAutoLogin = null;
        return primed != null && primed.equals(appId);
    }

    public static boolean isAppInstalled(String appId) {
        boolean installed = false;
        ArrayList<ApplicationRecord> apps = AppUtils.
                getInstalledAppRecords();
        for (ApplicationRecord app : apps) {
            if (appId.equals(app.getUniqueId())) {
                installed = true;
                break;
            }
        }
        return installed;
    }

    private boolean downloading = false;
    private ResourceEngineListener downloadListener = null;

    public static void downloadAppOrResumeUpdates(String installUrl, ResourceEngineListener listener) {
        ConnectManager instance = getInstance();
        instance.downloadListener = listener;
        if (!instance.downloading) {
            instance.downloading = true;
            //Start a new download
            ResourceInstallUtils.startAppInstallAsync(false, APP_DOWNLOAD_TASK_ID, new CommCareTaskConnector<ResourceEngineListener>() {
                @Override
                public void connectTask(CommCareTask task) {

                }

                @Override
                public void startBlockingForTask(int id) {

                }

                @Override
                public void stopBlockingForTask(int id) {
                    instance.downloading = false;
                }

                @Override
                public void taskCancelled() {

                }

                @Override
                public ResourceEngineListener getReceiver() {
                    return instance.downloadListener;
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
            }, installUrl);
        }
    }

    public static String checkAutoLoginAndOverridePassword(Context context, String appId, String username,
                                                           String passwordOrPin, boolean appLaunchedFromConnect, boolean uiInAutoLogin) {
        if (PersonalIdManager.getInstance().isloggedIn()) {
            if (appLaunchedFromConnect) {
                //Configure some things if we haven't already
                ConnectLinkedAppRecord record = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(context,
                        appId, username);
                if (record == null) {
                    record = prepareConnectManagedApp(context, appId, username);
                }

                passwordOrPin = record.getPassword();
            } else if (uiInAutoLogin) {
                String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
                ConnectLinkedAppRecord record = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(context, seatedAppId,
                        username);
                passwordOrPin = record != null ? record.getPassword() : null;

                if (record != null && record.isUsingLocalPassphrase()) {
                    //Report to analytics so we know when this stops happening
                    FirebaseAnalyticsUtil.reportCccAppAutoLoginWithLocalPassphrase(seatedAppId);
                }
            }
        }

        return passwordOrPin;
    }

    public static ConnectLinkedAppRecord prepareConnectManagedApp(Context context, String appId, String username) {
        //Create app password
        String password = generatePassword();

        //Store ConnectLinkedAppRecord (note worker already linked)
        return ConnectAppDatabaseUtil.storeApp(context, appId, username, true, password, true, false);
    }

    public static String generatePassword() {
        int passwordLength = 20;

        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_!.?";
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder password = new StringBuilder(passwordLength);
        for (int i = 0; i < passwordLength; i++) {
            password.append(charSet.charAt(secureRandom.nextInt(charSet.length())));
        }

        return password.toString();
    }

    public static void launchApp(Activity activity, boolean isLearning, String appId) {
        CommCareApplication.instance().closeUserSession();

        String appType = isLearning ? "Learn" : "Deliver";
        FirebaseAnalyticsUtil.reportCccAppLaunch(appType, appId);

        getInstance().primedAppIdForAutoLogin = appId;

        CommCareLauncher.launchCommCareForAppId(activity, appId);

        activity.finish();
    }
}
