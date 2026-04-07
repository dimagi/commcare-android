package org.commcare.connect.repository

import java.util.Date
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.base.ConnectApiException

sealed class DataState<out T> {
    object Loading : DataState<Nothing>()
    data class Cached<T>(val data: T, val timestamp: Date) : DataState<T>()
    data class Success<T>(val data: T) : DataState<T>()

    data class Error<T>(
        val errorCode: PersonalIdOrConnectApiErrorCodes = PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR,
        val throwable: Throwable? = null,
    ) : DataState<T>() {
        companion object {
            /**
             * Builds a DataState.Error from a throwable, extracting the typed error code from
             * ConnectApiException or falling back to UNKNOWN_ERROR.
             */
            fun <T> from(throwable: Throwable): Error<T> = Error(
                errorCode = (throwable as? ConnectApiException)?.errorCode
                    ?: PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR,
                throwable = throwable
            )
        }
    }
}
