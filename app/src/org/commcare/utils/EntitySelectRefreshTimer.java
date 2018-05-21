package org.commcare.utils;

import org.commcare.activities.EntitySelectActivity;
import org.commcare.preferences.DeveloperPreferences;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Timer to refresh the case list every once in a while. Used in special cases
 * (and has to be manually enabled) when case lists contain scheduling info
 * that needs updating.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class EntitySelectRefreshTimer {
    private Timer myTimer;
    private final Object timerLock = new Object();
    private boolean cancelled;

    public EntitySelectRefreshTimer() {
    }

    public void start(final EntitySelectActivity activity) {
        if (DeveloperPreferences.isListRefreshEnabled() && myTimer == null) {
            myTimer = new Timer();
            synchronized (timerLock) {
                myTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        activity.runOnUiThread(() -> {
                            if (!cancelled) {
                                activity.loadEntities();
                            }
                        });
                    }
                }, 15 * 1000, 15 * 1000);
                cancelled = false;
            }
        }
    }

    public void stop() {
        synchronized (timerLock) {
            if (myTimer != null) {
                myTimer.cancel();
                myTimer = null;
                cancelled = true;
            }
        }
    }
}
