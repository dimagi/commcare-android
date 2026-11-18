package org.commcare.fragments.connectMessaging

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState
import org.commcare.connect.database.ConnectMessagingAttachmentDatabaseHelper
import org.commcare.connect.database.ConnectMessagingAttachmentFileStore
import org.commcare.connect.messaging.ConnectMessagingMessagePackage
import org.commcare.dalvik.databinding.FragmentConnectMessageCardViewerBinding

/**
 * Shows a `card-html` message full screen, for a card cut off at its height cap in the chat
 * (rich-messaging spec 12, section 7). Same sandbox as inline, at the screen's size, scrolling
 * inside itself. Goes straight back if the card cannot be shown or stops.
 */
class ConnectMessageCardViewerFragment : Fragment() {
    private var binding: FragmentConnectMessageCardViewerBinding? = null
    private var title: String? = null

    private val cardListener =
        object : ConnectMessageCardView.Listener {
            override fun onCardStopped(messageId: String) {
                if (isAdded) {
                    findNavController().popBackStack()
                }
            }

            override fun onCardOpenRequested(messageId: String) = Unit
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val viewerBinding = FragmentConnectMessageCardViewerBinding.inflate(inflater, container, false)
        binding = viewerBinding
        return viewerBinding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val messageId = ConnectMessageCardViewerFragmentArgs.fromBundle(requireArguments()).messageId
        val message = ConnectMessagingAttachmentDatabaseHelper.getMessage(messageId)
        val records = ConnectMessagingAttachmentDatabaseHelper.getAttachmentsForMessage(messageId)
        val card = binding?.card
        if (card == null || message == null || !ConnectMessageCardSupport.isCard(message.format) ||
            ConnectMessagingMessagePackage.stateOfAttachments(records) != ConnectMessagingAttachmentState.AVAILABLE ||
            ConnectMessageCardSupport.unavailableNotice(messageId, records.map { it.name }, ConnectMessageCardSupport.isDeviceSupported()) != null
        ) {
            findNavController().popBackStack()
            return
        }
        val files = records.associate { it.name to ConnectMessagingAttachmentFileStore.fileFor(requireContext(), it.attachmentId) }
        if (!card.showFullScreen(messageId, files, cardListener)) {
            findNavController().popBackStack()
            return
        }
        title =
            DateUtils.formatDateTime(
                requireContext(),
                message.timeStamp.time,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
            )
    }

    override fun onResume() {
        super.onResume()
        title?.let { requireActivity().title = it }
        binding?.card?.setActive(true)
    }

    override fun onPause() {
        binding?.card?.setActive(false)
        super.onPause()
    }

    override fun onDestroyView() {
        binding?.card?.destroy()
        binding = null
        super.onDestroyView()
    }
}
