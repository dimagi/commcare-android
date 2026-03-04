package org.commcare.services

import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.commcare.CommCareTestApplication
import org.commcare.services.NetworkNotificationService.Companion.START_NOTIFICATION_ACTION
import org.commcare.services.NetworkNotificationService.Companion.STOP_NOTIFICATION_ACTION
import org.commcare.services.NetworkNotificationService.Companion.TASK_TAG_INTENT_EXTRA
import org.commcare.services.NetworkNotificationService.Companion.UPDATE_PROGRESS_NOTIFICATION_ACTION
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.S_V2])
class NetworkNotificationServiceTest {
    private lateinit var controller: ServiceController<NetworkNotificationService>
    private lateinit var service: NetworkNotificationService
    private val availableTasks = arrayOf("Task A", "Task B", "Task C")

    @Before
    fun setUp() {
        controller = Robolectric.buildService(
            NetworkNotificationService::class.java,
            getIntentForAction(START_NOTIFICATION_ACTION, availableTasks[0])
        )
        service = controller.create().startCommand(0,0).get()
    }

    @Test
    fun isServiceRunning_shouldBeTrueAfterOnCreate() {
        assertTrue(NetworkNotificationService.isServiceRunning)
    }

    @Test
    fun whenSingleComplete_shouldStopService() {
        val shadow = Shadows.shadowOf(service)
        sendIntent(STOP_NOTIFICATION_ACTION, availableTasks[0])
        assertTrue(shadow.isStoppedBySelf)
    }

    @Test
    fun whenAllTasksComplete_shouldStopService() {
        val shadow = Shadows.shadowOf(service)
        sendIntent(START_NOTIFICATION_ACTION, availableTasks[1])
        sendIntent(START_NOTIFICATION_ACTION, availableTasks[2])
        sendIntent(START_NOTIFICATION_ACTION, availableTasks[0])
        sendIntent(START_NOTIFICATION_ACTION, availableTasks[0])

        repeat(10) {
            sendIntent(UPDATE_PROGRESS_NOTIFICATION_ACTION, availableTasks[(0..<availableTasks.size).random()])
        }

        sendIntent(STOP_NOTIFICATION_ACTION, availableTasks[0])
        sendIntent(STOP_NOTIFICATION_ACTION, availableTasks[1])
        sendIntent(STOP_NOTIFICATION_ACTION, availableTasks[0])
        assertFalse(shadow.isStoppedBySelf)

        sendIntent(STOP_NOTIFICATION_ACTION, availableTasks[0])
        sendIntent(STOP_NOTIFICATION_ACTION, availableTasks[2])
        assertTrue(shadow.isStoppedBySelf)
    }

    @Test
    fun whenTaskIsRegisteredOnUpdate_shouldStopService() {
        val shadow = Shadows.shadowOf(service)
        sendIntent(UPDATE_PROGRESS_NOTIFICATION_ACTION, availableTasks[1])
        sendIntent(STOP_NOTIFICATION_ACTION, availableTasks[0])
        assertFalse(shadow.isStoppedBySelf)

        sendIntent(STOP_NOTIFICATION_ACTION, availableTasks[1])
        assertTrue(shadow.isStoppedBySelf)
    }

    @Test
    fun whenStopCalledTwiceForNonDuplicateTask_shouldIgnoreStopService() {
        val shadow = Shadows.shadowOf(service)
        sendIntent(START_NOTIFICATION_ACTION, availableTasks[1])
        sendIntent(STOP_NOTIFICATION_ACTION, availableTasks[0])
        sendIntent(STOP_NOTIFICATION_ACTION, availableTasks[0])
        assertFalse(shadow.isStoppedBySelf)
        sendIntent(STOP_NOTIFICATION_ACTION, availableTasks[1])
        assertTrue(shadow.isStoppedBySelf)
    }

    @After
    fun tearDown() {
        controller.destroy()
    }

    private fun sendIntent(action: String, taskTag: String) {
        service.onStartCommand(getIntentForAction(action, taskTag), 0, 0)
    }

    private fun getIntentForAction(action: String, taskTag: String): Intent {
        return Intent(
            CommCareTestApplication.instance(),
            NetworkNotificationService::class.java,
        ).apply {
            this.action = action
            putExtra(TASK_TAG_INTENT_EXTRA, taskTag)
        }
    }

}