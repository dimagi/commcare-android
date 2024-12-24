package org.commcare.tasks

import android.content.Context
import android.util.Pair
import androidx.lifecycle.LifecycleOwner
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
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference

class EntityLoaderHelper(
    detail: Detail,
    evalCtx: EvaluationContext,
    lifecycleOwner: LifecycleOwner? = null,
) : Cancellable {

    var focusTargetIndex: Int = -1
    private var stopLoading: Boolean = false
    var factory: NodeEntityFactory

    init {
        evalCtx.addFunctionHandler(EntitySelectActivity.getHereFunctionHandler())
        if (detail.useAsyncStrategy() || detail.shouldCache()) {
            val entityStorageCache: EntityStorageCache = CommCareEntityStorageCache("case")
            factory = AndroidAsyncNodeEntityFactory(detail, evalCtx, entityStorageCache, lifecycleOwner)
        } else {
            factory = NodeEntityFactory(detail, evalCtx)
            if (DeveloperPreferences.collectAndDisplayEntityTraces()) {
                factory.activateDebugTraceOutput()
            }
        }
    }

    /**
     * Loads and prepares a list of entities derived from the given nodeset
     */
    fun loadEntities(
        nodeset: TreeReference,
        progressListener: EntityLoadingProgressListener
    ): Pair<List<Entity<TreeReference>>, List<TreeReference>>? {
        val references = factory.expandReferenceList(nodeset)
        factory.setEntityProgressListener(progressListener)
        val entities = loadEntitiesWithReferences(references, progressListener)
        entities?.let {
            factory.prepareEntities(entities)
            factory.printAndClearTraces("build")
            return Pair<List<Entity<TreeReference>>, List<TreeReference>>(entities, references)
        }
        return null
    }

    /**
     *  Primes the entity cache
     */
    fun cacheEntities(nodeset: TreeReference): Pair<List<Entity<TreeReference>>, List<TreeReference>> {
        val references = factory.expandReferenceList(nodeset)
        val entities = loadEntitiesWithReferences(references, null)
        cacheEntities(entities)
        return Pair<List<Entity<TreeReference>>, List<TreeReference>>(entities, references)
    }

    fun cacheEntities(entities: MutableList<Entity<TreeReference>>?) {
        factory.cacheEntities(entities)
    }

    /**
     * Loads a list of entities corresponding to the given references
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

    override fun cancel() {
        stopLoading = true
    }
}
