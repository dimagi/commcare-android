package org.commcare.android.javarosa;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.ContextCompat;

import org.commcare.CommCareApplication;
import org.commcare.utils.GeoUtils;

import java.util.ArrayList;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Singleton that controls location acquisition for Poll Sensor XForm extension
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
@SuppressWarnings("ResourceType")
public enum PollSensorController implements LocationListener {
    INSTANCE;

    private LocationManager mLocationManager;
    private final ArrayList<PollSensorAction> actions = new ArrayList<>();
    private Timer timeoutTimer = new Timer();

    void startLocationPolling(PollSensorAction action) {
        actions.add(action);
        resetTimeoutTimer();

        // LocationManager needs to be dealt with in the main UI thread, so
        // wrap GPS-checking logic in a Handler
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                // Start requesting GPS updates
                Context context = CommCareApplication._();
                mLocationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);

                Set<String> providers = GeoUtils.evaluateProviders(mLocationManager);
                if (providers.isEmpty()) {
                    context.registerReceiver(
                            new ProvidersChangedHandler(),
                            new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
                    );

                    // This thread can't take action on the UI, so instead send
                    // a message that actual activities notice and then display
                    // a dialog asking user to enable location access
                    Intent noGPSIntent = new Intent(GeoUtils.ACTION_CHECK_GPS_ENABLED);
                    context.sendStickyBroadcast(noGPSIntent);
                }
                requestLocationUpdates(providers);
            }
        });
    }

    private void resetTimeoutTimer() {
        timeoutTimer.cancel();
        timeoutTimer.purge();
        timeoutTimer = new Timer();
    }

    /**
     * Start polling for location, based on whatever providers are given, and
     * set up a timeout after MAXIMUM_WAIT is exceeded.
     *
     * @param providers Set of String objects that may contain
     *                  LocationManager.GPS_PROVDER and/or LocationManager.NETWORK_PROVIDER
     */
    private void requestLocationUpdates(Set<String> providers) {
        if (providers.isEmpty()) {
            stopLocationPolling();
        } else {
            for (String provider : providers) {
                if (hasLocationPerms()) {
                    mLocationManager.requestLocationUpdates(provider, 0, 0, this);
                }
            }

            // Cancel polling after maximum time is exceeded
            timeoutTimer.schedule(new PollingTimeoutTask(), GeoUtils.MAXIMUM_WAIT);
        }
    }

    /**
     * If this action has a target node, update its value with the given location.
     */
    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            for (PollSensorAction action : actions) {
                action.update(location);
            }

            if (location.getAccuracy() <= GeoUtils.GOOD_ACCURACY) {
                stopLocationPolling();
            }
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    private class ProvidersChangedHandler extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Set<String> providers = GeoUtils.evaluateProviders(mLocationManager);
            requestLocationUpdates(providers);
        }
    }

    private class PollingTimeoutTask extends TimerTask {
        @Override
        public void run() {
            stopLocationPolling();
        }
    }

    void stopLocationPolling() {
        actions.clear();
        resetTimeoutTimer();

        if (hasLocationPerms() && mLocationManager != null) {
            mLocationManager.removeUpdates(this);
            mLocationManager = null;
        }
    }

    private static boolean hasLocationPerms() {
        Context context = CommCareApplication._().getApplicationContext();
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
