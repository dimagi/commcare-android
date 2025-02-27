package org.commcare.tasks

import android.util.Pair
import io.reactivex.functions.Cancellable
import org.commcare.activities.EntitySelectActivity
import org.commcare.cases.entity.AsyncNodeEntityFactory
import org.commcare.cases.entity.Entity
import org.commcare.cases.entity.EntityLoadingProgressListener
import org.commcare.cases.entity.EntityStorageCache
import org.commcare.cases.entity.NodeEntityFactory
import org.commcare.entity.AndroidAsyncNodeEntityFactory
import org.commcare.models.database.user.models.CommCareEntityStorageCache
import org.commcare.preferences.DeveloperPreferences
import org.commcare.suite.model.Detail
import org.commcare.suite.model.EntityDatum
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference

/**
 * Helper class to load entities for an entity screen
 */
class EntityLoaderHelper(
    detail: Detail,
    sessionDatum: EntityDatum?,
    evalCtx: EvaluationContext,
    inBackground: Boolean
) : Cancellable {

    var focusTargetIndex: Int = -1
    private var stopLoading: Boolean = false
    var factory: NodeEntityFactory

    init {
        evalCtx.addFunctionHandler(EntitySelectActivity.getHereFunctionHandler())
        if (detail.shouldOptimize()) {
            val entityStorageCache: EntityStorageCache = CommCareEntityStorageCache("case")
            factory = AndroidAsyncNodeEntityFactory(detail, sessionDatum, evalCtx, entityStorageCache, inBackground)
        } else if (detail.useAsyncStrategy()) {
            // legacy cache and index
            val entityStorageCache: EntityStorageCache = CommCareEntityStorageCache("case")
            factory = AsyncNodeEntityFactory(detail, evalCtx, entityStorageCache, inBackground)
        } else {
            factory = NodeEntityFactory(detail, evalCtx)
            if (DeveloperPreferences.collectAndDisplayEntityTraces()) {
                factory.activateDebugTraceOutput()
            }
        }
    }

    /**
     * Loads and prepares entities from the specified nodeset while reporting progress.
     *
     * The method first expands the nodeset into a list of references and then attempts to load entities for each reference,
     * updating progress via the provided listener. If entities are successfully loaded, they are prepared and any trace output is cleared.
     * The function returns a pair containing the list of loaded entities and their corresponding references.
     * If the loading process is interrupted or fails, the method returns null.
     *
     * @param nodeset the base reference used to derive entity references.
     * @param progressListener listener to receive updates on the entity loading progress.
     * @return a pair where the first component is the list of loaded and prepared entities and the second is the list of corresponding references,
     *         or null if the loading process is interrupted or fails.
     */
    fun loadEntities(
        nodeset: TreeReference,
        progressListener: EntityLoadingProgressListener
    ): Pair<List<Entity<TreeReference>>, List<TreeReference>>? {
        val references = factory.expandReferenceList(nodeset)
        val entities = loadEntitiesWithReferences(references, progressListener)
        entities?.let {
            factory.prepareEntities(entities)
            factory.printAndClearTraces("build")
            return Pair<List<Entity<TreeReference>>, List<TreeReference>>(entities, references)
        }
        return null
    }

    /**
     * Loads and caches entities derived from a tree reference.
     *
     * Expands the provided tree reference into a list of entity references, loads the corresponding entities,
     * caches them, and returns a pair containing the loaded entities and the expanded references.
     *
     * @param nodeset the root tree reference to be expanded into entity references.
     * @return a pair where the first element is the list of loaded entities and the second element is the list of expanded references.
     */
    fun cacheEntities(nodeset: TreeReference): Pair<List<Entity<TreeReference>>, List<TreeReference>> {
        val references = factory.expandReferenceList(nodeset)
        val entities = loadEntitiesWithReferences(references, null)
        cacheEntities(entities)
        return Pair<List<Entity<TreeReference>>, List<TreeReference>>(entities, references)
    }

    /**
     * Caches the provided list of entities using the underlying entity factory.
     *
     * Delegates caching to the factory implementation.
     *
     * @param entities A mutable list of entities to cache; may be null.
     */
    fun cacheEntities(entities: MutableList<Entity<TreeReference>>?) {
        factory.cacheEntities(entities)
    }

    /**
     * Loads entities corresponding to the provided references while reporting loading progress.
     *
     * This function iterates over the list of references, publishing progress updates via the optional
     * progress listener and retrieving the corresponding entities from the factory. It aborts processing
     * and returns null if a cancellation flag is encountered. Additionally, it updates the focus target index
     * based on whether a loaded entity should receive focus.
     *
     * @param references the list of entity references to process.
     * @param progressListener an optional listener for reporting the loading progress.
     * @return a mutable list of loaded entities, or null if the loading process was cancelled.
     */
    private fun loadEntitiesWithReferences(
        references: List<TreeReference>,
        progressListener: EntityLoadingProgressListener?
    ): MutableList<Entity<TreeReference>>? {
        val entities: MutableList<Entity<TreeReference>> = ArrayList()
        focusTargetIndex = -1
        var indexInFullList = 0
        for ((index, ref) in references.withIndex()) {
            progressListener?.publishEntityLoadingProgress(
                EntityLoadingProgressListener.EntityLoadingProgressPhase.PHASE_PROCESSING,
                index,
                references.size
            )
            if (stopLoading) {
                return null
            }
            val e = factory.getEntity(ref)
            if (e != null) {
                entities.add(e)
                if (e.shouldReceiveFocus()) {
                    focusTargetIndex = indexInFullList
                }
                indexInFullList++
            }
        }
        return entities
    }

    /**
     * Cancels the ongoing entity loading process.
     *
     * Sets an internal flag to stop the loading and delegates the cancellation to the entity factory,
     * ensuring that any active loading operations are halted.
     */
    override fun cancel() {
        stopLoading = true
        factory.cancelLoading()
    }
}
