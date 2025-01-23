package org.commcare.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.Settings;

import org.javarosa.core.services.Logger;

import static org.commcare.tasks.ConnectionDiagnosticTask.CONNECTION_DIAGNOSTIC_REPORT;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ConnectivityStatus {
    private static final String logNotConnectedMessage = "Network test: Not connected.";
    private static final String logConnectionSuccessMessage = "Network test: Success.";
    public static boolean isAirplaneModeOn(Context context) {
        return Settings.Global.getInt(context.getApplicationContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager conManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
         if (conManager == null) {
                 Logger.log(CONNECTION_DIAGNOSTIC_REPORT, logNotConnectedMessage);
                 return false;
         }
        NetworkInfo netInfo = conManager.getActiveNetworkInfo();
        boolean notInAirplaneMode = (netInfo != null && netInfo.isConnected());

        //if user is not online, log not connected. if online, log success
        String logMessage = !notInAirplaneMode ? logNotConnectedMessage : logConnectionSuccessMessage;
        Logger.log(CONNECTION_DIAGNOSTIC_REPORT, logMessage);

        return notInAirplaneMode;
    }
}
