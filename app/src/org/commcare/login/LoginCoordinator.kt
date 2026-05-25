package org.commcare.login

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.commcare.activities.CommCareActivity

/**
 * Java-friendly bridge for invoking LoginController.performLogin from a CommCareActivity.
 * The activity supplies itself as both the LifecycleOwner (for coroutine scoping) and the
 * CommCareActivity (for the post-success side-effect chain).
 */
class LoginCoordinator(
    private val controller: LoginController,
) {
    fun interface ResultCallback {
        fun onResult(result: LoginResult)
    }

    fun <T> start(
        activity: T,
        request: LoginRequest,
        sink: LoginProgressSink,
        callback: ResultCallback,
    ): Job where T : CommCareActivity<*>, T : LifecycleOwner =
        activity.lifecycleScope.launch {
            val result = controller.performLogin(activity, request, sink)
            callback.onResult(result)
        }
}
