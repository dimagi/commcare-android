package org.commcare.dalvik.activities.utils;

import org.commcare.dalvik.activities.EntitySelectActivity;
import org.commcare.dalvik.preferences.DeveloperPreferences;

import java.util.Timer;
import java.util.TimerTask;

/**
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
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!cancelled) {
                                    activity.loadEntities();
                                }
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
