package org.commcare.tasks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.reactivex.functions.Cancellable
import org.commcare.CommCareApplication
import org.commcare.cases.entity.Entity
import org.commcare.cases.entity.EntityLoadingProgressListener
import org.commcare.suite.model.Detail
import org.commcare.suite.model.EntityDatum
import org.commcare.utils.AndroidCommCarePlatform
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.services.Logger

/**
 * Helper to prime cache for all entity screens in the app
 *
 * Implemented as a singleton to restrict caller from starting another
 * cache prime process if one is already in progress. Therefore it's advisable
 * to initiate all caching operations using this class
 */
class PrimeEntityCacheHelper private constructor() : Cancellable {

    private var entityLoaderHelper: EntityLoaderHelper? = null
    private var inProgress = false
    private var currentDetailInProgress: String? = null
    private var listener: EntityLoadingProgressListener? = null

    private val _cachedEntitiesLiveData = MutableLiveData<List<Entity<TreeReference>>>()
    val cachedEntitiesLiveData: LiveData<List<Entity<TreeReference>>> get() = _cachedEntitiesLiveData


    companion object {
        @Volatile
        private var instance: PrimeEntityCacheHelper? = null

        private const val PRIME_ENTITY_CACHE_REQUEST = "prime-entity-cache-request"

        @JvmStatic
        fun getInstance() =
            instance ?: synchronized(this) {
                instance ?: PrimeEntityCacheHelper().also { instance = it }
            }

        /**
         * Schedules a background worker request to prime cache for all
         * cache backed entity list screens in the current seated app
         */
        @JvmStatic
        fun schedulePrimeEntityCacheWorker() {
            val primeEntityCacheRequest = OneTimeWorkRequest.Builder(PrimeEntityCache::class.java).build()
            WorkManager.getInstance(CommCareApplication.instance())
                .enqueueUniqueWork(
                    PRIME_ENTITY_CACHE_REQUEST,
                    ExistingWorkPolicy.KEEP,
                    primeEntityCacheRequest
                )
        }

        @JvmStatic
        fun cancelWork() {
            instance?.cancel()
            WorkManager.getInstance(CommCareApplication.instance()).cancelUniqueWork(PRIME_ENTITY_CACHE_REQUEST)
        }
    }

    /**
     * Primes cache for all entity screens in the app
     * @throws IllegalStateException if a cache prime is already in progress or user session is not active
     */
    fun primeEntityCache() {
        checkPreConditions()
        primeEntityCacheForApp(CommCareApplication.instance().commCarePlatform)
        clearState()
    }

    /**
     * Primes cache for given entities set against the [detail]
     * @throws IllegalStateException if a cache prime is already in progress or user session is not active
     */
    fun primeEntityCacheForDetail(
        detail: Detail,
        entities: MutableList<Entity<TreeReference>>,
        progressListener: EntityLoadingProgressListener
    ) {
        checkPreConditions()
        primeCacheForDetail(detail,null, entities, progressListener)
        clearState()
    }

    /**
     * Cancel any current cache prime process to expedite cache calculations for given [detail]
     * Reschedules the work again in background afterwards
     */
    fun expediteDetailWithId(
        detail: Detail,
        entities: MutableList<Entity<TreeReference>>,
        progressListener: EntityLoadingProgressListener
    ) {
        cancel()
        primeEntityCacheForDetail(detail, entities, progressListener)
        schedulePrimeEntityCacheWorker()
    }

    fun isDetailInProgress(detailId: String): Boolean {
        return currentDetailInProgress?.contentEquals(detailId) ?: false
    }

    private fun primeEntityCacheForApp(commCarePlatform: AndroidCommCarePlatform) {
        inProgress = true
        val commandMap = commCarePlatform.commandToEntryMap
        for (command in commandMap.keys()) {
            val entry = commandMap[command]!!
            val sessionDatums = entry.sessionDataReqs
            for (sessionDatum in sessionDatums) {
                if (sessionDatum is EntityDatum) {
                    val shortDetailId = sessionDatum.shortDetail
                    if (shortDetailId != null) {
                        val detail = commCarePlatform.getDetail(shortDetailId)
                        primeCacheForDetail(detail, sessionDatum)
                    }
                }
            }
        }
    }

    private fun primeCacheForDetail(
        detail: Detail,
        sessionDatum: EntityDatum? = null,
        entities: MutableList<Entity<TreeReference>>? = null,
        progressListener: EntityLoadingProgressListener? = null
    ) {
        if (!detail.shouldCache()) return
        currentDetailInProgress = detail.id
        entityLoaderHelper = EntityLoaderHelper(detail, evalCtx()).also {
            it.factory.setEntityProgressListener(progressListener)
        }
        // Handle the cache operation based on the available input
        val cachedEntities = when {
            sessionDatum != null -> entityLoaderHelper!!.cacheEntities(sessionDatum.nodeset).first
            entities != null -> {
                entityLoaderHelper!!.cacheEntities(entities)
                entities
            }
            else -> return
        }
        _cachedEntitiesLiveData.postValue(cachedEntities)
        currentDetailInProgress = null
    }

    private fun evalCtx(): EvaluationContext {
        return CommCareApplication.instance().currentSessionWrapper.evaluationContext
    }

    /**
     * Clears any volatile state and nullify the singleton instance
     */
    fun clearState() {
        entityLoaderHelper = null
        inProgress = false
        listener = null
        currentDetailInProgress = null
        instance = null
    }

    private fun checkPreConditions() {
        require(CommCareApplication.instance().session.isActive) { "User session must be active to prime entity cache" }
        require(!inProgress) { "We are already priming the cache" }
    }

    override fun cancel() {
        entityLoaderHelper?.cancel()
        clearState()
    }

    fun isInProgress(): Boolean {
        return inProgress
    }

    fun setListener(entityLoadingProgressListener: EntityLoadingProgressListener) {
        entityLoaderHelper?.factory?.setEntityProgressListener(entityLoadingProgressListener)
    }
}
