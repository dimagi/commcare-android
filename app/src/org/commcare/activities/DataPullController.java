package org.commcare.activities;

import org.commcare.views.notifications.MessageTag;
import org.commcare.views.notifications.NotificationMessage;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public interface DataPullController {

    enum DataPullMode {
        NORMAL,
        CONSUMER_APP,

        /**
         * Pulls data from demo user restore file present in CCZ app
         */
        CCZ_DEMO
    }

    void startDataPull(DataPullMode mode, String password);

    void dataPullCompleted();

    void raiseLoginMessage(MessageTag messageTag, boolean showTop);

    void raiseLoginMessageWithInfo(MessageTag messageTag, String additionalInfo, boolean showTop);

    void raiseMessage(NotificationMessage message, boolean showTop);
}
