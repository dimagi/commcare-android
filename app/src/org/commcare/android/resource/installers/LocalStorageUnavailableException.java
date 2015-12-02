/**
 *
 */
package org.commcare.android.resource.installers;

import org.commcare.xml.CommCareElementParser;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

/**
 * An exception to represent that the local system can't provide an expected
 * location to store data.
 *
 * @author ctsims
 */
public class LocalStorageUnavailableException extends UnfullfilledRequirementsException {

    private static final int REQUIREMENT_WRITEABLE_REFERENCE = 4;

    private final String reference;

    public LocalStorageUnavailableException(String message, String reference) {
        super(message, CommCareElementParser.SEVERITY_ENVIRONMENT, REQUIREMENT_WRITEABLE_REFERENCE);
        this.reference = reference;
    }

    public String getReference() {
        return reference;
    }
}
