package org.commcare.fragments.connectMessaging

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException

object ConnectMessageAudioPlayer {
    fun interface Listener {
        fun onAudioPlaybackChanged()
    }

    private const val PROGRESS_INTERVAL_MS = 200L

    private var mediaPlayer: MediaPlayer? = null
    private var loadedAttachmentId: String? = null
    private val pendingPositionsMs = HashMap<String, Int>()
    private val durationsMs = HashMap<String, Int>()
    private val listeners = LinkedHashSet<Listener>()
    private val handler = Handler(Looper.getMainLooper())
    private val progressTick =
        object : Runnable {
            override fun run() {
                notifyListeners()
                scheduleProgressTick()
            }
        }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun isPlaying(attachmentId: String): Boolean = isLoaded(attachmentId) && mediaPlayer?.isPlaying == true

    fun hasStarted(attachmentId: String): Boolean = isLoaded(attachmentId) || (pendingPositionsMs[attachmentId] ?: 0) > 0

    fun positionMs(attachmentId: String): Int {
        val player = mediaPlayer
        return if (player != null && attachmentId == loadedAttachmentId) {
            player.currentPosition
        } else {
            pendingPositionsMs[attachmentId] ?: 0
        }
    }

    fun durationMs(
        attachmentId: String,
        file: File,
    ): Int = durationsMs.getOrPut(attachmentId) { readDurationMs(file) }

    @Throws(IOException::class)
    fun togglePlayback(
        attachmentId: String,
        file: File,
    ) {
        val player = mediaPlayer
        if (player != null && attachmentId == loadedAttachmentId) {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.start()
            }
            onPlaybackStateChanged()
            return
        }

        stop()
        val newPlayer = MediaPlayer()
        try {
            newPlayer.setDataSource(file.path)
            newPlayer.prepare()
        } catch (e: IOException) {
            newPlayer.release()
            throw e
        }
        newPlayer.setOnCompletionListener { finishPlayback(attachmentId) }
        durationsMs[attachmentId] = newPlayer.duration
        pendingPositionsMs.remove(attachmentId)?.let { newPlayer.seekTo(it) }
        newPlayer.start()
        mediaPlayer = newPlayer
        loadedAttachmentId = attachmentId
        onPlaybackStateChanged()
    }

    fun seekTo(
        attachmentId: String,
        positionMs: Int,
    ) {
        val player = mediaPlayer
        if (player != null && attachmentId == loadedAttachmentId) {
            player.seekTo(positionMs)
        } else {
            pendingPositionsMs[attachmentId] = positionMs
        }
        notifyListeners()
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
        loadedAttachmentId = null
        pendingPositionsMs.clear()
        onPlaybackStateChanged()
    }

    private fun isLoaded(attachmentId: String): Boolean = mediaPlayer != null && attachmentId == loadedAttachmentId

    private fun finishPlayback(attachmentId: String) {
        mediaPlayer?.release()
        mediaPlayer = null
        loadedAttachmentId = null
        pendingPositionsMs.remove(attachmentId)
        onPlaybackStateChanged()
    }

    private fun onPlaybackStateChanged() {
        handler.removeCallbacks(progressTick)
        notifyListeners()
        scheduleProgressTick()
    }

    private fun scheduleProgressTick() {
        if (mediaPlayer?.isPlaying == true) {
            handler.postDelayed(progressTick, PROGRESS_INTERVAL_MS)
        }
    }

    private fun notifyListeners() {
        for (listener in listeners.toList()) {
            listener.onAudioPlaybackChanged()
        }
    }

    private fun readDurationMs(file: File): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0
        } catch (e: RuntimeException) {
            0
        } finally {
            retriever.release()
        }
    }
}
