package org.commcare.connect.network.base

import org.json.JSONException
import java.io.IOException
import java.io.InputStream

/**
 * This is base interface for all response parsers for connect.
 */
interface BaseApiResponseParser<T> {
    @Throws(IOException::class,JSONException::class)
    fun parse(responseCode: Int, responseData: InputStream, anyInputObject:Any?=null): T
}