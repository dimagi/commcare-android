package org.commcare.interfaces;

import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.views.dialogs.DialogController;

/**
 * Expose success/failure reporting to task connectors
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public interface ConnectorWithResultCallback<R> extends DialogController, CommCareTaskConnector<R> {
    void reportSuccess(String message);
    void reportFailure(String message, boolean showPopupNotification);
}
