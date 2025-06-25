package org.commcare.connect.network

import java.io.InputStream

/**
 * This is base interface for all response parsers for connect.
 */
interface PersonalIdOrConnectApiResponseParser<T> {
    fun parse(responseCode: Int, responseData: InputStream): T
}