package org.commcare.connect.network

import java.io.IOException

class TokenUnavailableException: IOException {
    var innerException: Exception? = null
    var responseCode: Int? = null

    override val message: String
        get() = "Token is unavailable"

    constructor() : super() {
    }

    constructor(innerException: Exception) : super(innerException) {
        this.innerException = innerException
    }

    constructor(responseCode: Int) : super() {
        this.responseCode = responseCode
    }
}