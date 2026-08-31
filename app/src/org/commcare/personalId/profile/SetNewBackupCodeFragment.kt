package org.commcare.personalId.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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

class SetNewBackupCodeFragment : BasePersonalIdBackupCodeFragment() {

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
        val user = ConnectUserDatabaseUtil.getUser(requireContext())!!

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
        }.setRecoveryPin(requireContext(), user.getUserId(), user.getPassword(), backupCode)
    }

    private fun onSetBackupCodeCallSuccess(
        backupCode: String,
        user: ConnectUserRecord,
    ) {
        user.pin = backupCode
        ConnectUserDatabaseUtil.storeUser(requireContext(), user)
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
