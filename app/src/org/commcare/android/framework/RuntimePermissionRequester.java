package org.commcare.android.framework;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public interface RuntimePermissionRequester {
    /**
     * Asks user for specific permissions needed to proceed
     */
    void requestNeededPermissions();
}
