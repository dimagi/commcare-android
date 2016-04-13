package org.commcare.interfaces;

import org.commcare.tasks.templates.CommCareTaskConnector;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public interface ConnectorWithHttpResponseProcessor<R>
        extends HttpResponseProcessor, CommCareTaskConnector<R> {
}
