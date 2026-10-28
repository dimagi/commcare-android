package org.commcare.connect.messaging

import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState.AVAILABLE
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState.DOWNLOADING
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState.EXPIRED
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState.FAILED
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState.QUEUED
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState.REQUESTED
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState.WAITING
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectMessagingMessagePackageTest {
    @Test
    fun `message without attachments is complete`() {
        assertEquals(AVAILABLE, ConnectMessagingMessagePackage.stateOf(emptyList()))
    }

    @Test
    fun `message is complete only once every attachment is available`() {
        assertEquals(AVAILABLE, ConnectMessagingMessagePackage.stateOf(listOf(AVAILABLE, AVAILABLE)))
        assertEquals(QUEUED, ConnectMessagingMessagePackage.stateOf(listOf(AVAILABLE, QUEUED)))
    }

    @Test
    fun `any expired attachment means the message can no longer be shown`() {
        assertEquals(EXPIRED, ConnectMessagingMessagePackage.stateOf(listOf(AVAILABLE, DOWNLOADING, EXPIRED)))
    }

    @Test
    fun `a download in progress or about to start outranks a failed part`() {
        assertEquals(DOWNLOADING, ConnectMessagingMessagePackage.stateOf(listOf(FAILED, DOWNLOADING)))
        assertEquals(DOWNLOADING, ConnectMessagingMessagePackage.stateOf(listOf(FAILED, REQUESTED)))
    }

    @Test
    fun `a failed part with nothing in progress fails the message`() {
        assertEquals(FAILED, ConnectMessagingMessagePackage.stateOf(listOf(AVAILABLE, FAILED, WAITING)))
        assertEquals(FAILED, ConnectMessagingMessagePackage.stateOf(listOf(FAILED, QUEUED)))
    }

    @Test
    fun `parts not being downloaded leave the message ready to download`() {
        assertEquals(WAITING, ConnectMessagingMessagePackage.stateOf(listOf(AVAILABLE, WAITING)))
        assertEquals(QUEUED, ConnectMessagingMessagePackage.stateOf(listOf(WAITING, QUEUED)))
    }
}
