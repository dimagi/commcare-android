package org.commcare.heartbeat;

import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapList;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedList;

/**
 * A helper object of an UpdateToPrompt, for tracking whether or not the prompt was shown on the
 * most recent set of logins, in order to inform whether it should be shown on the next one.
 *
 * Created by amstone326 on 2/12/18.
 */
public class UpdatePromptShowHistory implements Externalizable {

    private LinkedList<Boolean> showHistory;

    public UpdatePromptShowHistory() {
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

    /**
     * The maximum number of previous shows that the show history could ever need to know about is
     * 1 less than the reduced show frequency number (e.g. if the reduced show frequency is 4,
     * that means we need to show the prompt once every 4 logins, which means that on any given
     * login, we need to know about the prior 3 logins in order to decide whether or not to show it.
     */
    private boolean isFull() {
        return showHistory.size() == UpdateToPrompt.getReducedShowFrequency()-1;
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        showHistory = (LinkedList<Boolean>)ExtUtil.read(in, new ExtWrapList(Boolean.class, LinkedList.class), pf);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.write(out, new ExtWrapList(showHistory));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < showHistory.size(); i++) {
            sb.append(showHistory.get(i));
            sb.append(" ");
        }
        return sb.toString();
    }
}
