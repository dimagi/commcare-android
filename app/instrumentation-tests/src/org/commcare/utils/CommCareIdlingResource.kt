package org.commcare.utils

import android.os.Handler
import android.os.Looper
import androidx.test.espresso.IdlingResource
import androidx.test.platform.app.InstrumentationRegistry
import org.commcare.CommCareInstrumentationTestApplication
import org.commcare.activities.CommCareActivity

/**
 * An implementation of idling resource useful for monitoring idleness of progress dialogs.
 *
 * It has an additional idleness constraint that the progress dialog must not be shown for a set
 * period of time before we declare the resource idle.
 *
 * Useful for cases where we make multiple network calls in succession where each response triggers
 * another request until loading is complete.
 * So the app will be like Not-Idle(Progress bar showing) -> Idle(Progress bar closed) -> Not-Idle -> Idle...
 * And we don't want to report idle when this happens.
 */
class CommCareIdlingResource: IdlingResource {

    companion object {
        const val timeoutMs = 1000L
    }

    lateinit var resourceCallback: IdlingResource.ResourceCallback
    var isIdle = false
    val handler = Handler(Looper.getMainLooper())

    override fun getName(): String {
        return CommCareIdlingResource::class.java.name
    }

    override fun isIdleNow(): Boolean {
        val activity = getCommCareActivity() ?: return true
        if (!(activity.currentProgressDialog != null && activity.currentProgressDialog.isAdded)) {
            handler.postDelayed({
                isIdle = true
                resourceCallback.onTransitionToIdle()
            }, timeoutMs)
        }
        return isIdle
    }

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) {
        this.resourceCallback = callback
    }

    private fun getCommCareActivity(): CommCareActivity<*>? {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as CommCareInstrumentationTestApplication
        return application.currentActivity as? CommCareActivity<*>
    }
}