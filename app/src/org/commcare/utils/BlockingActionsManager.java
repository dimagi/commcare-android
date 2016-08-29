package org.commcare.utils;

import android.content.Context;
import android.os.Handler;

import java.util.ArrayList;

/**
 * Can be used to keep track of actions that should block certain other actions from being
 * processed until they have completed
 *
 * @author Aliza Stone
 * @author ctsims
 */
public class BlockingActionsManager {

    private final Handler mainHandler;
    private final ArrayList<DelayedBlockingAction> actions = new ArrayList<>();

    private final Object lock = new Object();

    public BlockingActionsManager(Context context) {
        mainHandler = new Handler(context.getMainLooper());
    }

    public void queue(DelayedBlockingAction action) {
        synchronized (lock) {
            cleanQueue();

            DelayedBlockingAction  pendingAction = null;
            for (DelayedBlockingAction existingAction : actions) {
                if (existingAction.isSameType(action)) {
                    pendingAction = existingAction;
                    break;
                }
            }

            // Only queue the new action if there isn't a pending action, or if we were able to
            // prevent it from firing
            if (pendingAction == null || pendingAction.invalidate()) {
                actions.add(action);
                mainHandler.postDelayed(action, action.getDelay());
            }
        }
    }

    private void cleanQueue() {
        synchronized (lock) {
            final ArrayList<DelayedBlockingAction> toClear = new ArrayList<>();
            for (DelayedBlockingAction action : actions) {
                if (!action.isPending()) {
                    toClear.add(action);
                }
            }
            actions.removeAll(toClear);
            toClear.clear();
        }
    }

    public boolean isBlocked() {
        synchronized (lock) {
            cleanQueue();
            return actions.size() > 0;
        }
    }
}
