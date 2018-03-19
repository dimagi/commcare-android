package org.commcare.engine.resource.installers;

import org.javarosa.xml.util.UnfullfilledRequirementsException;

/**
 * An exception to represent that the local system can't provide an expected
 * location to store data.
 *
 * @author ctsims
 */
public class LocalStorageUnavailableException extends UnfullfilledRequirementsException {

    private final String reference;

    public LocalStorageUnavailableException(String message, String reference) {
        super(message, RequirementType.WRITEABLE_REFERENCE);
        this.reference = reference;
    }

    public String getReference() {
        return reference;
    }
}
