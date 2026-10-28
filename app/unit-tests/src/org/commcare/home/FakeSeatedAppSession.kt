package org.commcare.home

/** Settable [SeatedAppSession] for delegate and coordinator unit tests. */
class FakeSeatedAppSession : SeatedAppSession {
    var inAppUpdateVisible: Boolean = true
    var hideInAppUpdateCount: Int = 0

    override fun shouldShowInAppUpdate(): Boolean = inAppUpdateVisible

    override fun hideInAppUpdate() {
        hideInAppUpdateCount++
        inAppUpdateVisible = false
    }
}
