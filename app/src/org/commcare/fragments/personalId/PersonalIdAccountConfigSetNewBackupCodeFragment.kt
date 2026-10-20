package org.commcare.fragments.personalId

import org.commcare.connect.ConnectConstants
import org.commcare.dalvik.R
import org.commcare.personalId.profile.BasePersonalIdSetNewBackupCodeFragment
import org.commcare.views.dialogs.StandardAlertDialog

class PersonalIdAccountConfigSetNewBackupCodeFragment : BasePersonalIdSetNewBackupCodeFragment() {
    override fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int,
    ) {
        navigate(
            PersonalIdAccountConfigSetNewBackupCodeFragmentDirections
                .actionSetNewBackupCodeToPersonalidMessage(
                    title,
                    message.orEmpty(),
                    phase,
                    getString(buttonText),
                    null,
                ).setIsCancellable(isCancellable),
        )
    }

    override fun showSuccess() {
        navigateToMessageDisplay(
            getString(R.string.connect_recovery_success_title),
            getString(R.string.connect_recovery_success_message),
            false,
            ConnectConstants.PERSONALID_RECOVERY_SUCCESS,
            R.string.ok,
        )
    }

    override fun showAbandonDialog() {
        val dialog =
            StandardAlertDialog(
                getString(R.string.personalid_configuration_set_new_backup_code_abandon_title),
                getString(R.string.personalid_configuration_set_new_backup_code_abandon_message),
            )
        dialog.setPositiveButton(getString(R.string.ok)) { d, _ ->
            d.dismiss()
        }
        dialog.makeCancelable()
        dialog.showNonPersistentDialog(requireActivity())
    }
}
