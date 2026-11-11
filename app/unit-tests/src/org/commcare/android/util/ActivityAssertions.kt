package org.commcare.android.util

import android.app.Activity
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.robolectric.Shadows

/**
 * Assertions on the activities a Robolectric activity launched.
 *
 * Reading either shadow queue drains it, so each assertion consumes one launch. A
 * `startActivityForResult` launch lands in *both* queues — the for-result one carries the request
 * code — so a test that asserts on one and then expects the other to be empty has to drain both.
 */
object ActivityAssertions {
    /** Asserts the next `startActivity` launch targeted [target], and returns its intent. */
    fun assertStarted(
        from: Activity,
        target: Class<*>,
    ): Intent = assertTargets(Shadows.shadowOf(from).nextStartedActivity, target)

    /** Asserts the next `startActivityForResult` launch targeted [target], and returns its intent. */
    fun assertStartedForResult(
        from: Activity,
        target: Class<*>,
    ): Intent = assertTargets(Shadows.shadowOf(from).nextStartedActivityForResult?.intent, target)

    /** Asserts [from] launched nothing with `startActivity`. */
    fun assertStartedNothing(from: Activity) {
        val started = Shadows.shadowOf(from).nextStartedActivity
        assertNull("expected no activity to be started, got ${className(started)}", started)
    }

    /** Every `startActivityForResult` intent [from] raised, oldest first. Drains the queue. */
    fun startedForResultIntents(from: Activity): List<Intent> {
        val shadow = Shadows.shadowOf(from)
        return buildList {
            while (true) {
                add((shadow.nextStartedActivityForResult ?: break).intent)
            }
        }
    }

    /** Every `startActivity` intent [from] raised, oldest first. Drains the queue. */
    fun startedIntents(from: Activity): List<Intent> {
        val shadow = Shadows.shadowOf(from)
        return buildList {
            while (true) {
                add(shadow.nextStartedActivity ?: break)
            }
        }
    }

    /** Asserts exactly one activity is in [started], and returns its intent. */
    fun assertOnly(started: List<Intent>): Intent {
        assertEquals(
            "expected exactly one started activity, got ${started.map { className(it) }}",
            1,
            started.size,
        )
        return started.single()
    }

    private fun assertTargets(
        intent: Intent?,
        target: Class<*>,
    ): Intent {
        assertEquals(target.name, className(intent))
        return intent!!
    }

    private fun className(intent: Intent?): String? = intent?.component?.className
}
