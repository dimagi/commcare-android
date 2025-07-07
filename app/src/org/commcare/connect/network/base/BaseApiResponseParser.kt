package org.commcare.connect.network.base

import java.io.InputStream

/**
 * This is base interface for all response parsers for connect.
 */
interface BaseApiResponseParser<T> {
    fun parse(responseCode: Int, responseData: InputStream,anyInputObject:Any?=null): T
}