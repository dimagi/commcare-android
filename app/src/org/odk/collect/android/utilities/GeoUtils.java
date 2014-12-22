package org.odk.collect.android.utilities;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.commcare.dalvik.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.location.Location;
import android.location.LocationManager;

/**
 * Static functions for dealing with GPS data, specifically Location and LocationManager objects.
 * @author jschweers
 *
 */
public class GeoUtils {
    public static final double GOOD_ACCURACY = 5;             // Good enough accuracy to stop pinging the GPS altogether
    public static final double ACCEPTABLE_ACCURACY = 1600;    // Good enough accuracy to ask user if they want to record
    public static final int MAXIMUM_WAIT = 300 * 1000;        // For passive collection, milliseconds to wait for GPS before giving up
    
    public static final String ACTION_CHECK_GPS_ENABLED = "org.odk.collect.android.utilities.GeoUtils.check";

    /**
     * Format location in a string for user display.
     * @param location
     * @return String in format "<latitude> <longitude> <altitude> <accuracy>"
     */
    public static String locationToString(Location location) {
        return String.format("%s %s %s %s", location.getLatitude(), location.getLongitude(), location.getAltitude(), location.getAccuracy());
    }
    
    /**
     * Get a LocationManager's providers, and trim the list down to providers we care about: GPS and network.
     * @param manager
     * @return Set of String objects that may contain LocationManager.GPS_PROVDER and/or LocationManager.NETWORK_PROVIDER
     */
    public static Set<String> evaluateProviders(LocationManager manager) {
        HashSet<String> set = new HashSet<String>();
        
        List<String> providers = manager.getProviders(true);
        for (String provider : providers) {
            if (provider.equalsIgnoreCase(LocationManager.GPS_PROVIDER)) {
                set.add(LocationManager.GPS_PROVIDER);
            }
            if (provider.equalsIgnoreCase(LocationManager.NETWORK_PROVIDER)) {
                set.add(LocationManager.NETWORK_PROVIDER);
            }
        }
                
        return set;
    }
    
    /**
     * Display a non-cancel-able dialog asking user if they want to turn on their GPS.
     * @param context
     * @param onChange Listener to call when dialog button is pressed.
     */
    public static void showNoGpsDialog(Context context, DialogInterface.OnClickListener onChange) {
        showNoGpsDialog(context, onChange, null);
    }

    /**
     * Display a cancel-able dialog asking user if they want to turn on their GPS.
     * @param context
     * @param onChange Listener to call when dialog button is pressed.
     * @param onCancel Listener to call when dialog is canceled.
     */
    public static void showNoGpsDialog(Context context, DialogInterface.OnClickListener onChange, DialogInterface.OnCancelListener onCancel) {
        AlertDialog dialog = new AlertDialog.Builder(context).create();
        dialog.setTitle(context.getString(R.string.no_gps_title));
        dialog.setMessage(context.getString(R.string.no_gps_message));
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.change_settings), onChange);
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, context.getString(R.string.cancel), onChange);
        if (onCancel != null) {
            dialog.setCancelable(true);
            dialog.setOnCancelListener(onCancel);
        }
        dialog.show();
    }
}
