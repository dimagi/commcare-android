package org.commcare.views.connect

import android.content.Context
import org.commcare.activities.CommCareActivity
import org.commcare.android.database.connect.models.ConnectTaskRecord
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectDateUtils
import org.commcare.connect.ConnectNavHelper
import org.commcare.dalvik.R
import org.commcare.personalId.UnlockPolicy
import java.text.DateFormat

/**
 * Shared rendering and routing for a pending task, so the dashboard and More tabs stay in step.
 *
 * Launching the delivery app is left to the caller: it needs the hosting fragment's job, which this
 * has no access to.
 */
object ConnectTaskPresenter {
    fun state(
        context: Context,
        task: ConnectTaskRecord,
        highlighted: Boolean,
        showExpiry: Boolean = true,
        chipLabel: CharSequence? = null,
        subtitle: CharSequence? = null,
        iconOnCircle: Boolean = true,
        onClick: () -> Unit,
    ) = ConnectTaskCard.State(
        title = task.name,
        iconRes =
            if (task.isOCSConversation) {
                R.drawable.ic_chat_bubble_outline
            } else {
                R.drawable.ic_connect_learn_app
            },
        expiryLabel =
            task.dueDate?.takeIf { showExpiry }?.let {
                context.getString(
                    R.string.connect_task_expires_on,
                    ConnectDateUtils.formatDate(it, DateFormat.LONG),
                )
            },
        chipLabel = chipLabel,
        subtitle = subtitle,
        iconOnCircle = iconOnCircle,
        highlighted = highlighted,
        onClick = onClick,
    )

    /** A conversation task is completed in Connect messaging, everything else in the delivery app. */
    fun open(
        activity: CommCareActivity<*>,
        task: ConnectTaskRecord,
        launchDeliveryApp: () -> Unit,
    ) {
        if (!task.isOCSConversation) {
            launchDeliveryApp()
            return
        }
        check(task.connectChannelId.isNotEmpty()) {
            "Conversation task ${task.taskId} has no channel id"
        }
        ConnectNavHelper.unlockAndGoToMessaging(
            activity,
            UnlockPolicy.SESSION_WITH_TIME_THRESHOLD,
            task.connectChannelId,
            object : ConnectActivityCompleteListener {
                override fun connectActivityComplete(
                    success: Boolean,
                    error: String?,
                ) = Unit
            },
        )
    }
}
