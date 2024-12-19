package org.commcare.entity

import android.util.Pair
import org.commcare.cases.entity.Entity
import org.javarosa.core.model.instance.TreeReference

interface PrimeEntityCacheListener {

    fun onPrimeEntityCacheComplete(
        currentDetailInProgress: String,
        cachedEntitiesWithRefs: Pair<List<Entity<TreeReference>>, List<TreeReference>>
    )
}
