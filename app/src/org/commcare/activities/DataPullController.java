package org.commcare.activities;

import org.commcare.views.notifications.MessageTag;
import org.commcare.views.notifications.NotificationMessage;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public interface DataPullController {
    void startDataPull();

    void dataPullCompleted();

    void raiseLoginMessage(MessageTag messageTag, boolean showTop);

    void raiseMessage(NotificationMessage message, boolean showTop);
}
