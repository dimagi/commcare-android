package org.commcare.android.framework;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public interface RuntimePermissionRequester {
    /**
     * Asks user for specific permissions needed to proceed
     *
     * @param requestCode Request permission using this code, allowing for
     *                    callback distinguishing
     */
    void requestNeededPermissions(int requestCode);
}
