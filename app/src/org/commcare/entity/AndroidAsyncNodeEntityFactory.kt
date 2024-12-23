package org.commcare.entity

import androidx.lifecycle.LifecycleOwner
import org.commcare.cases.entity.AsyncNodeEntityFactory
import org.commcare.cases.entity.Entity
import org.commcare.cases.entity.EntityStorageCache
import org.commcare.suite.model.Detail
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
    ec: EvaluationContext?,
    entityStorageCache: EntityStorageCache?,
    private val lifecycleOwner: LifecycleOwner? = null
) : AsyncNodeEntityFactory(d, ec, entityStorageCache) {

    companion object {
        const val TWO_MINUTES = 2 * 60 * 1000
    }

    private var completedCachePrime = false

    override fun prepareEntitiesInternal(
        entities: MutableList<Entity<TreeReference>>
    ) {
        if (detail.shouldCache()) {
            // we only want to block if lazy load is not enabled
            if (!detail.shouldLazyLoad()) {
                val primeEntityCacheHelper = PrimeEntityCacheHelper.getInstance()
                if (primeEntityCacheHelper.isInProgress()) {
                    // if we are priming something else at the moment, expedite the current detail
                    if (!primeEntityCacheHelper.isDetailInProgress(detail.id)) {
                        primeEntityCacheHelper.expediteDetailWithId(detail, entities, progressListener)
                    } else {
                        primeEntityCacheHelper.setListener(progressListener)
                        primeEntityCacheHelper.cachedEntitiesLiveData.observe(lifecycleOwner!!) { cachedEntities ->
                            if (cachedEntities != null) {
                                entities.clear()
                                entities.addAll(cachedEntities)
                                primeEntityCacheHelper.cachedEntitiesLiveData.removeObservers(lifecycleOwner)
                            }
                        }

                        // otherwise wait for existing prime process to complete
                        waitForCachePrimeWork(entities, primeEntityCacheHelper)
                    }
                } else {
                    // we either have not started priming or already completed. In both cases
                    // we want to re-prime to make sure we calculate any uncalculated data first
                    primeEntityCacheHelper.primeEntityCacheForDetail(detail, entities, progressListener)
                }
            }
        } else {
            super.prepareEntitiesInternal(entities)
        }
    }

    private fun waitForCachePrimeWork(
        entities: MutableList<Entity<TreeReference>>,
        primeEntityCacheHelper: PrimeEntityCacheHelper,
    ) {
        val startTime = System.currentTimeMillis()
        while (!completedCachePrime && (System.currentTimeMillis() - startTime) < TWO_MINUTES) {
            // wait for it to be completed
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
            }
        }
        if (!completedCachePrime) {
            Logger.log(LogTypes.TYPE_MAINTENANCE, "Still Waiting for cache priming work to complete")
            // confirm if we are still priming in the worker. If yes, wait more
            // otherwise recall prepareEntitiesInternal to re-evaluate the best thing to do
            if (primeEntityCacheHelper.isInProgress() && primeEntityCacheHelper.isDetailInProgress(detail.id)) {
                waitForCachePrimeWork(entities, primeEntityCacheHelper)
            } else {
                prepareEntitiesInternal(entities)
            }
        }
    }
}
