package org.commcare.interfaces;

import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.tasks.templates.CommCareTaskConnector;

/**
 * Represents a task connector that has callbacks to handle http responses
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public interface ConnectorWithHttpResponseProcessor<R>
        extends HttpResponseProcessor, CommCareTaskConnector<R> {
}
