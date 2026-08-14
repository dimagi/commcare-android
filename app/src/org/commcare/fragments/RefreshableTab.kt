package org.commcare.fragments

/**
 * A tab fragment hosted in a ViewPager container should implement this class, allowing its view
 * to be re-rendered on demand by the host.
 * The host invokes updateView on each active tab when data changes (e.g., after a sync).
 */
interface RefreshableTab {
    fun updateView()
}
