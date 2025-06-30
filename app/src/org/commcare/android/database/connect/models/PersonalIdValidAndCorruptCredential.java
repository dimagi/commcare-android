package org.commcare.android.database.connect.models;

import java.util.List;

/**
 * Wraps the result of parsing Personal ID Credential response.
 * Contains valid and corrupt items separately.
 */
public class PersonalIdValidAndCorruptCredential  {
    private final List<PersonalIdCredential> validCredentials;
    private final List<PersonalIdCredential> corruptCredentials;

    public PersonalIdValidAndCorruptCredential(List<PersonalIdCredential> valid, List<PersonalIdCredential> corrupt) {
        this.validCredentials = valid;
        this.corruptCredentials = corrupt;
    }

    public List<PersonalIdCredential> getValidCredentials() {
        return validCredentials;
    }

    public List<PersonalIdCredential> getCorruptCredentials() {
        return corruptCredentials;
    }
}
