package org.commcare.tasks

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.reactivex.functions.Cancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
class PrimeEntityCacheHelper private constructor() : Cancellable {

    private var entityLoaderHelper: EntityLoaderHelper? = null
    private var inProgress = false
    private var currentDatumInProgress: String? = null
    private var listener: EntityLoadingProgressListener? = null

    private val _cachedEntitiesState = MutableStateFlow<List<Entity<TreeReference>>?>(null)
    val cachedEntitiesState: StateFlow<List<Entity<TreeReference>>?> get() = _cachedEntitiesState


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
        commandId: String,
        detail: Detail,
        entityDatum: EntityDatum,
        entities: MutableList<Entity<TreeReference>>,
        progressListener: EntityLoadingProgressListener
    ) {
        checkPreConditions()
        primeCacheForDetail(commandId, detail, entityDatum, entities, progressListener)
        clearState()
    }

    /**
     * Cancel any current cache prime process to expedite cache calculations for given [detail]
     * Reschedules the work again in background afterwards
     */
    fun expediteDetailWithId(
        commandId: String,
        detail: Detail,
        entityDatum: EntityDatum,
        entities: MutableList<Entity<TreeReference>>,
        progressListener: EntityLoadingProgressListener
    ) {
        cancel()
        primeEntityCacheForDetail(commandId, detail, entityDatum, entities, progressListener)
        schedulePrimeEntityCacheWorker()
    }

    fun isDatumInProgress(datumId: String): Boolean {
        return currentDatumInProgress?.contentEquals(datumId) ?: false
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
                        try {
                            primeCacheForDetail(entry.commandId, detail, sessionDatum)
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
        progressListener: EntityLoadingProgressListener? = null
    ) {
        if (!detail.shouldCache()) return
        currentDatumInProgress = entityDatum.dataId
        entityLoaderHelper = EntityLoaderHelper(detail, entityDatum, evalCtx(commandId)).also {
            it.factory.setEntityProgressListener(progressListener)
        }
        // Handle the cache operation based on the available input
        val cachedEntities = when {
            entities != null -> {
                entityLoaderHelper!!.cacheEntities(entities)
                entities
            }
            else -> entityLoaderHelper!!.cacheEntities(entityDatum.nodeset).first
        }
        _cachedEntitiesState.value = cachedEntities
        currentDatumInProgress = null
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
