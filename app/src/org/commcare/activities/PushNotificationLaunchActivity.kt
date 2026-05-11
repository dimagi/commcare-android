package org.commcare.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import org.commcare.util.LogTypes
import org.javarosa.core.services.Logger

/**
 * Transparent, no-UI launch activity for push-notification PendingIntents.
 *
 * Decides at tap time whether to:
 *   - dispatch the wrapped Intent directly (no form in progress), or
 *   - re-enter [FormEntryActivity] in singleTop carrying the wrapped Intent so the
 *     user is prompted before navigation occurs (form in progress) — added in
 *     a follow-up task.
 */
class PushNotificationLaunchActivity : Activity() {
    companion object {
        const val EXTRA_WRAPPED_NAV_INTENT = "org.commcare.push.wrapped_nav_intent"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wrapped: Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_WRAPPED_NAV_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_WRAPPED_NAV_INTENT)
            }

        if (wrapped == null) {
            Logger.log(
                LogTypes.TYPE_FCM,
                "PushNotificationLaunchActivity received intent without wrapped nav intent",
            )
            finish()
            return
        }

        dispatchWithoutForm(wrapped)
        finish()
    }

    private fun dispatchWithoutForm(wrapped: Intent) {
        val toStart = Intent(wrapped)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(toStart)
        } catch (e: ActivityNotFoundException) {
            Logger.exception("Push notification target activity not found", e)
        }
    }
}
