package org.commcare.activities;

import java.util.ArrayList;

/**
 * Can be used to keep track of actions that should block certain other actions from being
 * processed until they have completed
 *
 * @author Aliza Stone
 */
public class BlockingActionsManager {

    private ArrayList<BlockingActionIdentifier> liveActions = new ArrayList<>();

    public void registerActionStart(BlockingActionIdentifier actionIdentifier) {
        liveActions.add(actionIdentifier);
    }

    public void registerActionCompletion(BlockingActionIdentifier actionIdentifier) {
        liveActions.remove(actionIdentifier);
    }

    public boolean actionsInProgress() {
        return liveActions.size() > 0;
    }

    public enum BlockingActionIdentifier {
        OnDateChanged
    }

}
