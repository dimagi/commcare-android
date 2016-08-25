package org.commcare.activities;

import java.util.ArrayList;

/**
 * Created by amstone326 on 8/25/16.
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
