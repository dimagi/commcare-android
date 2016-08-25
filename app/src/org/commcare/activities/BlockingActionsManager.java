package org.commcare.activities;

import java.util.ArrayList;

/**
 * Created by amstone326 on 8/25/16.
 */
public class BlockingActionsManager {

    private ArrayList<BlockingAction> liveActions = new ArrayList<>();

    public void registerActionStart(BlockingAction action) {
        liveActions.add(action);
    }

    public void registerActionCompletion(BlockingAction action) {
        liveActions.remove(0);
    }

    public boolean actionsInProgress() {
        return liveActions.size() > 0;
    }

    class BlockingAction {

    }
}
