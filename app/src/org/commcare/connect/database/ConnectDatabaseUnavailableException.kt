package org.commcare.connect.database

/**
 * Thrown when Connect storage is accessed with no passphrase to open it, which is the expected state
 * once the user has signed out of PersonalId.
 *
 * Deliberately not a [org.commcare.connect.network.LoginInvalidatedException]: that one is
 * reserved for a DB that can't be opened despite having a passphrase (corruption or bad
 * encryption), and reaching the uncaught handler with it wipes the account and restarts the
 * process. A missing passphrase just means the caller raced with sign-out and its work is no
 * longer wanted.
 *
 * @author dviggiano
 */
class ConnectDatabaseUnavailableException(
    message: String,
) : RuntimeException(message)
