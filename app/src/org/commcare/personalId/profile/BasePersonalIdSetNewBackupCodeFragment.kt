package org.commcare.personalId.profile

import android.os.Bundle
import android.view.View
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
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdUnlocker
import org.commcare.personalId.UnlockPolicy
import org.commcare.views.dialogs.StandardAlertDialog

abstract class BasePersonalIdSetNewBackupCodeFragment : BasePersonalIdBackupCodeFragment() {
    /** The `workflow` analytics param, which differs per graph and per entry into this screen. */
    abstract fun analyticsWorkflow(): String

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

    protected open fun showAbandonDialog() {
        val dialog =
            StandardAlertDialog(
                getString(R.string.personalid_set_new_backup_code_abandon_title),
                getString(R.string.personalid_set_new_backup_code_abandon_message),
            )
        dialog.setPositiveButton(getString(R.string.personalid_set_new_backup_code_abandon_positive)) { d, _ ->
            reportAbandonPrompt(AnalyticsParamValue.USER_PROMPT_ACTION_CANCEL)
            d.dismiss()
        }
        dialog.setNegativeButton(getString(R.string.personalid_set_new_backup_code_abandon_negative)) { d, _ ->
            reportAbandonPrompt(AnalyticsParamValue.USER_PROMPT_ACTION_ACCEPT)
            d.dismiss()
            onAbandon()
        }
        dialog.makeCancelable()
        dialog.showNonPersistentDialog(requireActivity())
    }

    private fun reportAbandonPrompt(action: String) {
        FirebaseAnalyticsUtil.reportUserPromptEvent(
            AnalyticsParamValue.USER_PROMPT_TYPE_BACKUP_CODE,
            action,
            AnalyticsParamValue.USER_PROMPT_INFO_SET_NEW_BACKUP_CODE_ABANDON,
        )
    }

    protected open fun onAbandon() {
        findNavController().popBackStack()
        if (findNavController().currentDestination == null) {
            requireActivity().finish()
        }
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
        FirebaseAnalyticsUtil.reportPersonalIdAccountSecurityAction(
            analyticsWorkflow(),
            AnalyticsParamValue.ACCOUNT_SECURITY_EVENT_SET_NEW_CODE_SHOWN,
            null,
            null,
            null,
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
        reportSetNewCodeAttempt(AnalyticsParamValue.OTP_OUTCOME_SUCCESS, null)
        showSuccess()
    }

    abstract fun showSuccess()

    private fun onSetBackupCodeCallFailure(
        errorCode: PersonalIdOrConnectApiErrorCodes,
        t: Throwable?,
    ) {
        reportSetNewCodeAttempt(AnalyticsParamValue.OTP_OUTCOME_FAILURE, errorCode.name)
        showError(
            PersonalIdOrConnectApiErrorHandler.handle(
                requireActivity(),
                errorCode,
                t,
            ),
        )
        enableContinueButton(true)
    }

    private fun reportSetNewCodeAttempt(
        outcome: String,
        reason: String?,
    ) {
        FirebaseAnalyticsUtil.reportPersonalIdAccountSecurityAction(
            analyticsWorkflow(),
            AnalyticsParamValue.ACCOUNT_SECURITY_EVENT_SET_NEW_CODE_ATTEMPT,
            outcome,
            reason,
            null,
        )
    }
}
