@file:JvmName("CommCareNavController")
package org.commcare.utils

import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavGraph


fun NavController.navigateSafely(direction: NavDirections?) {
    val currentDestination = this.currentDestination
    if (direction!=null && currentDestination != null) {
        val navAction = currentDestination.getAction(direction.actionId)
        if (navAction != null) {
            val destinationId: Int = navAction.destinationId.orEmpty()
            val currentNode: NavGraph? = if (currentDestination is NavGraph) currentDestination else currentDestination.parent
            if (destinationId != 0 && currentNode != null && currentNode.findNode(destinationId) != null) {
                this.navigate(direction)
            }
        }
    }
}

fun Int?.orEmpty(default: Int = 0): Int {
    return this ?: default
}