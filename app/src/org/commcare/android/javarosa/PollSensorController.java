package org.commcare.android.javarosa;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;

import org.commcare.CommCareApplication;
import org.commcare.location.CommCareLocationController;
import org.commcare.location.CommCareLocationControllerFactory;
import org.commcare.location.CommCareLocationListener;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.utils.GeoUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
public enum PollSensorController implements CommCareLocationListener {
    INSTANCE;

    private CommCareLocationController mLocationController;
    private final ArrayList<PollSensorAction> actions = new ArrayList<>();
    private Timer timeoutTimer = new Timer();

    private ResolvableApiException apiException;
    private boolean noProviders;
    private boolean missingPermissions;

    @Nullable
    public ResolvableApiException getException() {
        return apiException;
    }

    public boolean isMissingPermissions() {
        return missingPermissions;
    }

    public boolean isNoProviders() {
        return noProviders;
    }

    void startLocationPolling(PollSensorAction action) {
        synchronized (actions) {
            actions.add(action);
        }
        resetTimeoutTimer();

        // LocationManager needs to be dealt with in the main UI thread, so
        // wrap GPS-checking logic in a Handler
        new Handler(Looper.getMainLooper()).post(() -> {
            // Start requesting GPS updates
            Context context = CommCareApplication.instance();
            mLocationController = CommCareLocationControllerFactory.Companion.getLocationController(context, this);
            mLocationController.start();
        });
    }

    private void resetTimeoutTimer() {
        timeoutTimer.cancel();
        timeoutTimer.purge();
        timeoutTimer = new Timer();
    }

    public void requestLocationUpdates() {
        mLocationController.start();
    }

    @Override
    public void onLocationRequestStart() {
        // Cancel polling after maximum time is exceeded
        timeoutTimer.schedule(new PollingTimeoutTask(),
                HiddenPreferences.getGpsAutoCaptureTimeoutInMilliseconds());
    }

    @Override
    public void onLocationResult(@NotNull Location location) {
        synchronized (actions) {
            if (location != null) {
                for (PollSensorAction action : actions) {
                    action.updateReference(location);
                }

                if (location.getAccuracy() <= HiddenPreferences.getGpsAutoCaptureAccuracy()) {
                    stopLocationPolling();
                }
            }
        }
    }

    private void sendBroadcast(Context context) {
        Intent noGPSIntent = new Intent(GeoUtils.ACTION_CHECK_GPS_ENABLED);
        context.sendStickyBroadcast(noGPSIntent);
    }

    @Override
    public void missingPermissions() {
        missingPermissions = true;
        Context context = CommCareApplication.instance();
        sendBroadcast(context);
    }

    @Override
    public void onLocationRequestFailure(@NotNull CommCareLocationListener.Failure failure) {
        Context context = CommCareApplication.instance();
        if (failure instanceof CommCareLocationListener.Failure.ApiException) {
            Exception exception = ((CommCareLocationListener.Failure.ApiException) failure).getException();
            if (exception instanceof ResolvableApiException) {
                apiException = (ResolvableApiException) exception;
                sendBroadcast(context);
            } else {
                // ignore and return, we can't do anything.
            }
        } else {
            noProviders = true;
            context.registerReceiver(
                    new ProvidersChangedHandler(),
                    new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
            );
            sendBroadcast(context);
        }
    }

    private class ProvidersChangedHandler extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            requestLocationUpdates();
        }
    }

    private class PollingTimeoutTask extends TimerTask {
        @Override
        public void run() {
            stopLocationPolling();
        }
    }

    public void stopLocationPolling() {
        synchronized (actions) {
            actions.clear();
        }
        resetTimeoutTimer();
        mLocationController.stop();
    }

}
