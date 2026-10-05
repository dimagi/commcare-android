package org.commcare.connect.messaging

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.sync.Mutex
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentRecord
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.commcare.connect.database.ConnectMessagingAttachmentDatabaseHelper
import org.commcare.connect.database.ConnectMessagingAttachmentFileStore
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.preferences.ConnectMessagingPreferences
import org.commcare.util.LogTypes
import org.commcare.utils.FirebaseMessagingUtil
import org.javarosa.core.services.Logger
import java.io.IOException
import java.net.HttpURLConnection
import java.security.GeneralSecurityException
import java.util.Date

class ConnectMessagingAttachmentDownloader(
    private val context: Context,
    private val fetcher: ConnectMessagingAttachmentFetcher,
    private val isAutomaticDownloadEnabled: () -> Boolean = {
        ConnectMessagingPreferences.isAutomaticDownloadEnabled(context)
    },
    private val clock: () -> Date = { Date() },
) {
    enum class PassResult {
        COMPLETE,
        RETRY_LATER,
        STOPPED_BY_AUTH_FAILURE,
    }

    private enum class Outcome {
        FINISHED,
        RETRY_LATER,
        AUTH_FAILURE,
    }

    private var pendingStateFor: (ConnectMessagingAttachmentRecord) -> ConnectMessagingAttachmentState =
        { ConnectMessagingAttachmentState.QUEUED }

    fun runPass(
        isEligible: (ConnectMessagingAttachmentRecord) -> Boolean,
        isRequestedByUser: Boolean,
        isStopped: () -> Boolean,
    ): PassResult {
        pendingStateFor = { attachment: ConnectMessagingAttachmentRecord ->
            if ((isRequestedByUser && isEligible(attachment)) || isAutomaticDownloadEnabled()) {
                ConnectMessagingAttachmentState.QUEUED
            } else {
                ConnectMessagingAttachmentState.WAITING
            }
        }
        resetInterruptedDownloads()
        val attempted = HashSet<String>()
        var retryLater = false
        while (!isStopped()) {
            val next = nextQueued(isEligible, attempted) ?: break
            attempted.add(next.attachment.attachmentId)
            when (download(next.attachment, next.message)) {
                Outcome.RETRY_LATER -> retryLater = true
                Outcome.AUTH_FAILURE -> return PassResult.STOPPED_BY_AUTH_FAILURE
                Outcome.FINISHED -> Unit
            }
        }
        return if (retryLater) PassResult.RETRY_LATER else PassResult.COMPLETE
    }

    private fun resetInterruptedDownloads() {
        for (attachment in ConnectMessagingAttachmentDatabaseHelper.getAttachmentsInState(
            ConnectMessagingAttachmentState.DOWNLOADING,
        )) {
            attachment.downloadState = pendingStateFor(attachment)
            ConnectMessagingAttachmentDatabaseHelper.save(attachment)
        }
    }

    private class QueuedAttachment(
        val attachment: ConnectMessagingAttachmentRecord,
        val message: ConnectMessagingMessageRecord?,
    )

    private fun nextQueued(
        isEligible: (ConnectMessagingAttachmentRecord) -> Boolean,
        attempted: Set<String>,
    ): QueuedAttachment? =
        ConnectMessagingAttachmentDatabaseHelper
            .getAttachmentsInState(ConnectMessagingAttachmentState.QUEUED)
            .filter { it.attachmentId !in attempted && isEligible(it) }
            .map { QueuedAttachment(it, ConnectMessagingAttachmentDatabaseHelper.getMessage(it.messageId)) }
            .minWithOrNull(compareBy<QueuedAttachment>({ it.message?.timeStamp ?: Date(0) }, { it.attachment.position }))

    private fun download(
        attachment: ConnectMessagingAttachmentRecord,
        message: ConnectMessagingMessageRecord?,
    ): Outcome {
        if (message == null) {
            return finish(attachment, ConnectMessagingAttachmentState.FAILED)
        }
        val expiresAt = message.expiresAt
        if (expiresAt != null && !expiresAt.after(clock())) {
            return finish(attachment, ConnectMessagingAttachmentState.EXPIRED)
        }
        val channelKey = ConnectMessagingDatabaseHelper.getMessagingChannel(message.channelId)?.key.orEmpty()
        if (channelKey.isEmpty()) {
            return countFailedAttempt(attachment, "no key for channel ${message.channelId}")
        }

        update(attachment, ConnectMessagingAttachmentState.DOWNLOADING)
        val response =
            try {
                fetcher.fetch(message.messageId, attachment.attachmentId, attachment.size)
            } catch (e: IOException) {
                return countFailedAttempt(attachment, "network error: ${e.message}")
            }

        return when (response.statusCode) {
            HttpURLConnection.HTTP_OK -> store(attachment, response.body, channelKey)
            HttpURLConnection.HTTP_GONE -> finish(attachment, ConnectMessagingAttachmentState.EXPIRED)
            HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> {
                update(attachment, pendingStateFor(attachment))
                Outcome.AUTH_FAILURE
            }
            else -> countFailedAttempt(attachment, "HTTP ${response.statusCode}")
        }
    }

    private fun store(
        attachment: ConnectMessagingAttachmentRecord,
        encrypted: ByteArray?,
        channelKey: String,
    ): Outcome {
        if (encrypted == null || encrypted.size.toLong() != attachment.size) {
            return countFailedAttempt(attachment, "expected ${attachment.size} bytes, received ${encrypted?.size}")
        }
        return try {
            val decrypted = ConnectMessagingAttachmentCrypto.decrypt(encrypted, channelKey)
            ConnectMessagingAttachmentFileStore.write(context, attachment.attachmentId, decrypted)
            attachment.attempts = 0
            finish(attachment, ConnectMessagingAttachmentState.AVAILABLE)
        } catch (e: GeneralSecurityException) {
            countFailedAttempt(attachment, "decryption failed: $e")
        } catch (e: IOException) {
            countFailedAttempt(attachment, "could not save: ${e.message}")
        }
    }

    private fun countFailedAttempt(
        attachment: ConnectMessagingAttachmentRecord,
        reason: String,
    ): Outcome {
        attachment.attempts += 1
        if (attachment.attempts < MAX_ATTEMPTS) {
            val pendingState = pendingStateFor(attachment)
            update(attachment, pendingState)
            return if (pendingState == ConnectMessagingAttachmentState.QUEUED) Outcome.RETRY_LATER else Outcome.FINISHED
        }
        Logger.log(
            LogTypes.TYPE_WARNING_NETWORK,
            "Messaging attachment ${attachment.attachmentId} failed after $MAX_ATTEMPTS attempts: $reason",
        )
        return finish(attachment, ConnectMessagingAttachmentState.FAILED)
    }

    private fun finish(
        attachment: ConnectMessagingAttachmentRecord,
        state: ConnectMessagingAttachmentState,
    ): Outcome {
        update(attachment, state)
        return Outcome.FINISHED
    }

    private fun update(
        attachment: ConnectMessagingAttachmentRecord,
        state: ConnectMessagingAttachmentState,
    ) {
        attachment.downloadState = state
        ConnectMessagingAttachmentDatabaseHelper.save(attachment)
        LocalBroadcastManager
            .getInstance(context)
            .sendBroadcast(Intent(FirebaseMessagingUtil.MESSAGING_UPDATE_BROADCAST))
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        val passLock = Mutex()
    }
}
