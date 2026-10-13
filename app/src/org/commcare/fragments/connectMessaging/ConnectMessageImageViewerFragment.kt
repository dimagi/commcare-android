package org.commcare.fragments.connectMessaging

import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import org.commcare.connect.database.ConnectMessagingAttachmentDatabaseHelper
import org.commcare.connect.database.ConnectMessagingAttachmentFileStore
import org.commcare.dalvik.databinding.FragmentConnectMessageImageViewerBinding
import org.commcare.utils.MarkupUtil
import java.io.File

class ConnectMessageImageViewerFragment : Fragment() {
    private var binding: FragmentConnectMessageImageViewerBinding? = null
    private var title: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val viewerBinding = FragmentConnectMessageImageViewerBinding.inflate(inflater, container, false)
        binding = viewerBinding
        return viewerBinding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val attachmentId = ConnectMessageImageViewerFragmentArgs.fromBundle(requireArguments()).attachmentId
        val attachment = ConnectMessagingAttachmentDatabaseHelper.getAttachment(attachmentId)
        val message = attachment?.let { ConnectMessagingAttachmentDatabaseHelper.getMessage(it.messageId) }
        val file = ConnectMessagingAttachmentFileStore.fileFor(requireContext(), attachmentId)
        val imageSize = if (file.isFile) ConnectMessageImageDimensions.read(file) else null
        if (attachment == null || message == null || imageSize == null) {
            findNavController().popBackStack()
            return
        }

        title =
            DateUtils.formatDateTime(
                requireContext(),
                message.timeStamp.time,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
            )
        val viewerBinding = binding ?: return
        bindCaption(message.displayText)
        bindImage(viewerBinding.ivImage, file, imageSize, attachment.name)
    }

    override fun onResume() {
        super.onResume()
        title?.let { requireActivity().title = it }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun bindCaption(text: String) {
        val caption = binding?.tvCaption ?: return
        if (text.isBlank()) {
            caption.visibility = View.GONE
            return
        }
        caption.visibility = View.VISIBLE
        MarkupUtil.setMarkdown(caption, SpannableStringBuilder(text), SpannableStringBuilder())
    }

    private fun bindImage(
        imageView: SubsamplingScaleImageView,
        file: File,
        imageSize: ConnectMessageMediaSizer.Size,
        name: String,
    ) {
        imageView.contentDescription = name
        imageView.orientation = SubsamplingScaleImageView.ORIENTATION_USE_EXIF
        imageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CUSTOM)
        imageView.doOnLayout {
            val limits =
                ConnectMessageImageZoom.limitsFor(
                    imageSize.width,
                    imageSize.height,
                    it.width,
                    it.height,
                    resources.displayMetrics.density,
                )
            imageView.minScale = limits.minScale
            imageView.maxScale = limits.maxScale
            imageView.setDoubleTapZoomScale(limits.doubleTapScale)
            imageView.setImage(ImageSource.uri(Uri.fromFile(file)))
        }
    }
}
