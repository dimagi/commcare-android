package org.commcare.personalId.profile

import android.widget.Toast
import androidx.navigation.fragment.findNavController
import org.commcare.dalvik.R

class PersonalIdProfileSetNewBackupCodeFragment : BasePersonalIdSetNewBackupCodeFragment() {
    override fun showSuccess() {
        Toast
            .makeText(
                requireContext(),
                R.string.personalid_backup_code_changed_success,
                Toast.LENGTH_LONG,
            ).show()
        moveToProfileOrFinish()
    }

    private fun moveToProfileOrFinish() {
        if (!findNavController().popBackStack(R.id.personalid_profile_fragment, false)) {
            requireActivity().finish()
        }
    }

    override fun onAbandon() {
        moveToProfileOrFinish()
    }
}
