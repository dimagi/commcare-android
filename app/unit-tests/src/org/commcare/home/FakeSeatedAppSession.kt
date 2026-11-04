package org.commcare.home

/** Settable [SeatedAppSession] for delegate and coordinator unit tests. */
class FakeSeatedAppSession : SeatedAppSession {
    var inAppUpdateVisible: Boolean = true
    var hideInAppUpdateCount: Int = 0
    var pinCreationDismissed: Boolean = false
    var pinSet: Boolean = false
    var closeUserSessionCount: Int = 0

    override fun shouldShowInAppUpdate(): Boolean = inAppUpdateVisible

    override fun hideInAppUpdate() {
        hideInAppUpdateCount++
        inAppUpdateVisible = false
    }

    override fun hasDismissedPinCreation(): Boolean = pinCreationDismissed

    override fun dismissPinCreation() {
        pinCreationDismissed = true
    }

    override fun hasPinSet(): Boolean = pinSet

    override fun closeUserSession() {
        closeUserSessionCount++
    }
}
