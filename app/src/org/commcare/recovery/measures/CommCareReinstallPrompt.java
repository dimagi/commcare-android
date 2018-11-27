package org.commcare.recovery.measures;

import org.commcare.interfaces.PromptItem;

public class CommCareReinstallPrompt extends PromptItem {


    public CommCareReinstallPrompt() {
    }

    @Override
    public boolean isForced() {
        return true;
    }

    @Override
    public void incrementTimesSeen() {
        // do nothing
    }

}
