package org.commcare.entity

import android.util.Pair
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
class AndroidAsyncNodeEntityFactory(d: Detail, ec: EvaluationContext?, entityStorageCache: EntityStorageCache?) :
    AsyncNodeEntityFactory(d, ec, entityStorageCache), PrimeEntityCacheListener {

    companion object {
        const val TWO_MINUTES = 2 * 60 * 1000
    }

    private var cachedEntities: List<Entity<TreeReference>>? = null
    private var completedCachePrime = false

    override fun prepareEntitiesInternal(entities: MutableList<Entity<TreeReference>>) {
        if (detail.shouldCache()) {
            // we only want to block if lazy load is not enabled
            if (!detail.shouldLazyLoad()) {
                val primeEntityCacheHelper = PrimeEntityCacheHelper.getInstance()
                if (primeEntityCacheHelper.isInProgress()) {
                    // if we are priming something else at the moment, expedite the current detail
                    if (!primeEntityCacheHelper.isDetailInProgress(detail.id)) {
                        primeEntityCacheHelper.expediteDetailWithId(detail, entities)
                    } else {
                        // otherwise wait for existing prime process to complete
                        primeEntityCacheHelper.setListener(this)
                        waitForCachePrimeWork(entities, primeEntityCacheHelper)
                        if (cachedEntities != null) {
                            entities.clear()
                            entities.addAll(cachedEntities!!)
                        }
                    }
                } else {
                    // we either have not started priming or already completed. In both cases
                    // we want to re-prime to make sure we calculate any uncalculated data first
                    primeEntityCacheHelper.primeEntityCacheForDetail(detail, entities)
                }
            }
        } else {
            super.prepareEntitiesInternal(entities)
        }
    }

    private fun waitForCachePrimeWork(
        entities: MutableList<Entity<TreeReference>>,
        primeEntityCacheHelper: PrimeEntityCacheHelper
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

    override fun onPrimeEntityCacheComplete(
        currentDetailInProgress: String,
        cachedEntitiesWithRefs: Pair<List<Entity<TreeReference>>, List<TreeReference>>
    ) {
        if (detail.id!!.contentEquals(currentDetailInProgress)) {
            cachedEntities = cachedEntitiesWithRefs.first
            completedCachePrime = true
        }
    }
}
