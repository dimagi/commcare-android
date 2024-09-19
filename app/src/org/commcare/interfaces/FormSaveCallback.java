package org.commcare.interfaces;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public interface FormSaveCallback {
    /**
     * Starts a task to save the current form being edited. Will be expected to call the provided
     * listener when saving is complete and the current session state is no longer volatile
     */
    void formSaveCallback(Runnable callback);
}
