package org.commcare.connect;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.ConnectSsoHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.preferences.AppManagerDeveloperPreferences;
import org.commcare.utils.CrashUtil;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;

/**
 * Manager class for ConnectID, handles workflow navigation and user management
 *
 * @author dviggiano
 */
public class ConnectManager {
    public static int getFailureAttempt() {
        return ConnectManager.getInstance().failedPinAttempts;
    }

    public static void setFailureAttempt(int failureAttempt) {
        ConnectManager.getInstance().failedPinAttempts = failureAttempt;
    }

    /**
     * Enum representing the current state of ConnectID
     */
    public enum ConnectIdStatus {
        NotIntroduced,
        Registering,
        LoggedIn
    }

    /**
     * Interface for handling callbacks when a ConnectID activity finishes
     */
    public interface ConnectActivityCompleteListener {
        void connectActivityComplete(boolean success);
    }

    private static ConnectManager manager = null;
    private ConnectIdStatus connectStatus = ConnectIdStatus.NotIntroduced;
    private CommCareActivity<?> parentActivity;
    private ConnectActivityCompleteListener loginListener;

    //Singleton, private constructor
    private ConnectManager() {
    }

    private static ConnectManager getInstance() {
        if (manager == null) {
            manager = new ConnectManager();
        }

        return manager;
    }

    public static void init(CommCareActivity<?> parent) {
        ConnectManager manager = getInstance();
        manager.parentActivity = parent;

        if(manager.connectStatus == ConnectIdStatus.NotIntroduced) {
            ConnectUserRecord user = ConnectDatabaseHelper.getUser(manager.parentActivity);
            if (user != null) {
                boolean registering = user.getRegistrationPhase() != ConnectTask.CONNECT_NO_ACTIVITY;
                manager.connectStatus = registering ? ConnectIdStatus.Registering : ConnectIdStatus.LoggedIn;

                String remotePassphrase = ConnectDatabaseHelper.getConnectDbEncodedPassphrase(parent, false);
                if(remotePassphrase == null) {
                    getRemoteDbPassphrase(parent, user);
                }
            } else if(ConnectDatabaseHelper.isDbBroken()) {
                //Corrupt DB, inform user to recover
                ConnectDatabaseHelper.handleCorruptDb(parent);
            }
        }
    }

    public static void setParent(CommCareActivity<?> parent) {
        getInstance().parentActivity = parent;
    }

    public static boolean isConnectIdIntroduced() {
        return AppManagerDeveloperPreferences.isConnectIdEnabled()
                && getInstance().connectStatus == ConnectIdStatus.LoggedIn;
    }

    public static boolean isUnlocked() {
        return AppManagerDeveloperPreferences.isConnectIdEnabled()
                && getInstance().connectStatus == ConnectIdStatus.LoggedIn;
    }

    private static final DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
    public static String formatDate(Date date) {
        return dateFormat.format(date);
    }

    public static boolean shouldShowSecondaryPhoneConfirmationTile(Context context) {
        boolean show = false;

        if(isConnectIdIntroduced()) {
            ConnectUserRecord user = getUser(context);
            show = !user.getSecondaryPhoneVerified();
        }

        return show;
    }

    public static void updateSecondaryPhoneConfirmationTile(Context context, ConstraintLayout tile, boolean show, View.OnClickListener listener) {
        tile.setVisibility(show ? View.VISIBLE : View.GONE);

        if(show) {
            ConnectUserRecord user = ConnectManager.getUser(context);
            String dateStr = ConnectManager.formatDate(user.getSecondaryPhoneVerifyByDate());
            String message = context.getString(R.string.login_connect_secondary_phone_message, dateStr);

            TextView view = tile.findViewById(R.id.connect_phone_label);
            view.setText(message);

            TextView yesButton = tile.findViewById(R.id.connect_phone_yes_button);
            yesButton.setOnClickListener(listener);

            TextView noButton = tile.findViewById(R.id.connect_phone_no_button);
            noButton.setOnClickListener(v -> {
                tile.setVisibility(View.GONE);
            });
        }
    }

    public static boolean shouldShowSignInMenuOption() {
        if (!AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            return false;
        }

        return getInstance().connectStatus != ConnectIdStatus.LoggedIn;
    }

    public static boolean shouldShowSignOutMenuOption() {
        if (!AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            return false;
        }

        return getInstance().connectStatus == ConnectIdStatus.LoggedIn;
    }

    public static boolean shouldShowConnectButton() {
        if (!AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            return false;
        }

        return getInstance().connectStatus == ConnectIdStatus.LoggedIn;
    }

    public static boolean isConnectTask(int code) {
        return ConnectTask.isConnectTaskCode(code);
    }

