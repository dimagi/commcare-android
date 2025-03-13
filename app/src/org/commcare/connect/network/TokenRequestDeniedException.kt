package org.commcare.connect.network

class TokenRequestDeniedException: Exception() {
    override val message: String
        get() = "Token request denied"
}