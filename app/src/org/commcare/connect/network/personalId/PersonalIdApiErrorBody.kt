package org.commcare.connect.network.personalId

/**
 * The fields the client reads out of a JSON API error response. A body that is empty, malformed, or
 * missing a field yields empty strings and null counts rather than an error.
 */
data class PersonalIdApiErrorBody(
    val errorCode: String,
    val errorSubCode: String,
    val retryAfterSeconds: Int?,
    val attemptsLeft: Int?,
)
