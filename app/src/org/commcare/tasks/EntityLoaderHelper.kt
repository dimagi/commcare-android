package org.commcare.tasks

import android.util.Pair
import io.reactivex.functions.Cancellable
import org.commcare.activities.EntitySelectActivity
import org.commcare.cases.entity.AsyncNodeEntityFactory
import org.commcare.cases.entity.Entity
import org.commcare.cases.entity.EntityStorageCache
import org.commcare.cases.entity.NodeEntityFactory
import org.commcare.models.database.user.models.CommCareEntityStorageCache
import org.commcare.preferences.DeveloperPreferences
import org.commcare.suite.model.Detail
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference

class EntityLoaderHelper(
    detail: Detail,
    evalCtx: EvaluationContext
) : Cancellable {

    var focusTargetIndex: Int = -1
    private var stopLoading: Boolean = false
    var factory: NodeEntityFactory

    init {
        evalCtx.addFunctionHandler(EntitySelectActivity.getHereFunctionHandler())
        if (detail.useAsyncStrategy()) {
            val entityStorageCache: EntityStorageCache = CommCareEntityStorageCache("case")
            factory = AsyncNodeEntityFactory(detail, evalCtx, entityStorageCache)
        } else {
            factory = NodeEntityFactory(detail, evalCtx)
            if (DeveloperPreferences.collectAndDisplayEntityTraces()) {
                factory.activateDebugTraceOutput()
            }
        }
    }

    fun loadEntities(nodeset: TreeReference): Pair<List<Entity<TreeReference>>, List<TreeReference>>? {
        val references = factory.expandReferenceList(nodeset)
        val full: MutableList<Entity<TreeReference>> = ArrayList()
        focusTargetIndex = -1
        var indexInFullList = 0
        for (ref in references) {
            if (stopLoading) {
                return null
            }
            val e = factory.getEntity(ref)
            if (e != null) {
                full.add(e)
                if (e.shouldReceiveFocus()) {
                    focusTargetIndex = indexInFullList
                }
                indexInFullList++
            }
        }

        factory.prepareEntities(full)
        factory.printAndClearTraces("build")
        return Pair<List<Entity<TreeReference>>, List<TreeReference>>(full, references)
    }

    override fun cancel() {
        stopLoading = true
    }
}
