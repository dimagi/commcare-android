package org.commcare.tasks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.reactivex.functions.Cancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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


    private val _completionStatus = MutableStateFlow<Boolean>(false)
    val completionStatus: StateFlow<Boolean> get() = _completionStatus

    private val _cachedEntitiesState = MutableStateFlow<Triple<String, String, List<Entity<TreeReference>>>?>(null)
    val cachedEntitiesState: StateFlow<Triple<String, String, List<Entity<TreeReference>>>?> get() = _cachedEntitiesState

    private val _progressState = MutableLiveData<Triple<String, String, Array<Int>>>(null)
    val progressState: LiveData<Triple<String, String, Array<Int>>> get() = _progressState


    companion object {
        const val PRIME_ENTITY_CACHE_REQUEST = "prime-entity-cache-request"

        /**
         * Schedules a unique background worker to prime the cache for all cache-backed entity list screens in the current app.
         *
         * This function creates a one-time work request tagged with the current app's identifier and enqueues it with a KEEP policy,
         * ensuring that duplicate cache priming tasks are not scheduled.
         */
        @JvmStatic
        fun schedulePrimeEntityCacheWorker() {
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

        /**
         * Generates a unique work request name for priming the entity cache.
         *
         * The work request name is formed by concatenating a constant prefix with the current application ID.
         *
         * @return A unique identifier for the work request.
         */
        private fun getWorkRequestName(): String {
            return PRIME_ENTITY_CACHE_REQUEST + "_" + getCurrentAppId()
        }
    }

    /**
     * Cancels the ongoing cache priming operation and its associated background work.
     *
     * This method marks the current caching process as cancelled by invoking the internal cancel() routine,
     * and then requests WorkManager to cancel the unique work associated with cache priming.
     */
    fun cancelWork() {
        cancel()
        WorkManager.getInstance(CommCareApplication.instance()).cancelUniqueWork(getWorkRequestName())
    }

    /**
     * Initiates caching of all entity screens in the application.
     *
     * This thread-safe method validates that there is an active user session and no other caching process is in progress.
     * On success, it proceeds to prime the cache across the application using the current CommCare platform.
     * Regardless of the outcome, the caching state is cleared and marked as complete.
     *
     * @throws IllegalStateException if caching is already underway or the user session is inactive.
     */
    @Synchronized
    fun primeEntityCache() {
        checkPreConditions()
        try {
            primeEntityCacheForApp(CommCareApplication.instance().commCarePlatform)
        } finally {
            clearState()
            _completionStatus.value = true
        }
    }

    /**
     * Initiates a cache priming operation for a specific detail and its associated entities.
     *
     * This function verifies that the user session is active and that no other priming operation is in progress
     * before starting the cache prime. It processes the provided entity list for the given detail, ensuring that
     * the internal state is cleared and the operation marked as complete, regardless of success or failure.
     *
     * @param commandId the identifier of the command triggering this cache operation.
     * @param detail the detail instance representing the screen for which caching is performed.
     * @param entityDatum the datum associated with the entity, used to parameterize the caching process.
     * @param entities a mutable list of entities targeted for caching.
     *
     * @throws IllegalStateException if a cache prime is already in progress or if the user session is inactive.
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
            _completionStatus.value = true
        }
    }

    /**
     * Checks if the caching process is currently handling the specified datum and detail.
     *
     * @param datumId The identifier of the datum.
     * @param detailId The identifier of the detail.
     * @return True if both the current datum and detail match the provided identifiers; false otherwise.
     */
    fun isSelectDatumInProgress(datumId: String, detailId: String): Boolean {
        return currentDetailInProgress?.contentEquals(detailId) == true &&
            currentDatumInProgress?.contentEquals(datumId) == true
    }

    /**
     * Primes the entity cache for the entire application.
     *
     * Iterates through each command in the provided platform's command map and processes associated entity datums.
     * For each datum with an associated detail, the function retrieves the detail and attempts to prime its cache.
     * Processing stops immediately if the operation is cancelled, and any XPath exceptions encountered are logged 
     * to avoid interrupting the overall cache priming process.
     *
     * @param commCarePlatform the AndroidCommCarePlatform instance providing command entries and detail retrieval.
     */
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

    /**
     * Primes the cache for a given detail and its associated entity datum.
     *
     * If caching is enabled for the detail and the operation has not been cancelled, this function
     * initializes an entity loader helper using the specified command context and configuration, then caches
     * the entities. It uses the provided list of entities if available; otherwise, it retrieves entities
     * from the datum's nodeset. Internal state trackers for the current datum and detail are updated during
     * the process and reset upon completion.
     *
     * @param commandId the identifier used to generate the evaluation context.
     * @param detail the detail object for which caching is performed; caching is skipped if disabled.
     * @param entityDatum the associated entity datum containing the data identifiers and nodeset.
     * @param entities (optional) a list of entities to be cached; if null, entities are loaded from the nodeset.
     * @param inBackground indicates whether the caching operation should run in the background.
     */
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
        _cachedEntitiesState.value = Triple(entityDatum.dataId, detail.id, cachedEntities)
        currentDatumInProgress = null
        currentDetailInProgress = null
    }

    /**
     * Retrieves a restricted evaluation context for the specified command.
     *
     * This function accesses the current session's wrapper to obtain an evaluation context that is limited based on
     * the provided command identifier.
     *
     * @param commandId The identifier used to scope the evaluation context.
     * @return A restricted evaluation context associated with the current session.
     */
    private fun evalCtx(commandId: String): EvaluationContext {
        return CommCareApplication.instance().currentSessionWrapper.getRestrictedEvaluationContext(commandId, null)
    }

    /**
     * Resets the internal state of the cache priming operation.
     *
     * This method clears all volatile data by resetting helper references, progress trackers,
     * and state flags to their initial values, ensuring the instance is ready for a new operation.
     */
    fun clearState() {
        entityLoaderHelper = null
        inProgress = false
        listener = null
        currentDatumInProgress = null
        currentDetailInProgress = null
        cancelled = false
    }

    /**
     * Checks that all required conditions for priming the entity cache are met.
     *
     * Preconditions:
     *  - The user session must be active.
     *  - No cache priming process should already be in progress.
     *  - The operation must not have been cancelled.
     *
     * @throws IllegalArgumentException if any of the preconditions is not satisfied.
     */
    private fun checkPreConditions() {
        require(CommCareApplication.instance().session.isActive) { "User session must be active to prime entity cache" }
        require(!inProgress) { "We are already priming the cache" }
        require(!cancelled) { "Trying to interact with a cancelled worker"}
    }

    /**
     * Cancels the ongoing cache priming operation.
     *
     * Marks the caching process as cancelled and triggers cancellation of the associated entity loader helper, if it exists.
     */
    override fun cancel() {
        cancelled = true
        entityLoaderHelper?.cancel()
    }

    /**
     * Checks if a cache priming operation is currently in progress.
     *
     * @return `true` if a cache priming process is active, `false` otherwise.
     */
    fun isInProgress(): Boolean {
        return inProgress
    }

    /**
     * Publishes the current entity loading progress.
     *
     * Updates the progress state with a tuple containing the active entity datum, detail, and an array of
     * progress metrics (phase value, current progress, and total count). This enables observers to track
     * the ongoing progress of the entity caching operation.
     *
     * @param phase The current phase of the entity loading process.
     * @param progress The current progress count.
     * @param total The total count representing completion.
     */
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
