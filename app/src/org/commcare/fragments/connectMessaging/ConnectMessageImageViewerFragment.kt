package org.commcare.fragments.connectMessaging

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.transition.Fade
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.material.transition.MaterialContainerTransform
import org.commcare.connect.database.ConnectMessagingAttachmentDatabaseHelper
import org.commcare.connect.database.ConnectMessagingAttachmentFileStore
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentConnectMessageImageViewerBinding
import org.commcare.utils.MarkupUtil
import java.io.File
import kotlin.math.roundToInt

class ConnectMessageImageViewerFragment : Fragment() {
    private var binding: FragmentConnectMessageImageViewerBinding? = null
    private var title: String? = null
    private var hasCaption = false
    private var isImageSeated = false
    private var isZoomableImageReady = false
    private val returnListener =
        NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.id != R.id.connectMessageImageViewerFragment) {
                showTransitionImage()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition =
            containerTransform().addListener(
                object : TransitionListenerAdapter() {
                    override fun onTransitionEnd(transition: Transition) {
                        seatImage()
                    }
                },
            )
        returnTransition =
            Fade().apply {
                addTarget(R.id.vBackdrop)
                addTarget(R.id.tvCaption)
                duration = TRANSITION_DURATION_MS
            }
        sharedElementReturnTransition = containerTransform()
    }

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
        val viewerBinding = binding
        if (attachment == null || message == null || imageSize == null || viewerBinding == null) {
            findNavController().popBackStack()
            return
        }

        isImageSeated = false
        isZoomableImageReady = false
        viewerBinding.ivTransitionImage.transitionName = transitionNameFor(attachmentId)
        postponeEnterTransition()
        view.postDelayed({ startPostponedEnterTransition() }, MAX_TRANSITION_WAIT_MS)
        view.postDelayed({ seatImage() }, SEAT_FALLBACK_MS)
        findNavController().addOnDestinationChangedListener(returnListener)
        title =
            DateUtils.formatDateTime(
                requireContext(),
                message.timeStamp.time,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
            )
        bindCaption(message.displayText)
        bindImage(viewerBinding, file, imageSize, attachment.name)
    }

    override fun onResume() {
        super.onResume()
        title?.let { requireActivity().title = it }
    }

    override fun onDestroyView() {
        findNavController().removeOnDestinationChangedListener(returnListener)
        binding = null
        super.onDestroyView()
    }

    private fun bindCaption(text: String) {
        val caption = binding?.tvCaption ?: return
        hasCaption = text.isNotBlank()
        if (hasCaption) {
            MarkupUtil.setMarkdown(caption, SpannableStringBuilder(text), SpannableStringBuilder())
        }
    }

    private fun bindImage(
        viewerBinding: FragmentConnectMessageImageViewerBinding,
        file: File,
        imageSize: ConnectMessageMediaSizer.Size,
        name: String,
    ) {
        val zoomableImage = viewerBinding.ivImage
        zoomableImage.contentDescription = name
        zoomableImage.orientation = SubsamplingScaleImageView.ORIENTATION_USE_EXIF
        zoomableImage.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CUSTOM)
        zoomableImage.setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    isZoomableImageReady = true
                    showZoomableImageIfSeated()
                }
            },
        )
        zoomableImage.doOnLayout {
            val limits =
                ConnectMessageImageZoom.limitsFor(
                    imageSize.width,
                    imageSize.height,
                    it.width,
                    it.height,
                    resources.displayMetrics.density,
                )
            zoomableImage.minScale = limits.minScale
            zoomableImage.maxScale = limits.maxScale
            zoomableImage.setDoubleTapZoomScale(limits.doubleTapScale)
            zoomableImage.setImage(ImageSource.uri(Uri.fromFile(file)))
            bindTransitionImage(
                viewerBinding,
                file,
                (imageSize.width * limits.minScale).roundToInt(),
                (imageSize.height * limits.minScale).roundToInt(),
            )
        }
    }

    private fun bindTransitionImage(
        viewerBinding: FragmentConnectMessageImageViewerBinding,
        file: File,
        seatedWidth: Int,
        seatedHeight: Int,
    ) {
        val transitionImage = viewerBinding.ivTransitionImage
        transitionImage.layoutParams =
            transitionImage.layoutParams.apply {
                width = seatedWidth
                height = seatedHeight
            }
        Glide
            .with(transitionImage)
            .load(file)
            .override(seatedWidth, seatedHeight)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .dontAnimate()
            .listener(
                object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>?,
                        isFirstResource: Boolean,
                    ): Boolean {
                        startPostponedEnterTransition()
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: Target<Drawable>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean,
                    ): Boolean {
                        startPostponedEnterTransition()
                        return false
                    }
                },
            ).into(transitionImage)
    }

    private fun seatImage() {
        val viewerBinding = binding ?: return
        if (isImageSeated) {
            return
        }
        isImageSeated = true
        viewerBinding.tvCaption.visibility = if (hasCaption) View.VISIBLE else View.GONE
        showZoomableImageIfSeated()
    }

    private fun showZoomableImageIfSeated() {
        val viewerBinding = binding ?: return
        if (isImageSeated && isZoomableImageReady) {
            viewerBinding.ivImage.alpha = 1f
            viewerBinding.ivTransitionImage.visibility = View.INVISIBLE
        }
    }

    private fun showTransitionImage() {
        val viewerBinding = binding ?: return
        viewerBinding.ivTransitionImage.visibility = View.VISIBLE
        viewerBinding.ivImage.alpha = 0f
    }

    private fun containerTransform() =
        MaterialContainerTransform().apply {
            drawingViewId = R.id.nav_host_fragment_connect_messaging
            scrimColor = Color.TRANSPARENT
            duration = TRANSITION_DURATION_MS
        }

    companion object {
        private const val MAX_TRANSITION_WAIT_MS = 300L
        const val TRANSITION_DURATION_MS = 300L
        private const val SEAT_FALLBACK_MS = MAX_TRANSITION_WAIT_MS + TRANSITION_DURATION_MS + 400L

        @JvmStatic
        fun transitionNameFor(attachmentId: String) = "connect_message_image_$attachmentId"
    }
}
