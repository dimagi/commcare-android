package org.commcare.tasks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import io.reactivex.functions.Cancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.commcare.AppUtils.getCurrentAppId
import org.commcare.CommCareApplication
import org.commcare.cases.entity.Entity
import org.commcare.cases.entity.EntityLoadingProgressListener
import org.commcare.suite.model.Detail
import org.commcare.suite.model.EntityDatum
import org.commcare.utils.AndroidCommCarePlatform
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.services.Logger
import org.javarosa.xpath.XPathException

/**
 * Helper to prime cache for all entity screens in the app
 *
 * Implemented as a singleton to restrict caller from starting another
 * cache prime process if one is already in progress. Therefore it's advisable
 * to initiate all caching operations using this class
 */
class PrimeEntityCacheHelper() : Cancellable, EntityLoadingProgressListener {

    private var entityLoaderHelper: EntityLoaderHelper? = null

    @Volatile
    private var inProgress = false

    @Volatile
    private var currentDatumInProgress: String? = null
    @Volatile
    private var currentDetailInProgress: String? = null
    private var listener: EntityLoadingProgressListener? = null
    @Volatile
    private var cancelled = false


    private val _completionStatus = MutableSharedFlow<Boolean>(replay = 0)
    val completionStatus = _completionStatus.asSharedFlow()

    private val _cachedEntitiesState = MutableSharedFlow<Triple<String, String, List<Entity<TreeReference>>>?>(replay = 0)
    val cachedEntitiesState = _cachedEntitiesState.asSharedFlow()

    private val _progressState = MutableLiveData<Triple<String, String, Array<Int>>>(null)
    val progressState: LiveData<Triple<String, String, Array<Int>>?> get() = _progressState


    companion object {
        const val PRIME_ENTITY_CACHE_REQUEST = "prime-entity-cache-request"
        const val ENTITY_CACHE_INVALIDATION_REQUEST = "entity-cache-invalidation-request"

        /**
         * Schedules a background worker request to prime cache for all
         * cache backed entity list screens in the current seated app
         */
        @JvmStatic
        fun schedulePrimeEntityCacheWorker() {
            if (CommCareApplication.isSessionActive() && CommCareApplication.instance().commCarePlatform.isEntityCachingEnabled()) {
                val primeEntityCacheRequest = OneTimeWorkRequest.Builder(PrimeEntityCache::class.java)
                    .addTag(getCurrentAppId())
                    .build()
                WorkManager.getInstance(CommCareApplication.instance())
                    .enqueueUniqueWork(
                        getWorkRequestName(),
                        ExistingWorkPolicy.KEEP,
                        primeEntityCacheRequest
                    )
            }
        }

        private fun getWorkRequestName(): String {
            return PRIME_ENTITY_CACHE_REQUEST + "_" + getCurrentAppId()
        }

        @JvmStatic
        fun scheduleEntityCacheInvalidation() {
            if (CommCareApplication.instance().commCarePlatform.isEntityCachingEnabled()) {
                val entityCacheInvalidationRequest =
                    OneTimeWorkRequest.Builder(EntityCacheInvalidationWorker::class.java)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                WorkManager.getInstance(CommCareApplication.instance()).enqueueUniqueWork(
                    ENTITY_CACHE_INVALIDATION_REQUEST, ExistingWorkPolicy.REPLACE,
                    entityCacheInvalidationRequest
                )
            }
        }
    }

    fun cancelWork() {
        cancel()
        WorkManager.getInstance(CommCareApplication.instance()).cancelUniqueWork(getWorkRequestName())
    }

    /**
     * Primes cache for all entity screens in the app
     * @throws IllegalStateException if a cache prime is already in progress or user session is not active
     */
    @Synchronized
    fun primeEntityCache() {
        checkPreConditions()
        try {
            primeEntityCacheForApp(CommCareApplication.instance().commCarePlatform)
        } finally {
            clearState()
            runBlocking {
                _completionStatus.emit(true)
            }
        }
    }

