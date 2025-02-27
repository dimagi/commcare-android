package org.commcare.entity

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.commcare.CommCareApplication
import org.commcare.cases.entity.AsyncNodeEntityFactory
import org.commcare.cases.entity.Entity
import org.commcare.cases.entity.EntityStorageCache
import org.commcare.suite.model.Detail
import org.commcare.suite.model.EntityDatum
import org.commcare.tasks.PrimeEntityCacheHelper
import org.commcare.tasks.PrimeEntityCacheHelper.Companion.schedulePrimeEntityCacheWorker
import org.commcare.util.LogTypes
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.services.Logger
import java.lang.RuntimeException

/**
 * Android Specific Implementation of AsyncNodeEntityFactory
 * Uses [PrimeEntityCacheHelper] to prime entity cache blocking the user when required
 */
class AndroidAsyncNodeEntityFactory(
    d: Detail,
    private val entityDatum: EntityDatum?,
    ec: EvaluationContext?,
    entityStorageCache: EntityStorageCache?,
    inBackground: Boolean
) : AsyncNodeEntityFactory(d, ec, entityStorageCache, inBackground) {

    companion object {
        const val TEN_MINUTES = 10 * 60 * 1000L
        const val THIRTY_SECONDS = 30 * 1000L
    }

    init {
        if (!detail.shouldOptimize()) {
            throw RuntimeException(AndroidAsyncNodeEntityFactory::class.simpleName + " can only be used for optimizable case lists");
        }
    }

    override fun prepareEntitiesInternal(
        entities: MutableList<Entity<TreeReference>>
    ) {
        if (detail.isCacheEnabled) {
            if (entityDatum == null) {
                throw RuntimeException("Entity Datum must be defined for an async entity factory");
            }
            val primeEntityCacheHelper = CommCareApplication.instance().currentApp.primeEntityCacheHelper
            if (primeEntityCacheHelper.isInProgress()) {
                // if we are priming something else at the moment, cancel it and expedite the current detail
                if (!primeEntityCacheHelper.isSelectDatumInProgress(entityDatum.dataId, detail.id)) {
                    cancelExistingWorkWithWait(primeEntityCacheHelper)
                    primeEntityCacheHelper.primeEntityCacheForDetail(
                        getCurrentCommandId(),
                        detail,
                        entityDatum,
                        entities
                    )
                    schedulePrimeEntityCacheWorker()
                } else {
                    observePrimeCacheWork(primeEntityCacheHelper, entities)
                }
            } else {
                // we either have not started priming or already completed. In both cases
                // we want to re-prime to make sure we calculate any uncalculated data first
                primeEntityCacheHelper.primeEntityCacheForDetail(
                    getCurrentCommandId(),
                    detail,
                    entityDatum,
                    entities
                )
            }
        }
    }

    private fun cancelExistingWorkWithWait(primeEntityCacheHelper: PrimeEntityCacheHelper) {
        runBlocking {
            try {
                withTimeout(THIRTY_SECONDS) {
                    primeEntityCacheHelper.cancelWork()
                    primeEntityCacheHelper.completionStatus.first { complete -> !complete }
                }
            } catch (_: TimeoutCancellationException) {
                Logger.log(
                    LogTypes.TYPE_MAINTENANCE,
                    "Timeout while listening for cancellation status"
                )
            }
        }
    }


    private fun observePrimeCacheWork(
        primeEntityCacheHelper: PrimeEntityCacheHelper,
        entities: MutableList<Entity<TreeReference>>
    ) {
        if (primeEntityCacheHelper.isInProgress() &&
            primeEntityCacheHelper.isSelectDatumInProgress(entityDatum!!.dataId, detail.id)
        ) {
            runBlocking {
                try {
                    withTimeout(TEN_MINUTES) {
                        val cachedEntities = primeEntityCacheHelper.cachedEntitiesState.first { triple ->
                            triple != null && triple.first == entityDatum.dataId && triple.second == detail.id
                        }!!.third
                        entities.clear()
                        entities.addAll(cachedEntities)
                    }
                } catch (_: TimeoutCancellationException) {
                    Logger.log(
                        LogTypes.TYPE_MAINTENANCE,
                        "Timeout while waiting for the prime cache worker to finish"
                    )
                    prepareEntitiesInternal(entities)
                }
            }
        } else {
            prepareEntitiesInternal(entities)
        }
    }

    private fun getCurrentCommandId(): String {
        return CommCareApplication.instance().currentSession.command
    }
}