    public static void handleFinishedActivity(CommCareActivity<?> activity, int requestCode, int resultCode, Intent intent) {
        getInstance().parentActivity = activity;
        if(ConnectIdWorkflows.handleFinishedActivity(activity, requestCode, resultCode, intent)) {
            getInstance().connectStatus = ConnectIdStatus.Registering;
        }
    }
    private static void completeSignin() {
        ConnectManager instance = getInstance();
        instance.connectStatus = ConnectIdStatus.LoggedIn;

        //scheduleHearbeat(); //When Connect is ready
        CrashUtil.registerConnectUser();

        if(instance.loginListener != null) {
            instance.loginListener.connectActivityComplete(true);
        }
    }

    public static ConnectUserRecord getUser(Context context) {
        return ConnectDatabaseHelper.getUser(context);
    }

    public static void forgetUser() {
        ConnectManager manager = getInstance();

        ConnectDatabaseHelper.forgetUser(manager.parentActivity);

        ConnectIdWorkflows.reset();

        manager.connectStatus = ConnectIdStatus.NotIntroduced;
        manager.loginListener = null;
    }

    private int failedPinAttempts = 0;

    public static void unlockConnect(CommCareActivity<?> parent, ConnectActivityCompleteListener listener) {
        if(manager.connectStatus == ConnectIdStatus.LoggedIn) {
            ConnectIdWorkflows.unlockConnect(parent, success -> {
                if(success) {
                    completeSignin();
                }
                listener.connectActivityComplete(success);
            });
        }
    }

    public static void registerUser(CommCareActivity<?> parent, ConnectActivityCompleteListener listener) {
        ConnectManager manager = getInstance();
        ConnectIdWorkflows.beginRegistration(parent, manager.connectStatus, success -> {
            if(success) {
                completeSignin();
            }
            listener.connectActivityComplete(success);
        });
    }

    public static void handleConnectButtonPress(CommCareActivity<?> parent, ConnectActivityCompleteListener listener) {
        ConnectManager manager = getInstance();
        manager.parentActivity = parent;
        manager.loginListener = listener;

        switch (manager.connectStatus) {
            case NotIntroduced, Registering -> {
                ConnectIdWorkflows.beginRegistration(parent, manager.connectStatus, success -> {
                    if(success) {
                        completeSignin();
                    }
                    listener.connectActivityComplete(success);
                });
            }
        }
    }

    public static void verifySecondaryPhone(CommCareActivity<?> parent, ConnectActivityCompleteListener listener) {
        ConnectIdWorkflows.beginSecondaryPhoneVerification(parent, listener);
    }

    public static void forgetAppCredentials(String appId, String userId) {
        ConnectLinkedAppRecord record = ConnectDatabaseHelper.getAppData(manager.parentActivity, appId, userId);
        if (record != null) {
            ConnectDatabaseHelper.deleteAppData(manager.parentActivity, record);
        }
    }

