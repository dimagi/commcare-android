package org.commcare.connect.network.connect.parser

import java.io.InputStream

/**
 * This is base interface for all response parsers for connect.
 */
interface ConnectApiResponseParser<T> {
    fun parse(responseCode: Int, responseData: InputStream): T
}