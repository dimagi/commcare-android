package org.commcare.heartbeat;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.logging.AndroidLogger;
import org.commcare.preferences.CommCareServerPreferences;
import org.commcare.services.CommCareSessionService;
import org.javarosa.core.services.Logger;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * While active, this class is responsible controlling a TimerTask that periodically pings the
 * server with a "heartbeat" request. The lifecycle of this object is tied directly to that of
 * the CommCareSessionService; it should be started whenever a session service is started,
 * and ended whenever a session service is ended for any reason.
 *
 * Created by amstone326 on 4/13/17.
 */
public class HeartbeatLifecycleManager {

    private static final long FIVE_MIN_IN_MS = 5 * 60 * 1000;

    private TimerTask heartbeatRequestTask;
    private HeartbeatRequester requester = new HeartbeatRequester();
    private CommCareSessionService enclosingSessionService;

    public HeartbeatLifecycleManager(CommCareSessionService sessionService) {
        this.enclosingSessionService = sessionService;
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
                            //requester.requestHeartbeat();
                            requester.parseTestHeartbeatResponse();
                        } catch (Exception e) {
                            // Encountered an unexpected issue, should just bail on this thread
                            HeartbeatLifecycleManager.this.endCurrentHeartbeatTask();
                            Logger.log(AndroidLogger.TYPE_ERROR_SERVER_COMMS,
                                    "Encountered unexpected exception during heartbeat communications: "
                                            + e.getMessage() + ". Stopping the heartbeat thread.");
                        }
                    }
                }
            };
            (new Timer()).schedule(heartbeatRequestTask, new Date(), FIVE_MIN_IN_MS);
        }
    }

    private boolean shouldStartHeartbeatRequests() {
        return appHasHeartbeatUrl() && !hasSucceededOnThisLogin() && endCurrentHeartbeatTask();
    }

    private boolean shouldStopHeartbeatRequests() {
        return sessionHasDied() || hasSucceededOnThisLogin();
    }

    private boolean appHasHeartbeatUrl() {
        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
        String urlString = currentApp.getAppPreferences().getString(
                CommCareServerPreferences.PREFS_HEARTBEAT_URL_KEY, null);
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
