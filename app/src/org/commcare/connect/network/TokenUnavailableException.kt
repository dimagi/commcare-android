package org.commcare.connect.network

class TokenUnavailableException: Exception() {
    override val message: String
        get() = "Token is unavailable"
}