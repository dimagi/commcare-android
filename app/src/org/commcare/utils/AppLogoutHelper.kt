package org.commcare.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import org.commcare.CommCareApplication
import org.commcare.activities.CommCareActivity
import org.commcare.activities.DispatchActivity
import org.commcare.activities.LoginActivity
import org.commcare.dalvik.R
import org.commcare.services.CommCareSessionService
import org.commcare.views.dialogs.StandardAlertDialog
import org.javarosa.core.services.locale.Localization

object AppLogoutHelper {
    @JvmStatic
    fun promptToReturnToLogin(
        activity: CommCareActivity<*>,
        @StringRes titleRes: Int,
        @StringRes messageRes: Int,
    ) {
        if (!CommCareApplication.isSessionActive()) {
            returnToLogin(activity)
            return
        }

        val dialog = StandardAlertDialog(activity.getString(titleRes), activity.getString(messageRes))
        dialog.setPositiveButton(activity.getString(R.string.nav_drawer_switch_app_dialog_confirm)) { _, _ ->
            activity.dismissAlertDialog()
            if (!isBlockedByActiveSync(activity)) {
                CommCareApplication.instance().closeUserSession()
                returnToLogin(activity)
            }
        }
        dialog.setNegativeButton(activity.getString(R.string.nav_drawer_switch_app_dialog_cancel)) { _, _ ->
            activity.dismissAlertDialog()
        }
        activity.showAlertDialog(dialog)
    }

    @JvmStatic
    fun isBlockedByActiveSync(context: Context): Boolean {
        if (CommCareSessionService.sessionAliveLock.isLocked) {
            Toast
                .makeText(
                    context,
                    Localization.get("background.sync.logout.attempt.during.sync"),
                    Toast.LENGTH_LONG,
                ).show()
            return true
        }
        return false
    }

    private fun returnToLogin(activity: Activity) {
        val intent =
            Intent(activity, DispatchActivity::class.java)
                .putExtra(LoginActivity.USER_TRIGGERED_LOGOUT, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finish()
    }
}
