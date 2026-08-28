package org.commcare.fragments.connect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import org.commcare.activities.CommCareActivity
import org.commcare.android.database.connect.models.ConnectTaskRecord
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectDateUtils
import org.commcare.connect.ConnectNavHelper
import org.commcare.connect.database.ConnectTaskUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentConnectDeliveryMoreBinding
import org.commcare.fragments.RefreshableTab
import org.commcare.personalId.UnlockPolicy
import org.commcare.views.connect.ConnectTaskCard
import org.commcare.views.connect.bindCertificate
import java.text.DateFormat

/**
 * More tab of a delivery opportunity: the tasks still outstanding on the opportunity, and a card for
 * revisiting the learn app and its certificate once learning is complete.
 *
 * A task opens where it is completed: a conversation task goes to Connect messaging, anything else to
 * the delivery app. The first task is highlighted as the tab's primary action.
 */
class ConnectDeliveryMoreFragment :
    ConnectJobFragment<FragmentConnectDeliveryMoreBinding>(),
    RefreshableTab {
    /** Survives the rebinds a sync triggers; the pager recreating the page collapses it again. */
    private var certificateExpanded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        binding.revisitLearningCertificateButton.setOnClickListener { toggleCertificate() }
        binding.revisitLearningViewButton.setOnClickListener { launchApp(isLearning = true) }
        updateView()
        return view
    }

    override fun updateView() {
        reloadActiveJob()
        bindTasks()
        bindRevisitLearning()
    }

    /**
     * Every outstanding task blocks delivery today and the API carries no blocking flag, so they all
     * render as mandatory and the optional group stays hidden until that flag exists.
     */
    private fun bindTasks() {
        val tasks = ConnectTaskUtils.getPendingTasksForJob(requireContext(), job.jobUUID)

        binding.deliveryTasksEmpty.isVisible = tasks.isEmpty()
        bindTaskGroup(binding.deliveryTasksMandatoryGroup, binding.deliveryTasksMandatoryList, tasks)
        binding.deliveryTasksOptionalGroup.isVisible = false
    }

    private fun bindTaskGroup(
        group: View,
        list: LinearLayout,
        tasks: List<ConnectTaskRecord>,
    ) {
        group.isVisible = tasks.isNotEmpty()
        list.removeAllViews()

        tasks.forEachIndexed { index, task ->
            list.addView(taskCard(task, highlighted = index == 0), rowParams(index))
        }
    }

    private fun taskCard(
        task: ConnectTaskRecord,
        highlighted: Boolean,
    ) = ConnectTaskCard(requireContext()).apply {
        bind(
            ConnectTaskCard.State(
                title = task.name,
                iconRes =
                    if (task.isOCSConversation) {
                        R.drawable.ic_chat_bubble_outline
                    } else {
                        R.drawable.ic_connect_learn_app
                    },
                expiryLabel =
                    task.dueDate?.let {
                        getString(
                            R.string.connect_task_expires_on,
                            ConnectDateUtils.formatDate(it, DateFormat.LONG),
                        )
                    },
                highlighted = highlighted,
                onClick = { openTask(task) },
            ),
        )
    }

    private fun rowParams(index: Int) =
        LinearLayout
            .LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (index > 0) topMargin = resources.getDimensionPixelSize(R.dimen.connect_space_md)
            }

    private fun openTask(task: ConnectTaskRecord) {
        if (task.isOCSConversation) {
            check(task.connectChannelId.isNotEmpty()) {
                "Conversation task ${task.taskId} has no channel id"
            }
            ConnectNavHelper.unlockAndGoToMessaging(
                requireActivity() as CommCareActivity<*>,
                UnlockPolicy.SESSION_WITH_TIME_THRESHOLD,
                task.connectChannelId,
                object : ConnectActivityCompleteListener {
                    override fun connectActivityComplete(
                        success: Boolean,
                        error: String?,
                    ) = Unit
                },
            )
        } else {
            launchApp(isLearning = false)
        }
    }

    /**
     * Learning is always complete by the delivery stage, so the card is always offered. Its date and
     * certificate wait on the learn records, which only reach a device that ran the learn sync.
     */
    private fun bindRevisitLearning() {
        val learnCompletionDate = job.learningCompletionDate
        val hasCompletionDate = learnCompletionDate != null
        binding.revisitLearningTitle.text = job.title

        if (hasCompletionDate) {
            binding.revisitLearningCompleted.isVisible = true
            binding.revisitLearningCertificateButton.isVisible = true
            binding.revisitLearningCertificate.root.isVisible = certificateExpanded
            binding.revisitLearningCompleted.text =
                getString(
                    R.string.connect_delivery_revisit_learning_completed,
                    ConnectDateUtils.formatDate(learnCompletionDate, DateFormat.LONG),
                )
            binding.revisitLearningCertificate.bindCertificate(
                job,
                ConnectUserDatabaseUtil.getUser(requireContext())?.name.orEmpty(),
                learnCompletionDate,
            )
        } else {
            binding.revisitLearningCompleted.isVisible = false
            binding.revisitLearningCertificateButton.isVisible = false
            binding.revisitLearningCertificate.root.isVisible = false
        }
    }

    private fun toggleCertificate() {
        certificateExpanded = !certificateExpanded
        binding.revisitLearningCertificate.root.isVisible = certificateExpanded
    }

    /** Launching is the host's job, so the install check and download screen stay in one place. */
    private fun launchApp(isLearning: Boolean) {
        (parentFragment as? ConnectDeliveryHomeFragment)?.launchApp(isLearning)
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentConnectDeliveryMoreBinding = FragmentConnectDeliveryMoreBinding.inflate(inflater, container, false)

    companion object {
        @JvmStatic
        fun newInstance() = ConnectDeliveryMoreFragment()
    }
}
