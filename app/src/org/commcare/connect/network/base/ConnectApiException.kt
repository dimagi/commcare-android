package org.commcare.connect.network.base

// Carries typed error code through Result<T> chain; repository extracts it into DataState.Error.
class ConnectApiException(
    val errorCode: BaseApiHandler.PersonalIdOrConnectApiErrorCodes,
    cause: Throwable? = null,
) : Exception(errorCode.name, cause)
