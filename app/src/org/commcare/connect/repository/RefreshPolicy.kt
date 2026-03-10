package org.commcare.connect.repository

abstract class RefreshPolicy {
    object ALWAYS : RefreshPolicy()

    // Fetch if new app session since last sync OR cache older than timeThresholdMs
    @Suppress("ClassName")
    data class SESSION_AND_TIME_BASED(
        val timeThresholdMs: Long = 60_000,
    ) : RefreshPolicy()
}
