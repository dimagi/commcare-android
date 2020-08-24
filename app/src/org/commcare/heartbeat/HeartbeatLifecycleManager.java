package org.commcare.heartbeat;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.preferences.ServerUrls;
import org.commcare.services.CommCareSessionService;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * While active, this class is responsible controlling a TimerTask that periodically pings the
 * server with a "heartbeat" request. The lifecycle of this object is tied directly to that of
 * the CommCareSessionService; it is started whenever a session service is started, and ended
 * whenever a session service is ended for any reason.
 *
 * Created by amstone326 on 4/13/17.
 */
public class HeartbeatLifecycleManager {

    private static final long ONE_HOUR_IN_MS = 60 * 60 * 1000;

    private TimerTask heartbeatRequestTask;
    private HeartbeatRequester requester;
    private CommCareSessionService enclosingSessionService;

    public HeartbeatLifecycleManager(CommCareSessionService sessionService) {
        this.enclosingSessionService = sessionService;
        this.requester = CommCareApplication.instance().getHeartbeatRequester();
    }

    public void startHeartbeatCommunications() {
        if (shouldStartHeartbeatRequests()) {
            this.heartbeatRequestTask = new TimerTask() {
                @Override
                public void run() {
                    if (shouldStopHeartbeatRequests()) {
                        HeartbeatLifecycleManager.this.endCurrentHeartbeatTask();
                    } else {
                        try {
                            requester.makeRequest();
                        } catch (Exception e) {
                            // Encountered an unexpected issue, should just bail on this thread
                            HeartbeatLifecycleManager.this.endCurrentHeartbeatTask();
                            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                                    "Encountered unexpected exception during heartbeat communications: "
                                            + e.getMessage() + ". Stopping the heartbeat thread.");
                        }
                    }
                }
            };
            (new Timer()).schedule(heartbeatRequestTask, new Date(), ONE_HOUR_IN_MS);
        }
    }

    private boolean shouldStartHeartbeatRequests() {
        return appNotCorrupted() && appHasHeartbeatUrl() && !hasSucceededOnThisLogin() &&
                endCurrentHeartbeatTask();
    }

    private boolean shouldStopHeartbeatRequests() {
        return sessionHasDied() || hasSucceededOnThisLogin();
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

    private boolean sessionHasDied() {
        return !enclosingSessionService.isActive();
    }

    public void endHeartbeatCommunications() {
        endCurrentHeartbeatTask();
        this.enclosingSessionService = null;
    }

    /**
     *
     * @return true if we have successfully canceled the current heartbeat task, or there is no
     * current heartbeat task
     */
    private boolean endCurrentHeartbeatTask() {
        if (heartbeatRequestTask == null) {
            return true;
        }
        if (heartbeatRequestTask.cancel()) {
            heartbeatRequestTask = null;
            return true;
        }
        return false;
    }

}
