package org.commcare.interfaces;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */

public interface FormSaveCallback {
    /**
     * Starts a task to save the current form being editted. Is expected to
     * eventually call CommCareApplication.expireUserSession
     */
    void formSaveCallback();
}
