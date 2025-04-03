package org.commcare.connect.network

import java.io.IOException

/**
 * Exception thrown when an SSO token is unavailable.
 * This can occur when the API call to retrieve an SSO token fails, i.e. due to bad network connectivity
 * It can also happen if one SSO token (ConnectID) is being used as auth to acquire another SSO token (HQ),
 *      and the first token is rejected by the second OAuth server (i.e. due to expired token)
 * The best course of action is usually to try again.
 */
class TokenUnavailableException() : IOException() {
    override val message: String
        get() = "Token is unavailable"
}