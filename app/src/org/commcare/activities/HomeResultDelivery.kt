package org.commcare.activities

import android.app.Activity
import android.content.Intent

/**
 * How a home host hands its result back when it finishes.
 */
object HomeResultDelivery {
    /**
     * Deliver [result] to the screen beneath, or to a relaunched DispatchActivity when [activity] is the
     * bottom of the task or requests recovery measures and no caller is waiting for it, then finish.
     */
    @JvmStatic
    fun deliverResultAndFinish(
        activity: Activity,
        result: Intent,
    ) {
        val recoveryRequestUnheard =
            activity.callingActivity == null &&
                result.getBooleanExtra(DispatchActivity.EXECUTE_RECOVERY_MEASURES, false)

        if (activity.isTaskRoot || recoveryRequestUnheard) {
            activity.startActivity(
                DispatchActivity
                    .buildRestartIntent(activity)
                    .putExtra(LoginActivity.USER_TRIGGERED_LOGOUT, true)
                    .putExtras(result),
            )
        } else {
            activity.setResult(Activity.RESULT_OK, result)
        }
        activity.finish()
    }
}
