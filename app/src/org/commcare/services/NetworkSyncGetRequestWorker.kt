package org.commcare.services

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.common.collect.ArrayListMultimap
import okhttp3.ResponseBody
import org.commcare.CommCareApplication
import org.commcare.network.CommcareRequestGenerator.buildAuth
import org.commcare.network.CommcareRequestGenerator.getHeaders
import org.javarosa.core.services.Logger
import retrofit2.Response
import java.io.File
import java.io.InputStream
import java.net.UnknownHostException

/**
 * Worker to perform network GET requests in the background.
 *
 * @author avazirna
 */

class NetworkSyncGetRequestWorker(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val username = inputData.getString(USERNAME_KEY)
        val password = inputData.getString(PASSWORD_KEY)
        val uri = inputData.getString(URI_KEY) ?: return Result.failure()
        val paramKeys = inputData.getString(PARAMS_KEYS_KEY) ?: return Result.failure()

        val params: ArrayListMultimap<String, String> = ArrayListMultimap.create()
        inputData.keyValueMap.forEach { (key, value) ->
            if (paramKeys.contains(key)) {
                params.put(key, value.toString())
            }
        }

        val requester = CommCareApplication.instance().createGetRequester(
            CommCareApplication.instance(),
            uri,
            params,
            getHeaders(null),
            buildAuth(username, password),
            null
        )
        try {
            val response: Response<ResponseBody> = requester.makeRequest()
            var outputData = workDataOf(
                RESPONSE_BODY_ABS_FILE_KEY to saveResponseBodyToCacheDir(id.toString(), response),
                RESPONSE_CODE_KEY to response.code())
            return if (response.isSuccessful) {
                Result.success(outputData)
            } else {
                Result.failure(outputData)
            }
        } catch (e: Exception) {
            return when (e) {
                is UnknownHostException -> {
                    Result.retry()
                } else -> {
                    Logger.exception("Encountered unexpected exception during network request, stopping the network request worker, ", e)
                    Result.failure()
                }
            }
        }
    }

    private fun saveResponseBodyToCacheDir(workerId: String, response: Response<ResponseBody>): String? {
        val tempFile = File(applicationContext.cacheDir, id.toString())
        val inputStream: InputStream = response.body()?.byteStream() ?: return null
        tempFile.outputStream().use { output ->
            inputStream.copyTo(output)
        }
        return tempFile.absolutePath
    }

    companion object {
        const val RESPONSE_CODE_KEY = "response_code"
        const val RESPONSE_BODY_ABS_FILE_KEY = "response_body_file"
        const val USERNAME_KEY = "username"
        const val PASSWORD_KEY = "password"
        const val URI_KEY = "uri"
        const val PARAMS_KEYS_KEY = "params_keys"
    }
}