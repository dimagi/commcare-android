package org.commcare.connect.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.commcare.CommCareApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.database.ConnectJobUtils.getCompositeJob
import org.commcare.connect.database.ConnectJobUtils.getCompositeJobs
import org.commcare.connect.database.ConnectJobUtils.updateJobLearnProgress
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.connect.ConnectNetworkClient
import org.commcare.connect.network.connect.models.ConnectOpportunitiesResponseModel
import org.commcare.connect.network.connect.models.LearningAppProgressResponseModel

class ConnectRepository
    @VisibleForTesting
    internal constructor(
        private val syncPrefs: ConnectSyncPreferences,
        private val networkClient: ConnectNetworkClient,
    ) {
        companion object {
            const val ENDPOINT_OPPORTUNITIES = "/opportunities"
            const val ENDPOINT_LEARNING_PREFIX = "/learning_progress/"

            @Volatile
            private var instance: ConnectRepository? = null

            fun getInstance(context: Context): ConnectRepository =
                instance ?: synchronized(this) {
                    instance ?: ConnectRepository(
                        ConnectSyncPreferences.getInstance(context),
                        ConnectNetworkClient.getInstance(),
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
                    getCompositeJobs(
                        CommCareApplication.instance(),
                        ConnectJobRecord.STATUS_ALL_JOBS,
                        null,
                    )
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
                loadCache = { getCompositeJob(CommCareApplication.instance(), job.jobUUID) },
                networkCall = { fetchLearningProgressFromNetwork(job) },
                onNetworkSuccess = { responseModel ->
                    job.learnings = responseModel.connectJobLearningRecords
                    job.learningModulesCompleted = responseModel.connectJobLearningRecords.size
                    job.assessments = responseModel.connectJobAssessmentRecords
                    updateJobLearnProgress(CommCareApplication.instance(), job)
                },
                mapToEmit = { _ -> getCompositeJob(CommCareApplication.instance(), job.jobUUID) },
            )

        /**
         * Emits Cached first,then Loading, then Success or Error after network call.
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
                val isCacheAvailable = cachedData != null && lastSyncTime != null
                if (isCacheAvailable) {
                    emit(DataState.Cached(cachedData, lastSyncTime))
                }

                if (isCacheAvailable && !forceRefresh && !syncPrefs.shouldRefresh(endpoint, policy)) return@flow

                emit(DataState.Loading)
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
                    .onFailure { throwable -> emit(DataState.Error.from(throwable)) }
            }.flowOn(Dispatchers.IO)

        private suspend fun fetchOpportunitiesFromNetwork(): Result<ConnectOpportunitiesResponseModel> {
            val user = requireNotNull(ConnectUserDatabaseUtil.getUser(CommCareApplication.instance())) { "No Connect user found" }
            return networkClient.getConnectOpportunities(user)
        }

        private suspend fun fetchLearningProgressFromNetwork(job: ConnectJobRecord): Result<LearningAppProgressResponseModel> {
            val user = requireNotNull(ConnectUserDatabaseUtil.getUser(CommCareApplication.instance())) { "No Connect user found" }
            return networkClient.getLearningProgress(user, job)
        }
    }
