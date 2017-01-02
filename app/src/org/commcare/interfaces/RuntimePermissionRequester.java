package org.commcare.interfaces;

/**
 * Ask user for permissions at runtime (for Android 6 and above)
 *
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
