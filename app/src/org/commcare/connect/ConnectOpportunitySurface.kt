package org.commcare.connect

/** A surface Opportunity Home shows, which redraws itself when the page publishes a refreshed job. */
interface ConnectOpportunitySurface {
    fun onActiveJobRefreshed()
}
