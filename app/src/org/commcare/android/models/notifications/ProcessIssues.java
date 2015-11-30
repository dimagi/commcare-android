package org.commcare.android.models.notifications;

import org.commcare.dalvik.activities.LoginActivity;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public enum ProcessIssues implements MessageTag {

    /**
     * Logs successfully submitted
     **/
    BadTransactions("notification.processing.badstructure"),

    /**
     * Logs saved, but not actually submitted
     **/
    StorageRemoved("notification.processing.nosdcard"),

    /**
     * You were logged out while something was occurring
     **/
    LoggedOut("notification.sending.loggedout", LoginActivity.NOTIFICATION_MESSAGE_LOGIN),

    /**
     * Logs saved, but not actually submitted
     **/
    RecordQuarantined("notification.sending.quarantine");

    ProcessIssues(String root) {
        this(root, "processing");
    }

    ProcessIssues(String root, String category) {
        this.root = root;
        this.category = category;
    }

    private final String root, category;

    public String getLocaleKeyBase() {
        return root;
    }

    public String getCategory() {
        return category;
    }
}

