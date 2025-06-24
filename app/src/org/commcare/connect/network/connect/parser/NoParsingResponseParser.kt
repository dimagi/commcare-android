package org.commcare.connect.network.connect.parser
import java.io.InputStream

/**
 * If no parsing (content) present, then return Boolean as success/failure
 */
class NoParsingResponseParser<T>() : ConnectApiResponseParser<T> {

    override fun parse(responseCode: Int, responseData: InputStream): T {
        val success = responseCode in 200..299
        return success as T
    }
}