    /**
     * Primes cache for given entities set against the [detail]
     * @throws IllegalStateException if a cache prime is already in progress or user session is not active
     */
    @Synchronized
    fun primeEntityCacheForDetail(
        commandId: String,
        detail: Detail,
        entityDatum: EntityDatum,
        entities: MutableList<Entity<TreeReference>>
    ) {
        checkPreConditions()
        try {
            primeCacheForDetail(commandId, detail, entityDatum, entities)
        } finally {
            clearState()
            runBlocking {
                _completionStatus.emit(true)
            }
        }
    }

    fun isSelectDatumInProgress(datumId: String, detailId: String): Boolean {
        return currentDetailInProgress?.contentEquals(detailId) == true &&
            currentDatumInProgress?.contentEquals(datumId) == true
    }

    private fun primeEntityCacheForApp(commCarePlatform: AndroidCommCarePlatform) {
        inProgress = true
        val commandMap = commCarePlatform.commandToEntryMap
        for (command in commandMap.keys()) {
            if(cancelled) return
            val entry = commandMap[command]!!
            val sessionDatums = entry.sessionDataReqs
            for (sessionDatum in sessionDatums) {
                if (sessionDatum is EntityDatum) {
                    if(cancelled) return
                    val shortDetailId = sessionDatum.shortDetail
                    if (shortDetailId != null) {
                        val detail = commCarePlatform.getDetail(shortDetailId)
                        try {
                            primeCacheForDetail(entry.commandId, detail, sessionDatum, null, true)
                        } catch (e: XPathException) {
                            // Bury any xpath exceptions here as we don't want to hold off priming cache
                            // for other datums because of an error with a particular detail.
                            Logger.exception(
                                "Xpath error on trying to prime cache for datum: " + sessionDatum.dataId,
                                e
                            )
                        }
                    }
                }
            }
        }
    }

    private fun primeCacheForDetail(
        commandId: String,
        detail: Detail,
        entityDatum: EntityDatum,
        entities: MutableList<Entity<TreeReference>>? = null,
        inBackground: Boolean = false,
    ) {
        if (!detail.isCacheEnabled() || cancelled) return
        currentDatumInProgress = entityDatum.dataId
        currentDetailInProgress = detail.id
        entityLoaderHelper = EntityLoaderHelper(detail, entityDatum, evalCtx(commandId), inBackground).also {
            it.factory.setEntityProgressListener(this)
        }
        // Handle the cache operation based on the available input
        val cachedEntities = when {
            entities != null -> {
                entityLoaderHelper!!.cacheEntities(entities)
                entities
            }
            else -> entityLoaderHelper!!.cacheEntities(entityDatum.nodeset).first
        }
        runBlocking {
            _cachedEntitiesState.emit(Triple(entityDatum.dataId, detail.id, cachedEntities))
        }
        currentDatumInProgress = null
        currentDetailInProgress = null
    }

    private fun evalCtx(commandId: String): EvaluationContext {
        return CommCareApplication.instance().currentSessionWrapper.getRestrictedEvaluationContext(commandId, null)
    }

    /**
     * Clears any volatile state and nullify the singleton instance
     */
    fun clearState() {
        entityLoaderHelper = null
        inProgress = false
        listener = null
        currentDatumInProgress = null
        currentDetailInProgress = null
        cancelled = false
    }

    private fun checkPreConditions() {
        require(CommCareApplication.instance().session.isActive) { "User session must be active to prime entity cache" }
        require(!inProgress) { "We are already priming the cache" }
        require(!cancelled) { "Trying to interact with a cancelled worker"}
    }

    override fun cancel() {
        cancelled = true
        entityLoaderHelper?.cancel()
    }

    fun isInProgress(): Boolean {
        return inProgress
    }

    override fun publishEntityLoadingProgress(
        phase: EntityLoadingProgressListener.EntityLoadingProgressPhase,
        progress: Int,
        total: Int
    ) {
        _progressState.postValue(
            Triple(
                currentDatumInProgress!!,
                currentDetailInProgress!!,
                arrayOf(phase.value, progress, total)
            )
        )
    }
}
