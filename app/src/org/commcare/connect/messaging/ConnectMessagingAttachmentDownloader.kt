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

    private var hasUnannouncedChanges = false

    fun runPass(
        isEligible: (ConnectMessagingAttachmentRecord) -> Boolean,
        isStopped: () -> Boolean,
    ): PassResult {
        try {
            failInterruptedDownloads()
            val attempted = HashSet<String>()
            var retryLater = false
            while (!isStopped()) {
                val next = nextPending(isEligible, attempted) ?: break
                attempted.add(next.attachment.attachmentId)
                when (download(next.attachment, next.message)) {
                    Outcome.RETRY_LATER -> retryLater = true
                    Outcome.AUTH_FAILURE -> return PassResult.STOPPED_BY_AUTH_FAILURE
                    Outcome.FINISHED -> Unit
                }
            }
            return if (retryLater) PassResult.RETRY_LATER else PassResult.COMPLETE
        } finally {
            announceChanges()
        }
    }

    private fun failInterruptedDownloads() {
        for (attachment in ConnectMessagingAttachmentDatabaseHelper.getAttachmentsInState(
            ConnectMessagingAttachmentState.DOWNLOADING,
        )) {
            update(attachment, ConnectMessagingAttachmentState.FAILED)
        }
    }

    private class PendingAttachment(
        val attachment: ConnectMessagingAttachmentRecord,
        val message: ConnectMessagingMessageRecord?,
    )

    private fun nextPending(
        isEligible: (ConnectMessagingAttachmentRecord) -> Boolean,
        attempted: Set<String>,
    ): PendingAttachment? =
        pendingAttachments()
            .filter { it.attachmentId !in attempted && isEligible(it) }
            .map { PendingAttachment(it, ConnectMessagingAttachmentDatabaseHelper.getMessage(it.messageId)) }
            .minWithOrNull(
                compareBy<PendingAttachment>(
                    { it.attachment.downloadState != ConnectMessagingAttachmentState.REQUESTED },
                    { it.message?.timeStamp ?: Date(0) },
                    { it.attachment.position },
                ),
            )

    private fun pendingAttachments(): List<ConnectMessagingAttachmentRecord> =
        ConnectMessagingAttachmentDatabaseHelper.getAttachmentsInState(ConnectMessagingAttachmentState.REQUESTED) +
            ConnectMessagingAttachmentDatabaseHelper.getAttachmentsInState(ConnectMessagingAttachmentState.QUEUED) +
            ConnectMessagingAttachmentDatabaseHelper
                .getAttachmentsInState(ConnectMessagingAttachmentState.FAILED)
                .filter { it.attempts < MAX_ATTEMPTS }

    private fun download(
        attachment: ConnectMessagingAttachmentRecord,
        message: ConnectMessagingMessageRecord?,
    ): Outcome {
        if (message == null) {
            attachment.attempts = MAX_ATTEMPTS
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
        announceChanges()
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
                update(attachment, ConnectMessagingAttachmentState.FAILED)
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
        update(attachment, ConnectMessagingAttachmentState.FAILED)
        if (attachment.attempts < MAX_ATTEMPTS) {
            return Outcome.RETRY_LATER
        }
        Logger.log(
            LogTypes.TYPE_WARNING_NETWORK,
            "Messaging attachment ${attachment.attachmentId} failed after $MAX_ATTEMPTS attempts: $reason",
        )
        return Outcome.FINISHED
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
        hasUnannouncedChanges = true
    }

    private fun announceChanges() {
        if (!hasUnannouncedChanges) {
            return
        }
        hasUnannouncedChanges = false
        LocalBroadcastManager
            .getInstance(context)
            .sendBroadcast(Intent(FirebaseMessagingUtil.MESSAGING_UPDATE_BROADCAST))
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        val passLock = Mutex()
    }
}
