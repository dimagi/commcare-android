package org.commcare.tasks

import io.reactivex.functions.Cancellable
import okhttp3.internal.notifyAll
import org.commcare.CommCareApplication
import org.commcare.suite.model.Detail
import org.commcare.suite.model.EntityDatum
import org.commcare.utils.AndroidCommCarePlatform
import org.javarosa.core.model.condition.EvaluationContext

class PrimeEntityCacheHelper private constructor() : Cancellable {

    private var entityLoaderHelper: EntityLoaderHelper? = null
    private var inProgress = false

    companion object {
        @Volatile
        private var instance: PrimeEntityCacheHelper? = null

        fun getInstance() =
            instance ?: synchronized(this) {
                instance ?: PrimeEntityCacheHelper().also { instance = it }
            }
    }

    fun primeEntityCache() {
        checkPreConditions()
        primeEntityCacheForApp(CommCareApplication.instance().commCarePlatform)
        clearState()
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

    private fun primeCacheForDetail(detail: Detail, sessionDatum: EntityDatum) {
        if (detail.shouldCache()) {
            entityLoaderHelper = EntityLoaderHelper(detail, evalCtx())
            entityLoaderHelper!!.cacheEntities(sessionDatum.nodeset)
        }
    }

    private fun evalCtx(): EvaluationContext {
        return CommCareApplication.instance().currentSessionWrapper.evaluationContext
    }

    private fun clearState() {
        entityLoaderHelper = null
        inProgress = false
    }

    private fun checkPreConditions() {
        require(CommCareApplication.instance().session.isActive) { "User session must be active to prime entity cache" }
        require(!inProgress) { "We are already priming the cache" }
    }

    override fun cancel() {
        entityLoaderHelper?.cancel()
    }
}
