package org.commcare.activities

import android.app.Activity
import android.content.Intent
import org.commcare.android.util.ActivityAssertions
import org.commcare.connect.PersonalIdManager
import org.commcare.personalId.PersonalIdUserPreferences
import org.commcare.personalId.profile.PersonalIdProfileActivity
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowActivity
import org.robolectric.shadows.ShadowLooper

/**
 * Tests that [DispatchActivity.dispatch] correctly gates on the pending-backup-code + logged-in
 * combination before any other checks (DB state, session, etc.).
 *
 * Extends [BaseHomeScreenActivityTest] to get the installed CommCare app + logged-in user that
 * dispatch() requires to reach the non-login, non-install branches. However these tests build
 * [DispatchActivity] directly since that is where dispatch() lives.
 */
class DispatchPendingBackupCodeTest : BaseHomeScreenActivityTest() {
    @After
    fun tearDownPendingBackupCode() {
        PersonalIdUserPreferences.setPendingBackupCode(false)
        PersonalIdManager.getInstance().setStatus(PersonalIdManager.PersonalIdStatus.NotIntroduced)
    }

    private fun setUpLoggedIn() {
        PersonalIdUserPreferences.setPendingBackupCode(true)
        PersonalIdManager.getInstance().setStatus(PersonalIdManager.PersonalIdStatus.LoggedIn)
    }

    private fun buildAndResumeDispatch(): DispatchActivity =
        Robolectric
            .buildActivity(DispatchActivity::class.java)
            .create()
            .resume()
            .get()
            .also { ShadowLooper.idleMainLooper() }

    private fun pendingBackupCodeIntent(startedForResult: List<Intent>): Intent? =
        startedForResult.firstOrNull { intent ->
            intent.component?.className == PersonalIdProfileActivity::class.java.name
        }

    @Test
    fun `dispatch launches PersonalIdProfileActivity when pending backup code and PersonalIdManager logged in`() {
        setUpLoggedIn()

        val activity = buildAndResumeDispatch()

        val startedForResult = ActivityAssertions.startedForResultIntents(activity)
        val pendingBackupCodeIntent = pendingBackupCodeIntent(startedForResult)
        assertTrue(
            "Expected PersonalIdProfileActivity to be started for result, got: ${startedForResult.map { it.component?.className }}",
            pendingBackupCodeIntent != null,
        )
        assertTrue(
            "Expected EXTRA_PENDING_BACKUP_CODE=true in intent",
            pendingBackupCodeIntent!!.getBooleanExtra(PersonalIdProfileActivity.EXTRA_PENDING_BACKUP_CODE, false),
        )
    }

    @Test
    fun `dispatch does not launch PersonalIdProfileActivity when PersonalIdManager is not logged in`() {
        PersonalIdUserPreferences.setPendingBackupCode(true)
        val activity = buildAndResumeDispatch()

        val pendingBackupCodeIntent = pendingBackupCodeIntent(ActivityAssertions.startedForResultIntents(activity))
        assertTrue(
            "Expected NO PersonalIdProfileActivity to be started for result",
            pendingBackupCodeIntent == null,
        )
    }

    private data class DispatchForResultSetup(
        val activityController: ActivityController<DispatchActivity>,
        val activity: DispatchActivity,
        val shadow: ShadowActivity,
        val startedIntent: Intent,
    )

    private fun setUpDispatchForResult(): DispatchForResultSetup {
        setUpLoggedIn()
        val activityController = Robolectric.buildActivity(DispatchActivity::class.java).create()
        val activity = activityController.resume().get()
        ShadowLooper.idleMainLooper()
        val shadow = Shadows.shadowOf(activity)
        return DispatchForResultSetup(activityController, activity, shadow, shadow.nextStartedActivity)
    }

    @Test
    fun `DispatchActivity does not finish when user exits pending backup code screen without setting code`() {
        val (activityController, activity, shadow, startedIntent) = setUpDispatchForResult()

        // isPendingBackupCode() still true — user abandoned without setting the code
        shadow.receiveResult(startedIntent, Activity.RESULT_OK, null)
        activityController.resume()
        ShadowLooper.idleMainLooper()

        assertFalse(
            "DispatchActivity should not finish when user exits without setting backup code",
            activity.isFinishing,
        )

        val nextActivity = shadow.nextStartedActivity
        assertTrue(
            "dispatch should not re-trigger the pending backup code screen",
            nextActivity == null || nextActivity.component?.className != PersonalIdProfileActivity::class.java.name,
        )
    }

    @Test
    fun `DispatchActivity does not finish when user successfully sets backup code`() {
        val (activityController, activity, shadow, startedIntent) = setUpDispatchForResult()

        // Simulate success — backup code was set before PersonalIdProfileActivity finished
        PersonalIdUserPreferences.setPendingBackupCode(false)
        shadow.receiveResult(startedIntent, Activity.RESULT_OK, null)
        activityController.resume()
        ShadowLooper.idleMainLooper()

        assertFalse(
            "DispatchActivity should not finish when backup code was successfully set",
            activity.isFinishing,
        )

        val nextActivity = shadow.nextStartedActivity
        assertNotNull(
            "Expected a new activity to be started",
            nextActivity,
        )
        assertTrue(
            "dispatch should re-run and start a non-PersonalIdProfileActivity",
            nextActivity != null && nextActivity.component?.className != PersonalIdProfileActivity::class.java.name,
        )
    }
}
