package org.commcare.home

import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.core.content.IntentCompat
import org.commcare.activities.CreatePinActivity
import org.commcare.activities.LoginActivity
import org.commcare.activities.LoginMode
import org.commcare.activities.PinAuthenticationActivity
import org.commcare.preferences.DeveloperPreferences
import org.commcare.views.dialogs.DialogChoiceItem
import org.commcare.views.dialogs.PaneledChoiceDialog
import org.commcare.views.dialogs.StandardAlertDialog
import org.javarosa.core.services.locale.Localization

/**
 * Both ways into PIN creation, and their results: the offer after a primed or password login, and
 * the user's own request from the home actions.
 */
class PinDelegate(
    private val host: HomeActivityHost,
    private val session: SeatedAppSession,
) {
    fun canSetPin(): Boolean = host.areAppActionsAvailable() && DeveloperPreferences.shouldOfferPinForLogin()

    /** Set or change the PIN, once the user has re-entered their password. */
    fun setPin() =
        host.startActivityForResult(
            Intent(host.hostContext, PinAuthenticationActivity::class.java),
            HomeRequestCodes.AUTHENTICATION_FOR_PIN,
        )

    /** Launch step: offer PIN creation after a primed or password login. */
    fun checkLaunchConditions() {
        val intent = host.launchIntent ?: return
        val loginMode = IntentCompat.getSerializableExtra(intent, LoginActivity.LOGIN_MODE, LoginMode::class.java)
        if (loginMode == LoginMode.PRIMED) {
            launchPinCreateScreen(loginMode)
        } else if (loginMode == LoginMode.PASSWORD && DeveloperPreferences.shouldOfferPinForLogin()) {
            val userManuallyEnteredPasswordMode = intent.getBooleanExtra(LoginActivity.MANUAL_SWITCH_TO_PW_MODE, false)
            if (!session.hasDismissedPinCreation() || userManuallyEnteredPasswordMode) {
                showPinChoiceDialog(loginMode)
            }
        }
    }

    /**
     * Handle the results of the password check and the PIN creation screen.
     *
     * @return true when [requestCode] belonged to a PIN screen
     */
    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        intent: Intent?,
    ): Boolean {
        when (requestCode) {
            HomeRequestCodes.AUTHENTICATION_FOR_PIN -> {
                if (resultCode == RESULT_OK) {
                    launchPinCreateScreen(LoginMode.PASSWORD)
                }
            }

            HomeRequestCodes.CREATE_PIN -> {
                handleCreatePinResult(resultCode, intent)
            }

            else -> {
                return false
            }
        }
        return true
    }

    private fun showPinChoiceDialog(loginMode: LoginMode) {
        val promptMessage = Localization.get(if (session.hasPinSet()) "pin.dialog.prompt.reset" else "pin.dialog.prompt.set")
        val dialog = PaneledChoiceDialog(host.hostContext, promptMessage)
        val createPinChoice =
            DialogChoiceItem(Localization.get("pin.dialog.yes"), -1) {
                dialog.dismiss()
                launchPinCreateScreen(loginMode)
            }
        val nextTimeChoice = DialogChoiceItem(Localization.get("pin.dialog.not.now"), -1) { dialog.dismiss() }
        val notAgainChoice =
            DialogChoiceItem(Localization.get("pin.dialog.never"), -1) {
                dialog.dismiss()
                session.dismissPinCreation()
                showPinFutureAccessDialog()
            }
        dialog.setChoiceItems(arrayOf(createPinChoice, nextTimeChoice, notAgainChoice))
        dialog.addCollapsibleInfoPane(Localization.get("pin.dialog.extra.info"))
        host.showAlertDialog(dialog)
    }

    private fun showPinFutureAccessDialog() {
        StandardAlertDialog
            .getBasicAlertDialog(
                Localization.get("pin.dialog.set.later.title"),
                Localization.get("pin.dialog.set.later.message"),
                null,
            ).showNonPersistentDialog(host.hostContext)
    }

    private fun launchPinCreateScreen(loginMode: LoginMode) {
        val intent =
            Intent(host.hostContext, CreatePinActivity::class.java)
                .putExtra(LoginActivity.LOGIN_MODE, loginMode)
        host.startActivityForResult(intent, HomeRequestCodes.CREATE_PIN)
    }

    private fun handleCreatePinResult(
        resultCode: Int,
        intent: Intent?,
    ) {
        val choseRememberPassword = intent?.getBooleanExtra(CreatePinActivity.CHOSE_REMEMBER_PASSWORD, false) == true
        if (choseRememberPassword) {
            session.closeUserSession()
        } else {
            val messageKey = if (resultCode == RESULT_OK) "pin.set.success" else "pin.not.set"
            host.displayShortToast(Localization.get(messageKey))
        }
    }
}
