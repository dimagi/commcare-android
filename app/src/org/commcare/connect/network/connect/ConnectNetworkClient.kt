package org.commcare.connect.network.connect

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CancellationException
import okhttp3.ResponseBody
import org.commcare.CommCareApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.network.ConnectApiService
import org.commcare.connect.network.ConnectNetworkHelper
import org.commcare.connect.network.LoginInvalidatedException
import org.commcare.connect.network.base.BaseApiClient
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.base.ConnectApiException
import org.commcare.connect.network.connect.models.ConnectOpportunitiesResponseModel
import org.commcare.connect.network.connect.models.LearningAppProgressResponseModel
import org.commcare.connect.network.connect.parser.ConnectOpportunitiesParser
import org.commcare.connect.network.connect.parser.LearningAppProgressResponseParser
import org.commcare.connect.network.getAuthorizationHeader
import org.commcare.connect.network.mapHttpErrorCode
import retrofit2.Response
import java.io.IOException
import java.io.InputStream

class ConnectNetworkClient
    @VisibleForTesting
    internal constructor(
        private val apiService: ConnectApiService,
    ) {
        companion object {
            private const val API_VERSION = "1.0"

            @Volatile
            private var instance: ConnectNetworkClient? = null

            fun getInstance(context: Context): ConnectNetworkClient =
                instance ?: synchronized(this) {
                    instance ?: ConnectNetworkClient(
                        BaseApiClient
                            .buildRetrofitClient(ConnectApiClient.BASE_URL)
                            .create(ConnectApiService::class.java),
                    ).also { instance = it }
                }
        }

        private fun versionHeaders(): Map<String, String> =
            HashMap<String, String>().also { ConnectNetworkHelper.addVersionHeader(it, API_VERSION) }

        suspend fun getConnectOpportunities(user: ConnectUserRecord): Result<ConnectOpportunitiesResponseModel> =
            executeApiCall(
                user = user,
                apiCall = { auth -> apiService.getConnectOpportunities(auth, versionHeaders()) },
                parse = { code, stream ->
                    ConnectOpportunitiesParser<ConnectOpportunitiesResponseModel>().parse(
                        code,
                        stream,
                    )
                },
            )

        suspend fun getLearningProgress(
            user: ConnectUserRecord,
            job: ConnectJobRecord,
        ): Result<LearningAppProgressResponseModel> =
            executeApiCall(
                user = user,
                apiCall = { auth -> apiService.getLearningProgress(auth, job.jobUUID, versionHeaders()) },
                parse = { code, stream -> LearningAppProgressResponseParser<LearningAppProgressResponseModel>().parse(code, stream, job) },
            )

        private suspend fun <T> executeApiCall(
            user: ConnectUserRecord,
            apiCall: suspend (authHeader: String) -> Response<ResponseBody>,
            parse: (responseCode: Int, stream: InputStream) -> T,
        ): Result<T> {
            return try {
                val authHeader =
                    getAuthorizationHeader(user)
                        .getOrElse { return Result.failure(it) }

                val response = apiCall(authHeader)

                if (response.isSuccessful) {
                    val body =
                        response.body()
                            ?: return Result.failure(ConnectApiException(PersonalIdOrConnectApiErrorCodes.JSON_PARSING_ERROR))
                    try {
                        body.use { Result.success(parse(response.code(), it.byteStream())) }
                    } catch (e: Exception) {
                        Result.failure(ConnectApiException(PersonalIdOrConnectApiErrorCodes.JSON_PARSING_ERROR, e))
                    }
                } else {
                    val errorCode = mapHttpErrorCode(response.code(), response.errorBody()?.string())
                    Result.failure(ConnectApiException(errorCode))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LoginInvalidatedException) {
                throw e
            } catch (e: IOException) {
                Result.failure(ConnectApiException(PersonalIdOrConnectApiErrorCodes.NETWORK_ERROR, e))
            } catch (e: Exception) {
                Result.failure(ConnectApiException(PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR, e))
            }
        }
    }
