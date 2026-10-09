package org.commcare.fragments.connectMessaging

import android.graphics.BitmapFactory
import android.text.format.Formatter
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectMessageAttachmentFileBinding
import org.commcare.dalvik.databinding.ViewConnectMessageAttachmentImageBinding
import org.commcare.dalvik.databinding.ViewConnectMessageAttachmentPendingBinding
import java.io.File

class ConnectMessageAttachmentsBinder(
    private val container: LinearLayout,
    private val listener: ConnectMessageAttachmentListener,
) {
    class Layout(
        val fillsBubbleWidth: Boolean,
        val contentWidth: Int,
    )

    private val context = container.context
    private val inflater = LayoutInflater.from(context)

    fun bindPendingMessage(
        messageId: String,
        pendingState: ConnectMessagingAttachmentState,
    ): Layout = bindTile(createPendingView(messageId, pendingState))

    fun bindUnsupportedMessage(): Layout {
        val binding = ViewConnectMessageAttachmentPendingBinding.inflate(inflater, container, false)
        binding.flAction.visibility = View.GONE
        binding.tvLabel.setText(R.string.connect_messaging_update_app_notice)
        return bindTile(binding.root)
    }

    private fun bindTile(tile: View): Layout {
        container.removeAllViews()
        container.visibility = View.VISIBLE
        container.addView(tile)
        return Layout(false, context.resources.getDimensionPixelSize(R.dimen.connect_message_pending_tile_width))
    }

    fun bind(
        attachments: List<ConnectMessageAttachmentItem>,
        maxMediaWidth: Int,
        maxMediaHeight: Int,
    ): Layout {
        container.removeAllViews()
        container.visibility = if (attachments.isEmpty()) View.GONE else View.VISIBLE
        var fillsBubbleWidth = false
        var contentWidth = 0
        attachments.forEachIndexed { index, attachment ->
            val view: View
            val width: Int
            val imageSize = attachment.file?.takeIf { attachment.isImage }?.let { imageSize(attachment.attachmentId, it) }
            when {
                imageSize != null -> {
                    val fitted =
                        ConnectMessageMediaSizer.fitImage(imageSize.width, imageSize.height, maxMediaWidth, maxMediaHeight)
                    view = createImageView(attachment, fitted)
                    width = fitted.width
                }

                attachment.file != null && attachment.isAudio -> {
                    view = ConnectMessageAudioAttachmentView(context).apply { bind(attachment) }
                    width = maxMediaWidth
                    fillsBubbleWidth = true
                }

                else -> {
                    view = createFileView(attachment)
                    width = maxMediaWidth
                }
            }
            contentWidth = maxOf(contentWidth, width)
            container.addView(view, layoutParamsFor(view, index))
        }
        return Layout(fillsBubbleWidth, contentWidth)
    }

    private fun layoutParamsFor(
        view: View,
        index: Int,
    ): LinearLayout.LayoutParams {
        val params =
            view.layoutParams?.let { LinearLayout.LayoutParams(it) }
                ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (view is ConnectMessageAudioAttachmentView) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        if (index > 0) {
            params.topMargin = context.resources.getDimensionPixelSize(R.dimen.connect_space_sm)
        }
        return params
    }

    private fun createImageView(
        attachment: ConnectMessageAttachmentItem,
        size: ConnectMessageMediaSizer.Size,
    ): View {
        val binding = ViewConnectMessageAttachmentImageBinding.inflate(inflater, container, false)
        val imageView = binding.root
        imageView.layoutParams = ViewGroup.LayoutParams(size.width, size.height)
        imageView.contentDescription = attachment.name
        Glide
            .with(imageView)
            .load(attachment.file)
            .override(size.width, size.height)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(imageView)
        return imageView
    }

    private fun createFileView(attachment: ConnectMessageAttachmentItem): View {
        val binding = ViewConnectMessageAttachmentFileBinding.inflate(inflater, container, false)
        binding.tvFileName.text = attachment.name
        binding.tvFileSize.text = Formatter.formatShortFileSize(context, attachment.sizeBytes)
        binding.root.setOnClickListener { listener.onAttachmentOpenRequested(attachment) }
        return binding.root
    }

    private fun createPendingView(
        messageId: String,
        pendingState: ConnectMessagingAttachmentState,
    ): View {
        val binding = ViewConnectMessageAttachmentPendingBinding.inflate(inflater, container, false)
        binding.tvLabel.setTextColor(ContextCompat.getColor(context, R.color.white))
        binding.tvSecondaryLabel.visibility = View.GONE
        binding.progress.visibility = View.GONE
        binding.ivAction.visibility = View.VISIBLE
        val canRequestDownload: Boolean
        when (pendingState) {
            ConnectMessagingAttachmentState.REQUESTED, ConnectMessagingAttachmentState.DOWNLOADING -> {
                binding.ivAction.visibility = View.GONE
                binding.progress.visibility = View.VISIBLE
                binding.tvLabel.setText(R.string.connect_messaging_attachment_downloading)
                canRequestDownload = false
            }

            ConnectMessagingAttachmentState.FAILED -> {
                binding.ivAction.setImageResource(R.drawable.ic_connect_message_retry)
                binding.tvLabel.setText(R.string.connect_messaging_attachment_download_failed)
                binding.tvLabel.setTextColor(ContextCompat.getColor(context, R.color.connect_red_light))
                binding.tvSecondaryLabel.visibility = View.VISIBLE
                canRequestDownload = true
            }

            ConnectMessagingAttachmentState.EXPIRED -> {
                binding.ivAction.visibility = View.GONE
                binding.tvLabel.setText(R.string.connect_messaging_attachment_expired)
                canRequestDownload = false
            }

            ConnectMessagingAttachmentState.WAITING,
            ConnectMessagingAttachmentState.QUEUED,
            ConnectMessagingAttachmentState.AVAILABLE,
            -> {
                binding.ivAction.setImageResource(R.drawable.ic_connect_message_download)
                binding.tvLabel.setText(R.string.connect_messaging_attachment_download)
                canRequestDownload = pendingState != ConnectMessagingAttachmentState.AVAILABLE
            }
        }
        if (canRequestDownload) {
            binding.root.setOnClickListener { listener.onMessageDownloadRequested(messageId) }
        }
        return binding.root
    }

    private fun imageSize(
        attachmentId: String,
        file: File,
    ): ConnectMessageMediaSizer.Size? {
        imageSizes.get(attachmentId)?.let { return it }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null
        }
        return ConnectMessageMediaSizer.Size(options.outWidth, options.outHeight).also { imageSizes.put(attachmentId, it) }
    }

    private companion object {
        const val IMAGE_SIZE_CACHE_ENTRIES = 200
        val imageSizes = LruCache<String, ConnectMessageMediaSizer.Size>(IMAGE_SIZE_CACHE_ENTRIES)
    }
}
