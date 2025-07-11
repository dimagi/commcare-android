package org.commcare.android.database.connect.models

/**
 * Wraps the result of parsing Personal ID Credential response.
 * Contains valid and corrupt items separately.
 */
data class PersonalIdValidAndCorruptCredential(
    val validCredentials: List<PersonalIdCredential>,
    val corruptCredentials: List<PersonalIdCredential>
)
