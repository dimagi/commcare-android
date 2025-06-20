package org.commcare.connect;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.activities.connect.ConnectMessagingActivity;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobLearningRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.commcaresupportlibrary.CommCareLauncher;
import org.commcare.connect.database.ConnectAppDatabaseUtil;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectDatabaseUtils;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiConnect;
import org.commcare.connect.network.ApiPersonalId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.tasks.templates.CommCareTaskConnector;

import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import androidx.annotation.Nullable;

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
    private PersonalIdManager.PersonalIdStatus connectStatus = PersonalIdManager.PersonalIdStatus.NotIntroduced;
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

    public static void init(Context context) {
        ConnectManager manager = getInstance();

        if (manager.connectStatus == PersonalIdManager.PersonalIdStatus.NotIntroduced) {
            ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(context);
            if (user != null) {
                boolean registering = user.getRegistrationPhase() != ConnectConstants.PERSONALID_NO_ACTIVITY;
                manager.connectStatus = registering ? PersonalIdManager.PersonalIdStatus.Registering :
                        PersonalIdManager.PersonalIdStatus.LoggedIn;

                String remotePassphrase = ConnectDatabaseUtils.getConnectDbEncodedPassphrase(context, false);
                if (remotePassphrase == null) {
                    getRemoteDbPassphrase(context, user);
                }
            } else if (ConnectDatabaseHelper.isDbBroken()) {
                //Corrupt DB, inform user to recover
                ConnectDatabaseHelper.crashDb();
            }
        }
    }

    private static final DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());

    public static String formatDate(Date date) {
        return dateFormat.format(date);
    }

    private static final DateFormat paymentDateFormat = new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault());

    public static String paymentDateFormat(Date date) {
        return paymentDateFormat.format(date);
    }

    public static String formatDateTime(Date date) {
        return SimpleDateFormat.getDateTimeInstance().format(date);
    }

    public static ConnectUserRecord getUser(Context context) {
        return ConnectUserDatabaseUtil.getUser(context);
    }

    private static ConnectJobRecord activeJob = null;


    public static boolean wasAppLaunchedFromConnect(String appId) {
        String primed = getInstance().primedAppIdForAutoLogin;
        getInstance().primedAppIdForAutoLogin = null;
        return primed != null && primed.equals(appId);
    }

    public static void setActiveJob(ConnectJobRecord job) {
        activeJob = job;
    }

    public static ConnectJobRecord getActiveJob() {
        return activeJob;
    }

    public static void goToMessaging(Context context) {
        Intent i = new Intent(context, ConnectMessagingActivity.class);
        context.startActivity(i);
    }

    public static ConnectJobRecord setConnectJobForApp(Context context, String appId) {
        ConnectJobRecord job = null;
        ConnectAppRecord appRecord = getAppRecord(context, appId);
        if (appRecord != null) {
            job = ConnectJobUtils.getCompositeJob(context, appRecord.getJobId());
        }
        setActiveJob(job);
        return job;
    }

    public static boolean shouldShowJobStatus(Context context, String appId) {
        ConnectAppRecord record = getAppRecord(context, appId);
        if(record == null || activeJob == null) {
            return false;
        }

        //Only time not to show is when we're in learn app but job is in delivery state
        return !record.getIsLearning() || activeJob.getStatus() != ConnectJobRecord.STATUS_DELIVERING;
    }

    public static ConnectAppRecord getAppRecord(Context context, String appId) {
        return ConnectJobUtils.getAppRecord(context, appId);
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

    public static void getRemoteDbPassphrase(Context context, ConnectUserRecord user) {
        ApiPersonalId.fetchDbPassphrase(context, user, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(
                            StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        JSONObject json = new JSONObject(responseAsString);
                        String key = ConnectConstants.CONNECT_KEY_DB_KEY;
                        if (json.has(key)) {
                            ConnectDatabaseHelper.handleReceivedDbPassphrase(context, json.getString(key));
                        }
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    Logger.exception("Parsing return from DB key request", e);
                }
            }

            @Override
            public void processFailure(int responseCode, @Nullable InputStream errorResponse) {
                Logger.log("ERROR", String.format(Locale.getDefault(), "Failed: %d", responseCode));
            }

            @Override
            public void processNetworkFailure() {
                Logger.log("ERROR", "Failed (network)");
            }

            @Override
            public void processTokenUnavailableError() {
                Logger.log("ERROR", "Failed (token unavailable)");
            }

            @Override
            public void processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenDeniedException();
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(context);
            }
        });
    }

    public static void updateLearningProgress(Context context, ConnectJobRecord job, ConnectActivityCompleteListener listener) {
        ConnectUserRecord user = getUser(context);
        ApiConnect.getLearnProgress(context, user, job.getJobId(), new IApiCallback() {
            private static void reportApiCall(boolean success) {
                FirebaseAnalyticsUtil.reportCccApiLearnProgress(success);
            }

            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        //Parse the JSON
                        JSONObject json = new JSONObject(responseAsString);

                        String key = "completed_modules";
                        JSONArray modules = json.getJSONArray(key);
                        List<ConnectJobLearningRecord> learningRecords = new ArrayList<>(modules.length());
                        for (int i = 0; i < modules.length(); i++) {
                            JSONObject obj = (JSONObject) modules.get(i);
                            ConnectJobLearningRecord record = ConnectJobLearningRecord.fromJson(obj, job.getJobId());
                            learningRecords.add(record);
                        }
                        job.setLearnings(learningRecords);
                        job.setCompletedLearningModules(learningRecords.size());

                        key = "assessments";
                        JSONArray assessments = json.getJSONArray(key);
                        List<ConnectJobAssessmentRecord> assessmentRecords = new ArrayList<>(assessments.length());
                        for (int i = 0; i < assessments.length(); i++) {
                            JSONObject obj = (JSONObject) assessments.get(i);
                            ConnectJobAssessmentRecord record = ConnectJobAssessmentRecord.fromJson(obj, job.getJobId());
                            assessmentRecords.add(record);
                        }
                        job.setAssessments(assessmentRecords);

                        ConnectJobUtils.updateJobLearnProgress(context, job);
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    Logger.exception("Parsing return from learn_progress request", e);
                }

                reportApiCall(true);
                listener.connectActivityComplete(true);
            }

            @Override
            public void processFailure(int responseCode, @Nullable InputStream errorResponse) {
                Logger.log("ERROR", String.format(Locale.getDefault(), "Failed: %d", responseCode));
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processNetworkFailure() {
                Logger.log("ERROR", "Failed (network)");
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processTokenUnavailableError() {
                ConnectNetworkHelper.handleTokenUnavailableException(context);
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenDeniedException();
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(context);
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }
        });
    }

    public static void updateDeliveryProgress(Context context, ConnectJobRecord job, ConnectActivityCompleteListener listener) {
        ConnectUserRecord user = getUser(context);
        ApiConnect.getDeliveries(context, user, job.getJobId(), new IApiCallback() {
            private static void reportApiCall(boolean success) {
                FirebaseAnalyticsUtil.reportCccApiDeliveryProgress(success);
            }

            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                boolean success = true;
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        //Parse the JSON
                        JSONObject json = new JSONObject(responseAsString);

                        boolean updatedJob = false;
                        String key = "max_payments";
                        if (json.has(key)) {
                            job.setMaxVisits(json.getInt(key));
                            updatedJob = true;
                        }

                        key = "end_date";
                        if (json.has(key)) {
                            job.setProjectEndDate(DateUtils.parseDate(json.getString(key)));
                            updatedJob = true;
                        }

                        key = "payment_accrued";
                        if (json.has(key)) {
                            job.setPaymentAccrued(json.getInt(key));
                            updatedJob = true;
                        }

                        key = "is_user_suspended";
                        if (json.has(key)) {
                            job.setIsUserSuspended(json.getBoolean(key));
                            updatedJob = true;
                        }

                        if (updatedJob) {
                            job.setLastDeliveryUpdate(new Date());
                            ConnectJobUtils.upsertJob(context, job);
                        }

                        List<ConnectJobDeliveryRecord> deliveries = new ArrayList<>(json.length());
                        key = "deliveries";
                        if (json.has(key)) {
                            JSONArray array = json.getJSONArray(key);
                            for (int i = 0; i < array.length(); i++) {
                                JSONObject obj = (JSONObject) array.get(i);
                                deliveries.add(ConnectJobDeliveryRecord.fromJson(obj, job.getJobId()));
                            }

                            //Store retrieved deliveries
                            ConnectJobUtils.storeDeliveries(context, deliveries, job.getJobId(), true);

                            job.setDeliveries(deliveries);
                        }

                        List<ConnectJobPaymentRecord> payments = new ArrayList<>();
                        key = "payments";
                        if (json.has(key)) {
                            JSONArray array = json.getJSONArray(key);
                            for (int i = 0; i < array.length(); i++) {
                                JSONObject obj = (JSONObject) array.get(i);
                                payments.add(ConnectJobPaymentRecord.fromJson(obj, job.getJobId()));
                            }

                            ConnectJobUtils.storePayments(context, payments, job.getJobId(), true);

                            job.setPayments(payments);
                        }
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    Logger.exception("Parsing return from delivery progress request", e);
                    success = false;
                }

                reportApiCall(success);
                listener.connectActivityComplete(success);
            }

            @Override
            public void processFailure(int responseCode, @Nullable InputStream errorResponse) {
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processNetworkFailure() {
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processTokenUnavailableError() {
                ConnectNetworkHelper.handleTokenUnavailableException(context);
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenDeniedException();
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(context);
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }
        });
    }

    public static void updatePaymentConfirmed(Context context, final org.commcare.android.database.connect.models.ConnectJobPaymentRecord payment, boolean confirmed, ConnectActivityCompleteListener listener) {
        ConnectUserRecord user = getUser(context);
        ApiConnect.setPaymentConfirmed(context, user, payment.getPaymentId(), confirmed, new IApiCallback() {
            private void reportApiCall(boolean success) {
                FirebaseAnalyticsUtil.reportCccApiPaymentConfirmation(success);
            }

            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                payment.setConfirmed(confirmed);
                ConnectJobUtils.storePayment(context, payment);

                //No need to report to user
                reportApiCall(true);
                listener.connectActivityComplete(true);
            }

            @Override
            public void processFailure(int responseCode, @Nullable InputStream errorResponse) {
                Toast.makeText(context, R.string.connect_payment_confirm_failed, Toast.LENGTH_SHORT).show();
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processNetworkFailure() {
                Toast.makeText(context, R.string.connect_payment_confirm_failed, Toast.LENGTH_SHORT).show();
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processTokenUnavailableError() {
                ConnectNetworkHelper.handleTokenUnavailableException(context);
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenDeniedException();
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(context);
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }
        });
    }

}
