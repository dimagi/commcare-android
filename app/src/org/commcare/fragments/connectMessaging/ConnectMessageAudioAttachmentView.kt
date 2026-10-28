package org.commcare.fragments.connectMessaging

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.slider.Slider
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectMessageAttachmentAudioBinding
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class ConnectMessageAudioAttachmentView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : LinearLayout(context, attrs),
        ConnectMessageAudioPlayer.Listener {
        private val binding = ViewConnectMessageAttachmentAudioBinding.inflate(LayoutInflater.from(context), this)
        private var attachmentId: String? = null
        private var file: File? = null
        private var isDragging = false

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val inset = resources.getDimensionPixelSize(R.dimen.connect_message_audio_inset)
            setPadding(inset, inset, inset, inset)
            binding.btnPlay.setOnClickListener { togglePlayback() }
            binding.slider.addOnChangeListener { _, value, fromUser ->
                val id = attachmentId
                if (fromUser && id != null) {
                    ConnectMessageAudioPlayer.seekTo(id, value.toInt())
                }
            }
            binding.slider.addOnSliderTouchListener(
                object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) {
                        isDragging = true
                    }

                    override fun onStopTrackingTouch(slider: Slider) {
                        isDragging = false
                        refresh()
                    }
                },
            )
        }

        fun bind(attachment: ConnectMessageAttachmentItem) {
            attachmentId = attachment.attachmentId
            file = attachment.file
            refresh()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            ConnectMessageAudioPlayer.addListener(this)
            refresh()
        }

        override fun onDetachedFromWindow() {
            ConnectMessageAudioPlayer.removeListener(this)
            super.onDetachedFromWindow()
        }

        override fun onAudioPlaybackChanged() {
            refresh()
        }

        private fun togglePlayback() {
            val id = attachmentId ?: return
            val audioFile = file ?: return
            try {
                ConnectMessageAudioPlayer.togglePlayback(id, audioFile)
            } catch (e: IOException) {
                Toast.makeText(context, R.string.connect_messaging_audio_play_failed, Toast.LENGTH_SHORT).show()
            }
        }

        private fun refresh() {
            val id = attachmentId ?: return
            val audioFile = file ?: return
            val playing = ConnectMessageAudioPlayer.isPlaying(id)
            binding.ivPlayIcon.setImageResource(
                if (playing) R.drawable.ic_connect_message_pause else R.drawable.ic_connect_message_play,
            )
            binding.btnPlay.contentDescription =
                context.getString(
                    if (playing) R.string.connect_messaging_audio_pause else R.string.connect_messaging_audio_play,
                )

            val durationMs = ConnectMessageAudioPlayer.durationMs(id, audioFile)
            val positionMs = ConnectMessageAudioPlayer.positionMs(id).coerceIn(0, maxOf(durationMs, 0))
            binding.slider.isEnabled = durationMs > 0
            if (!isDragging) {
                binding.slider.valueTo = maxOf(durationMs, 1).toFloat()
                binding.slider.value = positionMs.toFloat().coerceAtMost(binding.slider.valueTo)
            }
            val shownMs = if (ConnectMessageAudioPlayer.hasStarted(id)) positionMs else durationMs
            binding.tvTime.text = formatTime(shownMs)
        }

        private fun formatTime(milliseconds: Int): String {
            val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.toLong())
            return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
        }
    }
