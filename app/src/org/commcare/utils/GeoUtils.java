package org.commcare.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;

import androidx.annotation.IntDef;
import androidx.annotation.StringDef;
import androidx.core.content.ContextCompat;

import org.commcare.activities.CommCareActivity;
import org.commcare.dalvik.R;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.model.data.GeoPointData;
import org.javarosa.core.model.data.UncastData;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Static functions for dealing with GPS data, specifically Location and LocationManager objects.
 *
 * @author jschweers
 */
public class GeoUtils {
    // Good enough accuracy to stop pinging the GPS altogether
    public static final double DEFAULT_GOOD_ACCURACY = 5;
    public static final double AUTO_CAPTURE_GOOD_ACCURACY = 10;

    // Good enough accuracy to ask user if they want to record
    public static final double DEFAULT_ACCEPTABLE_ACCURACY = 1600;

    // For passive collection, milliseconds to wait for GPS before giving up
    public static final int AUTO_CAPTURE_MAX_WAIT_IN_MINUTES = 2;

    public static final String ACTION_CHECK_GPS_ENABLED = "org.commcare.utils.GeoUtils.check";


    /**
     * Format location in a string for user display.
     *
     * @return String in format "<latitude> <longitude> <altitude> <accuracy>"
     */
    public static String locationToString(Location location) {
        return String.format("%s %s %s %s", location.getLatitude(), location.getLongitude(), location.getAltitude(), location.getAccuracy());
    }

    /**
     * Get a LocationManager's providers, and trim the list down to providers we care about: GPS and network.
     *
     * @return Set of String objects that may contain LocationManager.GPS_PROVDER and/or LocationManager.NETWORK_PROVIDER
     */
    public static Set<String> evaluateProviders(LocationManager manager) {
        HashSet<String> set = new HashSet<>();

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
     * Gets the same list of providers returned by evaluateProviders, but filtered out
     * to include only providers with the appropriate permissions granted.
     */
    protected static Set<String> evaluateProvidersWithPermissions(LocationManager manager, Context context) {
        HashSet<String> set = new HashSet<>();

        List<String> providers = manager.getProviders(true);
        for (String provider : providers) {
            if (provider.equalsIgnoreCase(LocationManager.GPS_PROVIDER) && ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                set.add(LocationManager.GPS_PROVIDER);
            }
            if (provider.equalsIgnoreCase(LocationManager.NETWORK_PROVIDER) && ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                set.add(LocationManager.NETWORK_PROVIDER);
            }
        }

        return set;
    }

    /**
     * Display a non-cancel-able dialog asking user if they want to turn on their GPS.
     *
     * @param onChange Listener to call when dialog button is pressed.
     */
    public static void showNoGpsDialog(CommCareActivity activity,
                                       DialogInterface.OnClickListener onChange) {
        StandardAlertDialog factory = setupAlertFactory(activity, onChange, null);
        activity.showAlertDialog(factory);
    }

    /**
     * Display a (possibly cancelable) dialog asking user if they want to turn on their GPS.
     *
     * @param onChange Listener to call when dialog button is pressed.
     * @param onCancel Listener to call when dialog is canceled.
     */
    public static void showNoGpsDialog(Activity activity,
                                       DialogInterface.OnClickListener onChange,
                                       DialogInterface.OnCancelListener onCancel) {
        StandardAlertDialog factory = setupAlertFactory(activity, onChange, onCancel);

        // NOTE PLM: this dialog will not persist through orientation changes.
        factory.showNonPersistentDialog();
    }

    private static StandardAlertDialog setupAlertFactory(Context context,
                                                         DialogInterface.OnClickListener onChange,
                                                         DialogInterface.OnCancelListener onCancel) {
        StandardAlertDialog factory =
                new StandardAlertDialog(context,
                        StringUtils.getStringRobust(context, R.string.no_gps_title),
                        StringUtils.getStringRobust(context, R.string.no_gps_message));
        factory.setPositiveButton(StringUtils.getStringRobust(context, R.string.change_settings), onChange);
        factory.setNegativeButton(StringUtils.getStringRobust(context, R.string.cancel_location), onChange);

        if (onCancel != null) {
            factory.setOnCancelListener(onCancel);
        }
        return factory;
    }

    public static void goToProperLocationSettingsScreen(Context context) {
        Intent intent;
        LocationManager lm = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        if (locationServicesEnabledGlobally(lm)) {
            intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", context.getPackageName(), null));
        } else {
            intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        }
        context.startActivity(intent);
    }

    private static boolean locationServicesEnabledGlobally(LocationManager lm) {
        boolean gpsEnabled = false, networkEnabled = false;
        try {
            gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {
            // Prior to API level 21, this will throw a SecurityException if the location
            // permissions are not sufficient to use the specified provider
        }

        try {
            networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
            // Prior to API level 21, this will throw a SecurityException if the location
            // permissions are not sufficient to use the specified provider
        }
        return gpsEnabled || networkEnabled;
    }

    /**
     * Pass in a string representing either a GeoPoint or an address and get back a valid
     * GeoURI that can be passed as an intent argument
     */
    public static String getGeoIntentURI(String rawInput) {
        try {
            GeoPointData mGeoPointData = new GeoPointData().cast(new UncastData(rawInput));
            String latitude = Double.toString(mGeoPointData.getValue()[0]);
            String longitude = Double.toString(mGeoPointData.getValue()[1]);
            return "geo:" + latitude + "," + longitude + "?q=" + latitude + "," + longitude;

        } catch (IllegalArgumentException iae) {
            return "geo:0,0?q=" + rawInput;
        }
    }
}
