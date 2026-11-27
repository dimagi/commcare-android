package org.commcare.home

/**
 * The outcome of one step of a sync: a form send or a data pull.
 *
 * @property formsSent forms the send delivered; 0 for a pull
 * @property pullFollows a pull runs next, so this step doesn't end the sync
 */
data class SyncStepResult(
    val message: String,
    val success: Boolean,
    val userTriggered: Boolean,
    val formsSent: Int = 0,
    val pullFollows: Boolean = false,
)
