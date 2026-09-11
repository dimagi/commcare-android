package org.commcare.fragments.personalId

import androidx.navigation.findNavController
import org.commcare.activities.CommCareActivity
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.views.dialogs.StandardAlertDialog

/**
 * Email verification fragment for the profile flow. This is used when an existing user adds an email to their profile.
 */
class PersonalIdProfileEmailVerificationFragment : BasePersonalIdEmailVerificationFragment() {

    private fun args() = PersonalIdProfileEmailVerificationFragmentArgs.fromBundle(requireArguments())

    override fun resolveEmail(): String = args().email

    override fun displayEmail(): String = args().email

    override fun resolveWorkflow(): EmailWorkFlow = EmailWorkFlow.EXISTING_USER

    override fun resolveEmailOtpRequestCount(): Int = args().emailOtpRequestCount

    override fun onEmailVerified() {
        val user = ConnectUserDatabaseUtil.getUser()
        user.email = enteredEmail
        ConnectUserDatabaseUtil.storeUser(user)
        showEmailAddedSuccessDialog()
    }

    private fun showEmailAddedSuccessDialog() {
        val commCareActivity = requireActivity() as CommCareActivity<*>
        val dialog =
            StandardAlertDialog(
                getString(R.string.personalid_email_added_title),
                getString(R.string.personalid_email_added_message),
            )
        dialog.setPositiveButton(getString(R.string.ok)) { _, _ ->
            commCareActivity.dismissAlertDialog()
            val returnedToProfile =
                binding.root.findNavController().popBackStack(R.id.personalid_profile_fragment, false)
            if (!returnedToProfile) {
                requireActivity().finish()
            }
        }
        commCareActivity.showAlertDialog(dialog)
    }
}
