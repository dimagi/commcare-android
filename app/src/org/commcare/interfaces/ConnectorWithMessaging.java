package org.commcare.interfaces;

import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.views.dialogs.DialogController;

/**
 * Expose message reporting to blocking task receivers
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public interface ConnectorWithMessaging<R> extends DialogController, CommCareTaskConnector<R> {
    void displayMessage(String message);

    void displayBadMessage(String message);

    void displayBadMessageWithoutToast(String message);
}
