package org.commcare.recovery.measures;

import org.commcare.interfaces.PromptItem;

public class CommCareReinstallPrompt extends PromptItem {

    public static final CommCareReinstallPrompt INSTANCE = new CommCareReinstallPrompt();

    private CommCareReinstallPrompt() {
    }

    @Override
    public void incrementTimesSeen() {
        // do nothing
    }

    public boolean isForced() {
        return true;
    }
}
