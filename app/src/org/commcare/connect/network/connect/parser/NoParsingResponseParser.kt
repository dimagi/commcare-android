package org.commcare.connect.network.connect.parser
import org.commcare.connect.network.PersonalIdOrConnectApiResponseParser
import java.io.InputStream

/**
 * If no parsing (content) present, then return Boolean as success/failure
 */
class NoParsingResponseParser<T>() : PersonalIdOrConnectApiResponseParser<T> {

    override fun parse(responseCode: Int, responseData: InputStream): T {
        val success = responseCode in 200..299
        return success as T
    }
}