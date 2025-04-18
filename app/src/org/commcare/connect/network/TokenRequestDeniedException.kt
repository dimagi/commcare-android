package org.commcare.connect.network

import java.io.IOException

/**
 * Exception thrown when an SSO token request is denied.
 * This is thrown when the user/pass auth is rejected by the OAuth server.
 * Specifically, this exception is only thrown when the ConnectID OAuth request is denied with a 400 error.
 * The 400 for bad auth (instead of 401) is because the actual HTTP call passes auth (using NoAuth).
 *
 * Trying again will not solve the problem in this case, as a new password must be established with the server.
 * Corrective action is to recover the ConnectID account on the device.
 */
class TokenRequestDeniedException: IOException() {
    override val message: String
        get() = "Token request denied"
}