package org.commcare.heartbeat;

import org.commcare.CommCareApplication;
import org.commcare.logging.AndroidLogger;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.model.User;
import org.javarosa.core.services.Logger;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * While active, this class is responsible for using a TimerTask to periodically ping the server
 * with a "heartbeat" request, and then handle the response. The lifecycle of the TimerTask is
 * tied to that of the CommCareSessionService; it should be started whenever a session service is
 * started, and ended whenever a session service is ended for any reason.
 *
 * Currently, the primary content of the server's response to the heartbeat request will be
 * information about potential binary or app updates that the app should prompt users to conduct.
 *
 * Created by amstone326 on 4/13/17.
 */
public class HeartbeatLifecycleManager {

    private static final long ONE_DAY_IN_MS = 24 * 60 * 60 * 1000;

    private Timer heartbeatTimer;
    private HeartbeatRequester requester = new HeartbeatRequester();

    private static HeartbeatLifecycleManager INSTANCE;
    public static HeartbeatLifecycleManager instance() {
        if (INSTANCE == null) {
            INSTANCE = new HeartbeatLifecycleManager();
        }
        return INSTANCE;
    }

    public void startHeartbeatCommunications() {
        if (heartbeatTimer != null) {
            // Make sure we end anything still in progress
            heartbeatTimer.cancel();
        }
        heartbeatTimer = new Timer();
        heartbeatTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    User currentUser = CommCareApplication.instance().getSession().getLoggedInUser();
                    //requester.simulateRequestGettingStuck();
                    //requester.requestHeartbeat(currentUser);
                    requester.parseTestHeartbeatResponse();
                } catch (SessionUnavailableException e) {
                    // Means the session has ended, so we should stop these requests
                    stopHeartbeatCommunications();
                } catch (Exception e) {
                    // Encountered a different, unexpected issue
                    stopHeartbeatCommunications();
                    Logger.log(AndroidLogger.TYPE_ERROR_SERVER_COMMS,
                            "Encountered unexpected exception during heartbeat communications: "
                                    + e.getMessage() + ". Stopping the heartbeat thread.");
                }
            }
        }, new Date(), ONE_DAY_IN_MS);
    }

    public void stopHeartbeatCommunications() {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
        }
    }

}
