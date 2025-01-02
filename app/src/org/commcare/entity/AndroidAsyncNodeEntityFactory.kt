package org.commcare.entity

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.commcare.CommCareApplication
import org.commcare.cases.entity.AsyncNodeEntityFactory
import org.commcare.cases.entity.Entity
import org.commcare.cases.entity.EntityStorageCache
import org.commcare.suite.model.Detail
import org.commcare.suite.model.EntityDatum
import org.commcare.tasks.PrimeEntityCacheHelper
import org.commcare.util.LogTypes
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.services.Logger

/**
 * Android Specific Implementation of AsyncNodeEntityFactory
 * Uses [PrimeEntityCacheHelper] to prime entity cache blocking the user when required
 */
class AndroidAsyncNodeEntityFactory(
    d: Detail,
    private val entityDatum: EntityDatum?,
    ec: EvaluationContext?,
    entityStorageCache: EntityStorageCache?
) : AsyncNodeEntityFactory(d, ec, entityStorageCache) {

    companion object {
        const val TEN_MINUTES = 10 * 60 * 1000L
    }

    override fun prepareEntitiesInternal(
        entities: MutableList<Entity<TreeReference>>
    ) {
        if (detail.shouldCache()) {
            // we only want to block if lazy load is not enabled
            if (!detail.shouldLazyLoad()) {
                val primeEntityCacheHelper = PrimeEntityCacheHelper.getInstance()
                if (primeEntityCacheHelper.isInProgress()) {
                    // if we are priming something else at the moment, expedite the current detail
                    if (!primeEntityCacheHelper.isDatumInProgress(detail.id)) {
                        primeEntityCacheHelper.expediteDetailWithId(
                            getCurrentCommandId(),
                            detail,
                            entityDatum!!,
                            entities,
                            progressListener
                        )
                    } else {
                        primeEntityCacheHelper.setListener(progressListener)
                        observePrimeCacheWork(primeEntityCacheHelper, entities)
                    }
                } else {
                    // we either have not started priming or already completed. In both cases
                    // we want to re-prime to make sure we calculate any uncalculated data first
                    primeEntityCacheHelper.primeEntityCacheForDetail(
                        getCurrentCommandId(),
                        detail,
                        entityDatum!!,
                        entities,
                        progressListener
                    )
                }
            }
        } else {
            super.prepareEntitiesInternal(entities)
        }
    }

    private fun observePrimeCacheWork(
        primeEntityCacheHelper: PrimeEntityCacheHelper,
        entities: MutableList<Entity<TreeReference>>
    ) {
        runBlocking {
            try {
                withTimeout(TEN_MINUTES) {
                    primeEntityCacheHelper.cachedEntitiesState.collect { cachedEntities ->
                        if (cachedEntities != null) {
                            entities.clear()
                            entities.addAll(cachedEntities)
                            return@collect
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Logger.log(
                    LogTypes.TYPE_MAINTENANCE,
                    "Timeout while waiting for the prime cache worker to finish"
                )
                if (primeEntityCacheHelper.isInProgress() &&
                    primeEntityCacheHelper.isDatumInProgress(detail.id)
                ) {
                    // keep observing
                    observePrimeCacheWork(primeEntityCacheHelper, entities)
                } else {
                    prepareEntitiesInternal(entities)
                }
            }
        }
    }

    private fun getCurrentCommandId(): String {
        return CommCareApplication.instance().currentSession.command
    }
}
