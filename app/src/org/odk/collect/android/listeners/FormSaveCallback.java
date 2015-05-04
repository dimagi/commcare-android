package org.odk.collect.android.listeners;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */

public interface FormSaveCallback {
    /**
     * Starts a task to save the current form being editted. Is expected to
     * eventually call CommCareSessionService.completeClosingSession()
     */
    void formSaveCallback();
}
