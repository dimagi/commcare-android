package org.commcare.connect.repository

// Note: abstract rather than sealed to allow MockK argument matchers in unit tests
// (MockK 1.12.x cannot instantiate sealed classes via Objenesis for signature recording).
// The else -> true branch in ConnectSyncPreferences.shouldRefresh() provides a safe fallback.
abstract class RefreshPolicy {
    object ALWAYS : RefreshPolicy()

    // Fetch if new app session since last sync OR cache older than timeThresholdMs
    @Suppress("ClassName")
    data class SESSION_AND_TIME_BASED(
        val timeThresholdMs: Long = 60_000,
    ) : RefreshPolicy()
}
