package org.commcare.tasks;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.ConnectivityStatus;
import org.commcare.utils.ConnectivityStatus.NetworkState;
import org.commcare.views.notifications.MessageTag;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.javarosa.core.services.Logger;

import java.io.IOException;

/**
 * Runs various tasks that diagnose problems that a user may be facing in connecting to commcare services.
 *
 * @author srengesh
 */
public class ConnectionDiagnosticTask<R> extends CommCareTask<Void, String, NetworkState, R> {
    private final Context c;

    private ConnectionDiagnosticListener listener;

    public static final int CONNECTION_ID = 12335800;

    /**
     * Problem reported via connection diagnostic tool
     **/
    public static final String CONNECTION_DIAGNOSTIC_REPORT = "connection-report";


    //strings used to in various diagnostics tests. Change these values if the URLs/HTML code is changed.
    private static final String googleURL = "www.google.com";
    private static final String pingPrefix = "ping -c 1 ";


    //the various log messages that will be returned regarding the outcomes of the tests
    private static final String logNotConnectedMessage = "Network test: Not connected.";
    private static final String logConnectionSuccessMessage = "Network test: Success.";

    private static final String logGoogleNullPointerMessage = "Google ping test: Process could not be started.";
    private static final String logGoogleIOErrorMessage = "Google ping test: Local error.";
    private static final String logGoogleInterruptedMessage = "Google ping test: Process was interrupted.";
    private static final String logGoogleSuccessMessage = "Google ping test: Success.";
    private static final String logGoogleUnexpectedResultMessage = "Google ping test: Unexpected HTML Result.";

    private static final String logCCIOErrorMessage = "CCHQ ping test: Local error.";

    public ConnectionDiagnosticTask(Context c) {
        this.c = c;
        this.taskId = CONNECTION_ID;

        TAG = ConnectionDiagnosticTask.class.getSimpleName();
    }

    public void setListener(ConnectionDiagnosticListener<R> listener) {
        this.listener = listener;
    }

    @Override
    protected NetworkState doTaskBackground(Void... params) {
        if (!isOnline(this.c) || !pingSuccess(googleURL)) {
            return NetworkState.DISCONNECTED;
        }
        return pingCC();
    }

    //checks if the network is connected or not.
    private boolean isOnline(Context context) {
        ConnectivityManager conManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = conManager.getActiveNetworkInfo();
        boolean notInAirplaneMode = (netInfo != null && netInfo.isConnected());

        //if user is not online, log not connected. if online, log success
        String logMessage = !notInAirplaneMode ? logNotConnectedMessage : logConnectionSuccessMessage;
        Logger.log(CONNECTION_DIAGNOSTIC_REPORT, logMessage);

        return notInAirplaneMode;
    }

    //check if a ping to a specific ip address (used for google url) is successful.
    private boolean pingSuccess(String url) {
        Process pingCommand;
        try {
            //append the input url to the ping command
            String pingURL = pingPrefix + url;

            //run the ping command at runtime
            pingCommand = java.lang.Runtime.getRuntime().exec(pingURL);
            if (pingCommand == null) {
                Logger.log(CONNECTION_DIAGNOSTIC_REPORT, logGoogleNullPointerMessage);
                return false;
            }
        } catch (IOException e) {
            Logger.log(CONNECTION_DIAGNOSTIC_REPORT, logGoogleIOErrorMessage + System.getProperty("line.separator") + "Stack trace: " + ForceCloseLogger.getStackTrace(e));
            return false;
        }
        int pingReturn;
        try {
            pingReturn = pingCommand.waitFor();
        } catch (InterruptedException e) {
            Logger.log(CONNECTION_DIAGNOSTIC_REPORT, logGoogleInterruptedMessage + System.getProperty("line.separator") + "Stack trace: " + ForceCloseLogger.getStackTrace(e));
            return false;
        }
        //0 if success, 2 if fail
        String messageOut = pingReturn == 0 ? logGoogleSuccessMessage : logGoogleUnexpectedResultMessage;
        Logger.log(CONNECTION_DIAGNOSTIC_REPORT, messageOut);
        return pingReturn == 0;
    }

    private NetworkState pingCC() {
        try {
            NetworkState state = ConnectivityStatus.checkCaptivePortal();
            Logger.log(CONNECTION_DIAGNOSTIC_REPORT, "Calling commcare for captive portal detection returned : " + state.name());
            return state;
        } catch (IOException e) {
            Logger.log(CONNECTION_DIAGNOSTIC_REPORT, logCCIOErrorMessage + System.getProperty("line.separator") + "Stack trace: " + ForceCloseLogger.getStackTrace(e));
            return NetworkState.DISCONNECTED;
        }
    }

    @Override
    protected void deliverResult(R r, NetworkState networkState) {
        String errorMessageId = null;
        MessageTag notificationTag = null;
        String analyticsMessage = null;
        switch (networkState) {
            case CONNECTED:
                listener.connected(r);
                return;
            case DISCONNECTED:
                if (ConnectivityStatus.isAirplaneModeOn(c)) {
                    errorMessageId = "notification.sync.airplane.action";
                    notificationTag = NotificationMessageFactory.StockMessages.Sync_AirplaneMode;
                } else {
                    errorMessageId = "notification.sync.connections.action";
                    notificationTag = NotificationMessageFactory.StockMessages.Sync_NoConnections;
                }
                analyticsMessage = AnalyticsParamValue.SYNC_FAIL_NO_CONNECTION;
                break;
            case CAPTIVE_PORTAL:
                errorMessageId = "connection.captive_portal.action";
                notificationTag = NotificationMessageFactory.StockMessages.Sync_CaptivePortal;
                analyticsMessage = AnalyticsParamValue.SYNC_FAIL_CAPTIVE_PORTAL;
                break;
            case COMMCARE_DOWN:
                errorMessageId = "connection.commcare_down.action";
                notificationTag = NotificationMessageFactory.StockMessages.Sync_CommcareDown;
                analyticsMessage = AnalyticsParamValue.SYNC_FAIL_COMMCARE_DOWN;
                break;
        }
        listener.failed(r, errorMessageId, notificationTag, analyticsMessage);
    }

    @Override
    protected void deliverUpdate(R r, String... update) {

    }

    @Override
    protected void deliverError(R r, Exception e) {

    }

    public interface ConnectionDiagnosticListener<R> {
        void connected(R r);
        void failed(R r, String errorMessageId, MessageTag notificationTag, String analyticsMessage);
    }
}
