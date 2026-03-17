package org.commcare.connect.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.connect.ConnectNetworkClient
import org.commcare.connect.network.connect.models.ConnectOpportunitiesResponseModel
import org.commcare.connect.network.connect.models.LearningAppProgressResponseModel

class ConnectRepository
    @VisibleForTesting
    internal constructor(
        private val context: Context,
        private val syncPrefs: ConnectSyncPreferences,
        private val networkClient: ConnectNetworkClient,
    ) {
        companion object {
            private const val ENDPOINT_OPPORTUNITIES = "/opportunities"
            private const val ENDPOINT_LEARNING_PREFIX = "/learning_progress/"

            @Volatile
            private var instance: ConnectRepository? = null

            fun getInstance(context: Context): ConnectRepository =
                instance ?: synchronized(this) {
                    instance ?: ConnectRepository(
                        context.applicationContext,
                        ConnectSyncPreferences.getInstance(context),
                        ConnectNetworkClient.getInstance(context),
                    ).also { instance = it }
                }
        }

        fun getOpportunities(
            forceRefresh: Boolean = false,
            policy: RefreshPolicy = RefreshPolicy.SESSION_AND_TIME_BASED(),
        ): Flow<DataState<List<ConnectJobRecord>>> =
            offlineFirstFlow(
                endpoint = ENDPOINT_OPPORTUNITIES,
                forceRefresh = forceRefresh,
                policy = policy,
                loadCache = {
                    ConnectJobUtils
                        .getCompositeJobs(context, ConnectJobRecord.STATUS_ALL_JOBS, null)
                        .takeIf { it.isNotEmpty() }
                },
                networkCall = { fetchOpportunitiesFromNetwork() },
                onNetworkSuccess = {},
                mapToEmit = { responseModel -> responseModel.validJobs },
            )

        fun getLearningProgress(
            job: ConnectJobRecord,
            forceRefresh: Boolean = false,
            policy: RefreshPolicy = RefreshPolicy.ALWAYS,
        ): Flow<DataState<ConnectJobRecord>> =
            offlineFirstFlow(
                endpoint = ENDPOINT_LEARNING_PREFIX + job.jobUUID,
                forceRefresh = forceRefresh,
                policy = policy,
                loadCache = { ConnectJobUtils.getCompositeJob(context, job.jobUUID) },
                networkCall = { fetchLearningProgressFromNetwork(job) },
                onNetworkSuccess = { responseModel ->
                    job.learnings = responseModel.connectJobLearningRecords
                    job.learningModulesCompleted = responseModel.connectJobLearningRecords.size
                    job.assessments = responseModel.connectJobAssessmentRecords
                    ConnectJobUtils.updateJobLearnProgress(context, job)
                },
                mapToEmit = { _ -> ConnectJobUtils.getCompositeJob(context, job.jobUUID) },
            )

        /**
         * Emits Loading or Cached first, then Success or Error after network call.
         * DB writes go in [onNetworkSuccess], re-read in [mapToEmit].
         */
        private fun <C, N> offlineFirstFlow(
            endpoint: String,
            forceRefresh: Boolean,
            policy: RefreshPolicy,
            loadCache: () -> C?,
            networkCall: suspend () -> Result<N>,
            onNetworkSuccess: suspend (N) -> Unit,
            mapToEmit: suspend (N) -> C,
        ): Flow<DataState<C>> =
            flow {
                val cachedData: C? = loadCache()
                val lastSyncTime = syncPrefs.getLastSyncTime(endpoint)

                if (cachedData != null) {
                    emit(DataState.Cached(cachedData, lastSyncTime!!))
                } else {
                    emit(DataState.Loading)
                }

                if (!forceRefresh && !syncPrefs.shouldRefresh(endpoint, policy)) return@flow

                try {
                    val result =
                        ConnectRequestManager.executeRequest(endpoint) {
                            networkCall().also { networkResult ->
                                networkResult.onSuccess { data ->
                                    onNetworkSuccess(data)
                                    syncPrefs.storeLastSyncTime(endpoint)
                                }
                            }
                        }
                    result
                        .onSuccess { data -> emit(DataState.Success(mapToEmit(data))) }
                        .onFailure { throwable -> emit(DataState.Error.from(throwable, cachedData)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emit(DataState.Error.from(e, cachedData))
                }
            }.flowOn(Dispatchers.IO)

        private suspend fun fetchOpportunitiesFromNetwork(): Result<ConnectOpportunitiesResponseModel> {
            val user = requireNotNull(ConnectUserDatabaseUtil.getUser(context)) { "No Connect user found" }
            return networkClient.getConnectOpportunities(user)
        }

        private suspend fun fetchLearningProgressFromNetwork(job: ConnectJobRecord): Result<LearningAppProgressResponseModel> {
            val user = requireNotNull(ConnectUserDatabaseUtil.getUser(context)) { "No Connect user found" }
            return networkClient.getLearningProgress(user, job)
        }
    }
