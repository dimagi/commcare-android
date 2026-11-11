package org.commcare.fragments.personalId

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.database.connect.models.PersonalIdSessionData

/**
 * Screen that sends an email OTP to the user during the configuration flow
 */
class PersonalIdConfigurationSendEmailOtpFragment : BasePersonalIdSendEmailOtpFragment() {
    private var personalIdSessionData: PersonalIdSessionData? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        personalIdSessionData =
            ViewModelProvider(requireActivity())
                .get(PersonalIdSessionDataViewModel::class.java)
                .personalIdSessionData
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun getSessionData(): PersonalIdSessionData? = personalIdSessionData

    override fun emailForApiCall(): String? = null

    override fun navigateToVerification() {
        val directions =
            PersonalIdConfigurationSendEmailOtpFragmentDirections
                .actionPersonalidSendEmailOtpToEmailVerification(email, workflow, emailOtpTracker.requestCount)
        binding.root.findNavController().navigate(directions)
    }
}