    public static void checkConnectIdLink(CommCareActivity<?> activity, boolean autoLoggedIn, String appId, String username, String password, ConnectActivityCompleteListener callback) {
        if(isLoginManagedByConnectId(appId, username)) {
            //ConnectID is configured
            if(!autoLoggedIn) {
                //See if user wishes to permanently sever the connection
                StandardAlertDialog d = new StandardAlertDialog(activity,
                        activity.getString(R.string.login_unlink_connectid_title),
                        activity.getString(R.string.login_unlink_connectid_message));

                d.setPositiveButton(activity.getString(R.string.login_link_connectid_yes), (dialog, which) -> {
                    activity.dismissAlertDialog();

                    unlockConnect(activity, success -> {
                        if(success) {
                            ConnectLinkedAppRecord linkedApp = ConnectDatabaseHelper.getAppData(activity, appId, username);
                            if(linkedApp != null) {
                                linkedApp.severConnectIdLink();
                                ConnectDatabaseHelper.storeApp(activity, linkedApp);
                            }
                        }

                        callback.connectActivityComplete(success);
                    });
                });

                d.setNegativeButton(activity.getString(R.string.login_link_connectid_no), (dialog, which) -> {
                    activity.dismissAlertDialog();

                    callback.connectActivityComplete(false);
                });

                activity.showAlertDialog(d);
                return;
            }
        } else {
            //ConnectID is NOT configured
            boolean offerToLink = true;
            boolean isSecondOffer = false;

            ConnectLinkedAppRecord linkedApp = ConnectDatabaseHelper.getAppData(activity, appId, username);
            //See if we've offered to link already
            Date firstOffer = linkedApp != null ? linkedApp.getLinkOfferDate1() : null;
            if(firstOffer != null) {
                isSecondOffer = true;
                //See if we've done the second offer
                Date secondOffer = linkedApp.getLinkOfferDate2();
                if(secondOffer != null) {
                    //They've declined twice, we won't bug them again
                    offerToLink = false;
                } else {
                    //Determine whether to do second offer
                    int daysToSecondOffer = 30;
                    long millis = (new Date()).getTime() - firstOffer.getTime();
                    long days = TimeUnit.DAYS.convert(millis, TimeUnit.MILLISECONDS);
                    offerToLink = days >= daysToSecondOffer;
                }
            }

            if(offerToLink) {
                if(linkedApp == null) {
                    //Create the linked app record (even if just to remember that we offered
                    linkedApp = ConnectDatabaseHelper.storeApp(activity, appId, username, false, "", false);
                }

                //Update that we offered
                if(isSecondOffer) {
                    linkedApp.setLinkOfferDate2(new Date());
                } else {
                    linkedApp.setLinkOfferDate1(new Date());
                }

                final ConnectLinkedAppRecord appRecordFinal = linkedApp;
                StandardAlertDialog d = new StandardAlertDialog(activity,
                        activity.getString(R.string.login_link_connectid_title),
                        activity.getString(R.string.login_link_connectid_message));

                d.setPositiveButton(activity.getString(R.string.login_link_connectid_yes), (dialog, which) -> {
                    activity.dismissAlertDialog();

                    unlockConnect(activity, success -> {
                        if(success) {
                            appRecordFinal.linkToConnectId(password);
                            ConnectDatabaseHelper.storeApp(activity, appRecordFinal);

                            //Link the HQ user by aqcuiring the SSO token for the first time
                            ConnectSsoHelper.retrieveHqSsoTokenAsync(activity, username, true, auth -> {
                                if(auth == null) {
                                    //Toast.makeText(activity, "Failed to acquire SSO token", Toast.LENGTH_SHORT).show();
                                    //TODO: Re-enable when token working again
                                    //ConnectManager.forgetAppCredentials(appId, username);
                                }

                                callback.connectActivityComplete(true);
                            });
                        } else {
                            callback.connectActivityComplete(false);
                        }
                    });
                });

                d.setNegativeButton(activity.getString(R.string.login_link_connectid_no), (dialog, which) -> {
                    activity.dismissAlertDialog();

                    //Save updated record indicating that we offered
                    ConnectDatabaseHelper.storeApp(activity, appRecordFinal);

                    callback.connectActivityComplete(false);
                });

                activity.showAlertDialog(d);
                return;
            }
        }

        callback.connectActivityComplete(false);
    }

    public static boolean isLoginManagedByConnectId(String appId, String userId) {
        AuthInfo.ProvidedAuth auth = getCredentialsForApp(appId, userId);
        return auth != null;
    }

    public static ConnectAppRecord getAppRecord(Context context, String appId) {
        return ConnectDatabaseHelper.getAppRecord(context, appId);
    }

    public static String getStoredPasswordForApp(String appId, String userId) {
        AuthInfo.ProvidedAuth auth = getCredentialsForApp(appId, userId);
        return auth != null ? auth.password : null;
    }

    @Nullable
    public static AuthInfo.ProvidedAuth getCredentialsForApp(String appId, String userId) {
        ConnectLinkedAppRecord record = ConnectDatabaseHelper.getAppData(manager.parentActivity, appId,
                userId);
        if (record != null && record.getConnectIdLinked() && record.getPassword().length() > 0) {
            return new AuthInfo.ProvidedAuth(record.getUserId(), record.getPassword(), false);
        }

        return null;
    }

    public static AuthInfo.TokenAuth getConnectToken() {
        if (isUnlocked()) {
            ConnectUserRecord user = ConnectDatabaseHelper.getUser(manager.parentActivity);
            if (user != null && (new Date()).compareTo(user.getConnectTokenExpiration()) < 0) {
                return new AuthInfo.TokenAuth(user.getConnectToken());
            }
        }

        return null;
    }

    public static AuthInfo.TokenAuth getTokenCredentialsForApp(String appId, String userId) {
        if (isUnlocked()) {
            ConnectLinkedAppRecord record = ConnectDatabaseHelper.getAppData(manager.parentActivity, appId,
                    userId);
            if (record != null && (new Date()).compareTo(record.getHqTokenExpiration()) < 0) {
                return new AuthInfo.TokenAuth(record.getHqToken());
            }
        }

        return null;
    }

    public static void getRemoteDbPassphrase(Context context, ConnectUserRecord user) {
        ApiConnectId.fetchDbPassphrase(context, user, new IApiCallback() {
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
                } catch (IOException | JSONException e) {
                    Logger.exception("Parsing return from DB key request", e);
                }
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Logger.log("ERROR", String.format(Locale.getDefault(), "Failed: %d", responseCode));
            }

            @Override
            public void processNetworkFailure() {
                Logger.log("ERROR", "Failed (network)");
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(context);
            }
        });
    }

    public static String generatePassword() {
        int passwordLength = 20;

        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_!.?";
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < passwordLength; i++) {
            password.append(charSet.charAt(new Random().nextInt(charSet.length())));
        }

        return password.toString();
    }
}