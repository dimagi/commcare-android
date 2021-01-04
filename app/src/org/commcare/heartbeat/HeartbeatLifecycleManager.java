package org.commcare.heartbeat;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.preferences.ServerUrls;
import org.commcare.services.CommCareSessionService;
import java.util.concurrent.TimeUnit;

/**
 * While active, this class is responsible controlling a TimerTask that periodically pings the
 * server with a "heartbeat" request. The lifecycle of this object is tied directly to that of
 * the CommCareSessionService; it is started whenever a session service is started, and ended
 * whenever a session service is ended for any reason.
 *
 * Created by amstone326 on 4/13/17.
 */
public class HeartbeatLifecycleManager {
    private static final String TAG = HeartbeatLifecycleManager.class.getSimpleName();

    private CommCareSessionService enclosingSessionService;
    private static final long HEARTBEAT_PERIODICITY_IN_HOURS = 2;
    private static final long HEARTBEAT_BACKOFF_DELAY_IN_MILLIS = 5 * 60 * 1000L;
    private static final String HEARTBEAT_REQUEST_NAME = "heartbeat_request";

    public HeartbeatLifecycleManager(CommCareSessionService sessionService) {
        this.enclosingSessionService = sessionService;
    }

    public void startHeartbeatCommunications(Context context) {
        if (shouldStartHeartbeatRequests()) {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build();
            PeriodicWorkRequest hearbeatRequest =
                    new PeriodicWorkRequest.Builder(HeartbeatWorker.class, HEARTBEAT_PERIODICITY_IN_HOURS, TimeUnit.HOURS)
                            .addTag(TAG)
                            .setConstraints(constraints)
                            .setBackoffCriteria(
                                    BackoffPolicy.EXPONENTIAL,
                                    HEARTBEAT_BACKOFF_DELAY_IN_MILLIS,
                                    TimeUnit.MILLISECONDS)
                            .build();

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    uniqueRequestName(),
                    ExistingPeriodicWorkPolicy.KEEP,
                    hearbeatRequest
            );
        }
    }

    public void endHeartbeatCommunications(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueRequestName());
        this.enclosingSessionService = null;
    }

    private boolean shouldStartHeartbeatRequests() {
        return appNotCorrupted() && appHasHeartbeatUrl() && !hasSucceededOnThisLogin();
    }

    private boolean appNotCorrupted() {
        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
        return currentApp.getAppResourceState() != CommCareApplication.STATE_CORRUPTED;
    }

    private boolean appHasHeartbeatUrl() {
        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
        String urlString = currentApp.getAppPreferences().getString(
                ServerUrls.PREFS_HEARTBEAT_URL_KEY, null);
        return urlString != null;
    }

    private boolean hasSucceededOnThisLogin() {
        return enclosingSessionService.heartbeatSucceededForSession();
    }

    private String uniqueRequestName() {
        return HEARTBEAT_REQUEST_NAME + "_" + CommCareApplication.instance().getCurrentApp().getUniqueId();
    }

}
