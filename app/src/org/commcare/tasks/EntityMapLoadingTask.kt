package org.commcare.tasks

import android.util.Pair
import org.commcare.CommCareApplication
import org.commcare.cases.entity.Entity
import org.commcare.gis.EntityMapActivity
import org.commcare.gis.EntityMapDisplayInfo
import org.commcare.gis.EntityMapUtils
import org.commcare.suite.model.Detail
import org.commcare.tasks.templates.CommCareTask
import org.javarosa.core.model.instance.TreeReference

class EntityMapLoadingTask : CommCareTask<Void, Void, EntityMapLoadingTask.EntityMapData, EntityMapActivity>() {

    companion object {
        const val ENTITY_MAP_LOADING_TASK_ID = 12336000
    }

    init {
        taskId = ENTITY_MAP_LOADING_TASK_ID
        TAG = EntityMapLoadingTask::class.java.simpleName
    }

    data class EntityMapData(
        val entityLocations: List<Pair<Entity<TreeReference>, EntityMapDisplayInfo>>,
        val detail: Detail?,
        val errorEncountered: Boolean,
    )

    override fun doTaskBackground(vararg params: Void?): EntityMapData {
        val selectDatum = EntityMapUtils.getNeededEntityDatum()
            ?: return EntityMapData(emptyList(), null, false)

        val detail = CommCareApplication.instance().currentSession
            .getDetail(selectDatum.shortDetail)

        val entityLocations = mutableListOf<Pair<Entity<TreeReference>, EntityMapDisplayInfo>>()
        var errorEncountered = false

        for (entity in EntityMapUtils.getEntities(detail, selectDatum.nodeset)) {
            val displayInfo = EntityMapUtils.getDisplayInfoForEntity(entity, detail)
            if (displayInfo != null) {
                entityLocations.add(Pair(entity, displayInfo))
                errorEncountered = errorEncountered || displayInfo.errorEncountered
            }
        }

        return EntityMapData(entityLocations, detail, errorEncountered)
    }

    override fun deliverResult(receiver: EntityMapActivity, result: EntityMapData) {
        receiver.onEntityDataLoaded(result)
    }

    override fun deliverUpdate(receiver: EntityMapActivity, vararg update: Void?) {
        // no progress updates
    }

    override fun deliverError(receiver: EntityMapActivity, e: Exception) {
        receiver.onEntityLoadError(e)
    }
}
