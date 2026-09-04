package org.commcare.personalId.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.findNavController
import org.commcare.activities.CommCareActivity
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.base.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.personalId.PersonalIdApiHandler
import org.commcare.dalvik.R
import org.commcare.fragments.personalId.BasePersonalIdBackupCodeFragment
import org.commcare.personalId.PersonalIdUnlocker
import org.commcare.personalId.UnlockPolicy
import org.commcare.views.dialogs.StandardAlertDialog

class SetNewBackupCodeFragment : BasePersonalIdBackupCodeFragment() {
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        handleBackNavigation()
    }

    private fun handleBackNavigation() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    showAbandonDialog()
                }
            },
        )
    }

    private fun showAbandonDialog() {
        val dialog =
            StandardAlertDialog(
                getString(R.string.personalid_set_new_backup_code_abandon_title),
                getString(R.string.personalid_set_new_backup_code_abandon_message),
            )
        dialog.setPositiveButton(getString(R.string.personalid_set_new_backup_code_abandon_positive)) { d, _ ->
            d.dismiss()
        }
        dialog.setNegativeButton(getString(R.string.personalid_set_new_backup_code_abandon_negative)) { d, _ ->
            d.dismiss()
            findNavController().popBackStack()
        }
        dialog.makeCancelable()
        dialog.showNonPersistentDialog(requireActivity())
    }

    override fun onResume() {
        super.onResume()
        validateBackupCodeAndEnableContinue()
    }

    override fun setUpView() {
        setUpInitialState(
            titleResId = R.string.personalid_set_new_backup_code_title,
            showConfirmCode = true,
            subtitle = getString(R.string.connect_backup_code_remember, BACKUP_CODE_LENGTH),
        )
    }

    override fun handleBackupCodeSubmission() {
        val backupCode = binding.backupCodeView.codeValue
        if (!validateBackupCodeInput()) {
            return
        }

        PersonalIdUnlocker.unlock(
            requireActivity() as CommCareActivity<*>,
            UnlockPolicy.ALWAYS,
        ) { unlocked ->
            if (!unlocked) return@unlock
            enableContinueButton(false)
            callSetBackupCodeApi(backupCode)
        }
    }

    private fun callSetBackupCodeApi(backupCode: String) {
        val user = ConnectUserDatabaseUtil.getUser()!!

        object : PersonalIdApiHandler<Boolean>() {
            override fun onSuccess(data: Boolean) {
                onSetBackupCodeCallSuccess(backupCode, user)
            }

            override fun onFailure(
                errorCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                onSetBackupCodeCallFailure(errorCode, t)
            }
        }.setBackupCode(requireContext(), user.getUserId(), user.getPassword(), backupCode)
    }

    private fun onSetBackupCodeCallSuccess(
        backupCode: String,
        user: ConnectUserRecord,
    ) {
        user.pin = backupCode
        ConnectUserDatabaseUtil.storeUser(user)
        Toast
            .makeText(
                requireContext(),
                R.string.personalid_backup_code_changed_success,
                Toast.LENGTH_LONG,
            ).show()
        findNavController().popBackStack(R.id.personalid_profile_backup_code_fragment, true)
    }

    private fun onSetBackupCodeCallFailure(
        errorCode: PersonalIdOrConnectApiErrorCodes,
        t: Throwable?,
    ) {
        showError(
            PersonalIdOrConnectApiErrorHandler.handle(
                requireActivity(),
                errorCode,
                t,
            ),
        )
        enableContinueButton(true)
    }
}
