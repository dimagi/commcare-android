package org.commcare.connect.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commcare.CommCareApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectJobUtils.getCompositeJob
import org.commcare.connect.database.ConnectJobUtils.getCompositeJobs
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.connect.ConnectNetworkClient
import org.commcare.connect.network.connect.models.ConnectPaymentConfirmationModel
import org.commcare.connect.network.connect.models.DeliveryAppProgressResponseModel
import org.commcare.connect.network.connect.models.LearningAppProgressResponseModel
import org.commcare.connect.network.connect.models.applyToJob
import org.commcare.google.services.analytics.AnalyticsParamValue.FINISH_DELIVERY
import org.commcare.google.services.analytics.AnalyticsParamValue.PAID_DELIVERY
import org.commcare.google.services.analytics.AnalyticsParamValue.START_DELIVERY
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.coroutines.DispatcherProvider

class ConnectRepository
    @VisibleForTesting
    internal constructor(
        private val syncPrefs: ConnectSyncPreferences,
        private val networkClient: ConnectNetworkClient,
    ) {
        companion object {
            const val SYNC_KEY_OPPORTUNITIES = "/opportunities"
            const val SYNC_KEY_LEARNING_PREFIX = "/learning_progress/"
            const val SYNC_KEY_DELIVERY_PREFIX = "/delivery_progress/"

            @Volatile
            private var instance: ConnectRepository? = null

            @JvmStatic
            fun getInstance(context: Context): ConnectRepository =
                instance ?: synchronized(this) {
                    instance ?: ConnectRepository(
                        ConnectSyncPreferences.getInstance(context),
                        ConnectNetworkClient.getInstance(),
                    ).also { instance = it }
                }

            @VisibleForTesting
            internal fun resetInstance() {
                instance = null
            }
        }

        fun getOpportunities(
            forceRefresh: Boolean = false,
            policy: RefreshPolicy = RefreshPolicy.SESSION_AND_TIME_BASED(),
        ): Flow<DataState<List<ConnectJobRecord>>> =
            offlineFirstFlow(
                syncKey = SYNC_KEY_OPPORTUNITIES,
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
                mapToEmit = { jobs -> jobs },
            )

        fun getLearningProgress(
            job: ConnectJobRecord,
            forceRefresh: Boolean = false,
            policy: RefreshPolicy = RefreshPolicy.ALWAYS,
        ): Flow<DataState<ConnectJobRecord>> =
            offlineFirstFlow(
                syncKey = SYNC_KEY_LEARNING_PREFIX + job.jobUUID,
                forceRefresh = forceRefresh,
                policy = policy,
                loadCache = { getCompositeJob(CommCareApplication.instance(), job.jobUUID) },
                networkCall = { fetchLearningProgressFromNetwork(job) },
                onNetworkSuccess = { responseModel ->
                    responseModel.applyToJob(job, CommCareApplication.instance())
                    if (job.passedAssessment()) {
                        FirebaseAnalyticsUtil.reportCccApiLearnProgress(true)
                    }
                },
                onNetworkFailure = { FirebaseAnalyticsUtil.reportCccApiLearnProgress(false) },
                mapToEmit = { _ -> getCompositeJob(CommCareApplication.instance(), job.jobUUID) },
            )

        fun getDeliveryProgress(
            job: ConnectJobRecord,
            forceRefresh: Boolean = false,
            policy: RefreshPolicy = RefreshPolicy.ALWAYS,
        ): Flow<DataState<ConnectJobRecord>> =
            offlineFirstFlow(
                syncKey = SYNC_KEY_DELIVERY_PREFIX + job.jobUUID,
                forceRefresh = forceRefresh,
                policy = policy,
                loadCache = { getCompositeJob(CommCareApplication.instance(), job.jobUUID) },
                networkCall = { fetchDeliveryProgressFromNetwork(job) },
                onNetworkSuccess = { responseModel ->
                    val events = mutableSetOf<String?>()
                    if (responseModel.updatedJob) events.add(START_DELIVERY)
                    if (responseModel.hasDeliveries && job.getDeliveryProgressPercentage() == 100) events.add(FINISH_DELIVERY)
                    if (responseModel.hasPayment && job.payments.isNotEmpty()) events.add(PAID_DELIVERY)
                    responseModel.applyToJob(job, CommCareApplication.instance())
                    events.forEach { event -> FirebaseAnalyticsUtil.reportCccApiDeliveryProgress(true, event) }
                },
                onNetworkFailure = { FirebaseAnalyticsUtil.reportCccApiDeliveryProgress(false, null) },
                mapToEmit = { _ -> getCompositeJob(CommCareApplication.instance(), job.jobUUID) },
            )

        fun startLearning(jobUUID: String): Flow<DataState<Unit>> =
            networkOnlyFlow(networkCall = { networkClient.startLearnApp(getConnectUser(), jobUUID) })

        fun claimJob(job: ConnectJobRecord): Flow<DataState<Unit>> =
            networkOnlyFlow(
                networkCall = { networkClient.claimJob(getConnectUser(), job.jobUUID) },
                onNetworkSuccess = {
                    job.status = ConnectJobRecord.STATUS_DELIVERING
                    ConnectJobUtils.upsertJob(job)
                },
            )

        fun confirmPayments(paymentConfirmations: List<ConnectPaymentConfirmationModel>): Flow<DataState<Unit>> =
            networkOnlyFlow(
                networkCall = { networkClient.confirmPayments(getConnectUser(), paymentConfirmations) },
                onNetworkSuccess = {
                    for (paymentConfirmation in paymentConfirmations) {
                        paymentConfirmation.payment.confirmed = paymentConfirmation.toConfirm
                        ConnectJobUtils.storePayment(CommCareApplication.instance(), paymentConfirmation.payment)
                    }
                    FirebaseAnalyticsUtil.reportCccApiPaymentConfirmation(true)
                },
                onNetworkFailure = { FirebaseAnalyticsUtil.reportCccApiPaymentConfirmation(false) },
            )

        fun syncJobProgress(job: ConnectJobRecord): Flow<DataState<ConnectJobRecord>> =
            when (job.status) {
                ConnectJobRecord.STATUS_LEARNING -> getLearningProgress(job)
                ConnectJobRecord.STATUS_DELIVERING -> getDeliveryProgress(job)
                else -> flow { emit(DataState.Success(job)) }
            }

        /**
         * Emits Cached first,then Loading, then Success or Error after network call.
         * DB writes go in [onNetworkSuccess], re-read in [mapToEmit].
         *
         * Used for GET requests that have a cached value to emit first, then make a network call to update the cache and emit the updated value.
         * Uses ConnectRequestManager to deduplicate requests for the same syncKey.
         */
        private fun <C, N> offlineFirstFlow(
            syncKey: String,
            forceRefresh: Boolean,
            policy: RefreshPolicy,
            loadCache: () -> C?,
            networkCall: suspend () -> Result<N>,
            onNetworkSuccess: suspend (N) -> Unit,
            onNetworkFailure: suspend (Throwable) -> Unit = {},
            mapToEmit: suspend (N) -> C,
        ): Flow<DataState<C>> =
            flow {
                val cachedData: C? = loadCache()
                val lastSyncTime = syncPrefs.getLastSyncTime(syncKey)
                val isCacheAvailable = cachedData != null && lastSyncTime != null
                if (isCacheAvailable) {
                    emit(DataState.Cached(cachedData, lastSyncTime))
                }

                if (isCacheAvailable && !forceRefresh && !syncPrefs.shouldRefresh(syncKey, policy)) return@flow

                emit(DataState.Loading)
                val result =
                    ConnectRequestManager.executeRequest(syncKey) {
                        networkCall().also { networkResult ->
                            networkResult.onSuccess { data ->
                                onNetworkSuccess(data)
                                syncPrefs.storeLastSyncTime(syncKey)
                            }
                        }
                    }
                result
                    .onSuccess { data -> emit(DataState.Success(mapToEmit(data))) }
                    .onFailure { throwable ->
                        onNetworkFailure(throwable)
                        emit(DataState.Error.from(throwable))
                    }
            }.flowOn(DispatcherProvider.io())

        /**
         * Emits Loading, then Success or Error after network call.
         * No cached emission, always make requests to network unlike [offlineFirstFlow].
         * Doesn't use ConnectRequestManager to deduplicate requests.
         *
         * Used for one-time actions, mostly POST requests, that don't have a cached value to emit first.
         */
        private fun <T> networkOnlyFlow(
            networkCall: suspend () -> Result<T>,
            onNetworkSuccess: suspend (T) -> Unit = {},
            onNetworkFailure: suspend (Throwable) -> Unit = {},
        ): Flow<DataState<T>> =
            flow {
                emit(DataState.Loading)
                networkCall()
                    .onSuccess { data ->
                        onNetworkSuccess(data)
                        emit(DataState.Success(data))
                    }.onFailure {
                        onNetworkFailure(it)
                        emit(DataState.Error.from(it))
                    }
            }.flowOn(DispatcherProvider.io())

        private fun getConnectUser(): ConnectUserRecord =
            requireNotNull(ConnectUserDatabaseUtil.getUser(CommCareApplication.instance())) { "No Connect user found" }

        private suspend fun fetchOpportunitiesFromNetwork(): Result<List<ConnectJobRecord>> =
            networkClient.getConnectOpportunities(getConnectUser())

        private suspend fun fetchLearningProgressFromNetwork(job: ConnectJobRecord): Result<LearningAppProgressResponseModel> =
            networkClient.getLearningProgress(getConnectUser(), job)

        private suspend fun fetchDeliveryProgressFromNetwork(job: ConnectJobRecord): Result<DeliveryAppProgressResponseModel> =
            networkClient.getDeliveryProgress(getConnectUser(), job)

        // Java interop — use the Flow-returning equivalents from Kotlin.

        fun retrieveOpportunitiesForJava(listener: ConnectActivityCompleteListener) =
            getOpportunities(forceRefresh = true).launchForJava(listener)

        fun updateDeliveryProgressForJava(
            job: ConnectJobRecord,
            listener: ConnectActivityCompleteListener,
        ) = getDeliveryProgress(job).launchForJava(listener)

        fun updatePaymentsConfirmedForJava(
            paymentConfirmations: List<ConnectPaymentConfirmationModel>,
            listener: ConnectActivityCompleteListener,
        ) = confirmPayments(paymentConfirmations).launchForJava(listener)

        private fun <T> Flow<DataState<T>>.launchForJava(listener: ConnectActivityCompleteListener) {
            CoroutineScope(DispatcherProvider.io()).launch {
                collect { state ->
                    when (state) {
                        is DataState.Success -> {
                            withContext(DispatcherProvider.main()) {
                                listener.connectActivityComplete(true)
                            }
                        }

                        is DataState.Error -> {
                            withContext(DispatcherProvider.main()) {
                                listener.connectActivityComplete(
                                    false,
                                    PersonalIdOrConnectApiErrorHandler.handle(
                                        CommCareApplication.instance(),
                                        state.errorCode,
                                        state.throwable,
                                    ),
                                )
                            }
                        }

                        else -> {}
                    }
                }
            }
        }
    }
