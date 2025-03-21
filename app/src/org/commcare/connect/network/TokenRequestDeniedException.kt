package org.commcare.connect.network

import java.io.IOException

class TokenRequestDeniedException: IOException() {
    override val message: String
        get() = "Token request denied"
}