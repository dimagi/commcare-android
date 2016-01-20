package org.commcare.dalvik.activities;

import org.commcare.android.models.notifications.MessageTag;
import org.commcare.android.models.notifications.NotificationMessage;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public interface DataRestorer {
    void startOta();

    void done();

    void raiseLoginMessage(MessageTag messageTag, boolean showTop);

    void raiseMessage(NotificationMessage message, boolean showTop);
}
