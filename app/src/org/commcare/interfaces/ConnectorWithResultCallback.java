package org.commcare.interfaces;

import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.views.dialogs.DialogController;

/**
 * Expose message reporting to blocking task receivers
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public interface ConnectorWithResultCallback<R> extends DialogController, CommCareTaskConnector<R> {
    void reportSuccess(String message);
    void reportFailure(String message, boolean showPopupNotification);
}
