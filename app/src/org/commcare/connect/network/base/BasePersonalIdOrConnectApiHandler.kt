package org.commcare.connect.network.base

/**
 * Base class for all API handlers
 */
abstract class BasePersonalIdOrConnectApiHandler<T> {

    abstract fun onSuccess(data: T)

    abstract fun onFailure(errorCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?)


    enum class PersonalIdOrConnectApiErrorCodes {
        UNKNOWN_ERROR,
        NETWORK_ERROR,
        OLD_API_ERROR,
        FORBIDDEN_ERROR,
        TOKEN_UNAVAILABLE_ERROR,
        TOKEN_DENIED_ERROR,
        INVALID_RESPONSE_ERROR,
        JSON_PARSING_ERROR,
        FAILED_AUTH_ERROR,
        SERVER_ERROR,
        RATE_LIMIT_EXCEEDED_ERROR;

        fun shouldAllowRetry(): Boolean {
            return this == NETWORK_ERROR || (this == TOKEN_UNAVAILABLE_ERROR) || (this == SERVER_ERROR
                    ) || (this == UNKNOWN_ERROR)
        }
    }


}