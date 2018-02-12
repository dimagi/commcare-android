package org.commcare.heartbeat;

import java.util.LinkedList;

/**
 * Created by amstone326 on 2/12/18.
 */

public class UpdatePromptShowHistory {

    private LinkedList<Boolean> showHistory;
    // the number of logins to keep in show history
    private int numLoginsToTrack;

    public UpdatePromptShowHistory() {
        this.numLoginsToTrack = UpdateToPrompt.getReducedShowFrequency() - 1;
        this.showHistory = new LinkedList<>();
    }

    /**
     *
     * @param showFrequency - The frequency with which we currently want to show update prompts;
     *                 if showFrequency is N, then this means "show the prompt once every N logins"
     * @return - If the prompt to whom this show history belongs should be shown right now, based
     * on the given showFrequency
     */
    boolean shouldShowOnThisLogin(int showFrequency) {
        boolean shouldShow = true;

        int numChecked = 0;
        int index = showHistory.size() - 1;
        while (index >= 0 && numChecked < showFrequency-1) {
            if (showHistory.get(index)) {
                shouldShow = false;
                break;
            }
            numChecked++;
            index--;
        }

        updateHistoryWithLatest(shouldShow);
        return shouldShow;
    }

    private void updateHistoryWithLatest(boolean lastShowValue) {
        if (isFull()) {
            showHistory.poll();
        }
        showHistory.add(lastShowValue);
    }

    private boolean isFull() {
        return showHistory.size() == numLoginsToTrack;
    }

}
