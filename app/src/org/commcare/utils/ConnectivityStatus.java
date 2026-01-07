package org.commcare.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import org.javarosa.core.services.Logger;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import static org.commcare.tasks.ConnectionDiagnosticTask.CONNECTION_DIAGNOSTIC_REPORT;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ConnectivityStatus {
    public static final String logNotConnectedMessage = "Network test: Not connected.";
    public static final String logConnectionSuccessMessage = "Network test: Success.";

    private static final String WIFI = "WIFI";
    private static final String ETHERNET = "ETHERNET";
    private static final String UNKNOWN_NETWORK = "UNKNOWN_NETWORK";
    private static final String READ_PHONE_STATE_UNKNOWN_NETWORK = "READ_PHONE_STATE_UNKNOWN_NETWORK";
    private static final String NO_NETWORK = "NO_NETWORK";
    private static final String TWO_G = "2G";
    private static final String THREE_G = "3G";
    private static final String FOUR_G = "4G";
    private static final String FIVE_G = "5G";



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

        return (netInfo != null && netInfo.isConnected());
    }

    public static String getNetworkType(Context context){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return getNetworkTypeFromAndroid24(context);
        }else{
            return getNetworkTypeFromBelowAndroid24(context);
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private static String getNetworkTypeFromAndroid24(Context context){
        ConnectivityManager connectivityManager = context.getSystemService(ConnectivityManager.class);
        Network currentNetwork = connectivityManager.getActiveNetwork();
        if(currentNetwork!=null) {
            NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(currentNetwork);
            if (networkCapabilities != null) {
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return WIFI;
                }else if(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)){
                    return ETHERNET;
                }else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    TelephonyManager tm = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                        return getNetworkType(tm.getDataNetworkType());
                    }else{
                        return READ_PHONE_STATE_UNKNOWN_NETWORK;
                    }
                }
                return UNKNOWN_NETWORK;
            }
        }
        return NO_NETWORK;
    }

    private static String getNetworkTypeFromBelowAndroid24(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork == null || !activeNetwork.isConnected()) {
            return NO_NETWORK;
        }else if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
            return WIFI;
        } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
            TelephonyManager teleMan = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                return getNetworkType(teleMan.getNetworkType());
            }else{
                return READ_PHONE_STATE_UNKNOWN_NETWORK;
            }
        }
        return UNKNOWN_NETWORK;
    }

    private static String getNetworkType(int networkType){
        return switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE,
                 TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT,
                 TelephonyManager.NETWORK_TYPE_IDEN, TelephonyManager.NETWORK_TYPE_GSM -> TWO_G;
            case TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0,
                 TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_HSDPA,
                 TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA,
                 TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD,
                 TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_TD_SCDMA ->
                    THREE_G;
            case TelephonyManager.NETWORK_TYPE_LTE -> FOUR_G;
            case TelephonyManager.NETWORK_TYPE_NR -> FIVE_G;
            default -> UNKNOWN_NETWORK;
        };
    }
}
