package org.commcare.interfaces;

public abstract class PromptItem {

    protected boolean isForced = false;

    public boolean isForced() {
        return isForced;
    }

    public abstract void incrementTimesSeen();
}
